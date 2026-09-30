package ru.rutcampustrack.attendance.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Receipts and acknowledges the Schedule effects that gate full semester archival. */
@Service
public class SemesterArchiveEffectService {

    private static final String ACK_EVENT_TYPE = "semester.archive.effect.ack";
    private static final String TARGET = "ATTENDANCE";
    private static final String SOURCE = "schedule-service";
    private static final int EVENT_VERSION = 1;
    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT);
    private static final List<String> TRACKED_EVENT_TYPES = List.of(
            "lesson.closed", "lesson.cancelled", "lesson.deleted", "lesson.one_off.cancelled");

    private final MongoTemplate mongoTemplate;
    private final SemesterArchiveFence fence;
    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public SemesterArchiveEffectService(MongoTemplate mongoTemplate,
                                        SemesterArchiveFence fence,
                                        OutboxStorage outboxStorage,
                                        ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.fence = fence;
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks for an exact durable receipt before a consumer makes any external
     * calls. Identity parsing and comparison are intentionally the same as the
     * transactional apply path.
     */
    public boolean hasReceipt(Map<String, Object> envelope) {
        Effect identity = parse(envelope);
        SemesterArchiveEffectReceiptDocument receipt = mongoTemplate.findById(
                identity.eventId() + ":" + TARGET, SemesterArchiveEffectReceiptDocument.class);
        if (receipt == null) {
            return false;
        }
        if (!matches(receipt, identity)) {
            throw new IllegalArgumentException("Schedule effect identity changed on replay");
        }
        return true;
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public void apply(Map<String, Object> envelope, Runnable effect) {
        Effect identity = parse(envelope);
        String receiptId = identity.eventId() + ":" + TARGET;
        SemesterArchiveEffectReceiptDocument receipt = mongoTemplate.findById(
                receiptId, SemesterArchiveEffectReceiptDocument.class);
        if (receipt != null) {
            if (!matches(receipt, identity)) {
                throw new IllegalArgumentException("Schedule effect identity changed on replay");
            }
            enqueueAcknowledgement(receipt);
            return;
        }

        String result = "APPLIED";
        String blockingReason = null;
        try {
            // Reject a post-SEAL effect before entering a nested transactional
            // domain service. Catching its exception after a REQUIRED method
            // throws would leave this transaction rollback-only and prevent
            // the durable ERROR receipt and ACK from committing.
            fence.lockAcceptedScheduleEffect(identity.semesterId(), Instant.now());
            effect.run();
        } catch (SemesterArchiveEffectRejectedException rejected) {
            result = "ERROR";
            blockingReason = rejected.blockingReason();
        }

        receipt = SemesterArchiveEffectReceiptDocument.builder()
                .id(receiptId)
                .sourceEventId(identity.eventId())
                .target(TARGET)
                .sourceEventType(identity.eventType())
                .semesterId(identity.semesterId())
                .payloadHash(identity.payloadHash())
                .result(result)
                .blockingReason(blockingReason)
                .createdAt(Instant.now())
                .build();
        mongoTemplate.insert(receipt);
        enqueueAcknowledgement(receipt);
    }

    private void enqueueAcknowledgement(SemesterArchiveEffectReceiptDocument receipt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_event_id", receipt.getSourceEventId());
        payload.put("target", receipt.getTarget());
        payload.put("source_event_type", receipt.getSourceEventType());
        payload.put("semester_id", receipt.getSemesterId());
        payload.put("payload_hash", receipt.getPayloadHash());
        payload.put("result", receipt.getResult());
        payload.put("blocking_reason", receipt.getBlockingReason());
        try {
            String json = objectMapper.writeValueAsString(
                    EventEnvelope.build(ACK_EVENT_TYPE, payload));
            outboxStorage.save(ACK_EVENT_TYPE, json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize semester archive effect acknowledgement", error);
        }
    }

    private Effect parse(Map<String, Object> envelope) {
        if (envelope == null || !SOURCE.equals(envelope.get("source"))
                || integer(envelope.get("event_version"), "event_version") != EVENT_VERSION) {
            throw new IllegalArgumentException("Unsupported or untrusted Schedule effect envelope");
        }
        requireCorrelationId(envelope.get("trace_id"));
        Object rawType = envelope.get("event_type");
        if (!(rawType instanceof String eventType) || !TRACKED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("Unsupported Schedule effect type");
        }
        String eventId = canonicalUuid(envelope.get("event_id"), "event_id");
        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("Schedule effect payload is required");
        }
        long semesterId = integer(payload.get("semester_id"), "semester_id");
        if (semesterId <= 0) {
            throw new IllegalArgumentException("semester_id must be positive");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> stringPayload = (Map<String, Object>) payload;
        return new Effect(eventId, eventType, semesterId, payloadHash(stringPayload));
    }

    private static boolean matches(SemesterArchiveEffectReceiptDocument receipt, Effect identity) {
        return identity.eventId().equals(receipt.getSourceEventId())
                && TARGET.equals(receipt.getTarget())
                && identity.eventType().equals(receipt.getSourceEventType())
                && identity.semesterId() == receipt.getSemesterId()
                && identity.payloadHash().equals(receipt.getPayloadHash());
    }

    private String payloadHash(Map<String, Object> payload) {
        try {
            byte[] canonical = CANONICAL_JSON.writeValueAsBytes(canonicalize(payload));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Schedule effect payload cannot be canonicalized", error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static Object canonicalize(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof Number) {
            throw new IllegalArgumentException("Schedule effect payload must not contain non-integer numbers");
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Schedule effect payload object keys must be strings");
                }
                sorted.put(key, canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> canonical = new ArrayList<>(list.size());
            list.forEach(item -> canonical.add(canonicalize(item)));
            return canonical;
        }
        throw new IllegalArgumentException("Unsupported Schedule effect payload value: "
                + value.getClass().getSimpleName());
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

    private static String canonicalUuid(Object value, String field) {
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

    private record Effect(String eventId, String eventType, long semesterId, String payloadHash) { }
}
