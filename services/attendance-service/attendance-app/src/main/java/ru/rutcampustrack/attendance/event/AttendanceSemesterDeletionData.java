package ru.rutcampustrack.attendance.event;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Reads, versions and removes Attendance records owned by one semester. */
@Component
public class AttendanceSemesterDeletionData {

    private static final String ATTENDANCES = "attendances";
    private static final String EXCUSE_TICKETS = "excuse_tickets";
    private static final String LATE_CHECKIN_REQUESTS = "late_checkin_requests";
    private static final String REQUEST_ATTACHMENTS = "request_attachments";
    private static final String LATE_CHECKIN_BUDGETS = "student_late_checkin_budgets";
    private static final String DIGEST_VERSION = "attendance-semester-domain-v1";

    private final MongoTemplate mongoTemplate;

    public AttendanceSemesterDeletionData(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Captures only business rows that a delete participant would remove.
     * Control-plane fences, receipts, and outbox rows are intentionally absent.
     */
    @Transactional(transactionManager = "mongoTransactionManager", readOnly = true)
    public Snapshot preview(long semesterId) {
        requireSemesterId(semesterId);
        return snapshot(loadRows(semesterId));
    }

    @Transactional(transactionManager = "mongoTransactionManager", readOnly = true)
    public Preview previewWithFenceVersion(long semesterId) {
        requireSemesterId(semesterId);
        Snapshot snapshot = snapshot(loadRows(semesterId));
        SemesterArchiveFenceDocument fence = mongoTemplate.findById(
                Long.toString(semesterId), SemesterArchiveFenceDocument.class);
        Long stateVersion = fence == null ? null : fence.getStateVersion();
        SemesterDeletionTombstoneDocument tombstone = mongoTemplate.findById(
                Long.toString(semesterId), SemesterDeletionTombstoneDocument.class);
        if (tombstone != null && (stateVersion == null || tombstone.getStateVersion() > stateVersion)) {
            // A permanent tombstone remains authoritative when the fence row
            // is missing or damaged after commit.
            stateVersion = tombstone.getStateVersion();
        }
        if (stateVersion != null && stateVersion < 0) {
            throw new IllegalStateException("Attendance semester fence version must not be negative");
        }
        return new Preview(snapshot, stateVersion == null ? 0L : stateVersion);
    }

    /**
     * Deletes the exact scoped row set only when it still matches the sealed
     * participant digest. The caller owns the surrounding Mongo transaction.
     */
    public Snapshot delete(long semesterId, String expectedDigest) {
        requireSemesterId(semesterId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Attendance semester deletion requires an active Mongo transaction");
        }
        if (expectedDigest == null || !expectedDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expected participant digest must be lowercase SHA-256");
        }

        DomainRows rows = loadRows(semesterId);
        Snapshot current = snapshot(rows);
        if (!MessageDigest.isEqual(current.participantDigest().getBytes(StandardCharsets.US_ASCII),
                expectedDigest.getBytes(StandardCharsets.US_ASCII))) {
            throw new DigestMismatchException(current);
        }

        removeRows(rows.rows(ATTENDANCES), ATTENDANCES);
        removeRows(rows.rows(EXCUSE_TICKETS), EXCUSE_TICKETS);
        removeRows(rows.rows(LATE_CHECKIN_REQUESTS), LATE_CHECKIN_REQUESTS);
        removeRows(rows.rows(REQUEST_ATTACHMENTS), REQUEST_ATTACHMENTS);
        removeRows(rows.rows(LATE_CHECKIN_BUDGETS), LATE_CHECKIN_BUDGETS);
        return current;
    }

    private DomainRows loadRows(long semesterId) {
        List<DomainRow> marks = findRows(ATTENDANCES, semesterId);
        List<DomainRow> excuses = findRows(EXCUSE_TICKETS, semesterId);
        List<DomainRow> lateRequests = findRows(LATE_CHECKIN_REQUESTS, semesterId);
        List<DomainRow> budgets = findRows(LATE_CHECKIN_BUDGETS, semesterId);

        Map<String, Set<Long>> attachmentOwners = new HashMap<>();
        for (DomainRow ticket : excuses) {
            long ownerId = positiveLong(ticket.document(), "student_id", EXCUSE_TICKETS);
            String requestId = excuseRequestId(ticket.document());
            attachmentOwners.computeIfAbsent(requestId, ignored -> new HashSet<>()).add(ownerId);
        }
        for (DomainRow mark : marks) {
            long ownerId = positiveLong(mark.document(), "user_id", ATTENDANCES);
            long lessonId = positiveLong(mark.document(), "lesson_id", ATTENDANCES);
            attachmentOwners.computeIfAbsent(ownerId + ":" + lessonId, ignored -> new HashSet<>()).add(ownerId);
        }
        for (DomainRow request : lateRequests) {
            positiveLong(request.document(), "student_id", LATE_CHECKIN_REQUESTS);
        }
        for (DomainRow budget : budgets) {
            positiveLong(budget.document(), "student_id", LATE_CHECKIN_BUDGETS);
        }

        List<DomainRow> attachments = new ArrayList<>(findSemesterAttachments(semesterId));
        attachments.addAll(findLegacyAttachments(attachmentOwners));
        Set<Long> cancelledLessonIds = findCancellationMarkerLessonIds(semesterId);
        attachments.addAll(findMarkerBackedLegacyAttachments(cancelledLessonIds));
        attachments = attachments.stream()
                .collect(java.util.stream.Collectors.toMap(DomainRow::identity, row -> row,
                        (first, ignored) -> first, TreeMap::new))
                .values().stream().toList();
        for (DomainRow attachment : attachments) {
            positiveLong(attachment.document(), "owner_student_id", REQUEST_ATTACHMENTS);
            requiredString(attachment.document(), "request_id", REQUEST_ATTACHMENTS);
            if (!hasUsableAttachmentVersion(attachment.document())) {
                throw new IllegalStateException("Semester deletion found an attachment without a content version");
            }
        }

        return new DomainRows(Map.of(
                ATTENDANCES, marks,
                EXCUSE_TICKETS, excuses,
                LATE_CHECKIN_REQUESTS, lateRequests,
                REQUEST_ATTACHMENTS, attachments,
                LATE_CHECKIN_BUDGETS, budgets));
    }

    private List<DomainRow> findRows(String collection, long semesterId) {
        Query query = Query.query(Criteria.where("semester_id").is(semesterId))
                .with(Sort.by(Sort.Direction.ASC, "_id"));
        if (REQUEST_ATTACHMENTS.equals(collection)) {
            // Hash the validated SHA-256 content version without transferring
            // up to 10 MiB of binary data for every attachment in a preview.
            query.fields().exclude("data");
        }
        return mongoTemplate.find(query, Document.class, collection).stream()
                .map(document -> new DomainRow(collection, document))
                .toList();
    }

    private List<DomainRow> findSemesterAttachments(long semesterId) {
        Query query = Query.query(Criteria.where("semester_id").is(semesterId))
                .with(Sort.by(Sort.Direction.ASC, "_id"));
        query.fields().exclude("data");
        return mongoTemplate.find(query, Document.class, REQUEST_ATTACHMENTS).stream()
                .map(document -> new DomainRow(REQUEST_ATTACHMENTS, document))
                .toList();
    }

    private List<DomainRow> findLegacyAttachments(Map<String, Set<Long>> attachmentOwners) {
        if (attachmentOwners.isEmpty()) {
            return List.of();
        }
        Set<Long> ownerIds = new HashSet<>();
        attachmentOwners.values().forEach(ownerIds::addAll);
        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("semester_id").is(null),
                        Criteria.where("owner_student_id").in(ownerIds),
                        Criteria.where("request_id").in(attachmentOwners.keySet())))
                .with(Sort.by(Sort.Direction.ASC, "_id"));
        query.fields().exclude("data");
        return mongoTemplate.find(query, Document.class, REQUEST_ATTACHMENTS).stream()
                .filter(document -> {
                    String requestId = stringValue(document.get("request_id"));
                    Long ownerId = nullableLong(document.get("owner_student_id"));
                    return ownerId != null && attachmentOwners.getOrDefault(requestId, Set.of()).contains(ownerId);
                })
                .map(document -> new DomainRow(REQUEST_ATTACHMENTS, document))
                .toList();
    }

    private Set<Long> findCancellationMarkerLessonIds(long semesterId) {
        Query query = Query.query(Criteria.where("semester_id").is(semesterId))
                .with(Sort.by(Sort.Direction.ASC, "lesson_id"));
        return mongoTemplate.find(query, LessonCancellationMarker.class).stream()
                .map(LessonCancellationMarker::getLessonId)
                .filter(lessonId -> lessonId != null && lessonId > 0)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Legacy pair attachments can outlive a deleted mark. A durable cancellation
     * marker is the only remaining authority that can prove their semester and
     * physical lesson; owner must still exactly match the pair key.
     */
    private List<DomainRow> findMarkerBackedLegacyAttachments(Set<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        String lessonAlternation = lessonIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("semester_id").is(null),
                        Criteria.where("request_id").regex("^[1-9][0-9]*:(?:" + lessonAlternation + ")$")))
                .with(Sort.by(Sort.Direction.ASC, "_id"));
        query.fields().exclude("data");
        return mongoTemplate.find(query, Document.class, REQUEST_ATTACHMENTS).stream()
                .filter(document -> {
                    Long ownerId = exactPositiveOwnerId(document.get("owner_student_id"));
                    String requestId = stringValue(document.get("request_id"));
                    return ownerId != null && lessonIds.stream().anyMatch(lessonId ->
                            PairWriteCoordinator.pairId(ownerId, lessonId).equals(requestId));
                })
                .map(document -> new DomainRow(REQUEST_ATTACHMENTS, document))
                .toList();
    }

    private Snapshot snapshot(DomainRows rows) {
        List<DomainRow> ordered = rows.allRows().stream()
                .sorted(Comparator.comparing(DomainRow::collection).thenComparing(DomainRow::identity))
                .toList();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            writeToken(digest, DIGEST_VERSION);
            for (DomainRow row : ordered) {
                writeToken(digest, row.collection());
                writeValue(digest, row.document());
            }
            return new Snapshot(rows.rows(ATTENDANCES).size(),
                    rows.rows(EXCUSE_TICKETS).size() + (long) rows.rows(LATE_CHECKIN_REQUESTS).size(),
                    hex(digest.digest()));
        } catch (NoSuchAlgorithmException | IOException error) {
            throw new IllegalStateException("Could not calculate Attendance semester deletion digest", error);
        }
    }

    private void removeRows(List<DomainRow> rows, String collection) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object> ids = rows.stream().map(row -> row.document().get("_id")).toList();
        long deleted = mongoTemplate.remove(Query.query(Criteria.where("_id").in(ids)), collection)
                .getDeletedCount();
        if (deleted != rows.size()) {
            throw new IllegalStateException("Attendance semester deletion row set changed during transaction");
        }
    }

    private static boolean hasUsableAttachmentVersion(Document document) {
        String sha256 = stringValue(document.get("sha256"));
        return sha256 != null && sha256.matches("[0-9a-f]{64}");
    }

    private static long positiveLong(Document document, String field, String collection) {
        Long value = nullableLong(document.get(field));
        if (value == null || value <= 0) {
            throw new IllegalStateException("Semester deletion found an unowned " + collection + " row");
        }
        return value;
    }

    private static String requiredString(Document document, String field, String collection) {
        String value = stringValue(document.get(field));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Semester deletion found an unowned " + collection + " row");
        }
        return value;
    }

    private static String excuseRequestId(Document ticket) {
        Object id = ticket.get("_id");
        if (id instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (id instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        throw new IllegalStateException("Semester deletion found an excuse ticket without its request identity");
    }

    private static Long nullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static Long exactPositiveOwnerId(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        try {
            long ownerId = new BigDecimal(number.toString()).longValueExact();
            return ownerId > 0 ? ownerId : null;
        } catch (NumberFormatException | ArithmeticException invalidOwnerId) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static void writeValue(MessageDigest digest, Object value) throws IOException {
        if (value == null) {
            writeToken(digest, "null");
        } else if (value instanceof Map<?, ?> map) {
            writeToken(digest, "map");
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), item));
            writeInteger(digest, sorted.size());
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                writeToken(digest, entry.getKey());
                writeValue(digest, entry.getValue());
            }
        } else if (value instanceof Collection<?> collection) {
            writeToken(digest, "list");
            writeInteger(digest, collection.size());
            for (Object item : collection) {
                writeValue(digest, item);
            }
        } else if (value instanceof Date date) {
            writeToken(digest, "date");
            writeToken(digest, Long.toString(date.getTime()));
        } else if (value instanceof Instant instant) {
            writeToken(digest, "instant");
            writeToken(digest, instant.toString());
        } else if (value instanceof ObjectId objectId) {
            writeToken(digest, "object-id");
            writeToken(digest, objectId.toHexString());
        } else if (value instanceof byte[] bytes) {
            writeToken(digest, "bytes-sha256");
            writeToken(digest, hex(sha256(bytes)));
        } else if (value instanceof Number number) {
            writeToken(digest, "number:" + value.getClass().getSimpleName());
            writeToken(digest, number.toString());
        } else if (value instanceof Boolean bool) {
            writeToken(digest, "boolean");
            writeToken(digest, bool.toString());
        } else {
            writeToken(digest, "string");
            writeToken(digest, value.toString());
        }
    }

    private static void writeInteger(MessageDigest digest, int value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Integer.BYTES);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(value);
        }
        digest.update(bytes.toByteArray());
    }

    private static void writeToken(MessageDigest digest, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeInteger(digest, bytes.length);
        digest.update(bytes);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    private static void requireSemesterId(long semesterId) {
        if (semesterId <= 0) {
            throw new IllegalArgumentException("semesterId must be positive");
        }
    }

    public record Snapshot(long attendanceMarks, long studentRequests, String participantDigest) {
    }

    public record Preview(Snapshot snapshot, long observedFenceVersion) {
    }

    public static final class DigestMismatchException extends RuntimeException {
        private final Snapshot current;

        private DigestMismatchException(Snapshot current) {
            super("Attendance semester contents changed after preview");
            this.current = current;
        }

        public Snapshot current() {
            return current;
        }
    }

    private record DomainRows(Map<String, List<DomainRow>> byCollection) {
        private List<DomainRow> rows(String collection) {
            return byCollection.getOrDefault(collection, List.of());
        }

        private List<DomainRow> allRows() {
            return byCollection.values().stream().flatMap(List::stream).toList();
        }
    }

    private record DomainRow(String collection, Document document) {
        private String identity() {
            Object rawId = document.get("_id");
            return rawId == null ? "" : rawId.getClass().getName() + ":" + rawId;
        }
    }
}
