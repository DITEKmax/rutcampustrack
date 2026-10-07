package ru.rutcampustrack.attendance.studentrequest;

import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.studentrequest.entity.ExcuseDecisionSelectionDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One canonical preview/command/receipt identity; attendance marks are deliberately not CAS input. */
final class ExcuseDecisionSelections {
    private ExcuseDecisionSelections() { }

    record Intent(String expectedRevision, List<String> excludedOccurrenceIds, String requestKey) {
        static Intent parse(String revision, List<String> excluded, String key, boolean approved) {
            if (revision == null && excluded == null && key == null) return null;
            if (revision == null || !revision.matches("[0-9a-f]{64}") || excluded == null || key == null) {
                throw new BadRequestException("expectedRevision, excludedOccurrenceIds и requestKey обязательны вместе");
            }
            try {
                if (!UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                throw new BadRequestException("requestKey должен быть canonical UUID");
            }
            for (String id : excluded) {
                try {
                    if (id == null || !id.matches("[1-9][0-9]*") || Long.parseLong(id) <= 0) throw new IllegalArgumentException();
                } catch (IllegalArgumentException invalid) {
                    throw new BadRequestException("Некорректный occurrenceId");
                }
            }
            if (new HashSet<>(excluded).size() != excluded.size()) throw new BadRequestException("Исключения должны быть уникальны");
            if (!approved && !excluded.isEmpty()) throw new BadRequestException("При отклонении нельзя исключать пары");
            return new Intent(revision, excluded.stream().sorted().toList(), key);
        }
    }

    static boolean open(ExcuseTicket ticket) {
        return ticket.getStatus() != ExcuseTicketStatus.APPROVED && ticket.getStatus() != ExcuseTicketStatus.REJECTED
                && ticket.getStatus() != ExcuseTicketStatus.CANCELLED;
    }

    static String revision(ExcuseTicket ticket, Map<Long, LessonResponse> lessons) {
        if (!open(ticket) || ticket.getLessonIds() == null || ticket.getLessonIds().isEmpty()
                || ticket.getLessonIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ticket.getLessonIds()).size() != ticket.getLessonIds().size()) return null;
        StringBuilder data = new StringBuilder("excuse-selection-v1\n").append(ticket.getId()).append('\n')
                .append(ticket.getStudentId()).append(':').append(ticket.getGroupId()).append(':')
                .append(ticket.getSemesterId()).append(':').append(ticket.getStatus()).append('\n');
        HashSet<Long> occurrences = new HashSet<>();
        for (Long id : ticket.getLessonIds().stream().distinct().sorted().toList()) {
            LessonResponse lesson = lessons.get(id);
            if (lesson == null || lesson.getId() != id || lesson.getGroupId() != ticket.getGroupId()
                    || lesson.getSemesterId() != ticket.getSemesterId() || lesson.getOccurrenceId() <= 0
                    || lesson.getOccurrenceRevision() <= 0 || lesson.getRevision() <= 0 || lesson.getGeneration() <= 0
                    || !lesson.getCurrent()
                    || (!lesson.getTransferState().isBlank() && !"COMPLETED".equals(lesson.getTransferState()))
                    || !Set.of("planned", "active", "closed").contains(lesson.getStatus().toLowerCase(Locale.ROOT))
                    || !occurrences.add(lesson.getOccurrenceId())) return null;
            data.append(id).append(':').append(lesson.getRevision()).append(':').append(lesson.getGeneration())
                    .append(':').append(lesson.getOccurrenceId()).append(':').append(lesson.getOccurrenceRevision()).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static List<Long> included(ExcuseTicket ticket, Map<Long, LessonResponse> lessons, Intent intent) {
        String current = revision(ticket, lessons);
        if (current == null || !Objects.equals(current, intent.expectedRevision())) {
            throw new ConflictException("Состав или состояние пар изменились; обнови заявку");
        }
        HashSet<String> currentOccurrences = new HashSet<>();
        lessons.values().forEach(lesson -> currentOccurrences.add(Long.toString(lesson.getOccurrenceId())));
        if (!currentOccurrences.containsAll(intent.excludedOccurrenceIds())) throw new BadRequestException("Исключение не принадлежит заявке");
        List<Long> included = ticket.getLessonIds().stream().distinct().sorted()
                .filter(id -> !intent.excludedOccurrenceIds().contains(Long.toString(lessons.get(id).getOccurrenceId()))).toList();
        if (included.isEmpty()) throw new BadRequestException("Нужно оставить хотя бы одну пару");
        return included;
    }

    static boolean matches(ExcuseDecisionSelectionDocument saved, Intent intent) {
        return saved != null && intent != null && Objects.equals(saved.getRequestKey(), intent.requestKey())
                && Objects.equals(saved.getExpectedRevision(), intent.expectedRevision())
                && saved.getExcludedLessons().stream().map(StudentLessonSnapshotDocument::getOccurrenceId).sorted().toList()
                .equals(intent.excludedOccurrenceIds());
    }
}
