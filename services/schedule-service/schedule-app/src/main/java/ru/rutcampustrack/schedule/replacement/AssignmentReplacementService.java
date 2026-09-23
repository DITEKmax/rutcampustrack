package ru.rutcampustrack.schedule.replacement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.grpc.PreparedAssignmentCloseResponse;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;
import ru.rutcampustrack.schedule.grpc.CommitAssignmentCloseRequest;
import ru.rutcampustrack.schedule.grpc.InstallAssignmentCloseCapRequest;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local Schedule half of the Academic replacement protocol. Academic reads
 * happen before a transaction starts; Schedule locks its fence pair, recurring
 * templates, occurrences and current lessons in that order.
 */
@Service
public class AssignmentReplacementService {

    private static final String OPERATION_GUC =
            "rutcampustrack.schedule_assignment_rebind_operation_id";

    private final JdbcTemplate jdbc;
    private final AcademicGrpcClient academicGrpcClient;
    private final TransactionTemplate transaction;

    public AssignmentReplacementService(JdbcTemplate jdbc,
                                        AcademicGrpcClient academicGrpcClient,
                                        PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.academicGrpcClient = academicGrpcClient;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public AssignmentCloseReceipt install(InstallAssignmentCloseCapRequest request) {
        UUID operationId = parseUuid(request.getOperationId());
        byte[] payloadHash = request.getPayloadHash().toByteArray();
        requireHash(payloadHash);
        LocalDate effectiveFrom = parseDate(request.getEffectiveFrom());

        PreparedAssignmentCloseResponse authority = academicGrpcClient
                .getPreparedAssignmentCloseOperation(operationId, request.getSourceAssignmentId());
        verifyAuthority(authority, operationId, request.getSourceAssignmentId(),
                request.getTargetAssignmentId(), effectiveFrom, payloadHash);
        return transaction.execute(status -> applyLocal(authority, payloadHash));
    }

    public AssignmentCloseReceipt commit(CommitAssignmentCloseRequest request) {
        UUID operationId = parseUuid(request.getOperationId());
        byte[] payloadHash = request.getPayloadHash().toByteArray();
        requireHash(payloadHash);

        // Read without a row lock before crossing the RPC boundary. The local
        // operation is locked again only after Academic returns its authority.
        List<Map<String, Object>> localRows = jdbc.queryForList("""
                SELECT payload_hash, source_assignment_id, target_assignment_id,
                       source_teacher_id, target_teacher_id, group_id, subject_id,
                       semester_id, lesson_type, source_valid_from, valid_until_exclusive,
                       effective_from
                  FROM schedule_assignment_replacement_operations
                 WHERE operation_id = ?
                """, operationId);
        if (localRows.isEmpty()) throw new ConflictException("Операция замены не применена в Schedule");
        Map<String, Object> localAuthority = localRows.get(0);
        long sourceAssignmentId = number(localAuthority.get("source_assignment_id"));
        PreparedAssignmentCloseResponse authority = academicGrpcClient
                .getPreparedAssignmentCloseOperation(operationId, sourceAssignmentId);
        verifyStoredAuthority(authority, operationId, payloadHash, localAuthority);
        return transaction.execute(status -> commitLocal(operationId, payloadHash, authority));
    }

    private AssignmentCloseReceipt applyLocal(PreparedAssignmentCloseResponse authority,
                                               byte[] payloadHash) {
        UUID operationId = parseUuid(authority.getOperationId());
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT payload_hash, source_assignment_id, target_assignment_id,
                       source_teacher_id, target_teacher_id, group_id, subject_id,
                       semester_id, lesson_type, source_valid_from, valid_until_exclusive,
                       effective_from, state, moved_count, skipped_count
                  FROM schedule_assignment_replacement_operations
                 WHERE operation_id = ?
                 FOR UPDATE
                """, operationId);
        if (existing.isEmpty()) {
            insertOperation(authority, payloadHash);
        }
        // INSERT .. ON CONFLICT waits for an identical concurrent install;
        // this fresh statement then observes its committed durable receipt.
        existing = jdbc.queryForList("""
                SELECT payload_hash, source_assignment_id, target_assignment_id,
                       source_teacher_id, target_teacher_id, group_id, subject_id,
                       semester_id, lesson_type, source_valid_from, valid_until_exclusive,
                       effective_from, state, moved_count, skipped_count
                  FROM schedule_assignment_replacement_operations
                 WHERE operation_id = ?
                 FOR UPDATE
                """, operationId);
        if (existing.isEmpty()) throw new ConflictException("Не удалось записать операцию замены");
        Map<String, Object> operation = existing.get(0);
        requireOperationTuple(operation, authority, payloadHash);
        String operationState = String.valueOf(operation.get("state"));
        if ("APPLIED".equals(operationState) || "COMMITTED".equals(operationState)) {
            return receipt(operationId, operationState,
                    number(operation.get("moved_count")), number(operation.get("skipped_count")));
        }

        // The operation UUID only selects a durable ledger row in the guards;
        // it is never sufficient permission to rewrite a snapshot.
        jdbc.queryForObject("SELECT set_config(?, ?, true)", String.class,
                OPERATION_GUC, operationId.toString());

        Map<Long, Map<String, Object>> fences = lockOrInstallFencePair(authority);
        LocalDate effectiveFrom = parseDate(authority.getEffectiveFrom());
        LocalDate sourceCreationCap = date(fences.get(authority.getSourceAssignmentId())
                .get("creation_cap_until_exclusive"));
        if (sourceCreationCap.isBefore(effectiveFrom)) {
            throw new ConflictException("Исходное назначение уже ограничено более ранней датой");
        }
        if (sourceCreationCap.isAfter(effectiveFrom)) {
            int narrowed = jdbc.update("""
                    UPDATE schedule_assignment_fences
                       SET creation_cap_until_exclusive = ?, revision = revision + 1
                     WHERE assignment_id = ? AND creation_cap_until_exclusive = ?
                    """, effectiveFrom, authority.getSourceAssignmentId(), sourceCreationCap);
            if (narrowed != 1) throw new ConflictException("Fence назначения изменился во время замены");
        }

        // This call is only the durable barrier. Academic activates the target
        // assignment after it receives this receipt; physical rows and
        // recurring templates stay on the source until commit() obtains that
        // exact APPLIED Academic tuple.
        jdbc.update("""
                UPDATE schedule_assignment_replacement_operations
                   SET state = 'APPLIED', updated_at = NOW()
                 WHERE operation_id = ? AND state = 'PREPARED'
                """, operationId);
        return receipt(operationId, "APPLIED", 0, 0);
    }

    private Map<Long, Map<String, Object>> lockOrInstallFencePair(
            PreparedAssignmentCloseResponse authority) {
        long sourceId = authority.getSourceAssignmentId();
        long targetId = authority.getTargetAssignmentId();
        long[] ids = new long[] {sourceId, targetId};
        java.util.Arrays.sort(ids);
        LocalDate sourceFrom = parseDate(authority.getSourceValidFrom());
        LocalDate effectiveFrom = parseDate(authority.getEffectiveFrom());
        LocalDate end = parseDate(authority.getTargetValidUntilExclusive());
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (long assignmentId : ids) {
            boolean source = assignmentId == sourceId;
            long teacherId = source ? authority.getSourceTeacherId() : authority.getTargetTeacherId();
            LocalDate validFrom = source ? sourceFrom : effectiveFrom;
            LocalDate creationCap = source ? end : end;
            jdbc.update("""
                    INSERT INTO schedule_assignment_fences
                        (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                         lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (assignment_id) DO NOTHING
                    """, assignmentId, authority.getGroupId(), authority.getSubjectId(),
                    authority.getSemesterId(), teacherId, authority.getLessonType(), validFrom,
                    end, creationCap);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                           lesson_type, valid_from, cap_until_exclusive,
                           creation_cap_until_exclusive
                      FROM schedule_assignment_fences
                     WHERE assignment_id = ?
                     FOR UPDATE
                    """, assignmentId);
            if (rows.isEmpty()) throw new ConflictException("Fence назначения отсутствует");
            Map<String, Object> fence = rows.get(0);
            requireEqual(fence.get("assignment_id"), assignmentId, "assignment");
            requireEqual(fence.get("group_id"), authority.getGroupId(), "group");
            requireEqual(fence.get("subject_id"), authority.getSubjectId(), "subject");
            requireEqual(fence.get("semester_id"), authority.getSemesterId(), "semester");
            requireEqual(fence.get("assigned_teacher_id"), teacherId, "teacher");
            requireEqual(String.valueOf(fence.get("lesson_type")), authority.getLessonType(), "lesson type");
            requireEqual(fence.get("valid_from"), validFrom, "validFrom");
            requireEqual(fence.get("cap_until_exclusive"), end, "retention cap");
            LocalDate localCreationCap = date(fence.get("creation_cap_until_exclusive"));
            if (source && localCreationCap.isBefore(effectiveFrom)) {
                throw new ConflictException("Fence исходного назначения уже закрыт");
            }
            if (!source && !localCreationCap.equals(end)) {
                throw new ConflictException("Fence нового назначения имеет другую creation cap");
            }
            result.put(assignmentId, fence);
        }
        return result;
    }

    private Map<Long, Map<String, Object>> lockCurrentLessons(List<Map<String, Object>> occurrences) {
        List<Long> ids = occurrences.stream()
                .map(row -> nullableNumber(row.get("current_lesson_id")))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, status::text AS status, revision
                  FROM lessons
                 WHERE id IN (""" + placeholders + ") ORDER BY id FOR UPDATE", ids.toArray());
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) result.put(number(row.get("id")), row);
        return result;
    }

    private void insertOperation(PreparedAssignmentCloseResponse authority, byte[] payloadHash) {
        jdbc.update("""
                INSERT INTO schedule_assignment_replacement_operations
                    (operation_id, payload_hash, source_assignment_id, target_assignment_id,
                     source_teacher_id, target_teacher_id, group_id, subject_id, semester_id,
                     lesson_type, source_valid_from, valid_until_exclusive, effective_from, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PREPARED')
                ON CONFLICT (operation_id) DO NOTHING
                """, parseUuid(authority.getOperationId()), payloadHash,
                authority.getSourceAssignmentId(), authority.getTargetAssignmentId(),
                authority.getSourceTeacherId(), authority.getTargetTeacherId(),
                authority.getGroupId(), authority.getSubjectId(), authority.getSemesterId(),
                authority.getLessonType(), parseDate(authority.getSourceValidFrom()),
                parseDate(authority.getTargetValidUntilExclusive()),
                parseDate(authority.getEffectiveFrom()));
    }

    private long targetItem(UUID operationId, Map<String, Object> source, long targetAssignmentId) {
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT target_schedule_item_id, source_was_active
                  FROM schedule_assignment_replacement_templates
                 WHERE operation_id = ? AND source_schedule_item_id = ?
                """, operationId, number(source.get("id")));
        boolean sourceWasActive = Boolean.TRUE.equals(source.get("is_active"));
        if (!existing.isEmpty()) {
            if (Boolean.TRUE.equals(existing.get(0).get("source_was_active")) != sourceWasActive) {
                throw new ConflictException("Состояние recurring-шаблона изменилось во время замены");
            }
            return number(existing.get(0).get("target_schedule_item_id"));
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active,
                     created_at, generated_count, generated_from, generated_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS week_type), ?, FALSE, NOW(), 0, NULL, NULL)
                RETURNING id
                """, Long.class, targetAssignmentId, number(source.get("group_id")),
                number(source.get("subject_id")), number(source.get("semester_id")),
                number(source.get("day_of_week")), number(source.get("lesson_number")),
                source.get("start_time"), source.get("end_time"), source.get("week_type"),
                source.get("room"));
        if (id == null) throw new ConflictException("Не удалось создать recurring-шаблон назначения");
        jdbc.update("""
                INSERT INTO schedule_assignment_replacement_templates
                    (operation_id, source_schedule_item_id, target_schedule_item_id, source_was_active)
                VALUES (?, ?, ?, ?)
                """, operationId, number(source.get("id")), id, sourceWasActive);
        return id;
    }

    private void insertLedger(UUID operationId,
                              long sourceItemId,
                              long targetItemId,
                              long occurrenceId,
                              Long lessonId,
                              LocalDate date,
                              long occurrenceRevision,
                              Long lessonRevision,
                              String result,
                              PreparedAssignmentCloseResponse authority) {
        boolean moved = "MOVED".equals(result);
        jdbc.update("""
                INSERT INTO schedule_assignment_rebind_ledger
                    (operation_id, source_schedule_item_id, target_schedule_item_id,
                     occurrence_id, lesson_id, occurrence_date,
                     expected_occurrence_revision, after_occurrence_revision,
                     expected_lesson_revision, after_lesson_revision,
                     before_schedule_item_id, after_schedule_item_id,
                     before_assignment_id, after_assignment_id,
                     before_teacher_id, after_teacher_id, result)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, operationId, sourceItemId, targetItemId, occurrenceId, lessonId, date,
                occurrenceRevision, occurrenceRevision + (moved ? 1 : 0),
                lessonRevision, lessonRevision == null ? null : lessonRevision + (moved ? 1 : 0),
                sourceItemId, moved ? targetItemId : sourceItemId,
                authority.getSourceAssignmentId(),
                moved ? authority.getTargetAssignmentId() : authority.getSourceAssignmentId(),
                authority.getSourceTeacherId(),
                moved ? authority.getTargetTeacherId() : authority.getSourceTeacherId(), result);
    }

    private AssignmentCloseReceipt commitLocal(UUID operationId,
                                               byte[] payloadHash,
                                               PreparedAssignmentCloseResponse authority) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT payload_hash, source_assignment_id, target_assignment_id,
                       source_teacher_id, target_teacher_id, group_id, subject_id,
                       semester_id, lesson_type, source_valid_from, valid_until_exclusive,
                       effective_from, state, moved_count, skipped_count
                  FROM schedule_assignment_replacement_operations
                 WHERE operation_id = ?
                 FOR UPDATE
                """, operationId);
        if (rows.isEmpty()) throw new ConflictException("Операция замены не подготовлена");
        Map<String, Object> row = rows.get(0);
        if (!MessageDigest.isEqual((byte[]) row.get("payload_hash"), payloadHash)) {
            throw new ConflictException("Операция замены использует другой payload");
        }
        requireOperationTuple(row, authority, payloadHash);
        String state = String.valueOf(row.get("state"));
        if ("PREPARED".equals(state)) throw new ConflictException("Операция ещё не применена");
        if (!"COMMITTED".equals(state)) {
            Map<Long, Map<String, Object>> fences = lockOrInstallFencePair(authority);
            LocalDate creationCap = date(fences.get(authority.getSourceAssignmentId())
                    .get("creation_cap_until_exclusive"));
            if (!creationCap.equals(parseDate(authority.getEffectiveFrom()))) {
                throw new ConflictException("Fence исходного назначения не удерживает дату замены");
            }
            jdbc.queryForObject("SELECT set_config(?, ?, true)", String.class,
                    OPERATION_GUC, operationId.toString());
            int[] counts = rebindFutureOccurrences(operationId, authority);
            jdbc.update("""
                UPDATE schedule_items item
                   SET is_active = FALSE
                 WHERE item.id IN (SELECT source_schedule_item_id
                                     FROM schedule_assignment_replacement_templates
                                    WHERE operation_id = ? AND source_was_active)
                """, operationId);
            jdbc.update("""
                UPDATE schedule_items item
                   SET is_active = TRUE
                 WHERE item.id IN (SELECT target_schedule_item_id
                                     FROM schedule_assignment_replacement_templates
                                    WHERE operation_id = ? AND source_was_active)
                """, operationId);
            jdbc.update("""
                    UPDATE schedule_assignment_replacement_operations
                       SET state = 'COMMITTED', moved_count = ?, skipped_count = ?, updated_at = NOW()
                     WHERE operation_id = ? AND state = 'APPLIED'
                    """, counts[0], counts[1], operationId);
            state = "COMMITTED";
            return receipt(operationId, state, counts[0], counts[1]);
        }
        return receipt(operationId, state, number(row.get("moved_count")),
                number(row.get("skipped_count")));
    }

    private int[] rebindFutureOccurrences(UUID operationId,
                                          PreparedAssignmentCloseResponse authority) {
        List<Map<String, Object>> sourceItems = jdbc.queryForList("""
                SELECT source_item.id, source_item.group_id, source_item.subject_id,
                       source_item.semester_id, source_item.day_of_week, source_item.lesson_number,
                       source_item.start_time, source_item.end_time,
                       source_item.week_type::text AS week_type, source_item.room, source_item.is_active
                  FROM schedule_items source_item
                 WHERE source_item.assignment_id = ?
                 ORDER BY source_item.id
                 FOR UPDATE OF source_item
                """, authority.getSourceAssignmentId());
        int moved = 0;
        int skipped = 0;
        LocalDate effectiveFrom = parseDate(authority.getEffectiveFrom());
        for (Map<String, Object> sourceItem : sourceItems) {
            long sourceItemId = number(sourceItem.get("id"));
            long targetItemId = targetItem(operationId, sourceItem,
                    authority.getTargetAssignmentId());
            List<Map<String, Object>> occurrences = jdbc.queryForList("""
                    SELECT id AS occurrence_id, occurrence_date, current_lesson_id, revision
                      FROM lesson_occurrences
                     WHERE schedule_item_id = ? AND occurrence_date >= ?
                       AND occurrence_date < ?
                     ORDER BY id
                     FOR UPDATE
                    """, sourceItemId, effectiveFrom,
                    parseDate(authority.getTargetValidUntilExclusive()));
            Map<Long, Map<String, Object>> lessons = lockCurrentLessons(occurrences);
            for (Map<String, Object> occurrence : occurrences) {
                long occurrenceId = number(occurrence.get("occurrence_id"));
                LocalDate date = date(occurrence.get("occurrence_date"));
                Long lessonId = nullableNumber(occurrence.get("current_lesson_id"));
                long occurrenceRevision = number(occurrence.get("revision"));
                Map<String, Object> lesson = lessonId == null ? null : lessons.get(lessonId);
                if (lessonId != null && lesson == null) {
                    throw new ConflictException("Текущая физическая пара исчезла во время замены");
                }
                String lessonStatus = lesson == null ? "" : String.valueOf(lesson.get("status"));
                boolean eligible = lesson != null && "planned".equalsIgnoreCase(lessonStatus);
                String result = eligible ? "MOVED" : skipResult(lessonStatus);
                Long lessonRevision = lesson == null ? null : number(lesson.get("revision"));
                insertLedger(operationId, sourceItemId, targetItemId, occurrenceId, lessonId,
                        date, occurrenceRevision, lessonRevision, result, authority);
                if (eligible) {
                    int occurrenceUpdated = jdbc.update("""
                            UPDATE lesson_occurrences
                               SET schedule_item_id = ?, assignment_id = ?, assigned_teacher_id = ?,
                                   revision = revision + 1
                             WHERE id = ? AND schedule_item_id = ? AND assignment_id = ?
                               AND occurrence_date = ? AND revision = ?
                            """, targetItemId, authority.getTargetAssignmentId(),
                            authority.getTargetTeacherId(), occurrenceId, sourceItemId,
                            authority.getSourceAssignmentId(), date, occurrenceRevision);
                    int lessonUpdated = jdbc.update("""
                            UPDATE lessons
                               SET schedule_item_id = ?, assignment_id = ?, assigned_teacher_id = ?,
                                   revision = revision + 1
                             WHERE id = ? AND occurrence_id = ? AND schedule_item_id = ?
                               AND assignment_id = ? AND assigned_teacher_id = ?
                               AND status::text = 'planned' AND date = ? AND revision = ?
                            """, targetItemId, authority.getTargetAssignmentId(),
                            authority.getTargetTeacherId(), lessonId, occurrenceId, sourceItemId,
                            authority.getSourceAssignmentId(), authority.getSourceTeacherId(),
                            date, lessonRevision);
                    if (occurrenceUpdated != 1 || lessonUpdated != 1) {
                        throw new ConflictException("Плановая пара изменилась во время замены");
                    }
                    moved++;
                } else {
                    skipped++;
                }
            }
        }
        return new int[] {moved, skipped};
    }

    private static String skipResult(String lessonStatus) {
        if ("cancelled".equalsIgnoreCase(lessonStatus)) return "SKIPPED_CANCELLED";
        if ("active".equalsIgnoreCase(lessonStatus)) return "SKIPPED_ACTIVE";
        if ("closed".equalsIgnoreCase(lessonStatus)) return "SKIPPED_CLOSED";
        if (lessonStatus == null || lessonStatus.isBlank()) return "SKIPPED_NO_CURRENT_LESSON";
        return "SKIPPED_ALREADY_STARTED";
    }

    private static void requireOperationTuple(Map<String, Object> row,
                                              PreparedAssignmentCloseResponse authority,
                                              byte[] payloadHash) {
        if (!MessageDigest.isEqual((byte[]) row.get("payload_hash"), payloadHash)
                || number(row.get("source_assignment_id")) != authority.getSourceAssignmentId()
                || number(row.get("target_assignment_id")) != authority.getTargetAssignmentId()
                || number(row.get("source_teacher_id")) != authority.getSourceTeacherId()
                || number(row.get("target_teacher_id")) != authority.getTargetTeacherId()
                || number(row.get("group_id")) != authority.getGroupId()
                || number(row.get("subject_id")) != authority.getSubjectId()
                || number(row.get("semester_id")) != authority.getSemesterId()
                || !String.valueOf(row.get("lesson_type")).equals(authority.getLessonType())
                || !date(row.get("source_valid_from")).equals(parseDate(authority.getSourceValidFrom()))
                || !date(row.get("valid_until_exclusive")).equals(
                        parseDate(authority.getTargetValidUntilExclusive()))
                || !date(row.get("effective_from")).equals(parseDate(authority.getEffectiveFrom()))) {
            throw new ConflictException("Повтор операции замены имеет другую immutable authority");
        }
    }

    private static void verifyAuthority(PreparedAssignmentCloseResponse authority,
                                        UUID operationId,
                                        long sourceAssignmentId,
                                        long targetAssignmentId,
                                        LocalDate effectiveFrom,
                                        byte[] payloadHash) {
        if (!operationId.toString().equals(authority.getOperationId())
                || authority.getSourceAssignmentId() != sourceAssignmentId
                || authority.getTargetAssignmentId() != targetAssignmentId
                || !effectiveFrom.toString().equals(authority.getEffectiveFrom())
                || !MessageDigest.isEqual(payloadHash, authority.getPayloadHash().toByteArray())
                || !"PREPARED".equals(authority.getState())
                || !"PREPARED".equals(authority.getTargetLifecycleState())
                || authority.getSourceValidFrom().isBlank()
                || authority.getTargetValidUntilExclusive().isBlank()) {
            throw new ConflictException("Academic authority does not match replacement request");
        }
        LocalDate from = parseDate(authority.getSourceValidFrom());
        LocalDate sourceEnd = parseDate(authority.getSourceValidUntilExclusive());
        LocalDate targetEnd = parseDate(authority.getTargetValidUntilExclusive());
        if (effectiveFrom.isBefore(from) || effectiveFrom.isAfter(sourceEnd)
                || !targetEnd.equals(sourceEnd) || !effectiveFrom.isBefore(targetEnd)) {
            throw new ConflictException("Academic replacement interval is not an exact source subinterval");
        }
    }

    private static void verifyStoredAuthority(PreparedAssignmentCloseResponse authority,
                                              UUID operationId,
                                              byte[] payloadHash,
                                              Map<String, Object> localAuthority) {
        if (!operationId.toString().equals(authority.getOperationId())) {
            throw new ConflictException("Academic authority does not match the stored replacement");
        }
        requireOperationTuple(localAuthority, authority, payloadHash);
        if ((!"APPLIED".equals(authority.getState()) && !"COMMITTED".equals(authority.getState()))
                || !"ACTIVE".equals(authority.getTargetLifecycleState())
                || !authority.getEffectiveFrom().equals(authority.getSourceValidUntilExclusive())) {
            throw new ConflictException("Academic target is not active at the exact source close date");
        }
    }

    private static AssignmentCloseReceipt receipt(UUID operationId, String state,
                                                  long moved, long skipped) {
        return AssignmentCloseReceipt.newBuilder().setOperationId(operationId.toString())
                .setState(state).setMovedCount(moved).setSkippedCount(skipped).build();
    }

    private static UUID parseUuid(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException error) { throw new ConflictException("operation_id must be a UUID"); }
    }

    private static LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (RuntimeException error) { throw new ConflictException("replacement dates must be ISO dates"); }
    }

    private static void requireHash(byte[] hash) {
        if (hash.length != 32) throw new ConflictException("payload hash is invalid");
    }

    private static void requireEqual(Object actual, Object expected, String field) {
        Object normalizedActual = actual instanceof java.sql.Date date ? date.toLocalDate() : actual;
        if (!java.util.Objects.equals(normalizedActual, expected)) {
            throw new ConflictException("Schedule replacement " + field + " does not match Academic authority");
        }
    }

    private static LocalDate date(Object value) {
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof LocalDate date) return date;
        return LocalDate.parse(String.valueOf(value));
    }

    private static Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }
}
