package ru.rutcampustrack.schedule.recurring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.RecurringProtocolConflictException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The only transactional recurring physical writer. Academic RPCs are
 * deliberately absent from this class: the coordinator passes a validated
 * immutable authority snapshot across the transaction boundary.
 */
@Service
public class RecurringScheduleItemWriter {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS");
    private static final String ACTION = "RECURRING_CREATE_V1";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ScheduleSemesterArchiveWriteFence archiveWriteFence;

    public RecurringScheduleItemWriter(JdbcTemplate jdbc,
                                       Clock clock,
                                       ScheduleSemesterArchiveWriteFence archiveWriteFence) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.archiveWriteFence = archiveWriteFence;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecurringCreateResult write(CreateScheduleItemRequest request,
                                       UUID requestKey,
                                       long actorId,
                                       RecurringAssignmentAuthority authority,
                                       LocalDate semesterStart,
                                       LocalDate semesterEnd) {
        return writeInternal(request, requestKey, actorId, authority, semesterStart, semesterEnd, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecurringCreateResult writePrepared(CreateScheduleItemRequest request, UUID requestKey,
            long actorId, RecurringAssignmentAuthority authority, LocalDate semesterStart,
            LocalDate semesterEnd, ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {
        return writeInternal(request, requestKey, actorId, authority, semesterStart, semesterEnd, prepared);
    }

    private RecurringCreateResult writeInternal(CreateScheduleItemRequest request, UUID requestKey,
            long actorId, RecurringAssignmentAuthority authority, LocalDate semesterStart,
            LocalDate semesterEnd, ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {
        if (requestKey == null || actorId <= 0) {
            throw new RecurringProtocolConflictException("actor and UUID idempotency key are required");
        }
        validateInputs(request, authority, semesterStart, semesterEnd);
        byte[] payloadHash = payloadHash(request);
        RecurringCreateResult existingReplay = replayIfPresent(actorId, requestKey, payloadHash);
        if (existingReplay != null) {
            return existingReplay;
        }
        if (prepared == null) archiveWriteFence.lockForBusinessWrite(authority.semesterId());
        else archiveWriteFence.lockPreparedBusinessWrite(prepared);
        LocalDate fenceCap;
        try {
            fenceCap = lockOrInstallFence(authority);
        } catch (DataIntegrityViolationException ex) {
            throw new RecurringProtocolConflictException("assignment fence authority is invalid");
        }

        // A concurrent identical request can have committed while this
        // transaction waited on the assignment fence. Re-read the replay
        // ledger after that wait before inserting another origin.
        existingReplay = replayIfPresent(actorId, requestKey, payloadHash);
        if (existingReplay != null) {
            return existingReplay;
        }

        if (inactiveTemplateId(request) != null) {
            throw new ConflictException("Слот ожидает продолжения прежней серии; повтори запрос");
        }
        List<LocalDate> dates = new ArrayList<>(RecurringDateCalculator.compute(
                request, authority, semesterStart, semesterEnd, fenceCap));
        dates.removeIf(replacementAlreadyCovered(request, authority, fenceCap));
        Long replacementTemplateId = replacementTemplateId(request);
        if (replacementTemplateId != null) {
            dates.removeIf(date -> occurrenceExists(replacementTemplateId, date));
        }
        OffsetDateTime createdAt = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
        LocalDate generatedFrom = dates.isEmpty() ? null : dates.get(0);
        LocalDate generatedUntil = dates.isEmpty() ? null : dates.get(dates.size() - 1);
        try {
            long scheduleItemId = replacementTemplateId == null
                    ? insertScheduleItem(request, authority, createdAt,
                            dates.size(), generatedFrom, generatedUntil)
                    : replacementTemplateId;
            for (LocalDate date : dates) {
                long occurrenceId = insertOccurrence(scheduleItemId, date, authority, createdAt);
                long lessonId = insertPhysicalLesson(scheduleItemId, occurrenceId, date,
                        request, authority, LessonStatus.PLANNED, createdAt, null);
                jdbc.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                        lessonId, occurrenceId);
                jdbc.update("""
                        INSERT INTO lesson_lifecycle_entries
                            (occurrence_id, revision, action, lesson_id, generation,
                             actor_id, occurred_at)
                        VALUES (?, 1, 'CREATED', ?, 1, ?, ?)
                        """, occurrenceId, lessonId, actorId, createdAt);
            }

                jdbc.update("""
                    INSERT INTO schedule_recurring_create_replay
                        (actor_id, request_key, payload_hash, schedule_item_id,
                         assignment_id, generated_count, generated_from, generated_until, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, actorId, requestKey, payloadHash, scheduleItemId,
                    authority.assignmentId(), dates.size(), generatedFrom, generatedUntil, createdAt);
            if (replacementTemplateId != null && !dates.isEmpty()) {
                jdbc.update("""
                        UPDATE schedule_items
                           SET generated_count = COALESCE(generated_count, 0) + ?,
                               generated_from = CASE WHEN generated_from IS NULL THEN ?
                                                     ELSE LEAST(generated_from, ?) END,
                               generated_until = CASE WHEN generated_until IS NULL THEN ?
                                                      ELSE GREATEST(generated_until, ?) END
                         WHERE id = ?
                        """, dates.size(), generatedFrom, generatedFrom,
                        generatedUntil, generatedUntil, scheduleItemId);
            }
            return new RecurringCreateResult(scheduleItemId, authority.assignmentId(),
                    dates.size(), generatedFrom, generatedUntil);
        } catch (DataIntegrityViolationException ex) {
            throw new RecurringProtocolConflictException(
                    "recurring request conflicts with an existing physical slot or canonical identity");
        }
    }

    private static void validateInputs(CreateScheduleItemRequest request,
                                       RecurringAssignmentAuthority authority,
                                       LocalDate semesterStart,
                                       LocalDate semesterEnd) {
        if (request == null || authority == null || semesterStart == null || semesterEnd == null
                || semesterStart.isAfter(semesterEnd)) {
            throw new RecurringProtocolConflictException("recurring create authority is incomplete");
        }
        if (request.assignmentId() == null || request.assignmentId() != authority.assignmentId()
                || request.groupId() == null || request.groupId() != authority.groupId()
                || request.subjectId() == null || request.subjectId() != authority.subjectId()
                || request.semesterId() == null || request.semesterId() != authority.semesterId()) {
            throw new RecurringProtocolConflictException("request tuple conflicts with assignment authority");
        }
        if (request.dayOfWeek() == null || request.dayOfWeek() < 1 || request.dayOfWeek() > 7
                || request.lessonNumber() == null || request.lessonNumber() < 1 || request.lessonNumber() > 8
                || request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())
                || request.weekType() == null
                || request.room() != null && request.room().length() > 64) {
            throw new RecurringProtocolConflictException("recurring slot/time tuple is invalid");
        }
    }

    RecurringCreateResult replayIfPresent(long actorId,
                                                  UUID requestKey,
                                                  byte[] payloadHash) {
        List<Map<String, Object>> replayRows = jdbc.queryForList("""
                SELECT schedule_item_id, assignment_id, generated_count,
                       generated_from, generated_until, payload_hash
                  FROM schedule_recurring_create_replay
                 WHERE actor_id = ? AND request_key = ?
                 FOR UPDATE
                """, actorId, requestKey);
        if (replayRows.isEmpty()) {
            return null;
        }
        Map<String, Object> replay = replayRows.get(0);
        byte[] storedHash = (byte[]) replay.get("payload_hash");
        if (!MessageDigest.isEqual(storedHash, payloadHash)) {
            throw new ConflictException("Idempotency-Key was already used for a different recurring request");
        }
        return resultFrom(replay);
    }

    LocalDate lockOrInstallFence(RecurringAssignmentAuthority authority) {
        jdbc.update("""
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id,
                     assigned_teacher_id, lesson_type, valid_from, cap_until_exclusive,
                     creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (assignment_id) DO NOTHING
                """, authority.assignmentId(), authority.groupId(), authority.subjectId(),
                authority.semesterId(), authority.teacherId(), authority.lessonType(),
                authority.validFrom(), authority.validUntilExclusive(), authority.validUntilExclusive());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT assignment_id, group_id, subject_id, semester_id,
                       assigned_teacher_id, lesson_type, valid_from, cap_until_exclusive,
                       creation_cap_until_exclusive
                  FROM schedule_assignment_fences
                 WHERE assignment_id = ?
                 FOR UPDATE
                """, authority.assignmentId());
        if (rows.isEmpty()) {
            throw new RecurringProtocolConflictException("assignment fence disappeared");
        }
        Map<String, Object> fence = rows.get(0);
        Boolean replacementPending = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM schedule_assignment_replacement_operations
                     WHERE target_assignment_id = ? AND state <> 'COMMITTED')
                """, Boolean.class, authority.assignmentId());
        if (Boolean.TRUE.equals(replacementPending)) {
            throw new RecurringProtocolConflictException(
                    "replacement target is fenced until Schedule commits the exact operation");
        }
        requireEqual(fence.get("group_id"), authority.groupId(), "group");
        requireEqual(fence.get("subject_id"), authority.subjectId(), "subject");
        requireEqual(fence.get("semester_id"), authority.semesterId(), "semester");
        requireEqual(fence.get("assigned_teacher_id"), authority.teacherId(), "teacher");
        requireEqual(String.valueOf(fence.get("lesson_type")), authority.lessonType(), "lesson type");
        requireEqual(fence.get("valid_from"), authority.validFrom(), "validFrom");
        LocalDate retentionCap = ((java.sql.Date) fence.get("cap_until_exclusive")).toLocalDate();
        LocalDate creationCap = ((java.sql.Date) fence.get("creation_cap_until_exclusive")).toLocalDate();
        if (creationCap.isAfter(authority.validUntilExclusive())) {
            throw new RecurringProtocolConflictException(
                    "verified assignment end is earlier than the local creation cap");
        }
        if (retentionCap.isAfter(authority.validUntilExclusive())) {
            Boolean replacementRetainedHistory = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM schedule_assignment_replacement_operations
                         WHERE source_assignment_id = ?
                           AND effective_from = ?
                           AND state = 'COMMITTED')
                    """, Boolean.class, authority.assignmentId(), creationCap);
            if (!Boolean.TRUE.equals(replacementRetainedHistory)) {
                throw new RecurringProtocolConflictException(
                        "retained fence exceeds Academic authority without a committed replacement");
            }
        }
        // A stale larger remote end is deliberately ignored. The durable
        // local creation cap remains authoritative and is never widened.
        verifyExistingCanonicalRows(authority, retentionCap);
        return creationCap;
    }

    private void verifyExistingCanonicalRows(RecurringAssignmentAuthority authority, LocalDate fenceCap) {
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT group_id, subject_id, semester_id
                  FROM schedule_items
                 WHERE assignment_id = ?
                """, authority.assignmentId());
        for (Map<String, Object> item : items) {
            requireEqual(item.get("group_id"), authority.groupId(), "existing group");
            requireEqual(item.get("subject_id"), authority.subjectId(), "existing subject");
            requireEqual(item.get("semester_id"), authority.semesterId(), "existing semester");
        }
        List<Map<String, Object>> lessons = jdbc.queryForList("""
                SELECT group_id, subject_id, semester_id, assigned_teacher_id,
                       lesson_type, date
                  FROM lessons
                 WHERE assignment_id = ?
                """, authority.assignmentId());
        for (Map<String, Object> lesson : lessons) {
            requireEqual(lesson.get("group_id"), authority.groupId(), "existing lesson group");
            requireEqual(lesson.get("subject_id"), authority.subjectId(), "existing lesson subject");
            requireEqual(lesson.get("semester_id"), authority.semesterId(), "existing lesson semester");
            requireEqual(lesson.get("assigned_teacher_id"), authority.teacherId(), "existing lesson teacher");
            requireEqual(String.valueOf(lesson.get("lesson_type")), authority.lessonType(), "existing lesson type");
            LocalDate date = ((java.sql.Date) lesson.get("date")).toLocalDate();
            if (date.isBefore(authority.validFrom()) || !date.isBefore(fenceCap)) {
                throw new RecurringProtocolConflictException("existing canonical lesson is outside assignment interval");
            }
        }
        List<Map<String, Object>> occurrences = jdbc.queryForList("""
                SELECT group_id, subject_id, semester_id, assigned_teacher_id,
                       lesson_type, occurrence_date
                  FROM lesson_occurrences
                 WHERE assignment_id = ?
                """, authority.assignmentId());
        for (Map<String, Object> occurrence : occurrences) {
            requireEqual(occurrence.get("group_id"), authority.groupId(), "existing occurrence group");
            requireEqual(occurrence.get("subject_id"), authority.subjectId(), "existing occurrence subject");
            requireEqual(occurrence.get("semester_id"), authority.semesterId(), "existing occurrence semester");
            requireEqual(occurrence.get("assigned_teacher_id"), authority.teacherId(),
                    "existing occurrence teacher");
            requireEqual(String.valueOf(occurrence.get("lesson_type")), authority.lessonType(),
                    "existing occurrence lesson type");
            LocalDate date = ((java.sql.Date) occurrence.get("occurrence_date")).toLocalDate();
            if (date.isBefore(authority.validFrom()) || !date.isBefore(fenceCap)) {
                throw new RecurringProtocolConflictException(
                        "existing canonical occurrence is outside assignment interval");
            }
        }
    }

    private long insertScheduleItem(CreateScheduleItemRequest request,
                                    RecurringAssignmentAuthority authority,
                                    OffsetDateTime createdAt,
                                    int generatedCount,
                                    LocalDate generatedFrom,
                                    LocalDate generatedUntil) {
        Long id = jdbc.queryForObject("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active,
                     created_at, generated_count, generated_from, generated_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS week_type), ?, TRUE, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, authority.assignmentId(), authority.groupId(),
                authority.subjectId(), authority.semesterId(), request.dayOfWeek(),
                request.lessonNumber(), request.startTime(), request.endTime(),
                request.weekType().name().toLowerCase(), request.room(), createdAt,
                generatedCount, generatedFrom, generatedUntil);
        if (id == null) throw new RecurringProtocolConflictException("schedule item id was not generated");
        return id;
    }

    /**
     * A cloned replacement template already accounts for moved and skipped
     * source dates.  If a later generation request targets the same tuple,
     * keep those dates out of the new physical batch so held rows cannot be
     * resurrected and moved rows cannot be duplicated.
     */
    private java.util.function.Predicate<LocalDate> replacementAlreadyCovered(
            CreateScheduleItemRequest request,
            RecurringAssignmentAuthority authority,
            LocalDate creationCap) {
        Set<LocalDate> covered = new HashSet<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT DISTINCT ledger.occurrence_date
                  FROM schedule_assignment_rebind_ledger ledger
                  JOIN schedule_items target_item
                    ON target_item.id = ledger.target_schedule_item_id
                 WHERE target_item.assignment_id = ?
                   AND target_item.group_id = ?
                   AND target_item.subject_id = ?
                   AND target_item.semester_id = ?
                   AND target_item.day_of_week = ?
                   AND target_item.lesson_number = ?
                   AND target_item.start_time = ?
                   AND target_item.end_time = ?
                   AND target_item.week_type = CAST(? AS week_type)
                   AND target_item.room IS NOT DISTINCT FROM ?
                   AND ledger.occurrence_date >= ?
                   AND ledger.occurrence_date < ?
                """, request.assignmentId(), request.groupId(), request.subjectId(),
                request.semesterId(), request.dayOfWeek(), request.lessonNumber(),
                request.startTime(), request.endTime(), request.weekType().name().toLowerCase(),
                request.room(), authority.validFrom(), creationCap)) {
            Object value = row.get("occurrence_date");
            covered.add(value instanceof java.sql.Date date ? date.toLocalDate() : LocalDate.parse(value.toString()));
        }
        return covered::contains;
    }

    private Long replacementTemplateId(CreateScheduleItemRequest request) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT target_item.id
                  FROM schedule_assignment_replacement_templates mapping
                 JOIN schedule_items target_item ON target_item.id = mapping.target_schedule_item_id
                 WHERE target_item.assignment_id = ?
                   AND mapping.source_was_active
                   AND target_item.is_active
                   AND target_item.group_id = ?
                   AND target_item.subject_id = ?
                   AND target_item.semester_id = ?
                   AND target_item.day_of_week = ?
                   AND target_item.lesson_number = ?
                   AND target_item.start_time = ?
                   AND target_item.end_time = ?
                   AND target_item.week_type = CAST(? AS week_type)
                   AND target_item.room IS NOT DISTINCT FROM ?
                 ORDER BY target_item.id
                 LIMIT 2
                """, request.assignmentId(), request.groupId(), request.subjectId(),
                request.semesterId(), request.dayOfWeek(), request.lessonNumber(),
                request.startTime(), request.endTime(), request.weekType().name().toLowerCase(),
                request.room());
        if (rows.size() > 1) {
            throw new RecurringProtocolConflictException(
                    "more than one replacement template matches this recurring schedule tuple");
        }
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("id")).longValue();
    }

    private boolean occurrenceExists(long scheduleItemId, LocalDate date) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM lesson_occurrences
                                WHERE schedule_item_id = ? AND occurrence_date = ?)
                """, Boolean.class, scheduleItemId, date);
        return Boolean.TRUE.equals(exists);
    }

    long insertOccurrence(long scheduleItemId,
                                  LocalDate date,
                                  RecurringAssignmentAuthority authority,
                                  OffsetDateTime createdAt) {
        Long id = jdbc.queryForObject("""
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type,
                     generation, revision, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?)
                RETURNING id
                """, Long.class, scheduleItemId, date, authority.assignmentId(),
                authority.groupId(), authority.subjectId(), authority.semesterId(),
                authority.teacherId(), authority.lessonType(), createdAt);
        if (id == null) throw new RecurringProtocolConflictException("occurrence id was not generated");
        return id;
    }

    long insertPhysicalLesson(long scheduleItemId,
                                      long occurrenceId,
                                      LocalDate date,
                                      CreateScheduleItemRequest request,
                                      RecurringAssignmentAuthority authority,
                                      LessonStatus status,
                                      OffsetDateTime createdAt,
                                      Long closedAtEpochMillis) {
        OffsetDateTime closedAt = closedAtEpochMillis == null ? null
                : OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(closedAtEpochMillis), ZoneOffset.UTC);
        Long id = jdbc.queryForObject("""
                INSERT INTO lessons
                    (schedule_item_id, occurrence_id, assignment_id, group_id, subject_id,
                     semester_id, assigned_teacher_id, lesson_type, lesson_number,
                     day_of_week, start_time, end_time, room_snapshot, week_type_snapshot,
                     generation, revision, date, status, is_geo_blocked, created_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?, ?::lesson_status,
                        FALSE, ?, ?)
                RETURNING id
                """, Long.class, scheduleItemId, occurrenceId, authority.assignmentId(),
                authority.groupId(), authority.subjectId(), authority.semesterId(),
                authority.teacherId(), authority.lessonType(), request.lessonNumber(),
                request.dayOfWeek(), request.startTime(), request.endTime(), request.room(),
                request.weekType().name().toLowerCase(), date, status.name().toLowerCase(),
                createdAt, closedAt);
        if (id == null) throw new RecurringProtocolConflictException("physical lesson id was not generated");
        return id;
    }

    static byte[] payloadHash(CreateScheduleItemRequest request) {
        String canonical = ACTION + "|assignment=" + request.assignmentId()
                + "|group=" + request.groupId()
                + "|subject=" + request.subjectId()
                + "|semester=" + request.semesterId()
                + "|day=" + request.dayOfWeek()
                + "|number=" + request.lessonNumber()
                + "|start=" + canonicalTime(request.startTime())
                + "|end=" + canonicalTime(request.endTime())
                + "|week=" + (request.weekType() == null ? "<null>" : request.weekType().name())
                + "|room=" + (request.room() == null ? "<null>" : request.room())
                ;
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    Long inactiveTemplateId(CreateScheduleItemRequest request) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM schedule_items
                 WHERE assignment_id = ? AND group_id = ? AND subject_id = ? AND semester_id = ?
                   AND day_of_week = ? AND lesson_number = ? AND start_time = ? AND end_time = ?
                   AND week_type = CAST(? AS week_type)
                   AND NOT is_active AND deactivated_at IS NOT NULL
                 ORDER BY id LIMIT 2
                """, (rs, row) -> rs.getLong(1), request.assignmentId(), request.groupId(),
                request.subjectId(), request.semesterId(), request.dayOfWeek(), request.lessonNumber(),
                request.startTime(), request.endTime(), request.weekType().name().toLowerCase());
        if (ids.size() > 1) throw new ConflictException("Найдено несколько прежних серий этого слота");
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String canonicalTime(LocalTime time) {
        return time == null ? "<null>" : time.withNano(time.getNano()).format(TIME_FORMAT);
    }

    private static RecurringCreateResult resultFrom(Map<String, Object> row) {
        java.sql.Date from = (java.sql.Date) row.get("generated_from");
        java.sql.Date until = (java.sql.Date) row.get("generated_until");
        return new RecurringCreateResult(
                ((Number) row.get("schedule_item_id")).longValue(),
                ((Number) row.get("assignment_id")).longValue(),
                ((Number) row.get("generated_count")).longValue(),
                from == null ? null : from.toLocalDate(),
                until == null ? null : until.toLocalDate());
    }

    private static void requireEqual(Object actual, Object expected, String field) {
        Object normalizedActual = actual instanceof java.sql.Date d ? d.toLocalDate() : actual;
        if (!java.util.Objects.equals(normalizedActual, expected)) {
            throw new RecurringProtocolConflictException("assignment fence " + field + " conflicts with authority");
        }
    }
}
