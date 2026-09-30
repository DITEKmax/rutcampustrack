package ru.rutcampustrack.attendance.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Applies the versioned Academic command to Attendance's durable write barrier. */
@Service
public class SemesterArchiveParticipantService {

    private static final String EVENT_TYPE = "semester.archive.participant.command";
    private static final String SOURCE = "academic-service";
    private static final String ACK_EVENT_TYPE = "semester.archive.participant.ack";
    private static final int EVENT_VERSION = 1;
    private static final String ARCHIVE_PREPARING = "ARCHIVE_PREPARING";
    private static final String ARCHIVE_SEALED = "ARCHIVE_SEALED";

    private final MongoTemplate mongoTemplate;
    private final SemesterArchiveFence fence;
    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public SemesterArchiveParticipantService(MongoTemplate mongoTemplate,
                                             SemesterArchiveFence fence,
                                             OutboxStorage outboxStorage,
                                             ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.fence = fence;
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    public boolean hasReceipt(Map<String, Object> envelope) {
        Command command = parse(envelope);
        SemesterArchiveParticipantReceiptDocument receipt = mongoTemplate.findById(
                command.operationId() + ":" + command.command(),
                SemesterArchiveParticipantReceiptDocument.class);
        if (receipt == null) {
            return false;
        }
        if (!matches(receipt, command)) {
            throw new IllegalArgumentException("Semester archive command identity changed on replay");
        }
        return true;
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public void apply(Map<String, Object> envelope) {
        Command command = parse(envelope);
        String receiptId = command.operationId() + ":" + command.command();
        SemesterArchiveParticipantReceiptDocument receipt = mongoTemplate.findById(
                receiptId, SemesterArchiveParticipantReceiptDocument.class);
        if (receipt != null && !matches(receipt, command)) {
            throw new IllegalArgumentException("Semester archive command identity changed on replay");
        }
        if (receipt != null && !"PENDING".equals(receipt.getStatus())) {
            enqueueAcknowledgement(receipt);
            return;
        }

        Instant now = Instant.now();
        SemesterArchiveFenceDocument current = fence.lock(command.semesterId(), now);
        Transition transition = transition(current, command);
        if (transition.nextFenceState() != null) {
            fence.transition(command.semesterId(), command.stateVersion(), command.operationId(),
                    transition.nextFenceState(), now);
        }

        if (receipt == null) {
            receipt = new SemesterArchiveParticipantReceiptDocument();
            receipt.setId(receiptId);
            receipt.setOperationId(command.operationId());
            receipt.setSemesterId(command.semesterId());
            receipt.setStateVersion(command.stateVersion());
            receipt.setCommand(command.command());
            receipt.setCreatedAt(now);
        }
        receipt.setStatus(transition.ackStatus());
        receipt.setBlockingReason(transition.blockingReason());
        mongoTemplate.save(receipt);
        enqueueAcknowledgement(receipt);
    }

    private Transition transition(SemesterArchiveFenceDocument current, Command command) {
        String state = current.getBarrierState() == null ? "OPEN" : current.getBarrierState();
        Long currentVersion = current.getStateVersion();
        boolean sameVersion = currentVersion != null && currentVersion == command.stateVersion();
        boolean sameOperation = command.operationId().equals(current.getOperationId());

        return switch (command.command()) {
            case "PREPARE_ARCHIVE" -> {
                if (ARCHIVE_PREPARING.equals(state) && sameVersion && sameOperation) {
                    yield pending("ARCHIVE_DRAIN_PENDING");
                }
                if (ARCHIVE_SEALED.equals(state) && sameVersion && sameOperation) {
                    yield pending("ARCHIVE_SEAL_ACK_PENDING");
                }
                if ("RESTORE_PREPARED".equals(state)) {
                    yield new Transition(null, "RELEASE_PENDING", "RESTORE_RELEASE_PENDING");
                }
                if (currentVersion != null && command.stateVersion() <= currentVersion) {
                    yield pending("STALE_STATE_VERSION");
                }
                if (!"OPEN".equals(state) && !"RELEASED".equals(state)) {
                    yield pending("ARCHIVE_TRANSITION_IN_PROGRESS");
                }
                yield new Transition(ARCHIVE_PREPARING, "PENDING", "ARCHIVE_DRAIN_PENDING");
            }
            case "SEAL_ARCHIVE" -> {
                if (ARCHIVE_SEALED.equals(state) && sameVersion && sameOperation) {
                    yield new Transition(null, "READY", null);
                }
                if (!ARCHIVE_PREPARING.equals(state) || !sameVersion || !sameOperation) {
                    yield pending("ARCHIVE_PREPARE_VERSION_MISMATCH");
                }
                String blocker = blockingEffectReason(command.semesterId());
                if (blocker != null) {
                    yield pending(blocker);
                }
                yield new Transition(ARCHIVE_SEALED, "READY", null);
            }
            case "PREPARE_RESTORE" -> {
                if ("RESTORE_PREPARED".equals(state) && sameVersion && sameOperation) {
                    yield new Transition(null, "PREPARED_RESTORE", null);
                }
                if (ARCHIVE_SEALED.equals(state)
                        && (currentVersion == null || command.stateVersion() > currentVersion)) {
                    yield new Transition("RESTORE_PREPARED", "PREPARED_RESTORE", null);
                }
                yield pending("RESTORE_REQUIRES_ARCHIVE_PREPARE");
            }
            case "RELEASE_RESTORE" -> {
                if ("RESTORE_PREPARED".equals(state) && sameVersion && sameOperation) {
                    yield new Transition("RELEASED", "RELEASED", null);
                }
                if ("RELEASED".equals(state) && sameVersion && sameOperation) {
                    yield new Transition(null, "RELEASED", null);
                }
                yield pending("RESTORE_RELEASE_VERSION_MISMATCH");
            }
            default -> throw new IllegalArgumentException("Unsupported semester archive participant command");
        };
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

    private static Transition pending(String reason) {
        return new Transition(null, "PENDING", reason);
    }

    private void enqueueAcknowledgement(SemesterArchiveParticipantReceiptDocument receipt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation_id", receipt.getOperationId());
        payload.put("semester_id", receipt.getSemesterId());
        payload.put("state_version", receipt.getStateVersion());
        payload.put("command", receipt.getCommand());
        payload.put("status", receipt.getStatus());
        payload.put("blocking_reason", receipt.getBlockingReason());
        try {
            String json = objectMapper.writeValueAsString(
                    EventEnvelope.build(ACK_EVENT_TYPE, payload));
            outboxStorage.save(ACK_EVENT_TYPE, json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize semester archive participant acknowledgement", error);
        }
    }

    private static boolean matches(SemesterArchiveParticipantReceiptDocument receipt, Command command) {
        return command.operationId().equals(receipt.getOperationId())
                && command.semesterId() == receipt.getSemesterId()
                && command.stateVersion() == receipt.getStateVersion()
                && command.command().equals(receipt.getCommand());
    }

    private static Command parse(Map<String, Object> envelope) {
        if (envelope == null || !EVENT_TYPE.equals(envelope.get("event_type"))
                || !SOURCE.equals(envelope.get("source"))
                || integer(envelope.get("event_version"), "event_version") != EVENT_VERSION) {
            throw new IllegalArgumentException("Unsupported or untrusted semester archive command envelope");
        }
        requireCorrelationId(envelope.get("trace_id"));
        uuid(envelope.get("event_id"), "event_id");
        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Semester archive command payload is required");
        }
        Object rawCommand = raw.get("command");
        if (!(rawCommand instanceof String command)
                || !command.equals("PREPARE_ARCHIVE")
                && !command.equals("SEAL_ARCHIVE")
                && !command.equals("PREPARE_RESTORE")
                && !command.equals("RELEASE_RESTORE")) {
            throw new IllegalArgumentException("Unsupported semester archive participant command");
        }
        return new Command(
                uuid(raw.get("operation_id"), "operation_id"),
                positive(raw.get("semester_id"), "semester_id"),
                nonNegative(raw.get("state_version"), "state_version"), command);
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
        if (result <= 0) throw new IllegalArgumentException(field + " must be positive");
        return result;
    }

    private static long nonNegative(Object value, String field) {
        long result = integer(value, field);
        if (result < 0) throw new IllegalArgumentException(field + " must not be negative");
        return result;
    }

    private static long integer(Object value, String field) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return ((Number) value).longValue();
    }

    private static void requireCorrelationId(Object value) {
        if (!(value instanceof String traceId) || traceId.isBlank()) {
            throw new IllegalArgumentException("trace_id must be a non-empty correlation string");
        }
    }

    private record Command(String operationId, long semesterId, long stateVersion, String command) { }

    private record Transition(String nextFenceState, String ackStatus, String blockingReason) { }
}
