package ru.rutcampustrack.attendance.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Attendance participant for the irreversible, final semester deletion protocol. */
@Service
public class SemesterDeletionParticipantService {

    private static final String EVENT_TYPE = "semester.archive.participant.command";
    private static final String ACK_EVENT_TYPE = "semester.archive.participant.ack";
    private static final String COMMAND_SOURCE = "academic-service";
    private static final int EVENT_VERSION = 1;
    private static final String DELETE_PREPARING = "DELETE_PREPARING";
    private static final String DELETE_SEALED = "DELETE_SEALED";
    private static final String DELETE_COMMITTED = "DELETE_COMMITTED";
    private static final Set<String> DELETE_COMMANDS = Set.of(
            "PREPARE_DELETE", "SEAL_DELETE", "COMMIT_DELETE", "RELEASE_DELETE");

    private final MongoTemplate mongoTemplate;
    private final SemesterArchiveFence fence;
    private final AttendanceSemesterDeletionData deletionData;
    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public SemesterDeletionParticipantService(MongoTemplate mongoTemplate,
                                              SemesterArchiveFence fence,
                                              AttendanceSemesterDeletionData deletionData,
                                              OutboxStorage outboxStorage,
                                              ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.fence = fence;
        this.deletionData = deletionData;
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    public static boolean isDeletionCommand(Map<String, Object> envelope) {
        if (envelope == null || !EVENT_TYPE.equals(envelope.get("event_type"))) {
            return false;
        }
        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) {
            return false;
        }
        return payload.get("command") instanceof String command && DELETE_COMMANDS.contains(command);
    }

    public boolean hasReceipt(Map<String, Object> envelope) {
        Command command = parse(envelope);
        SemesterDeletionParticipantReceiptDocument receipt = mongoTemplate.findById(
                command.operationId() + ":" + command.command(),
                SemesterDeletionParticipantReceiptDocument.class);
        if (receipt == null) {
            return false;
        }
        if (!matches(receipt, command)) {
            throw new IllegalArgumentException("Semester deletion command identity changed on replay");
        }
        return true;
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public void apply(Map<String, Object> envelope) {
        Command command = parse(envelope);
        String receiptId = command.operationId() + ":" + command.command();
        SemesterDeletionParticipantReceiptDocument receipt = mongoTemplate.findById(
                receiptId, SemesterDeletionParticipantReceiptDocument.class);
        if (receipt != null && !matches(receipt, command)) {
            throw new IllegalArgumentException("Semester deletion command identity changed on replay");
        }
        if (receipt != null && isTerminal(receipt.getStatus())) {
            enqueueAcknowledgement(receipt);
            return;
        }

        Instant now = Instant.now();
        Outcome outcome = switch (command.command()) {
            case "PREPARE_DELETE" -> prepare(command, now);
            case "SEAL_DELETE" -> seal(command, now);
            case "COMMIT_DELETE" -> commit(command, now);
            case "RELEASE_DELETE" -> release(command, now);
            default -> throw new IllegalArgumentException("Unsupported semester deletion participant command");
        };

        if (receipt == null) {
            receipt = new SemesterDeletionParticipantReceiptDocument();
            receipt.setId(receiptId);
            receipt.setOperationId(command.operationId());
            receipt.setSemesterId(command.semesterId());
            receipt.setStateVersion(command.stateVersion());
            receipt.setCommand(command.command());
            receipt.setExpectedParticipantDigest(command.expectedParticipantDigest());
            receipt.setCreatedAt(now);
        }
        applyOutcome(receipt, outcome);
        mongoTemplate.save(receipt);
        enqueueAcknowledgement(receipt);
    }

    private Outcome prepare(Command command, Instant now) {
        SemesterDeletionTombstoneDocument tombstone = tombstone(command.semesterId());
        if (tombstone != null) {
            return pending("ATTENDANCE_SEMESTER_ALREADY_DELETED", fromTombstone(tombstone));
        }

        SemesterDeletionParticipantOperationDocument operation = operation(command.operationId());
        if (operation != null && !matches(operation, command)) {
            throw new IllegalArgumentException("Semester deletion operation identity changed");
        }

        SemesterArchiveFenceDocument current = fence.lock(command.semesterId(), now);
        if (operation != null) {
            if ("PREPARING".equals(operation.getStatus())
                    && isFence(current, command, DELETE_PREPARING)) {
                return pending("ATTENDANCE_DELETE_DRAIN_PENDING", deletionData.preview(command.semesterId()));
            }
            return pending("ATTENDANCE_DELETE_PHASE_ADVANCED", deletionData.preview(command.semesterId()));
        }

        Long currentVersion = current.getStateVersion();
        if (currentVersion != null && command.stateVersion() <= currentVersion) {
            return pending("STALE_STATE_VERSION", deletionData.preview(command.semesterId()));
        }
        String previousState = current.getBarrierState() == null ? "OPEN" : current.getBarrierState();
        if (!isRestorable(previousState)) {
            return pending("ATTENDANCE_TRANSITION_IN_PROGRESS", deletionData.preview(command.semesterId()));
        }

        fence.transition(command.semesterId(), command.stateVersion(), command.operationId(), DELETE_PREPARING, now);
        AttendanceSemesterDeletionData.Snapshot snapshot = deletionData.preview(command.semesterId());
        operation = new SemesterDeletionParticipantOperationDocument();
        operation.setId(command.operationId());
        operation.setSemesterId(command.semesterId());
        operation.setStateVersion(command.stateVersion());
        operation.setExpectedParticipantDigest(command.expectedParticipantDigest());
        operation.setPreviousBarrierState(previousState);
        operation.setPreviousStateVersion(currentVersion);
        operation.setParticipantDigest(snapshot.participantDigest());
        operation.setAttendanceMarksCount(snapshot.attendanceMarks());
        operation.setStudentRequestsCount(snapshot.studentRequests());
        operation.setStatus("PREPARING");
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        mongoTemplate.insert(operation);
        return pending("ATTENDANCE_DELETE_DRAIN_PENDING", snapshot);
    }

    private Outcome seal(Command command, Instant now) {
        SemesterDeletionParticipantOperationDocument operation = operation(command.operationId());
        if (operation == null) {
            return pending("ATTENDANCE_DELETE_PREPARE_REQUIRED", deletionData.preview(command.semesterId()));
        }
        requireOperationIdentity(operation, command);
        if (!"PREPARING".equals(operation.getStatus())) {
            return pending("ATTENDANCE_DELETE_PREPARE_REQUIRED", deletionData.preview(command.semesterId()));
        }
        SemesterArchiveFenceDocument current = fence.lock(command.semesterId(), now);
        if (!isFence(current, command, DELETE_PREPARING)) {
            return pending("ATTENDANCE_DELETE_PREPARE_VERSION_MISMATCH", deletionData.preview(command.semesterId()));
        }
        String blocker = blockingEffectReason(command.semesterId());
        if (blocker != null) {
            return pending(blocker, deletionData.preview(command.semesterId()));
        }

        AttendanceSemesterDeletionData.Snapshot snapshot = deletionData.preview(command.semesterId());
        if (!sameDigest(snapshot.participantDigest(), command.expectedParticipantDigest())) {
            return pending("ATTENDANCE_DELETE_PREVIEW_CHANGED", snapshot);
        }
        fence.transition(command.semesterId(), command.stateVersion(), command.operationId(), DELETE_SEALED, now);
        operation.setParticipantDigest(snapshot.participantDigest());
        operation.setAttendanceMarksCount(snapshot.attendanceMarks());
        operation.setStudentRequestsCount(snapshot.studentRequests());
        operation.setStatus("SEALED");
        operation.setUpdatedAt(now);
        mongoTemplate.save(operation);
        return new Outcome("READY", null, snapshot);
    }

    private Outcome commit(Command command, Instant now) {
        SemesterDeletionTombstoneDocument existingTombstone = tombstone(command.semesterId());
        if (existingTombstone != null) {
            if (matches(existingTombstone, command)) {
                return new Outcome("DELETED", null, fromTombstone(existingTombstone));
            }
            return pending("ATTENDANCE_SEMESTER_ALREADY_DELETED", fromTombstone(existingTombstone));
        }

        SemesterDeletionParticipantOperationDocument operation = operation(command.operationId());
        if (operation == null) {
            return pending("ATTENDANCE_DELETE_SEAL_REQUIRED", deletionData.preview(command.semesterId()));
        }
        requireOperationIdentity(operation, command);
        if (!"SEALED".equals(operation.getStatus())) {
            return pending("ATTENDANCE_DELETE_SEAL_REQUIRED", deletionData.preview(command.semesterId()));
        }
        if (!sameDigest(operation.getParticipantDigest(), command.expectedParticipantDigest())) {
            throw new IllegalStateException("Attendance deletion seal digest changed");
        }
        SemesterArchiveFenceDocument current = fence.lock(command.semesterId(), now);
        if (!isFence(current, command, DELETE_SEALED)) {
            return pending("ATTENDANCE_DELETE_SEAL_VERSION_MISMATCH", deletionData.preview(command.semesterId()));
        }

        AttendanceSemesterDeletionData.Snapshot snapshot;
        try {
            snapshot = deletionData.delete(command.semesterId(), operation.getParticipantDigest());
        } catch (AttendanceSemesterDeletionData.DigestMismatchException changed) {
            return pending("ATTENDANCE_DELETE_DOMAIN_CHANGED_AFTER_SEAL", changed.current());
        }

        fence.transition(command.semesterId(), command.stateVersion(), command.operationId(), DELETE_COMMITTED, now);
        operation.setStatus("DELETED");
        operation.setUpdatedAt(now);
        operation.setParticipantDigest(snapshot.participantDigest());
        operation.setAttendanceMarksCount(snapshot.attendanceMarks());
        operation.setStudentRequestsCount(snapshot.studentRequests());
        mongoTemplate.save(operation);

        SemesterDeletionTombstoneDocument tombstone = new SemesterDeletionTombstoneDocument();
        tombstone.setId(Long.toString(command.semesterId()));
        tombstone.setSemesterId(command.semesterId());
        tombstone.setOperationId(command.operationId());
        tombstone.setStateVersion(command.stateVersion());
        tombstone.setParticipantDigest(snapshot.participantDigest());
        tombstone.setAttendanceMarksCount(snapshot.attendanceMarks());
        tombstone.setStudentRequestsCount(snapshot.studentRequests());
        tombstone.setDeletedAt(now);
        mongoTemplate.insert(tombstone);
        return new Outcome("DELETED", null, snapshot);
    }

    private Outcome release(Command command, Instant now) {
        SemesterDeletionParticipantOperationDocument operation = operation(command.operationId());
        if (operation == null) {
            return pending("ATTENDANCE_DELETE_RELEASE_NOT_ALLOWED", deletionData.preview(command.semesterId()));
        }
        requireOperationIdentity(operation, command);
        if (!("PREPARING".equals(operation.getStatus()) || "SEALED".equals(operation.getStatus()))) {
            return pending("ATTENDANCE_DELETE_RELEASE_NOT_ALLOWED", deletionData.preview(command.semesterId()));
        }
        SemesterArchiveFenceDocument current = fence.lock(command.semesterId(), now);
        String expectedFence = "PREPARING".equals(operation.getStatus()) ? DELETE_PREPARING : DELETE_SEALED;
        if (!isFence(current, command, expectedFence)) {
            return pending("ATTENDANCE_DELETE_RELEASE_VERSION_MISMATCH", deletionData.preview(command.semesterId()));
        }
        String previousState = operation.getPreviousBarrierState();
        if (!isRestorable(previousState)) {
            return pending("ATTENDANCE_DELETE_PRIOR_STATE_UNAVAILABLE", deletionData.preview(command.semesterId()));
        }

        fence.transition(command.semesterId(), command.stateVersion(), command.operationId(), previousState, now);
        operation.setStatus("RELEASED");
        operation.setUpdatedAt(now);
        mongoTemplate.save(operation);
        return new Outcome("RELEASED", null, deletionData.preview(command.semesterId()));
    }

    private String blockingEffectReason(long semesterId) {
        Query effectErrors = Query.query(Criteria.where("semester_id").is(semesterId)
                .and("result").is("ERROR"));
        if (mongoTemplate.exists(effectErrors, SemesterArchiveEffectReceiptDocument.class)) {
            return "ATTENDANCE_EFFECT_REQUIRES_RECONCILIATION";
        }
        Query transferErrors = Query.query(Criteria.where("semester_id").is(semesterId)
                .and("result").is("ERROR"));
        if (mongoTemplate.exists(transferErrors, LessonTransferReceiptDocument.class)) {
            return "ATTENDANCE_TRANSFER_REQUIRES_RECONCILIATION";
        }
        return null;
    }

    private SemesterDeletionParticipantOperationDocument operation(String operationId) {
        return mongoTemplate.findById(operationId, SemesterDeletionParticipantOperationDocument.class);
    }

    private SemesterDeletionTombstoneDocument tombstone(long semesterId) {
        return mongoTemplate.findById(Long.toString(semesterId), SemesterDeletionTombstoneDocument.class);
    }

    private static AttendanceSemesterDeletionData.Snapshot fromTombstone(
            SemesterDeletionTombstoneDocument tombstone) {
        return new AttendanceSemesterDeletionData.Snapshot(
                value(tombstone.getAttendanceMarksCount()), value(tombstone.getStudentRequestsCount()),
                tombstone.getParticipantDigest());
    }

    private static boolean matches(SemesterDeletionParticipantOperationDocument operation, Command command) {
        return operation != null
                && command.operationId().equals(operation.getId())
                && command.semesterId() == value(operation.getSemesterId())
                && command.stateVersion() == value(operation.getStateVersion())
                && sameDigest(command.expectedParticipantDigest(), operation.getExpectedParticipantDigest());
    }

    private static void requireOperationIdentity(SemesterDeletionParticipantOperationDocument operation,
                                                 Command command) {
        if (!matches(operation, command)) {
            throw new IllegalArgumentException("Semester deletion command does not match its prepared operation");
        }
    }

    private static boolean matches(SemesterDeletionTombstoneDocument tombstone, Command command) {
        return command.operationId().equals(tombstone.getOperationId())
                && command.semesterId() == value(tombstone.getSemesterId())
                && command.stateVersion() == value(tombstone.getStateVersion())
                && sameDigest(command.expectedParticipantDigest(), tombstone.getParticipantDigest());
    }

    private static boolean matches(SemesterDeletionParticipantReceiptDocument receipt, Command command) {
        return command.operationId().equals(receipt.getOperationId())
                && command.semesterId() == value(receipt.getSemesterId())
                && command.stateVersion() == value(receipt.getStateVersion())
                && command.command().equals(receipt.getCommand())
                && sameDigest(command.expectedParticipantDigest(), receipt.getExpectedParticipantDigest());
    }

    private static boolean isFence(SemesterArchiveFenceDocument fence, Command command, String state) {
        return fence != null && command.operationId().equals(fence.getOperationId())
                && command.stateVersion() == value(fence.getStateVersion())
                && state.equals(fence.getBarrierState());
    }

    private static boolean isRestorable(String state) {
        return "OPEN".equals(state) || "RELEASED".equals(state) || "ARCHIVE_SEALED".equals(state);
    }

    private static boolean isTerminal(String status) {
        return "READY".equals(status) || "RELEASED".equals(status) || "DELETED".equals(status);
    }

    private static Outcome pending(String reason, AttendanceSemesterDeletionData.Snapshot snapshot) {
        return new Outcome("PENDING", reason, snapshot);
    }

    private static void applyOutcome(SemesterDeletionParticipantReceiptDocument receipt, Outcome outcome) {
        receipt.setStatus(outcome.status());
        receipt.setBlockingReason(outcome.blockingReason());
        receipt.setParticipantDigest(outcome.snapshot().participantDigest());
        receipt.setAttendanceMarksCount(outcome.snapshot().attendanceMarks());
        receipt.setStudentRequestsCount(outcome.snapshot().studentRequests());
    }

    private void enqueueAcknowledgement(SemesterDeletionParticipantReceiptDocument receipt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation_id", receipt.getOperationId());
        payload.put("semester_id", receipt.getSemesterId());
        payload.put("state_version", receipt.getStateVersion());
        payload.put("command", receipt.getCommand());
        payload.put("status", receipt.getStatus());
        payload.put("blocking_reason", receipt.getBlockingReason());
        payload.put("participant_digest", receipt.getParticipantDigest());
        payload.put("counts", counts(receipt.getAttendanceMarksCount(), receipt.getStudentRequestsCount()));
        try {
            String json = objectMapper.writeValueAsString(EventEnvelope.build(ACK_EVENT_TYPE, payload));
            outboxStorage.save(ACK_EVENT_TYPE, json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize semester deletion acknowledgement", error);
        }
    }

    private static Map<String, Object> counts(long attendanceMarks, long studentRequests) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("scheduleTemplates", 0L);
        counts.put("oneOffLessons", 0L);
        counts.put("lessons", 0L);
        counts.put("assignments", 0L);
        counts.put("homeworks", 0L);
        counts.put("attendanceMarks", attendanceMarks);
        counts.put("studentRequests", studentRequests);
        return counts;
    }

    private static Command parse(Map<String, Object> envelope) {
        if (envelope == null || !EVENT_TYPE.equals(envelope.get("event_type"))
                || !COMMAND_SOURCE.equals(envelope.get("source"))
                || integer(envelope.get("event_version"), "event_version") != EVENT_VERSION) {
            throw new IllegalArgumentException("Unsupported or untrusted semester deletion command envelope");
        }
        requiredString(envelope.get("trace_id"), "trace_id");
        uuid(envelope.get("event_id"), "event_id");
        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("Semester deletion command payload is required");
        }
        Object rawCommand = payload.get("command");
        if (!(rawCommand instanceof String command) || !DELETE_COMMANDS.contains(command)) {
            throw new IllegalArgumentException("Unsupported semester deletion participant command");
        }
        return new Command(
                uuid(payload.get("operation_id"), "operation_id"),
                positive(payload.get("semester_id"), "semester_id"),
                positive(payload.get("state_version"), "state_version"),
                command,
                digest(payload.get("expected_participant_digest"), "expected_participant_digest"));
    }

    private static String digest(Object value, String field) {
        if (!(value instanceof String raw) || !raw.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return raw;
    }

    private static String uuid(Object value, String field) {
        if (!(value instanceof String raw)) {
            throw new IllegalArgumentException(field + " must be a UUID string");
        }
        try {
            String normalized = UUID.fromString(raw).toString();
            if (!normalized.equals(raw)) {
                throw new IllegalArgumentException(field + " must be a canonical UUID");
            }
            return normalized;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a canonical UUID", error);
        }
    }

    private static long positive(Object value, String field) {
        long result = integer(value, field);
        if (result <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return result;
    }

    private static long integer(Object value, String field) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return ((Number) value).longValue();
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return string;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static boolean sameDigest(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return MessageDigest.isEqual(left.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                right.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private record Command(String operationId, long semesterId, long stateVersion, String command,
                           String expectedParticipantDigest) {
    }

    private record Outcome(String status, String blockingReason,
                           AttendanceSemesterDeletionData.Snapshot snapshot) {
    }
}
