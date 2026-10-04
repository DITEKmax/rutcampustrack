package ru.rutcampustrack.schedule.lesson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonResponse;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.InvalidLessonStateException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.event.LessonTransferRequestedEvent;
import ru.rutcampustrack.schedule.event.OneOffLessonTransferRequestedEvent;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import ru.rutcampustrack.schedule.contract.enums.LessonSlot;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PostgreSQL authority for transfer replay, generation, lifecycle, batches and outbox. */
@Service
public class LessonTransferWriter {

    public static final int BINDINGS_PER_BATCH = 64;
    private static final String TRANSFER_SETTING = "rutcampustrack.lesson_transfer_operation_id";
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduleSemesterArchiveWriteFence archiveWriteFence;

    public LessonTransferWriter(JdbcTemplate jdbc,
                                Clock clock,
                                ObjectMapper objectMapper,
                                ApplicationEventPublisher eventPublisher,
                                ScheduleSemesterArchiveWriteFence archiveWriteFence) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.archiveWriteFence = archiveWriteFence;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferLessonResponse transfer(long sourceLessonId,
                                           long actorId,
                                           TransferLessonRequest request) {
        return transferInternal(sourceLessonId, actorId, request, null, null);
    }

    /** Same V21 participant protocol, admitted by the recurring template writer. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferLessonResponse transferForTemplate(long sourceLessonId, long actorId,
            TransferLessonRequest request, UUID templateOperationId,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {
        if (templateOperationId == null || prepared == null) {
            throw new ConflictException("Не подтверждена операция изменения шаблона");
        }
        return transferInternal(sourceLessonId, actorId, request, templateOperationId, prepared);
    }

    private TransferLessonResponse transferInternal(long sourceLessonId, long actorId,
            TransferLessonRequest request, UUID templateOperationId,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {
        if (actorId <= 0) throw new IllegalArgumentException("actorId must be positive");
        byte[] requestHash = requestHash(sourceLessonId, request);
        TransferLessonResponse replay = findReplay(actorId, request.requestKey(), requestHash);
        if (replay != null) return replay;

        try {
            Map<String, Object> before = readSource(sourceLessonId);
            long occurrenceId = number(before.get("occurrence_id"));
            // Academic is checked before the local advisory lock. The exact
            // same lock as PREPARE then serializes acceptance against the
            // assignment/item/occurrence/binding lock chain.
            if (prepared == null) archiveWriteFence.lockForBusinessWrite(number(before.get("semester_id")));
            else {
                if (prepared.semesterId() != number(before.get("semester_id"))) {
                    throw new ConflictException("Подтверждён другой семестр");
                }
                archiveWriteFence.lockPreparedBusinessWrite(prepared);
            }
            lockRequestKey(actorId, request.requestKey());
            replay = findReplay(actorId, request.requestKey(), requestHash);
            if (replay != null) return replay;
            lockFences(List.of(number(before.get("assignment_id"))));
            lockOrigin(before);
            Map<String, Object> current = lockOccurrence(occurrenceId);
            Map<String, Object> source = lockSource(sourceLessonId, occurrenceId);
            requireCurrent(sourceLessonId, source, current, request.expectedRevision());
            lockBindingsForTransfer(occurrenceId, sourceLessonId);
            rejectPendingTransfer(occurrenceId);
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM lesson_homework_bindings
                        WHERE occurrence_id = ? AND pending_edit_operation_id IS NOT NULL)
                    """, Boolean.class, occurrenceId))) {
                throw new ConflictException("Домашнее задание ожидает подтверждения изменения; повтори перенос позже");
            }

            LocalDate today = LocalDate.now(clock.withZone(MOSCOW));
            LocalDate sourceDate = localDate(source.get("date"));
            boolean future = templateOperationId == null ? sourceDate.isAfter(today)
                    : sourceDate.atTime(localTime(source.get("start_time")))
                        .isAfter(java.time.LocalDateTime.now(clock.withZone(MOSCOW)));
            if (!"planned".equals(source.get("status")) || !future) {
                throw new InvalidLessonStateException("Переносить можно только будущую запланированную пару");
            }
            LocalDate targetDate = request.targetDate();
            if (templateOperationId == null && !targetDate.isAfter(today)) {
                throw new ConflictException("Новая дата должна быть в будущем");
            }
            short targetDay = (short) targetDate.getDayOfWeek().getValue();
            if (templateOperationId == null && (targetDay < 1 || targetDay > 6)) {
                throw new ConflictException("Для переноса выбери учебный день с понедельника по субботу");
            }
            requireTargetFence(number(source.get("assignment_id")), targetDate,
                    number(source.get("group_id")), number(source.get("subject_id")),
                    number(source.get("semester_id")), number(source.get("assigned_teacher_id")),
                    String.valueOf(source.get("lesson_type")));

            short targetNumber = request.targetLessonNumber().shortValue();
            // Template operations change only room and retain immutable historical times.
            var slot = LessonSlot.forNumber(targetNumber);
            if (templateOperationId == null) slot.validateTimes(request.targetStartTime(), request.targetEndTime());
            LocalTime targetStart = templateOperationId == null ? slot.startTime()
                    : (request.targetStartTime() == null ? localTime(source.get("start_time")) : request.targetStartTime());
            LocalTime targetEnd = templateOperationId == null ? slot.endTime()
                    : (request.targetEndTime() == null ? localTime(source.get("end_time")) : request.targetEndTime());
            String targetRoom = request.targetRoom() == null && templateOperationId == null
                    ? (String) source.get("room_snapshot") : request.targetRoom();
            if (targetEnd == null || targetStart == null || !targetEnd.isAfter(targetStart)) {
                throw new ConflictException("Для переноса не удалось определить корректное время пары");
            }
            if (templateOperationId != null && (!targetDate.equals(sourceDate)
                    || targetNumber != number(source.get("lesson_number"))
                    || !targetStart.equals(localTime(source.get("start_time")))
                    || !targetEnd.equals(localTime(source.get("end_time")))
                    || java.util.Objects.equals(targetRoom, source.get("room_snapshot")))) {
                throw new ConflictException("Шаблон может изменить только аудиторию будущей пары");
            }
            if (templateOperationId == null && targetDate.equals(sourceDate)
                    && targetNumber == number(source.get("lesson_number"))) {
                throw new ConflictException("Новый слот совпадает с занятым исходным номером пары");
            }
            if (targetSlotOccupied(number(source.get("group_id")), targetDate, targetNumber,
                    sourceLessonId)) {
                throw new ConflictException("Новый слот уже занят");
            }

            List<Map<String, Object>> bindings = readAndLockActiveBindings(occurrenceId, sourceLessonId);
            int batchCount = Math.max(1, (bindings.size() + BINDINGS_PER_BATCH - 1) / BINDINGS_PER_BATCH);
            UUID operationId = UUID.randomUUID();
            long targetLessonId = nextLessonId();
            long expectedRevision = number(current.get("revision"));
            long generation = number(current.get("generation"));
            long lessonRevision = number(source.get("revision"));
            OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));

            Map<String, Object> sourceSnapshot = sourceSnapshot(source);
            Map<String, Object> targetSnapshot = targetSnapshot(source, targetLessonId,
                    targetDate, targetDay, targetNumber, targetStart, targetEnd, targetRoom,
                    generation + 1);
            List<List<Map<String, Object>>> bindingBatches = split(bindings, batchCount);
            String operationHash = operationHash(requestHash, sourceSnapshot, targetSnapshot, bindingBatches);

            jdbc.update("""
                    INSERT INTO lesson_transfer_operations
                        (operation_id, actor_id, request_key, occurrence_id, source_lesson_id,
                         target_lesson_id, request_hash, operation_hash, expected_occurrence_revision,
                         result_occurrence_revision, source_generation, target_generation, batch_count,
                         source_snapshot, target_snapshot, state, created_at, updated_at, template_operation_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb,
                            'PENDING', ?, ?, ?)
                    """, operationId, actorId, request.requestKey(), occurrenceId, sourceLessonId,
                    targetLessonId, requestHash, HexFormat.of().parseHex(operationHash),
                    expectedRevision, expectedRevision + 1, generation, generation + 1, batchCount,
                    json(sourceSnapshot), json(targetSnapshot), now, now, templateOperationId);

            setTransferSetting(operationId);
            jdbc.update("""
                    INSERT INTO lesson_transfer_authorities
                        (operation_id, occurrence_id, source_lesson_id, target_lesson_id, actor_id,
                         operation_hash, expected_occurrence_revision, expected_generation,
                         expected_lesson_revision, target_date, target_lesson_number,
                         target_start_time, target_end_time, target_room, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, operationId, occurrenceId, sourceLessonId, targetLessonId, actorId,
                    HexFormat.of().parseHex(operationHash), expectedRevision, generation,
                    lessonRevision, targetDate, targetNumber, targetStart, targetEnd, targetRoom, now);

            updateSourceAsTransferred(sourceLessonId, occurrenceId, lessonRevision);
            int occurrenceUpdated = jdbc.update("""
                    UPDATE lesson_occurrences
                       SET occurrence_date = ?, current_lesson_id = ?, generation = generation + 1,
                           revision = revision + 1
                     WHERE id = ? AND current_lesson_id = ? AND occurrence_date = ?
                       AND generation = ? AND revision = ?
                    """, targetDate, targetLessonId, occurrenceId, sourceLessonId,
                    sourceDate, generation, expectedRevision);
            if (occurrenceUpdated != 1) {
                throw new ConflictException("Текущая версия пары изменилась во время переноса");
            }
            insertTargetLesson(source, targetLessonId, targetDate, targetDay, targetNumber,
                    targetStart, targetEnd, targetRoom, generation + 1, now);
            if (source.get("one_off_lesson_id") != null) {
                int originUpdated = jdbc.update("""
                        UPDATE schedule_one_off_lessons SET physical_lesson_id = ?
                         WHERE id = ? AND physical_lesson_id = ? AND group_id = ?
                           AND subject_id = ? AND semester_id = ?
                        """, targetLessonId, number(source.get("one_off_lesson_id")), sourceLessonId,
                        number(source.get("group_id")), number(source.get("subject_id")),
                        number(source.get("semester_id")));
                if (originUpdated != 1) throw new ConflictException("Текущая разовая пара изменилась во время переноса");
            }
            // Persist the exact immutable participant batches before changing
            // bindings. V21's binding guard validates each mutation against
            // this durable snapshot inside the same transaction.
            for (int index = 0; index < batchCount; index++) {
                Map<String, Object> eventPayload = eventPayload(operationId, actorId,
                        request.requestKey(), operationHash, expectedRevision, sourceSnapshot,
                        targetSnapshot, index, batchCount, bindingBatches.get(index));
                byte[] batchHash = sha256(bytes(bindingBatches.get(index)));
                jdbc.update("""
                        INSERT INTO lesson_transfer_binding_batches
                            (operation_id, batch_index, batch_hash, payload, binding_count, created_at)
                        VALUES (?, ?, ?, ?::jsonb, ?, ?)
                        """, operationId, index, batchHash, json(eventPayload),
                        bindingBatches.get(index).size(), now);
                publishTransfer(eventPayload);
            }
            moveBindings(occurrenceId, sourceLessonId, targetLessonId, bindings);
            jdbc.update("""
                    INSERT INTO lesson_lifecycle_entries
                        (occurrence_id, revision, action, lesson_id, target_lesson_id,
                         generation, reason, actor_id, occurred_at, transfer_operation_id, template_operation_id)
                    VALUES (?, ?, 'TRANSFERRED', ?, ?, ?, NULL, ?, ?, ?, ?)
                    """, occurrenceId, expectedRevision + 1, sourceLessonId, targetLessonId,
                    generation + 1, actorId, now, operationId, templateOperationId);
            jdbc.update("""
                    INSERT INTO schedule_transfer_replay
                        (actor_id, request_key, occurrence_id, payload_hash, source_lesson_id,
                         target_lesson_id, accepted_revision, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, actorId, request.requestKey(), occurrenceId, requestHash,
                    sourceLessonId, targetLessonId, expectedRevision + 1, now);

            return status(operationId);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Перенос конфликтует с занятым слотом или актуальным состоянием пары");
        }
    }

    @Transactional(readOnly = true)
    public TransferLessonResponse status(UUID operationId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                SELECT operation_id, state, occurrence_id, source_lesson_id, target_lesson_id,
                       result_occurrence_revision, error_code, target_snapshot ->> 'date' AS target_date
                  FROM lesson_transfer_operations WHERE operation_id = ?
                """, operationId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ResourceNotFoundException("LessonTransfer", "operationId", operationId);
        }
        return response(row);
    }

    @Transactional(readOnly = true)
    public long groupId(UUID operationId) {
        try {
            return jdbc.queryForObject("""
                    SELECT occurrence.group_id
                      FROM lesson_transfer_operations operation
                      JOIN lesson_occurrences occurrence ON occurrence.id = operation.occurrence_id
                     WHERE operation.operation_id = ?
                    """, Long.class, operationId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ResourceNotFoundException("LessonTransfer", "operationId", operationId);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int republishPendingBatches() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT batch.payload::text AS payload
                  FROM lesson_transfer_operations operation
                  JOIN lesson_transfer_binding_batches batch ON batch.operation_id = operation.operation_id
                 WHERE operation.state = 'PENDING'
                   AND ((batch.batch_index = 0 AND NOT EXISTS (
                            SELECT 1 FROM lesson_transfer_participant_receipts receipt
                             WHERE receipt.operation_id = operation.operation_id
                               AND receipt.participant = 'ATTENDANCE' AND receipt.batch_index = -1
                               AND receipt.result = 'APPLIED'))
                        OR NOT EXISTS (
                            SELECT 1 FROM lesson_transfer_participant_receipts receipt
                             WHERE receipt.operation_id = operation.operation_id
                               AND receipt.participant = 'ACADEMIC'
                               AND receipt.batch_index = batch.batch_index
                               AND receipt.result = 'APPLIED'))
                 ORDER BY operation.created_at, batch.batch_index
                 LIMIT 256
                """);
        for (Map<String, Object> row : rows) {
            publishTransfer(readObject(String.valueOf(row.get("payload"))));
        }
        return rows.size();
    }

    public Map<Long, TransferState> pendingStatesForLessons(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) return Map.of();
        Array array = null;
        try {
            array = jdbc.execute((Connection connection) -> connection.createArrayOf(
                    "bigint", lessonIds.toArray(Long[]::new)));
            Array finalArray = array;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT DISTINCT ON (lesson_id) lesson_id, operation_id, state
                      FROM (
                        SELECT unnest(?::bigint[]) AS lesson_id
                      ) requested
                      JOIN LATERAL (
                        SELECT operation_id, state, source_lesson_id, target_lesson_id, created_at
                          FROM lesson_transfer_operations operation
                         WHERE operation.source_lesson_id = requested.lesson_id
                            OR operation.target_lesson_id = requested.lesson_id
                         ORDER BY (operation.state = 'PENDING') DESC,
                                  operation.created_at DESC, operation.operation_id DESC
                         LIMIT 1
                      ) operation ON TRUE
                     ORDER BY lesson_id
                    """, finalArray);
            Map<Long, TransferState> result = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                result.put(number(row.get("lesson_id")), new TransferState(
                        String.valueOf(row.get("operation_id")), String.valueOf(row.get("state"))));
            }
            return Map.copyOf(result);
        } finally {
            if (array != null) {
                try { array.free(); } catch (SQLException ignored) { }
            }
        }
    }

    public Map<Long, Long> occurrenceRevisionsForLessons(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) return Map.of();
        Array array = null;
        try {
            array = jdbc.execute((Connection connection) -> connection.createArrayOf(
                    "bigint", lessonIds.toArray(Long[]::new)));
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT lesson.id AS lesson_id, occurrence.revision AS occurrence_revision
                      FROM lessons lesson
                      JOIN lesson_occurrences occurrence ON occurrence.id = lesson.occurrence_id
                     WHERE lesson.id = ANY(?::bigint[])
                    """, array);
            Map<Long, Long> result = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                result.put(number(row.get("lesson_id")), number(row.get("occurrence_revision")));
            }
            return Map.copyOf(result);
        } finally {
            if (array != null) {
                try { array.free(); } catch (SQLException ignored) { }
            }
        }
    }

    @Transactional
    public void acknowledge(String operationId,
                            String participant,
                            int batchIndex,
                            String result,
                            String errorCode,
                            String payloadHash,
                            long sourceLessonId,
                            long targetLessonId,
                            boolean retryable) {
        UUID operation = parseUuid(operationId, "operation_id");
        if (!List.of("ATTENDANCE", "ACADEMIC").contains(participant)
                || !List.of("APPLIED", "ERROR").contains(result)) {
            throw new IllegalArgumentException("Unsupported lesson transfer participant result");
        }
        if ("ATTENDANCE".equals(participant) && batchIndex != -1
                || "ACADEMIC".equals(participant) && batchIndex < 0) {
            throw new IllegalArgumentException("Invalid lesson transfer batch receipt index");
        }
        byte[] operationHash = parseHash(payloadHash);
        Map<String, Object> op = jdbc.queryForMap("""
                SELECT operation_hash, source_lesson_id, target_lesson_id, batch_count, state
                  FROM lesson_transfer_operations WHERE operation_id = ? FOR UPDATE
                """, operation);
        if (!MessageDigest.isEqual((byte[]) op.get("operation_hash"), operationHash)
                || number(op.get("source_lesson_id")) != sourceLessonId
                || number(op.get("target_lesson_id")) != targetLessonId
                || ("ACADEMIC".equals(participant) && batchIndex >= number(op.get("batch_count")))) {
            throw new ConflictException("Подтверждение не соответствует операции переноса");
        }
        if ("ERROR".equals(result)
                && !List.of("TARGET_DATA_CONFLICT", "SOURCE_STATE_CONFLICT", "SCOPE_MISMATCH",
                "INVALID_SNAPSHOT", "DEPENDENCY_UNAVAILABLE", "ARCHIVED_SEMESTER").contains(errorCode)) {
            throw new IllegalArgumentException("Unsupported lesson transfer error code");
        }
        if (retryable && (!"ERROR".equals(result) || !"DEPENDENCY_UNAVAILABLE".equals(errorCode))) {
            throw new IllegalArgumentException("Only dependency-unavailable receipts may be retryable");
        }
        if (!retryable && "ERROR".equals(result) && "DEPENDENCY_UNAVAILABLE".equals(errorCode)) {
            throw new IllegalArgumentException("Dependency-unavailable transfer receipts must remain retryable");
        }
        if ("APPLIED".equals(result) && errorCode != null) {
            throw new IllegalArgumentException("APPLIED transfer receipt cannot carry an error code");
        }
        if (retryable) {
            // The producer will retry the immutable command. A transient failure
            // never becomes a durable receipt that can block a later APPLIED ack.
            return;
        }
        jdbc.update("""
                INSERT INTO lesson_transfer_participant_receipts
                    (operation_id, participant, batch_index, result, error_code, payload_hash,
                     source_lesson_id, target_lesson_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (operation_id, participant, batch_index) DO NOTHING
                """, operation, participant, batchIndex, result, errorCode,
                operationHash, sourceLessonId, targetLessonId);
        Map<String, Object> receipt = jdbc.queryForMap("""
                SELECT result, error_code, payload_hash, source_lesson_id, target_lesson_id
                  FROM lesson_transfer_participant_receipts
                 WHERE operation_id = ? AND participant = ? AND batch_index = ?
                """, operation, participant, batchIndex);
        if (!result.equals(receipt.get("result"))
                || !java.util.Objects.equals(errorCode, receipt.get("error_code"))
                || !MessageDigest.isEqual((byte[]) receipt.get("payload_hash"), operationHash)
                || number(receipt.get("source_lesson_id")) != sourceLessonId
                || number(receipt.get("target_lesson_id")) != targetLessonId) {
            throw new ConflictException("Дубликат подтверждения переноса содержит другие данные");
        }
        if (!"PENDING".equals(op.get("state"))) return;
        if ("ERROR".equals(result)) {
            jdbc.update("UPDATE lesson_transfer_operations SET state = 'ERROR', error_code = ?, updated_at = NOW() WHERE operation_id = ? AND state = 'PENDING'",
                    errorCode, operation);
            return;
        }
        Integer attendanceApplied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_transfer_participant_receipts
                 WHERE operation_id = ? AND participant = 'ATTENDANCE'
                   AND batch_index = -1 AND result = 'APPLIED'
                """, Integer.class, operation);
        Integer academicApplied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_transfer_participant_receipts
                 WHERE operation_id = ? AND participant = 'ACADEMIC' AND result = 'APPLIED'
                """, Integer.class, operation);
        if (attendanceApplied != null && attendanceApplied == 1
                && academicApplied != null && academicApplied == number(op.get("batch_count"))) {
            jdbc.update("UPDATE lesson_transfer_operations SET state = 'COMPLETED', updated_at = NOW() WHERE operation_id = ? AND state = 'PENDING'",
                    operation);
        }
    }

    private void publishTransfer(Map<String, Object> payload) {
        // Version is recoverable from the immutable stored snapshot; never consult current rows.
        Object source = payload.get("source");
        if (source instanceof Map<?, ?> snapshot && snapshot.get("one_off_lesson_id") != null) {
            eventPublisher.publishEvent(new OneOffLessonTransferRequestedEvent(this, payload));
        } else {
            eventPublisher.publishEvent(new LessonTransferRequestedEvent(this, payload));
        }
    }

    private void lockRequestKey(long actorId, UUID requestKey) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, actorId + ":" + requestKey);
                statement.execute();
            }
            return null;
        });
    }

    private TransferLessonResponse findReplay(long actorId, UUID requestKey, byte[] requestHash) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT operation.operation_id, operation.state, operation.occurrence_id,
                       operation.source_lesson_id, operation.target_lesson_id,
                       operation.result_occurrence_revision, operation.error_code,
                       operation.target_snapshot ->> 'date' AS target_date,
                       replay.payload_hash
                  FROM schedule_transfer_replay replay
                  JOIN lesson_transfer_operations operation
                    ON operation.actor_id = replay.actor_id AND operation.request_key = replay.request_key
                 WHERE replay.actor_id = ? AND replay.request_key = ?
                """, actorId, requestKey);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.getFirst();
        if (!MessageDigest.isEqual((byte[]) row.get("payload_hash"), requestHash)) {
            throw new ConflictException("Ключ переноса уже использован с другими параметрами");
        }
        return response(row);
    }

    private Map<String, Object> readSource(long lessonId) {
        try {
            return jdbc.queryForMap("""
                    SELECT l.id AS lesson_id, l.schedule_item_id, l.one_off_lesson_id, l.occurrence_id, l.assignment_id,
                           l.group_id, l.subject_id, l.semester_id, l.assigned_teacher_id, l.lesson_type,
                           l.date, l.status::text AS status, l.lesson_number, l.day_of_week,
                           l.start_time, l.end_time, l.room_snapshot, l.week_type_snapshot,
                           l.generation, l.revision, l.is_geo_blocked, l.is_blocked_by_headman,
                           l.blocked_by_user_id, l.blocked_at,
                           occurrence.revision AS occurrence_revision,
                           occurrence.generation AS occurrence_generation,
                           occurrence.current_lesson_id
                      FROM lessons l JOIN lesson_occurrences occurrence ON occurrence.id = l.occurrence_id
                     WHERE l.id = ?
                    """, lessonId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ResourceNotFoundException("Lesson", "id", lessonId);
        }
    }

    private Map<String, Object> lockOccurrence(long occurrenceId) {
        return jdbc.queryForMap("""
                SELECT id, schedule_item_id, one_off_lesson_id, assignment_id, assigned_teacher_id,
                       group_id, subject_id, semester_id, lesson_type, occurrence_date,
                       generation, revision, current_lesson_id
                  FROM lesson_occurrences WHERE id = ? FOR UPDATE
                """, occurrenceId);
    }

    private Map<String, Object> lockSource(long lessonId, long occurrenceId) {
        return jdbc.queryForMap("""
                SELECT l.id AS lesson_id, l.schedule_item_id, l.one_off_lesson_id, l.occurrence_id, l.assignment_id,
                       l.group_id, l.subject_id, l.semester_id, l.assigned_teacher_id, l.lesson_type,
                       l.date, l.status::text AS status, l.lesson_number, l.day_of_week,
                       l.start_time, l.end_time, l.room_snapshot, l.week_type_snapshot,
                       l.generation, l.revision, l.is_geo_blocked, l.is_blocked_by_headman,
                       l.blocked_by_user_id, l.blocked_at,
                       occurrence.revision AS occurrence_revision,
                       occurrence.generation AS occurrence_generation,
                       occurrence.current_lesson_id
                  FROM lessons l
                  JOIN lesson_occurrences occurrence ON occurrence.id = l.occurrence_id
                 WHERE l.id = ? AND l.occurrence_id = ?
                 FOR UPDATE OF l
                """, lessonId, occurrenceId);
    }

    private void requireCurrent(long lessonId, Map<String, Object> source,
                                Map<String, Object> occurrence, String expectedRevision) {
        if (!java.util.Objects.equals(occurrence.get("schedule_item_id"), source.get("schedule_item_id"))
                || !java.util.Objects.equals(occurrence.get("one_off_lesson_id"), source.get("one_off_lesson_id"))
                || number(occurrence.get("current_lesson_id")) != lessonId
                || number(occurrence.get("revision")) != parsePositive(expectedRevision)
                || number(source.get("occurrence_revision")) != parsePositive(expectedRevision)
                || number(occurrence.get("generation")) != number(source.get("generation"))
                || number(occurrence.get("generation")) != number(source.get("occurrence_generation"))) {
            throw new ConflictException("Текущая версия пары изменилась; обнови расписание и повтори перенос");
        }
    }

    private void rejectPendingTransfer(long occurrenceId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_transfer_operations
                 WHERE occurrence_id = ? AND state <> 'COMPLETED'
                """, Integer.class, occurrenceId);
        if (count != null && count > 0) {
            throw new ConflictException("Предыдущий перенос пары ещё не завершён");
        }
    }

    private void lockBindingsForTransfer(long occurrenceId, long lessonId) {
        jdbc.queryForList("""
                SELECT binding_id FROM lesson_homework_bindings
                 WHERE occurrence_id = ? AND current_lesson_id = ? AND state IN ('PENDING', 'ACTIVE')
                 ORDER BY binding_id FOR UPDATE
                """, occurrenceId, lessonId);
    }

    private List<Map<String, Object>> readAndLockActiveBindings(long occurrenceId, long lessonId) {
        return jdbc.query("""
                SELECT binding_id, actor_id, request_key, homework_id, payload_hash, state, revision
                  FROM lesson_homework_bindings
                 WHERE occurrence_id = ? AND current_lesson_id = ? AND state IN ('PENDING', 'ACTIVE')
                 ORDER BY binding_id FOR UPDATE
                """, (rs, index) -> {
            Map<String, Object> binding = new LinkedHashMap<>();
            binding.put("binding_id", rs.getLong("binding_id"));
            binding.put("actor_id", rs.getLong("actor_id"));
            binding.put("request_key", rs.getObject("request_key", UUID.class).toString());
            long homework = rs.getLong("homework_id");
            binding.put("homework_id", rs.wasNull() ? null : homework);
            binding.put("payload_hash", HexFormat.of().formatHex(rs.getBytes("payload_hash")));
            binding.put("state", rs.getString("state"));
            binding.put("revision", rs.getLong("revision"));
            return binding;
        }, occurrenceId, lessonId);
    }

    private void moveBindings(long occurrenceId, long sourceLessonId,
                              long targetLessonId, List<Map<String, Object>> bindings) {
        for (Map<String, Object> binding : bindings) {
            int changed = jdbc.update("""
                    UPDATE lesson_homework_bindings
                       SET current_lesson_id = ?, revision = revision + 1, updated_at = NOW()
                     WHERE binding_id = ? AND occurrence_id = ? AND current_lesson_id = ?
                       AND revision = ? AND state IN ('PENDING', 'ACTIVE')
                    """, targetLessonId, number(binding.get("binding_id")), occurrenceId,
                    sourceLessonId, number(binding.get("revision")));
            if (changed != 1) throw new ConflictException("Связанная домашняя работа изменилась во время переноса");
        }
    }

    private void updateSourceAsTransferred(long lessonId, long occurrenceId, long revision) {
        int changed = jdbc.update("""
                UPDATE lessons SET status = 'transferred'::lesson_status, revision = revision + 1
                 WHERE id = ? AND occurrence_id = ? AND status::text = 'planned' AND revision = ?
                """, lessonId, occurrenceId, revision);
        if (changed != 1) throw new ConflictException("Исходная пара больше не запланирована");
    }

    private void insertTargetLesson(Map<String, Object> source, long targetLessonId,
                                    LocalDate date, short dayOfWeek, short lessonNumber,
                                    LocalTime start, LocalTime end, String room,
                                    long generation, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO lessons
                    (id, schedule_item_id, one_off_lesson_id, occurrence_id, assignment_id,
                     group_id, subject_id, semester_id, assigned_teacher_id, lesson_type,
                     lesson_number, day_of_week, start_time, end_time, room_snapshot,
                     week_type_snapshot, generation, revision, date, status, is_geo_blocked,
                     is_blocked_by_headman, blocked_by_user_id, blocked_at, cancel_reason,
                     cancelled_by, cancelled_at, created_at)
                OVERRIDING SYSTEM VALUE
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?,
                        'planned'::lesson_status, ?, ?, ?, ?, NULL, NULL, NULL, ?)
                """, targetLessonId, source.get("schedule_item_id"), source.get("one_off_lesson_id"),
                number(source.get("occurrence_id")), number(source.get("assignment_id")),
                number(source.get("group_id")), number(source.get("subject_id")),
                number(source.get("semester_id")), number(source.get("assigned_teacher_id")),
                String.valueOf(source.get("lesson_type")), lessonNumber, dayOfWeek,
                start, end, room, source.get("week_type_snapshot"), generation, date,
                source.get("is_geo_blocked"), source.get("is_blocked_by_headman"),
                source.get("blocked_by_user_id"), source.get("blocked_at"), now);
    }

    private void requireTargetFence(long assignmentId, LocalDate targetDate,
                                   long groupId, long subjectId, long semesterId,
                                   long teacherId, String lessonType) {
        Map<String, Object> fence = jdbc.queryForMap("""
                SELECT assignment_id, assigned_teacher_id, group_id, subject_id, semester_id,
                       lesson_type, valid_from, creation_cap_until_exclusive
                  FROM schedule_assignment_fences WHERE assignment_id = ? FOR UPDATE
                """, assignmentId);
        if (number(fence.get("assigned_teacher_id")) != teacherId
                || number(fence.get("group_id")) != groupId
                || number(fence.get("subject_id")) != subjectId
                || number(fence.get("semester_id")) != semesterId
                || !lessonType.equals(String.valueOf(fence.get("lesson_type")))
                || targetDate.isBefore(localDate(fence.get("valid_from")))
                || !targetDate.isBefore(localDate(fence.get("creation_cap_until_exclusive")))) {
            throw new ConflictException("Новая дата выходит за действующее назначение или семестр пары");
        }
    }

    private boolean targetSlotOccupied(long groupId, LocalDate date, short number, long sourceLessonId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lessons
                 WHERE group_id = ? AND date = ? AND lesson_number = ?
                   AND status::text IN ('planned', 'active', 'closed') AND id <> ?
                """, Integer.class, groupId, date, number, sourceLessonId);
        return count != null && count > 0;
    }

    private void lockFences(List<Long> assignmentIds) {
        for (long id : assignmentIds.stream().distinct().sorted().toList()) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT assignment_id FROM schedule_assignment_fences WHERE assignment_id = ? FOR UPDATE", id);
            if (rows.size() != 1) throw new ConflictException("Не найдено действующее назначение преподавателя");
        }
    }

    private void lockOrigin(Map<String, Object> source) {
        Object item = source.get("schedule_item_id");
        Object oneOff = source.get("one_off_lesson_id");
        if ((item == null) == (oneOff == null)) throw new ConflictException("Не найден точный источник пары");
        if (item != null) {
            lockItems(List.of(number(item)));
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, physical_lesson_id, group_id, subject_id, semester_id
                  FROM schedule_one_off_lessons WHERE id = ? FOR UPDATE
                """, number(oneOff));
        if (rows.size() != 1) throw new ConflictException("Источник разовой пары изменился");
        Map<String, Object> origin = rows.getFirst();
        if (!java.util.Objects.equals(origin.get("physical_lesson_id"), source.get("lesson_id"))
                || !java.util.Objects.equals(origin.get("group_id"), source.get("group_id"))
                || !java.util.Objects.equals(origin.get("subject_id"), source.get("subject_id"))
                || !java.util.Objects.equals(origin.get("semester_id"), source.get("semester_id"))) {
            throw new ConflictException("Текущая разовая пара изменилась; обнови расписание");
        }
    }

    private void lockItems(List<Long> itemIds) {
        for (long id : itemIds.stream().distinct().sorted().toList()) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id FROM schedule_items WHERE id = ? FOR UPDATE", id);
            if (rows.size() != 1) throw new ConflictException("Шаблон пары изменился; обнови расписание");
        }
    }

    private long nextLessonId() {
        return jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('lessons', 'id'))", Long.class);
    }

    private void setTransferSetting(UUID operationId) {
        jdbc.queryForObject("SELECT set_config(?, ?, true)", String.class, TRANSFER_SETTING, operationId.toString());
    }

    private static Map<String, Object> sourceSnapshot(Map<String, Object> source) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putSnapshot(snapshot, source, "lesson_id", "lesson_id");
        for (String key : List.of("schedule_item_id", "occurrence_id", "assignment_id", "group_id",
                "subject_id", "semester_id", "assigned_teacher_id", "lesson_type", "generation",
                "date", "lesson_number", "day_of_week", "start_time", "end_time",
                "week_type_snapshot", "status")) {
            putSnapshot(snapshot, source, key, key);
        }
        putSnapshot(snapshot, source, "lesson_revision", "revision");
        putSnapshot(snapshot, source, "occurrence_revision", "occurrence_revision");
        putSnapshot(snapshot, source, "room", "room_snapshot");
        // V1 recurring payload/hash stays byte-for-byte compatible with persisted replays.
        if (source.get("one_off_lesson_id") != null) {
            putSnapshot(snapshot, source, "one_off_lesson_id", "one_off_lesson_id");
        }
        return snapshot;
    }

    private static void putSnapshot(Map<String, Object> target, Map<String, Object> source,
                                    String targetKey, String sourceKey) {
        Object value = source.get(sourceKey);
        if (value instanceof java.sql.Date date) value = date.toLocalDate().toString();
        else if (value instanceof LocalDate date) value = date.toString();
        else if (value instanceof LocalTime time) value = time.toString();
        target.put(targetKey, value);
    }

    private static Map<String, Object> targetSnapshot(Map<String, Object> source, long targetId,
                                                       LocalDate date, short dayOfWeek, short number,
                                                       LocalTime start, LocalTime end, String room,
                                                       long generation) {
        Map<String, Object> target = new LinkedHashMap<>(sourceSnapshot(source));
        target.put("lesson_id", targetId);
        target.put("generation", generation);
        target.put("lesson_revision", 1L);
        target.put("occurrence_revision", number(source.get("occurrence_revision")) + 1);
        target.put("date", date.toString());
        target.put("lesson_number", number);
        target.put("day_of_week", dayOfWeek);
        target.put("start_time", start.toString());
        target.put("end_time", end.toString());
        target.put("room", room);
        target.put("status", "planned");
        return target;
    }

    private static List<List<Map<String, Object>>> split(List<Map<String, Object>> bindings, int batchCount) {
        List<List<Map<String, Object>>> batches = new ArrayList<>(batchCount);
        for (int index = 0; index < batchCount; index++) {
            int from = index * BINDINGS_PER_BATCH;
            int to = Math.min(bindings.size(), from + BINDINGS_PER_BATCH);
            batches.add(List.copyOf(bindings.subList(Math.min(from, bindings.size()), to)));
        }
        return List.copyOf(batches);
    }

    private String operationHash(byte[] requestHash, Map<String, Object> source,
                                 Map<String, Object> target,
                                 List<List<Map<String, Object>>> batches) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("request_hash", HexFormat.of().formatHex(requestHash));
        operation.put("source", source);
        operation.put("target", target);
        operation.put("binding_batches", batches);
        return HexFormat.of().formatHex(sha256(bytes(operation)));
    }

    private static Map<String, Object> eventPayload(UUID operationId, long actorId,
                                                     UUID requestKey, String operationHash,
                                                     long expectedRevision,
                                                     Map<String, Object> source,
                                                     Map<String, Object> target,
                                                     int batchIndex, int batchCount,
                                                     List<Map<String, Object>> bindings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation_id", operationId.toString());
        payload.put("request_key", requestKey.toString());
        payload.put("actor_id", actorId);
        payload.put("transfer_payload_hash", operationHash);
        payload.put("occurrence_id", source.get("occurrence_id"));
        payload.put("group_id", source.get("group_id"));
        payload.put("semester_id", source.get("semester_id"));
        payload.put("transfer_revision", expectedRevision + 1);
        payload.put("source_lesson_id", source.get("lesson_id"));
        payload.put("target_lesson_id", target.get("lesson_id"));
        payload.put("source", source);
        payload.put("target", target);
        payload.put("batch_index", batchIndex);
        payload.put("batch_count", batchCount);
        payload.put("bindings", bindings);
        return payload;
    }

    private byte[] requestHash(long sourceLessonId, TransferLessonRequest request) {
        String payload = String.join("\n", Long.toString(sourceLessonId), request.targetDate().toString(),
                request.targetLessonNumber().toString(), nullable(request.targetStartTime()),
                nullable(request.targetEndTime()), nullable(request.targetRoom()), request.expectedRevision());
        return sha256(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Unable to serialize transfer snapshot", error); }
    }

    private byte[] bytes(Object value) {
        try { return objectMapper.writeValueAsBytes(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Unable to hash transfer snapshot", error); }
    }

    private Map<String, Object> readObject(String json) {
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Stored transfer batch is invalid", error); }
    }

    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 is unavailable", error); }
    }

    private static byte[] parseHash(String value) {
        try {
            byte[] parsed = HexFormat.of().parseHex(value);
            if (parsed.length != 32) throw new IllegalArgumentException();
            return parsed;
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid operation hash", invalid); }
    }

    private static UUID parseUuid(String value, String field) {
        try { return UUID.fromString(value); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid " + field, invalid); }
    }

    private static long parsePositive(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) { throw new ConflictException("Некорректная ожидаемая версия пары"); }
    }

    private static String nullable(Object value) { return value == null ? "" : value.toString(); }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); }
    private static LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }
    private static LocalTime localTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalTime time) return time;
        if (value instanceof java.sql.Time time) return time.toLocalTime();
        return LocalTime.parse(String.valueOf(value));
    }

    private static TransferLessonResponse response(Map<String, Object> row) {
        String state = String.valueOf(row.get("state"));
        return new TransferLessonResponse(String.valueOf(row.get("operation_id")), state,
                Long.toString(number(row.get("occurrence_id"))),
                Long.toString(number(row.get("source_lesson_id"))),
                Long.toString(number(row.get("target_lesson_id"))),
                Long.toString(number(row.get("result_occurrence_revision"))),
                state.equals("PENDING"), row.get("error_code") == null ? null : String.valueOf(row.get("error_code")),
                LocalDate.parse(String.valueOf(row.get("target_date"))));
    }

    public record TransferState(String operationId, String state) { }
}
