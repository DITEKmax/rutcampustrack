package ru.rutcampustrack.attendance.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.SemesterTransition;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

/** Applies an admitted initial enrollment without reopening an already committed close receipt. */
@Service
public class InitialMembershipAttendanceBackfillService {
    public static final String TYPE = "student.initial-membership.recorded";
    public static final String ACK_TYPE = "student.initial-membership.attendance-backfilled";
    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper();
    private final MongoTemplate mongo;
    private final TransactionTemplate transactions;
    private final AcademicGrpcClient academic;
    private final ScheduleGrpcClient schedule;
    private final LessonEventService lessons;
    private final PairWriteCoordinator fences;
    private final OutboxStorage outbox;
    private final ObjectMapper mapper;

    public InitialMembershipAttendanceBackfillService(
            MongoTemplate mongo,
            TransactionTemplate transactions,
            AcademicGrpcClient academic,
            ScheduleGrpcClient schedule,
            LessonEventService lessons,
            PairWriteCoordinator fences,
            OutboxStorage outbox,
            ObjectMapper mapper) {
        this.mongo = mongo;
        this.transactions = transactions;
        this.academic = academic;
        this.schedule = schedule;
        this.lessons = lessons;
        this.fences = fences;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    public void handle(Map<String, Object> envelope) {
        Identity identity = parse(envelope); // validation precedes every replay/dedup path
        if (receipt(identity) != null) {
            transactions.executeWithoutResult(status -> acknowledge(requiredReceipt(identity)));
            return;
        }
        // All authoritative reads finish outside the Mongo transaction/fences.
        List<LessonEventService.LessonClosedSnapshot> prepared = prepare(identity);
        transactions.executeWithoutResult(
                status -> {
                    var existing = receipt(identity);
                    if (existing != null) {
                        acknowledge(existing);
                        return;
                    }
                    // Semester first, then the complete sorted physical-lesson set before any pair
                    // writes.
                    fences.lockAcceptedScheduleEffectLessons(
                            identity.semesterId(),
                            prepared.stream()
                                    .map(LessonEventService.LessonClosedSnapshot::lessonId)
                                    .toList(),
                            identity.groupId(),
                            Instant.now());
                    for (var snapshot : prepared) {
                        lessons.applyInitialMembership(snapshot);
                    }
                    var applied =
                            InitialMembershipAttendanceBackfillReceiptDocument.builder()
                                    .id(identity.eventId())
                                    .sourceEventId(identity.eventId())
                                    .historyId(identity.historyId())
                                    .studentId(identity.studentId())
                                    .groupId(identity.groupId())
                                    .semesterId(identity.semesterId())
                                    .joinedOn(identity.joinedOn())
                                    .semesterEndsOn(identity.semesterEndsOn())
                                    .payloadHash(identity.payloadHash())
                                    .result("APPLIED")
                                    .createdAt(Instant.now())
                                    .build();
                    mongo.insert(applied);
                    acknowledge(applied);
                });
    }

    private List<LessonEventService.LessonClosedSnapshot> prepare(Identity identity) {
        var semester = academic.getSemesterState(identity.semesterId());
        if (semester == null
                || semester.getId() != identity.semesterId()
                || semester.getStateVersion() < 0
                || semester.getArchived()
                || semester.getReleasePending()
                || semester.getWriteBlocked()
                        != (semester.getTransition() != SemesterTransition.NONE)
                || semester.getTransition() == SemesterTransition.DELETING
                        && !"PREPARING".equals(semester.getDeletionPhase())
                || !Set.of(
                                SemesterTransition.NONE,
                                SemesterTransition.ARCHIVING,
                                SemesterTransition.DELETING)
                        .contains(semester.getTransition())
                || !Set.of("", "PREPARING", "CANCELLED").contains(semester.getDeletionPhase()))
            throw new AcademicServiceUnavailableException(
                    "Initial membership semester is unavailable for admitted effects");
        var response =
                schedule.getLessonsByGroup(
                        identity.groupId(),
                        identity.semesterId(),
                        identity.joinedOn().toString(),
                        identity.semesterEndsOn().toString());
        if (response == null)
            throw new ScheduleServiceUnavailableException("Missing initial membership lessons");
        Set<Long> physicalIds = new HashSet<>();
        Map<LocalDate, GroupMembersResponse> rosters = new HashMap<>();
        List<LessonEventService.LessonClosedSnapshot> prepared = new ArrayList<>();
        for (var lesson :
                response.getLessonsList().stream()
                        .sorted(
                                Comparator.comparingLong(
                                        ru.rutcampustrack.schedule.grpc.LessonResponse::getId))
                        .toList()) {
            LocalDate date;
            try {
                date = LocalDate.parse(lesson.getDate());
            } catch (RuntimeException invalid) {
                throw new ScheduleServiceUnavailableException(
                        "Malformed initial membership lesson date");
            }
            if (lesson.getId() <= 0
                    || !physicalIds.add(lesson.getId())
                    || lesson.getGroupId() != identity.groupId()
                    || lesson.getSemesterId() != identity.semesterId()
                    || lesson.getSubjectId() <= 0
                    || lesson.getLessonNumber() <= 0
                    || date.isBefore(identity.joinedOn())
                    || date.isAfter(identity.semesterEndsOn())
                    || !Set.of("planned", "active", "closed", "cancelled", "transferred")
                            .contains(lesson.getStatus()))
                throw new ScheduleServiceUnavailableException(
                        "Foreign or malformed initial membership lesson scope");
            if (!"closed".equals(lesson.getStatus())) continue;
            if (fences.isSubjectDeleted(lesson.getId(), identity.semesterId(), identity.groupId()))
                continue;
            var roster =
                    rosters.computeIfAbsent(
                            date,
                            day ->
                                    academic.getGroupMembers(
                                            identity.groupId(), day, identity.semesterId()));
            prepared.add(
                    lessons.prepareInitialMembershipLesson(
                            lesson,
                            identity.groupId(),
                            identity.semesterId(),
                            identity.studentId(),
                            roster));
        }
        return List.copyOf(prepared);
    }

    private InitialMembershipAttendanceBackfillReceiptDocument requiredReceipt(Identity identity) {
        var receipt = receipt(identity);
        if (receipt == null)
            throw new IllegalStateException("Initial membership receipt disappeared");
        return receipt;
    }

    private InitialMembershipAttendanceBackfillReceiptDocument receipt(Identity identity) {
        var receipt =
                mongo.findById(
                        identity.eventId(),
                        InitialMembershipAttendanceBackfillReceiptDocument.class);
        if (receipt == null) return null;
        if (!identity.eventId().equals(receipt.getSourceEventId())
                || !Long.valueOf(identity.historyId()).equals(receipt.getHistoryId())
                || !Long.valueOf(identity.studentId()).equals(receipt.getStudentId())
                || !Long.valueOf(identity.groupId()).equals(receipt.getGroupId())
                || !Long.valueOf(identity.semesterId()).equals(receipt.getSemesterId())
                || !identity.joinedOn().equals(receipt.getJoinedOn())
                || !identity.semesterEndsOn().equals(receipt.getSemesterEndsOn())
                || !identity.payloadHash().equals(receipt.getPayloadHash())
                || !"APPLIED".equals(receipt.getResult()))
            throw new IllegalArgumentException("Initial membership identity changed on replay");
        return receipt;
    }

    private void acknowledge(InitialMembershipAttendanceBackfillReceiptDocument receipt) {
        Map<String, Object> payload =
                Map.of(
                        "source_event_id",
                        receipt.getSourceEventId(),
                        "history_id",
                        receipt.getHistoryId(),
                        "semester_id",
                        receipt.getSemesterId(),
                        "payload_hash",
                        receipt.getPayloadHash(),
                        "result",
                        receipt.getResult());
        try {
            outbox.save(
                    ACK_TYPE, mapper.writeValueAsString(EventEnvelope.build(ACK_TYPE, payload)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot serialize initial membership acknowledgement", failure);
        }
    }

    private static Identity parse(Map<String, Object> envelope) {
        if (envelope == null
                || !TYPE.equals(envelope.get("event_type"))
                || !"academic-service".equals(envelope.get("source"))
                || positive(envelope.get("event_version")) != 1)
            throw new IllegalArgumentException("Untrusted initial membership envelope");
        String eventId = uuid(envelope.get("event_id"));
        if (!(envelope.get("occurred_at") instanceof String occurred))
            throw new IllegalArgumentException("Missing initial membership occurrence time");
        java.time.OffsetDateTime.parse(occurred);
        if (!(envelope.get("trace_id") instanceof String trace) || trace.isBlank())
            throw new IllegalArgumentException("Missing initial membership correlation");
        if (!(envelope.get("payload") instanceof Map<?, ?> payload)
                || !payload.keySet()
                        .equals(
                                Set.of(
                                        "history_id",
                                        "student_id",
                                        "group_id",
                                        "semester_id",
                                        "joined_on",
                                        "semester_ends_on")))
            throw new IllegalArgumentException("Invalid initial membership payload");
        long historyId = positive(payload.get("history_id")),
                studentId = positive(payload.get("student_id")),
                groupId = positive(payload.get("group_id")),
                semesterId = positive(payload.get("semester_id"));
        LocalDate joined = date(payload.get("joined_on")),
                end = date(payload.get("semester_ends_on"));
        if (end.isBefore(joined))
            throw new IllegalArgumentException("Invalid initial membership bounds");
        try {
            Map<String, Object> canonical = new TreeMap<>();
            canonical.put("history_id", historyId);
            canonical.put("student_id", studentId);
            canonical.put("group_id", groupId);
            canonical.put("semester_id", semesterId);
            canonical.put("joined_on", joined.toString());
            canonical.put("semester_ends_on", end.toString());
            String hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(CANONICAL_JSON.writeValueAsBytes(canonical)));
            return new Identity(
                    eventId, historyId, studentId, groupId, semesterId, joined, end, hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot hash initial membership identity", failure);
        }
    }

    private static long positive(Object raw) {
        if (!(raw instanceof Byte
                        || raw instanceof Short
                        || raw instanceof Integer
                        || raw instanceof Long)
                || ((Number) raw).longValue() <= 0)
            throw new IllegalArgumentException("Expected positive identity integer");
        return ((Number) raw).longValue();
    }

    private static LocalDate date(Object raw) {
        if (!(raw instanceof String text))
            throw new IllegalArgumentException("Expected canonical membership date");
        LocalDate parsed = LocalDate.parse(text);
        if (!parsed.toString().equals(text))
            throw new IllegalArgumentException("Expected canonical membership date");
        return parsed;
    }

    private static String uuid(Object raw) {
        if (!(raw instanceof String text))
            throw new IllegalArgumentException("Expected canonical event UUID");
        if (!UUID.fromString(text).toString().equals(text))
            throw new IllegalArgumentException("Expected canonical event UUID");
        return text;
    }

    private record Identity(
            String eventId,
            long historyId,
            long studentId,
            long groupId,
            long semesterId,
            LocalDate joinedOn,
            LocalDate semesterEndsOn,
            String payloadHash) {}
}
