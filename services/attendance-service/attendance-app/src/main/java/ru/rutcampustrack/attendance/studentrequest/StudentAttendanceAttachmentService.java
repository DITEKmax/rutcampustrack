package ru.rutcampustrack.attendance.studentrequest;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.attendance.audit.LessonActionDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;

/** Read-only causal binding shared by the projection and the lesson-bound download. */
@Service
public class StudentAttendanceAttachmentService {
    private final MongoTemplate mongo;
    private final Clock clock;

    public StudentAttendanceAttachmentService(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    public record DocumentRef(String id, String name, String contentType, long size,
            String state, Instant expiresAt, String markRevision, long lessonId) {}

    /** Three bounded batch reads; metadata queries never materialize binary attachment data. */
    public Map<Long, List<DocumentRef>> documents(long semesterId, long userId,
            Collection<AttendanceRecord> records) {
        List<AttendanceRecord> marks = records.stream()
                .filter(m -> m.status() == AttendanceStatus.EXCUSED && m.userId() != null
                        && m.userId() == userId && m.markRevision() != null).toList();
        if (marks.isEmpty()) return Map.of();
        Map<Long, String> requests = requestIds(semesterId, userId, marks);
        Set<String> ids = new HashSet<>(requests.values());
        Map<String, ExcuseTicket> tickets = new HashMap<>();
        if (!ids.isEmpty()) {
            Query query = Query.query(Criteria.where("_id").in(ids));
            for (ExcuseTicket ticket : mongo.find(query, ExcuseTicket.class)) tickets.put(ticket.getId(), ticket);
        }
        Set<String> bindingKeys = new HashSet<>(ids);
        marks.stream().filter(m -> m.attachmentId() != null)
                .forEach(m -> bindingKeys.add(PairWriteCoordinator.pairId(userId, m.lessonId())));
        if (bindingKeys.isEmpty()) return Map.of();
        Query filesQuery = Query.query(Criteria.where("request_id").in(bindingKeys)
                .and("owner_student_id").is(userId));
        filesQuery.fields().exclude("data");
        List<RequestAttachmentDocument> files = mongo.find(filesQuery, RequestAttachmentDocument.class);
        Map<Long, List<DocumentRef>> result = new HashMap<>();
        for (AttendanceRecord mark : marks) {
            String requestId = requests.get(mark.lessonId());
            ExcuseTicket ticket = tickets.get(requestId);
            boolean approved = approved(ticket, mark, semesterId, userId);
            String journalKey = PairWriteCoordinator.pairId(userId, mark.lessonId());
            List<RequestAttachmentDocument> related = files.stream()
                    .filter(f -> Objects.equals(f.getOwnerStudentId(), userId)
                            && Objects.equals(f.getGroupId(), mark.groupId())
                            && Objects.equals(f.getSemesterId(), semesterId)
                            && (approved && Objects.equals(f.getRequestId(), requestId)
                                || Objects.equals(f.getId(), mark.attachmentId())
                                    && Objects.equals(f.getRequestId(), journalKey)))
                    .sorted(Comparator.comparing((RequestAttachmentDocument f) ->
                            f.getPosition() == null ? Integer.MAX_VALUE : f.getPosition())
                            .thenComparing(RequestAttachmentDocument::getId)).toList();
            result.put(mark.lessonId(), related.stream().map(f -> ref(f, mark.markRevision(), mark.lessonId())).toList());
        }
        return Map.copyOf(result);
    }

    public StudentRequestModels.AttachmentDownload download(long semesterId, long userId,
            AttendanceRecord mark, String attachmentId) {
        boolean bound = documents(semesterId, userId, List.of(mark))
                .getOrDefault(mark.lessonId(), List.of()).stream()
                .anyMatch(f -> f.id().equals(attachmentId));
        if (!bound) throw new ResourceNotFoundException("Attachment", "id", attachmentId);
        RequestAttachmentDocument file = mongo.findById(attachmentId, RequestAttachmentDocument.class);
        // Revalidate binding after the binary fetch too; association and lifecycle can change.
        if (file == null || !Objects.equals(file.getOwnerStudentId(), userId)
                || !Objects.equals(file.getGroupId(), mark.groupId())
                || !Objects.equals(file.getSemesterId(), semesterId))
            throw new ResourceNotFoundException("Attachment", "id", attachmentId);
        String requestId = requestIds(semesterId, userId, List.of(mark)).get(mark.lessonId());
        boolean journal = Objects.equals(file.getId(), mark.attachmentId())
                && Objects.equals(file.getRequestId(), PairWriteCoordinator.pairId(userId, mark.lessonId()));
        if (!journal && (!Objects.equals(file.getRequestId(), requestId)
                || (requestId == null || !approved(mongo.findById(requestId, ExcuseTicket.class), mark, semesterId, userId))))
            throw new ResourceNotFoundException("Attachment", "id", attachmentId);
        if (!"ACTIVE".equals(ref(file, mark.markRevision(), mark.lessonId()).state()) || file.getData() == null)
            throw new ResponseStatusException(HttpStatus.GONE, "Attachment is no longer available");
        return new StudentRequestModels.AttachmentDownload(file.getData().getData(), file.getContentType(), file.getName());
    }

    private DocumentRef ref(RequestAttachmentDocument file, String revision, long lessonId) {
        boolean active = file.getState() == AttachmentState.ACTIVE
                && file.getExpiresAt() != null && clock.instant().isBefore(file.getExpiresAt());
        return new DocumentRef(file.getId(), file.getName(), file.getContentType(),
                file.getSize() == null ? 0 : file.getSize(), active ? "ACTIVE" : "EXPIRED",
                file.getExpiresAt(), revision, lessonId);
    }

    private static boolean approved(ExcuseTicket ticket, AttendanceRecord mark, long semesterId, long userId) {
        return ticket != null && ticket.getStatus() == ExcuseTicketStatus.APPROVED
                && Objects.equals(ticket.getStudentId(), userId)
                && Objects.equals(ticket.getGroupId(), mark.groupId())
                && Objects.equals(ticket.getSemesterId(), semesterId)
                && ticket.getLessonIds() != null && ticket.getLessonIds().contains(mark.lessonId());
    }

    private Map<Long, String> requestIds(long semesterId, long userId, List<AttendanceRecord> marks) {
        Map<Long, String> result = new HashMap<>();
        Map<Long, AttendanceRecord> legacy = new HashMap<>();
        for (AttendanceRecord mark : marks) {
            if (mark.excuseRequestId() != null && !mark.excuseRequestId().isBlank())
                result.put(mark.lessonId(), mark.excuseRequestId());
            else legacy.put(mark.lessonId(), mark);
        }
        if (legacy.isEmpty()) return result;
        Query query = Query.query(Criteria.where("semester_id").is(semesterId)
                .and("lesson_id").in(legacy.keySet()).and("payload.student.user_id").is(userId)
                .and("payload.ticket.kind").is("EXCUSE")
                .and("payload.ticket.status").is("APPROVED")
                .and("payload.after.mark_revision").in(legacy.values().stream().map(AttendanceRecord::markRevision).toList()));
        Map<Long, Set<String>> candidates = new HashMap<>();
        for (LessonActionDocument action : mongo.find(query, LessonActionDocument.class)) {
            AttendanceRecord mark = legacy.get(action.getLessonId());
            Map<String, Object> p = action.getPayload();
            if (mark == null || !Objects.equals(action.getGroupId(), mark.groupId()) || p == null) continue;
            if (!(p.get("after") instanceof Map<?, ?> after)
                    || !Objects.equals(after.get("mark_revision"), mark.markRevision())
                    || !"EXCUSED".equals(after.get("status"))
                    || !(p.get("ticket") instanceof Map<?, ?> ticket)
                    || !(ticket.get("id") instanceof String id) || id.isBlank()) continue;
            candidates.computeIfAbsent(mark.lessonId(), ignored -> new HashSet<>()).add(id);
        }
        candidates.forEach((lesson, candidatesForLesson) -> {
            if (candidatesForLesson.size() == 1) result.put(lesson, candidatesForLesson.iterator().next());
        });
        return result;
    }
}
