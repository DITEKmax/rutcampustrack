package ru.rutcampustrack.attendance.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentLessonsResponse.ApprovedTicket;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Ticket decisions persist independently of the current nullable attendance mark. */
@Service
@RequiredArgsConstructor
public class HeadmanApprovedTicketResolver {
    private final ExcuseRepository excuses;
    private final LateCheckinRepository lateCheckins;

    public record Cell(long lessonId, long studentId) { }
    private record Decision(Instant at, String id, String kind) { }

    public Map<Cell, ApprovedTicket> resolve(long groupId, Long semesterId, Set<Long> students, List<Long> lessons) {
        if (semesterId == null || students.isEmpty() || lessons.isEmpty()) return Map.of();
        var late = lateCheckins.findByGroupIdAndSemesterIdAndStatusIn(groupId, semesterId, List.of(LateCheckinRequestStatus.APPROVED));
        var excuse = excuses.findByLessonIdsInAndStatusIn(lessons, List.of(ExcuseTicketStatus.APPROVED));
        if (late == null || excuse == null) throw new ReportExportUnavailableException("Approved ticket source unavailable");
        Map<Cell, Decision> decisions = new HashMap<>();
        java.util.function.BiConsumer<Cell, Decision> retain = (cell, candidate) -> decisions.merge(cell, candidate,
                (left, right) -> Comparator.comparing(Decision::at, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Decision::id).compare(left, right) >= 0 ? left : right);
        for (var ticket : late) {
            if (ticket.getStatus() == LateCheckinRequestStatus.APPROVED && Objects.equals(ticket.getGroupId(), groupId)
                    && Objects.equals(ticket.getSemesterId(), semesterId) && students.contains(ticket.getStudentId())
                    && lessons.contains(ticket.getLessonId()) && ticket.getId() != null && !ticket.getId().isBlank()) {
                retain.accept(new Cell(ticket.getLessonId(), ticket.getStudentId()), new Decision(ticket.getDecisionAt(), ticket.getId(), "LATE_CHECKIN"));
            }
        }
        for (var ticket : excuse) {
            if (ticket.getStatus() == ExcuseTicketStatus.APPROVED && Objects.equals(ticket.getGroupId(), groupId)
                    && Objects.equals(ticket.getSemesterId(), semesterId) && students.contains(ticket.getStudentId())
                    && ticket.getLessonIds() != null && ticket.getId() != null && !ticket.getId().isBlank()) {
                ticket.getLessonIds().stream().filter(lessons::contains).forEach(id -> retain.accept(new Cell(id, ticket.getStudentId()),
                        new Decision(ticket.getDecisionAt(), ticket.getId(), "EXCUSE")));
            }
        }
        Map<Cell, ApprovedTicket> result = new HashMap<>();
        decisions.forEach((cell, value) -> result.put(cell, new ApprovedTicket(value.kind(), value.id(), "APPROVED")));
        return Map.copyOf(result);
    }
}
