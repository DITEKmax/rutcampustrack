package ru.rutcampustrack.schedule.lesson;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.InvalidLessonStateException;
import ru.rutcampustrack.schedule.event.HomeworkBindingArchivedEvent;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transactional lifecycle writer for recurring V17 occurrences. The caller
 * performs group authorization before entering this writer; all physical and
 * canonical history changes are committed together here.
 */
@Service
public class RecurringLessonLifecycleWriter {

    private static final String RESTORE_OPERATION_SETTING =
            "rutcampustrack.lesson_restore_operation_id";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduleSemesterArchiveWriteFence archiveWriteFence;

    public RecurringLessonLifecycleWriter(JdbcTemplate jdbc,
                                          Clock clock,
                                          ApplicationEventPublisher eventPublisher,
                                          ScheduleSemesterArchiveWriteFence archiveWriteFence) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.archiveWriteFence = archiveWriteFence;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(long lessonId, String reason, long actorId) {
        try {
            Snapshot before = readByLesson(lessonId);
            requireRecurring(before);
            archiveWriteFence.lockForBusinessWrite(before.occurrence().semesterId());
            lockFences(List.of(before.occurrence().assignmentId()));
            lockItems(List.of(before.occurrence().scheduleItemId()));
            LockedCurrent current = lockCurrent(before.occurrenceId());
            requireNoUnfinishedTransfer(before.occurrenceId());
            requireRequestedLessonIsCurrent(lessonId, current);
            if (!"planned".equals(current.lesson().status())
                    && !"active".equals(current.lesson().status())
                    && !"closed".equals(current.lesson().status())) {
                throw new InvalidLessonStateException("Lesson is already cancelled");
            }

            OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
            int lessonUpdated = jdbc.update("""
                    UPDATE lessons
                       SET status = 'cancelled'::lesson_status,
                           cancel_reason = ?, cancelled_by = ?, cancelled_at = ?,
                           revision = revision + 1
                     WHERE id = ? AND occurrence_id = ? AND status::text IN ('planned', 'active', 'closed')
                       AND revision = ?
                    """, reason, actorId, now, lessonId, current.occurrence().id(),
                    current.lesson().revision());
            int occurrenceUpdated = jdbc.update("""
                    UPDATE lesson_occurrences
                       SET revision = revision + 1
                     WHERE id = ? AND current_lesson_id = ? AND generation = ? AND revision = ?
                    """, current.occurrence().id(), lessonId,
                    current.occurrence().generation(), current.occurrence().revision());
            if (lessonUpdated != 1 || occurrenceUpdated != 1) {
                throw new ConflictException("Пара изменилась во время отмены; обнови журнал и повтори действие");
            }
            insertLifecycle(current.occurrence(), current.occurrence().revision() + 1,
                    "CANCELLED", lessonId, null, current.occurrence().generation(),
                    reason, actorId, now, null);
            archiveOccurrenceBindings(current.occurrence().id(), lessonId, current.occurrence().semesterId());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Отмена конфликтует с текущим состоянием пары");
        }
    }

    /** Returns the id of the physical lesson that is current after restore. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long restore(long lessonId, long actorId) {
        try {
            Snapshot before = readByLesson(lessonId);
            requireRecurring(before);
            if (!"cancelled".equals(before.lesson().status())) {
                throw new InvalidLessonStateException(
                        "Only cancelled lessons can be restored, current status: " + before.lesson().status());
            }

            archiveWriteFence.lockForBusinessWrite(before.occurrence().semesterId());
            Target targetBeforeLocks = resolveTarget(before.occurrence());
            lockFences(targetBeforeLocks.assignmentIds());
            lockItems(targetBeforeLocks.scheduleItemIds());
            lockReplacementOperations(targetBeforeLocks.operationIds());
            LockedCurrent current = lockCurrent(before.occurrenceId());
            requireNoUnfinishedTransfer(before.occurrenceId());
            requireRequestedLessonIsCurrent(lessonId, current);
            if (!"cancelled".equals(current.lesson().status())) {
                throw new InvalidLessonStateException(
                        "Only cancelled lessons can be restored, current status: " + current.lesson().status());
            }

            Target target = resolveTarget(current.occurrence());
            if (!target.equals(targetBeforeLocks)) {
                throw new ConflictException("Назначение пары изменилось во время восстановления; обнови журнал");
            }
            Map<String, Object> sourceItem = readItem(current.occurrence().scheduleItemId());
            Map<String, Object> targetItem = readItem(target.scheduleItemId());
            requireSameRecurringSlot(sourceItem, targetItem, current.occurrence());
            requireFenceForDate(target.assignmentId(), target.teacherId(), current.occurrence());

            if (target.assignmentId() == current.occurrence().assignmentId()
                    && target.teacherId() == current.occurrence().assignedTeacherId()
                    && target.scheduleItemId() == current.occurrence().scheduleItemId()) {
                return restoreSamePhysicalLesson(current, lessonId, actorId);
            }
            return restoreAsNewGeneration(current, target, targetItem, lessonId, actorId);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Восстановление конфликтует с занятым слотом или актуальным назначением");
        }
    }

    private long restoreSamePhysicalLesson(LockedCurrent current, long lessonId, long actorId) {
        OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
        int lessonUpdated = jdbc.update("""
                UPDATE lessons
                   SET status = 'planned'::lesson_status,
                       cancel_reason = NULL, cancelled_by = NULL, cancelled_at = NULL,
                       revision = revision + 1
                 WHERE id = ? AND occurrence_id = ? AND status::text = 'cancelled'
                   AND revision = ?
                """, lessonId, current.occurrence().id(), current.lesson().revision());
        int occurrenceUpdated = jdbc.update("""
                UPDATE lesson_occurrences
                   SET revision = revision + 1
                 WHERE id = ? AND current_lesson_id = ? AND generation = ? AND revision = ?
                """, current.occurrence().id(), lessonId,
                current.occurrence().generation(), current.occurrence().revision());
        if (lessonUpdated != 1 || occurrenceUpdated != 1) {
            throw new ConflictException("Пара изменилась во время восстановления; обнови журнал и повтори действие");
        }
        insertLifecycle(current.occurrence(), current.occurrence().revision() + 1,
                "RESTORED", lessonId, null, current.occurrence().generation(),
                null, actorId, now, null);
        return lessonId;
    }

    private long restoreAsNewGeneration(LockedCurrent current,
                                        Target target,
                                        Map<String, Object> targetItem,
                                        long sourceLessonId,
                                        long actorId) {
        UUID operationId = UUID.randomUUID();
        long targetLessonId = jdbc.queryForObject(
                "SELECT nextval(pg_get_serial_sequence('lessons', 'id'))", Long.class);
        OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
        jdbc.queryForObject("SELECT set_config(?, ?, true)", String.class,
                RESTORE_OPERATION_SETTING, operationId.toString());

        jdbc.update("""
                INSERT INTO lesson_restore_authorities
                    (operation_id, occurrence_id, source_lesson_id, source_schedule_item_id,
                     source_assignment_id, source_teacher_id, expected_occurrence_revision,
                     expected_generation, expected_lesson_revision, target_lesson_id,
                     target_schedule_item_id, target_assignment_id, target_teacher_id,
                     target_generation, replacement_operation_ids, actor_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid[], ?, ?)
                """, operationId, current.occurrence().id(), sourceLessonId,
                current.occurrence().scheduleItemId(), current.occurrence().assignmentId(),
                current.occurrence().assignedTeacherId(), current.occurrence().revision(),
                current.occurrence().generation(), current.lesson().revision(), targetLessonId,
                target.scheduleItemId(), target.assignmentId(), target.teacherId(),
                current.occurrence().generation() + 1, uuidArray(target.operationIds()), actorId, now);

        int occurrenceUpdated = jdbc.update("""
                UPDATE lesson_occurrences
                   SET schedule_item_id = ?, assignment_id = ?, assigned_teacher_id = ?,
                       current_lesson_id = ?, generation = generation + 1, revision = revision + 1
                 WHERE id = ? AND current_lesson_id = ? AND schedule_item_id = ?
                   AND assignment_id = ? AND assigned_teacher_id = ?
                   AND generation = ? AND revision = ?
                """, target.scheduleItemId(), target.assignmentId(), target.teacherId(),
                targetLessonId, current.occurrence().id(), sourceLessonId,
                current.occurrence().scheduleItemId(), current.occurrence().assignmentId(),
                current.occurrence().assignedTeacherId(), current.occurrence().generation(),
                current.occurrence().revision());
        if (occurrenceUpdated != 1) {
            throw new ConflictException("Текущая версия пары изменилась во время восстановления");
        }
        insertRestoredGeneration(targetLessonId, current, target, targetItem, now);
        insertLifecycle(current.occurrence(), current.occurrence().revision() + 1,
                "RESTORED", sourceLessonId, targetLessonId,
                current.occurrence().generation() + 1, null, actorId, now, operationId);
        return targetLessonId;
    }

    private void insertRestoredGeneration(long lessonId,
                                          LockedCurrent current,
                                          Target target,
                                          Map<String, Object> item,
                                          OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO lessons
                    (id, schedule_item_id, one_off_lesson_id, occurrence_id, assignment_id,
                     group_id, subject_id, semester_id, assigned_teacher_id, lesson_type,
                     lesson_number, day_of_week, start_time, end_time, room_snapshot,
                     week_type_snapshot, generation, revision, date, status, is_geo_blocked,
                     is_blocked_by_headman, blocked_by_user_id, blocked_at,
                     cancel_reason, cancelled_by, cancelled_at, created_at)
                OVERRIDING SYSTEM VALUE
                VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?,
                        'planned'::lesson_status, ?, ?, ?, ?, NULL, NULL, NULL, ?)
                """, lessonId, target.scheduleItemId(), current.occurrence().id(),
                target.assignmentId(), current.occurrence().groupId(),
                current.occurrence().subjectId(), current.occurrence().semesterId(),
                target.teacherId(), current.occurrence().lessonType(),
                item.get("lesson_number"), item.get("day_of_week"), item.get("start_time"),
                item.get("end_time"), item.get("room"), item.get("week_type"),
                current.occurrence().generation() + 1, current.occurrence().date(),
                current.lesson().geoBlocked(), current.lesson().blockedByHeadman(),
                current.lesson().blockedByUserId(), current.lesson().blockedAt(), now);
    }

    private void insertLifecycle(Occurrence occurrence,
                                 long revision,
                                 String action,
                                 long lessonId,
                                 Long targetLessonId,
                                 long generation,
                                 String reason,
                                 long actorId,
                                 OffsetDateTime occurredAt,
                                 UUID restoreOperationId) {
        jdbc.update("""
                INSERT INTO lesson_lifecycle_entries
                    (occurrence_id, revision, action, lesson_id, target_lesson_id,
                     generation, reason, actor_id, occurred_at, restore_operation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, occurrence.id(), revision, action, lessonId, targetLessonId,
                generation, reason, actorId, occurredAt, restoreOperationId);
    }

    private Target resolveTarget(Occurrence occurrence) {
        long itemId = occurrence.scheduleItemId();
        long assignmentId = occurrence.assignmentId();
        long teacherId = occurrence.assignedTeacherId();
        List<UUID> operations = new ArrayList<>();
        LinkedHashSet<Long> itemIds = new LinkedHashSet<>();
        LinkedHashSet<Long> assignmentIds = new LinkedHashSet<>();
        itemIds.add(itemId);
        assignmentIds.add(assignmentId);
        Set<UUID> seen = new HashSet<>();
        for (int hop = 0; hop < 32; hop++) {
            List<Map<String, Object>> candidates = jdbc.queryForList("""
                    SELECT operation.operation_id, operation.state,
                           operation.target_assignment_id, operation.target_teacher_id,
                           mapping.target_schedule_item_id
                      FROM schedule_assignment_replacement_operations operation
                      JOIN schedule_assignment_replacement_templates mapping
                        ON mapping.operation_id = operation.operation_id
                       AND mapping.source_schedule_item_id = ?
                     WHERE operation.source_assignment_id = ?
                       AND operation.effective_from <= ?
                       AND ? < operation.valid_until_exclusive
                     ORDER BY operation.operation_id
                    """, itemId, assignmentId, occurrence.date(), occurrence.date());
            if (candidates.isEmpty()) break;
            if (candidates.size() != 1) {
                throw new ConflictException("Для пары найдено несколько назначений на одну дату");
            }
            Map<String, Object> candidate = candidates.get(0);
            UUID operationId = UUID.fromString(String.valueOf(candidate.get("operation_id")));
            if (!"COMMITTED".equals(candidate.get("state")) || !seen.add(operationId)) {
                throw new ConflictException("Замена преподавателя ещё не завершена или образует цикл");
            }
            long nextAssignmentId = number(candidate.get("target_assignment_id"));
            long nextTeacherId = number(candidate.get("target_teacher_id"));
            long nextItemId = number(candidate.get("target_schedule_item_id"));
            operations.add(operationId);
            itemIds.add(nextItemId);
            assignmentIds.add(nextAssignmentId);
            itemId = nextItemId;
            assignmentId = nextAssignmentId;
            teacherId = nextTeacherId;
        }
        if (operations.size() == 32) {
            throw new ConflictException("Цепочка замен преподавателя превышает поддерживаемую глубину");
        }
        return new Target(itemId, assignmentId, teacherId,
                List.copyOf(operations), List.copyOf(itemIds), List.copyOf(assignmentIds));
    }

    private void lockFences(List<Long> assignmentIds) {
        for (long assignmentId : assignmentIds.stream().distinct().sorted().toList()) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT assignment_id FROM schedule_assignment_fences
                     WHERE assignment_id = ? FOR UPDATE
                    """, assignmentId);
            if (rows.size() != 1) throw new ConflictException("Не найдено актуальное назначение преподавателя");
        }
    }

    private void lockItems(List<Long> itemIds) {
        for (long itemId : itemIds.stream().distinct().sorted().toList()) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id FROM schedule_items WHERE id = ? FOR UPDATE
                    """, itemId);
            if (rows.size() != 1) throw new ConflictException("Шаблон пары изменился; обнови журнал");
        }
    }

    private void lockReplacementOperations(List<UUID> operationIds) {
        for (UUID operationId : operationIds.stream().distinct().sorted().toList()) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT operation_id FROM schedule_assignment_replacement_operations
                     WHERE operation_id = ? AND state = 'COMMITTED' FOR UPDATE
                    """, operationId);
            if (rows.size() != 1) {
                throw new ConflictException("Замена преподавателя изменилась; обнови журнал и повтори действие");
            }
        }
    }

    private void archiveOccurrenceBindings(long occurrenceId, long currentLessonId, long semesterId) {
        List<BindingSnapshot> bindings = jdbc.query("""
                SELECT binding_id, actor_id, request_key, homework_id, revision
                  FROM lesson_homework_bindings
                 WHERE occurrence_id = ? AND current_lesson_id = ?
                   AND state IN ('PENDING', 'ACTIVE')
                 ORDER BY binding_id FOR UPDATE
                """, (rs, rowNum) -> new BindingSnapshot(
                rs.getLong("binding_id"), rs.getLong("actor_id"),
                rs.getObject("request_key", UUID.class),
                rs.getObject("homework_id", Long.class), rs.getLong("revision")),
                occurrenceId, currentLessonId);
        for (BindingSnapshot binding : bindings) {
            int updated = jdbc.update("""
                    UPDATE lesson_homework_bindings
                       SET state = 'ARCHIVED', revision = revision + 1, updated_at = NOW()
                     WHERE binding_id = ? AND occurrence_id = ? AND current_lesson_id = ?
                       AND revision = ? AND state IN ('PENDING', 'ACTIVE')
                    """, binding.bindingId(), occurrenceId, currentLessonId, binding.revision());
            if (updated != 1) {
                throw new ConflictException("Homework binding изменился во время отмены пары");
            }
            eventPublisher.publishEvent(new HomeworkBindingArchivedEvent(this,
                    binding.bindingId(), binding.actorId(), binding.requestKey(), occurrenceId,
                    currentLessonId, binding.homeworkId(), binding.revision() + 1, semesterId));
        }
    }

    private LockedCurrent lockCurrent(long occurrenceId) {
        Map<String, Object> occurrence = jdbc.queryForMap("""
                SELECT id, schedule_item_id, assignment_id, group_id, subject_id, semester_id,
                       assigned_teacher_id, lesson_type, occurrence_date, generation, revision,
                       current_lesson_id
                  FROM lesson_occurrences WHERE id = ? FOR UPDATE
                """, occurrenceId);
        long currentLessonId = number(occurrence.get("current_lesson_id"));
        Map<String, Object> lesson = jdbc.queryForMap("""
                SELECT id, occurrence_id, schedule_item_id, assignment_id, assigned_teacher_id,
                       generation, revision, status::text AS status, is_geo_blocked,
                       is_blocked_by_headman, blocked_by_user_id, blocked_at
                  FROM lessons WHERE id = ? AND occurrence_id = ? FOR UPDATE
                """, currentLessonId, occurrenceId);
        return new LockedCurrent(Occurrence.from(occurrence), LessonState.from(lesson));
    }

    private Snapshot readByLesson(long lessonId) {
        Map<String, Object> occurrenceIdRow = jdbc.queryForMap(
                "SELECT occurrence_id FROM lessons WHERE id = ?", lessonId);
        long occurrenceId = number(occurrenceIdRow.get("occurrence_id"));
        Map<String, Object> occurrence = jdbc.queryForMap("""
                SELECT id, schedule_item_id, assignment_id, group_id, subject_id, semester_id,
                       assigned_teacher_id, lesson_type, occurrence_date, generation, revision,
                       current_lesson_id
                  FROM lesson_occurrences WHERE id = ?
                """, occurrenceId);
        Map<String, Object> lesson = jdbc.queryForMap("""
                SELECT id, occurrence_id, schedule_item_id, assignment_id, assigned_teacher_id,
                       generation, revision, status::text AS status, is_geo_blocked,
                       is_blocked_by_headman, blocked_by_user_id, blocked_at
                  FROM lessons WHERE id = ? AND occurrence_id = ?
                """, lessonId, occurrenceId);
        return new Snapshot(Occurrence.from(occurrence), LessonState.from(lesson));
    }

    private Map<String, Object> readItem(long itemId) {
        return jdbc.queryForMap("""
                SELECT id, assignment_id, group_id, subject_id, semester_id, day_of_week,
                       lesson_number, start_time, end_time, week_type::text AS week_type, room
                  FROM schedule_items WHERE id = ?
                """, itemId);
    }

    private void requireFenceForDate(long assignmentId, long teacherId, Occurrence occurrence) {
        Map<String, Object> fence = jdbc.queryForMap("""
                SELECT assignment_id, assigned_teacher_id, group_id, subject_id, semester_id,
                       lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive
                  FROM schedule_assignment_fences WHERE assignment_id = ?
                """, assignmentId);
        if (number(fence.get("assigned_teacher_id")) != teacherId
                || number(fence.get("group_id")) != occurrence.groupId()
                || number(fence.get("subject_id")) != occurrence.subjectId()
                || number(fence.get("semester_id")) != occurrence.semesterId()
                || !String.valueOf(fence.get("lesson_type")).equals(occurrence.lessonType())
                || occurrence.date().isBefore(localDate(fence.get("valid_from")))
                || !occurrence.date().isBefore(localDate(fence.get("cap_until_exclusive")))
                || !occurrence.date().isBefore(localDate(fence.get("creation_cap_until_exclusive")))) {
            throw new ConflictException("Нет действующего назначения для даты этой пары");
        }
    }

    private void requireSameRecurringSlot(Map<String, Object> source,
                                          Map<String, Object> target,
                                          Occurrence occurrence) {
        if (number(target.get("group_id")) != occurrence.groupId()
                || number(target.get("subject_id")) != occurrence.subjectId()
                || number(target.get("semester_id")) != occurrence.semesterId()
                || number(target.get("day_of_week")) != number(source.get("day_of_week"))
                || number(target.get("lesson_number")) != number(source.get("lesson_number"))
                || !target.get("start_time").equals(source.get("start_time"))
                || !target.get("end_time").equals(source.get("end_time"))
                || !target.get("week_type").equals(source.get("week_type"))
                || !java.util.Objects.equals(target.get("room"), source.get("room"))) {
            throw new ConflictException("Замена преподавателя изменила физический слот пары");
        }
    }

    private void requireRequestedLessonIsCurrent(long lessonId, LockedCurrent current) {
        if (current.occurrence().currentLessonId() != lessonId) {
            throw new ConflictException("Эта версия пары уже заменена; обнови журнал перед повтором");
        }
    }

    private void requireNoUnfinishedTransfer(long occurrenceId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_transfer_operations
                 WHERE occurrence_id = ? AND state <> 'COMPLETED'
                """, Integer.class, occurrenceId);
        if (count != null && count > 0) {
            throw new ConflictException("Предыдущий перенос пары ещё не завершён");
        }
    }

    private void requireRecurring(Snapshot snapshot) {
        if (snapshot.occurrence().scheduleItemId() == null) {
            throw new ConflictException("Восстановление этой пары не поддерживается текущим циклом расписания");
        }
    }

    private static String uuidArray(List<UUID> values) {
        return "{" + values.stream().map(UUID::toString).collect(Collectors.joining(",")) + "}";
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        throw new IllegalArgumentException("Expected a JDBC date value, got " + value);
    }

    private record Snapshot(Occurrence occurrence, LessonState lesson) {
        long occurrenceId() { return occurrence.id(); }
    }

    private record BindingSnapshot(long bindingId,
                                   long actorId,
                                   UUID requestKey,
                                   Long homeworkId,
                                   long revision) { }

    private record LockedCurrent(Occurrence occurrence, LessonState lesson) { }

    private record Target(long scheduleItemId,
                          long assignmentId,
                          long teacherId,
                          List<UUID> operationIds,
                          List<Long> scheduleItemIds,
                          List<Long> assignmentIds) { }

    private record Occurrence(long id,
                              Long scheduleItemId,
                              long assignmentId,
                              long groupId,
                              long subjectId,
                              long semesterId,
                              long assignedTeacherId,
                              String lessonType,
                              LocalDate date,
                              long generation,
                              long revision,
                              long currentLessonId) {
        static Occurrence from(Map<String, Object> row) {
            return new Occurrence(number(row.get("id")),
                    row.get("schedule_item_id") == null ? null : number(row.get("schedule_item_id")),
                    number(row.get("assignment_id")), number(row.get("group_id")),
                    number(row.get("subject_id")), number(row.get("semester_id")),
                    number(row.get("assigned_teacher_id")), String.valueOf(row.get("lesson_type")),
                    localDate(row.get("occurrence_date")), number(row.get("generation")),
                    number(row.get("revision")), number(row.get("current_lesson_id")));
        }
    }

    private record LessonState(long id,
                               String status,
                               long revision,
                               boolean geoBlocked,
                               boolean blockedByHeadman,
                               Long blockedByUserId,
                               OffsetDateTime blockedAt) {
        static LessonState from(Map<String, Object> row) {
            return new LessonState(number(row.get("id")), String.valueOf(row.get("status")),
                    number(row.get("revision")), Boolean.TRUE.equals(row.get("is_geo_blocked")),
                    Boolean.TRUE.equals(row.get("is_blocked_by_headman")),
                    row.get("blocked_by_user_id") == null ? null : number(row.get("blocked_by_user_id")),
                    (OffsetDateTime) row.get("blocked_at"));
        }
    }
}
