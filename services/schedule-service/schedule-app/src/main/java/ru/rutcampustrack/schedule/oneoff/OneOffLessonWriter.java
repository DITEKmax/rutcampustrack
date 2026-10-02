package ru.rutcampustrack.schedule.oneoff;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.event.HomeworkBindingArchivedEvent;
import ru.rutcampustrack.schedule.event.LessonCancelledEvent;
import ru.rutcampustrack.schedule.event.OneOffLessonCreatedEvent;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.InvalidLessonStateException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One transaction owns canonical one-off creation/cancellation and durable events. */
@Service
public class OneOffLessonWriter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ScheduleSemesterArchiveWriteFence archiveFence;
    private final ApplicationEventPublisher events;

    public OneOffLessonWriter(JdbcTemplate jdbc, Clock clock,
                              ScheduleSemesterArchiveWriteFence archiveFence,
                              ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.archiveFence = archiveFence;
        this.events = events;
    }

    /** Receipt refers to accepted creation identity, not an assertion that the lesson is active. */
    public record Creation(long oneOffLessonId, long occurrenceId, long physicalLessonId) { }

    public LocalDate today() { return LocalDate.now(clock.withZone(java.time.ZoneId.of("Europe/Moscow"))); }

    public record RestorePlan(long sourceLessonId, long originId, long occurrenceId, long groupId,
                              long subjectId, long semesterId, String lessonType, LocalDate date,
                              long sourceAssignmentId, long sourceTeacherId, long targetAssignmentId,
                              long targetTeacherId, long occurrenceRevision, long generation,
                              List<UUID> replacements, List<Long> assignments) { }

    @Transactional(readOnly = true)
    public RestorePlan planRestore(long lessonId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT lesson.one_off_lesson_id, occurrence.*
                  FROM lessons lesson JOIN lesson_occurrences occurrence ON occurrence.id = lesson.occurrence_id
                 WHERE lesson.id = ? AND lesson.one_off_lesson_id IS NOT NULL
                """, lessonId);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        if (number(row.get("current_lesson_id")) != lessonId) {
            throw new ConflictException("Эта версия пары уже заменена; обнови журнал");
        }
        long assignment = number(row.get("assignment_id"));
        long teacher = number(row.get("assigned_teacher_id"));
        List<UUID> path = new ArrayList<>();
        List<Long> assignments = new ArrayList<>(List.of(assignment));
        LocalDate date = date(row.get("occurrence_date"));
        // Past snapshots keep their original teacher. Future restores follow the committed assignment chain.
        if (!date.isBefore(LocalDate.now(clock.withZone(java.time.ZoneId.of("Europe/Moscow"))))) {
            HashSet<UUID> seen = new HashSet<>();
            while (true) {
                List<Map<String, Object>> candidates = jdbc.queryForList("""
                        SELECT * FROM schedule_assignment_replacement_operations
                         WHERE source_assignment_id = ? AND effective_from <= ? AND ? < valid_until_exclusive
                         ORDER BY operation_id
                        """, assignment, date, date);
                if (candidates.isEmpty()) break;
                if (candidates.size() != 1 || path.size() >= 32) {
                    throw new ConflictException("Неоднозначная или слишком длинная цепочка замены преподавателя");
                }
                Map<String, Object> replacement = candidates.get(0);
                UUID operation = (UUID) replacement.get("operation_id");
                if (!"COMMITTED".equals(replacement.get("state")) || !seen.add(operation)
                        || number(replacement.get("source_teacher_id")) != teacher
                        || number(replacement.get("group_id")) != number(row.get("group_id"))
                        || number(replacement.get("subject_id")) != number(row.get("subject_id"))
                        || number(replacement.get("semester_id")) != number(row.get("semester_id"))
                        || !Objects.equals(replacement.get("lesson_type"), row.get("lesson_type"))) {
                    throw new ConflictException("Замена преподавателя не подтверждена для этой пары");
                }
                path.add(operation);
                assignment = number(replacement.get("target_assignment_id"));
                teacher = number(replacement.get("target_teacher_id"));
                assignments.add(assignment);
            }
        }
        return new RestorePlan(lessonId, number(row.get("one_off_lesson_id")), number(row.get("id")),
                number(row.get("group_id")), number(row.get("subject_id")), number(row.get("semester_id")),
                String.valueOf(row.get("lesson_type")), date, number(row.get("assignment_id")),
                number(row.get("assigned_teacher_id")), assignment, teacher, number(row.get("revision")),
                number(row.get("generation")), List.copyOf(path), List.copyOf(assignments));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long restore(RestorePlan observed, RecurringAssignmentAuthority authority, long actor) {
        if (actor <= 0) throw new ConflictException("Для восстановления нужен пользователь");
        archiveFence.lockForBusinessWrite(observed.semesterId());
        try {
            for (long assignment : observed.assignments().stream().distinct().sorted().toList()) {
                jdbc.queryForObject("SELECT assignment_id FROM schedule_assignment_fences WHERE assignment_id = ? FOR UPDATE",
                        Long.class, assignment);
            }
            if (authority != null) {
                LocalDate cap = lockOrInstallFence(authority);
                if (authority.assignmentId() != observed.targetAssignmentId()
                        || authority.teacherId() != observed.targetTeacherId()
                        || authority.groupId() != observed.groupId() || authority.subjectId() != observed.subjectId()
                        || authority.semesterId() != observed.semesterId()
                        || !authority.lessonType().equals(observed.lessonType())
                        || observed.date().isBefore(authority.validFrom()) || !observed.date().isBefore(cap)) {
                    throw new ConflictException("Актуальное назначение не разрешает восстановить эту пару");
                }
            }
            for (UUID operation : observed.replacements().stream().sorted().toList()) {
                jdbc.queryForObject("SELECT operation_id FROM schedule_assignment_replacement_operations WHERE operation_id = ? AND state = 'COMMITTED' FOR UPDATE",
                        UUID.class, operation);
            }
            jdbc.queryForObject("SELECT id FROM schedule_one_off_lessons WHERE id = ? FOR UPDATE", Long.class, observed.originId());
            jdbc.queryForObject("SELECT id FROM lesson_occurrences WHERE id = ? FOR UPDATE", Long.class, observed.occurrenceId());
            if (!observed.equals(planRestore(observed.sourceLessonId()))) {
                throw new ConflictException("Пара или назначение изменились во время восстановления");
            }
            Map<String, Object> source = jdbc.queryForMap("SELECT *, status::text AS current_status FROM lessons WHERE id = ? FOR UPDATE",
                    observed.sourceLessonId());
            if (!"cancelled".equals(source.get("current_status"))) {
                throw new InvalidLessonStateException("Восстановить можно только отменённую пару");
            }
            Boolean unfinished = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM lesson_transfer_operations WHERE occurrence_id = ? AND state <> 'COMPLETED')",
                    Boolean.class, observed.occurrenceId());
            if (Boolean.TRUE.equals(unfinished)) throw new ConflictException("Перенос пары ещё не завершён");
            OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
            if (observed.targetAssignmentId() == observed.sourceAssignmentId()) {
                int changed = jdbc.update("""
                        UPDATE lessons SET status = 'planned', cancel_reason = NULL, cancelled_by = NULL,
                               cancelled_at = NULL, revision = revision + 1 WHERE id = ? AND revision = ? AND status = 'cancelled'
                        """, observed.sourceLessonId(), number(source.get("revision")));
                int occurrenceChanged = jdbc.update("UPDATE lesson_occurrences SET revision = revision + 1 WHERE id = ? AND revision = ? AND current_lesson_id = ?",
                        observed.occurrenceId(), observed.occurrenceRevision(), observed.sourceLessonId());
                if (changed != 1 || occurrenceChanged != 1) throw new ConflictException("Пара изменилась во время восстановления");
                jdbc.update("""
                        INSERT INTO lesson_lifecycle_entries(occurrence_id, revision, action, lesson_id, generation, actor_id, occurred_at)
                        VALUES (?, ?, 'RESTORED', ?, ?, ?, ?)
                        """, observed.occurrenceId(), observed.occurrenceRevision() + 1, observed.sourceLessonId(), observed.generation(), actor, now);
                return observed.sourceLessonId();
            }
            UUID operation = UUID.randomUUID();
            long target = jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('lessons','id'))", Long.class);
            jdbc.queryForObject("SELECT set_config('rutcampustrack.one_off_restore_operation_id', ?, true)", String.class, operation.toString());
            String path = "{" + String.join(",", observed.replacements().stream().map(UUID::toString).toList()) + "}";
            jdbc.update("""
                    INSERT INTO one_off_lesson_restore_authorities
                        (operation_id, semester_id, occurrence_id, source_lesson_id, source_assignment_id, source_teacher_id,
                         expected_occurrence_revision, expected_generation, expected_lesson_revision, target_lesson_id,
                         target_assignment_id, target_teacher_id, target_generation, replacement_operation_ids, actor_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid[], ?, ?)
                    """, operation, observed.semesterId(), observed.occurrenceId(), observed.sourceLessonId(), observed.sourceAssignmentId(),
                    observed.sourceTeacherId(), observed.occurrenceRevision(), observed.generation(), number(source.get("revision")), target,
                    observed.targetAssignmentId(), observed.targetTeacherId(), observed.generation() + 1, path, actor, now);
            jdbc.update("""
                    UPDATE lesson_occurrences SET current_lesson_id = ?, assignment_id = ?, assigned_teacher_id = ?,
                           generation = generation + 1, revision = revision + 1 WHERE id = ? AND current_lesson_id = ? AND revision = ?
                    """, target, observed.targetAssignmentId(), observed.targetTeacherId(), observed.occurrenceId(), observed.sourceLessonId(), observed.occurrenceRevision());
            jdbc.update("""
                    INSERT INTO lessons(id, one_off_lesson_id, occurrence_id, assignment_id, group_id, subject_id, semester_id,
                        assigned_teacher_id, lesson_type, lesson_number, day_of_week, start_time, end_time, room_snapshot,
                        week_type_snapshot, generation, revision, date, status, is_geo_blocked, is_blocked_by_headman,
                        blocked_by_user_id, blocked_at, created_at)
                    OVERRIDING SYSTEM VALUE
                    SELECT ?, one_off_lesson_id, occurrence_id, ?, group_id, subject_id, semester_id, ?, lesson_type,
                        lesson_number, day_of_week, start_time, end_time, room_snapshot, week_type_snapshot, ?, 1, date,
                        'planned', is_geo_blocked, is_blocked_by_headman, blocked_by_user_id, blocked_at, ? FROM lessons WHERE id = ?
                    """, target, observed.targetAssignmentId(), observed.targetTeacherId(), observed.generation() + 1, now, observed.sourceLessonId());
            jdbc.update("UPDATE schedule_one_off_lessons SET physical_lesson_id = ? WHERE id = ?", target, observed.originId());
            jdbc.update("""
                    INSERT INTO lesson_lifecycle_entries(occurrence_id, revision, action, lesson_id, target_lesson_id, generation,
                        actor_id, occurred_at, one_off_restore_operation_id) VALUES (?, ?, 'RESTORED', ?, ?, ?, ?, ?, ?)
                    """, observed.occurrenceId(), observed.occurrenceRevision() + 1, observed.sourceLessonId(), target, observed.generation() + 1, actor, now, operation);
            return target;
        } catch (DataIntegrityViolationException error) {
            throw new ConflictException("Восстановление конфликтует с занятым слотом или текущим назначением");
        }
    }

    @Transactional(readOnly = true)
    public Creation findReplay(CreateOneOffLessonRequest request, UUID key, long actor) {
        return replay(actor, key, payloadHash(request));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Creation create(CreateOneOffLessonRequest request, UUID key, long actor,
                           RecurringAssignmentAuthority authority) {
        if (key == null || actor <= 0) throw new ConflictException("Нужны пользователь и UUID request key");
        byte[] hash = payloadHash(request);
        Creation replay = replay(actor, key, hash);
        if (replay != null) return replay;
        archiveFence.lockForBusinessWrite(authority.semesterId());
        try {
            LocalDate creationCap = lockOrInstallFence(authority);
            replay = replay(actor, key, hash);
            if (replay != null) return replay;
            if (request.assignmentId() != authority.assignmentId()
                    || request.groupId() != authority.groupId() || request.subjectId() != authority.subjectId()
                    || request.date().isBefore(authority.validFrom()) || !request.date().isBefore(creationCap)) {
                throw new ConflictException("Дата или scope пары не входят в актуальное назначение");
            }
            OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
            long originId = jdbc.queryForObject("""
                    INSERT INTO schedule_one_off_lessons
                        (group_id, subject_id, semester_id, date, lesson_number, classroom, created_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, authority.groupId(), authority.subjectId(), authority.semesterId(),
                    request.date(), request.lessonNumber(), request.classroom(), actor, now);
            long occurrenceId = jdbc.queryForObject("""
                    INSERT INTO lesson_occurrences
                        (one_off_lesson_id, occurrence_date, assignment_id, group_id, subject_id,
                         semester_id, assigned_teacher_id, lesson_type, generation, revision, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, ?) RETURNING id
                    """, Long.class, originId, request.date(), authority.assignmentId(), authority.groupId(),
                    authority.subjectId(), authority.semesterId(), authority.teacherId(), authority.lessonType(), now);
            long lessonId = jdbc.queryForObject("""
                    INSERT INTO lessons
                        (one_off_lesson_id, occurrence_id, assignment_id, group_id, subject_id, semester_id,
                         assigned_teacher_id, lesson_type, lesson_number, day_of_week, start_time, end_time,
                         room_snapshot, week_type_snapshot, generation, revision, date, status,
                         is_geo_blocked, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'all', 1, 1, ?, 'planned', FALSE, ?)
                    RETURNING id
                    """, Long.class, originId, occurrenceId, authority.assignmentId(), authority.groupId(),
                    authority.subjectId(), authority.semesterId(), authority.teacherId(), authority.lessonType(),
                    request.lessonNumber(), request.date().getDayOfWeek().getValue(), request.startTime(),
                    request.endTime(), request.classroom(), request.date(), now);
            jdbc.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?", lessonId, occurrenceId);
            jdbc.update("UPDATE schedule_one_off_lessons SET physical_lesson_id = ? WHERE id = ?", lessonId, originId);
            jdbc.update("""
                    INSERT INTO lesson_lifecycle_entries
                        (occurrence_id, revision, action, lesson_id, generation, actor_id, occurred_at)
                    VALUES (?, 1, 'CREATED', ?, 1, ?, ?)
                    """, occurrenceId, lessonId, actor, now);
            jdbc.update("""
                    INSERT INTO schedule_one_off_create_replay
                        (actor_id, request_key, payload_hash, semester_id, one_off_lesson_id,
                         occurrence_id, physical_lesson_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, actor, key, hash, authority.semesterId(), originId, occurrenceId, lessonId, now);
            events.publishEvent(new OneOffLessonCreatedEvent(this, originId, authority.groupId(),
                    authority.subjectId(), request.date(), request.lessonNumber().intValue(), request.classroom()));
            return new Creation(originId, occurrenceId, lessonId);
        } catch (DataIntegrityViolationException error) {
            throw new ConflictException("Слот пары или request key уже занят; обнови расписание");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long cancelOrigin(long originId, String reason, long actor) {
        return cancel(originId, null, reason, actor);
    }

    /** Shared lesson API delegates here and must not publish a second cancellation event. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long cancelPhysical(long lessonId, String reason, long actor) {
        List<Long> origins = jdbc.query("SELECT one_off_lesson_id FROM lessons WHERE id = ?",
                (rs, row) -> rs.getObject(1, Long.class), lessonId);
        if (origins.isEmpty()) throw new ResourceNotFoundException("Lesson", "id", lessonId);
        if (origins.get(0) == null) throw new ConflictException("Пара не относится к разовому расписанию");
        return cancel(origins.get(0), lessonId, reason, actor);
    }

    private long cancel(long originId, Long requestedLessonId, String reason, long actor) {
        if (actor <= 0 || reason == null || reason.isBlank() || reason.length() > 512) {
            throw new ConflictException("Для отмены нужны пользователь и причина");
        }
        List<Map<String, Object>> observed = jdbc.queryForList("""
                SELECT id, assignment_id, semester_id FROM lesson_occurrences WHERE one_off_lesson_id = ?
                """, originId);
        if (observed.size() != 1) throw new ConflictException("Не найдена каноническая разовая пара");
        Map<String, Object> before = observed.get(0);
        long semesterId = number(before.get("semester_id"));
        archiveFence.lockForBusinessWrite(semesterId);
        try {
            jdbc.queryForObject("SELECT assignment_id FROM schedule_assignment_fences WHERE assignment_id = ? FOR UPDATE",
                    Long.class, number(before.get("assignment_id")));
            jdbc.queryForObject("SELECT id FROM schedule_one_off_lessons WHERE id = ? FOR UPDATE", Long.class, originId);
            Map<String, Object> occurrence = jdbc.queryForMap("""
                    SELECT id, current_lesson_id, assignment_id, semester_id, generation, revision
                      FROM lesson_occurrences WHERE id = ? AND one_off_lesson_id = ? FOR UPDATE
                    """, number(before.get("id")), originId);
            if (number(occurrence.get("assignment_id")) != number(before.get("assignment_id"))) {
                throw new ConflictException("Назначение пары изменилось во время отмены");
            }
            long lessonId = number(occurrence.get("current_lesson_id"));
            if (requestedLessonId != null && lessonId != requestedLessonId) {
                throw new ConflictException("Эта физическая версия пары уже заменена; обнови журнал");
            }
            Map<String, Object> lesson = jdbc.queryForMap("""
                    SELECT id, occurrence_id, one_off_lesson_id, group_id, subject_id, semester_id,
                           date, lesson_number, start_time, end_time, status::text AS status, revision
                      FROM lessons WHERE id = ? AND occurrence_id = ? AND one_off_lesson_id = ? FOR UPDATE
                    """, lessonId, number(occurrence.get("id")), originId);
            Boolean transferring = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM lesson_transfer_operations
                                   WHERE occurrence_id = ? AND state <> 'COMPLETED')
                    """, Boolean.class, number(occurrence.get("id")));
            if (Boolean.TRUE.equals(transferring)) throw new ConflictException("Перенос пары ещё не завершён");
            String status = String.valueOf(lesson.get("status"));
            if (!List.of("planned", "active", "closed").contains(status)) {
                throw new InvalidLessonStateException("Пара уже отменена или перенесена");
            }
            OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
            int changed = jdbc.update("""
                    UPDATE lessons SET status = 'cancelled', cancel_reason = ?, cancelled_by = ?,
                           cancelled_at = ?, revision = revision + 1
                     WHERE id = ? AND occurrence_id = ? AND revision = ?
                       AND status::text IN ('planned','active','closed')
                    """, reason, actor, now, lessonId, number(occurrence.get("id")), number(lesson.get("revision")));
            int occurrenceChanged = jdbc.update("""
                    UPDATE lesson_occurrences SET revision = revision + 1
                     WHERE id = ? AND current_lesson_id = ? AND generation = ? AND revision = ?
                    """, number(occurrence.get("id")), lessonId,
                    number(occurrence.get("generation")), number(occurrence.get("revision")));
            if (changed != 1 || occurrenceChanged != 1) throw new ConflictException("Пара изменилась во время отмены");
            jdbc.update("""
                    INSERT INTO lesson_lifecycle_entries
                        (occurrence_id, revision, action, lesson_id, generation, reason, actor_id, occurred_at)
                    VALUES (?, ?, 'CANCELLED', ?, ?, ?, ?, ?)
                    """, number(occurrence.get("id")), number(occurrence.get("revision")) + 1,
                    lessonId, number(occurrence.get("generation")), reason, actor, now);
            archiveBindings(number(occurrence.get("id")), lessonId, semesterId);
            events.publishEvent(new LessonCancelledEvent(this, lessonId, number(lesson.get("group_id")),
                    number(lesson.get("subject_id")), date(lesson.get("date")), time(lesson.get("start_time")),
                    time(lesson.get("end_time")), Math.toIntExact(number(lesson.get("lesson_number"))),
                    reason, actor, now, semesterId));
            return lessonId;
        } catch (DataIntegrityViolationException error) {
            throw new ConflictException("Отмена конфликтует с текущим состоянием разовой пары");
        }
    }

    private void archiveBindings(long occurrenceId, long lessonId, long semesterId) {
        List<Map<String, Object>> bindings = jdbc.queryForList("""
                SELECT binding_id, actor_id, request_key, homework_id, revision
                  FROM lesson_homework_bindings WHERE occurrence_id = ? AND current_lesson_id = ?
                   AND state IN ('PENDING','ACTIVE') ORDER BY binding_id FOR UPDATE
                """, occurrenceId, lessonId);
        for (Map<String, Object> binding : bindings) {
            long revision = number(binding.get("revision"));
            int changed = jdbc.update("""
                    UPDATE lesson_homework_bindings SET state = 'ARCHIVED', revision = revision + 1, updated_at = NOW()
                     WHERE binding_id = ? AND occurrence_id = ? AND current_lesson_id = ?
                       AND revision = ? AND state IN ('PENDING','ACTIVE')
                    """, number(binding.get("binding_id")), occurrenceId, lessonId, revision);
            if (changed != 1) throw new ConflictException("ДЗ изменилось во время отмены пары");
            events.publishEvent(new HomeworkBindingArchivedEvent(this, number(binding.get("binding_id")),
                    number(binding.get("actor_id")), (UUID) binding.get("request_key"), occurrenceId, lessonId,
                    binding.get("homework_id") == null ? null : number(binding.get("homework_id")),
                    revision + 1, semesterId));
        }
    }

    private Creation replay(long actor, UUID key, byte[] hash) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT one_off_lesson_id, occurrence_id, physical_lesson_id, payload_hash
                  FROM schedule_one_off_create_replay WHERE actor_id = ? AND request_key = ?
                """, actor, key);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        if (!MessageDigest.isEqual(hash, (byte[]) row.get("payload_hash"))) {
            throw new ConflictException("Idempotency-Key уже использован для другой разовой пары");
        }
        return new Creation(number(row.get("one_off_lesson_id")), number(row.get("occurrence_id")),
                number(row.get("physical_lesson_id")));
    }

    private LocalDate lockOrInstallFence(RecurringAssignmentAuthority authority) {
        jdbc.update("""
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(assignment_id) DO NOTHING
                """, authority.assignmentId(), authority.groupId(), authority.subjectId(), authority.semesterId(),
                authority.teacherId(), authority.lessonType(), authority.validFrom(),
                authority.validUntilExclusive(), authority.validUntilExclusive());
        Map<String, Object> fence = jdbc.queryForMap("""
                SELECT group_id, subject_id, semester_id, assigned_teacher_id, lesson_type, valid_from,
                       cap_until_exclusive, creation_cap_until_exclusive
                  FROM schedule_assignment_fences WHERE assignment_id = ? FOR UPDATE
                """, authority.assignmentId());
        Boolean pending = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM schedule_assignment_replacement_operations
                               WHERE target_assignment_id = ? AND state <> 'COMMITTED')
                """, Boolean.class, authority.assignmentId());
        if (Boolean.TRUE.equals(pending)) throw new ConflictException("Замена назначения ещё не завершена");
        if (number(fence.get("group_id")) != authority.groupId()
                || number(fence.get("subject_id")) != authority.subjectId()
                || number(fence.get("semester_id")) != authority.semesterId()
                || number(fence.get("assigned_teacher_id")) != authority.teacherId()
                || !Objects.equals(fence.get("lesson_type"), authority.lessonType())
                || !date(fence.get("valid_from")).equals(authority.validFrom())) {
            throw new ConflictException("Локальная граница назначения не соответствует Academic");
        }
        LocalDate cap = date(fence.get("creation_cap_until_exclusive"));
        if (cap.isAfter(authority.validUntilExclusive())) {
            throw new ConflictException("Academic сократил период назначения");
        }
        LocalDate retentionCap = date(fence.get("cap_until_exclusive"));
        if (retentionCap.isAfter(authority.validUntilExclusive())) {
            Boolean retained = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM schedule_assignment_replacement_operations
                                   WHERE source_assignment_id = ? AND effective_from = ? AND state = 'COMMITTED')
                    """, Boolean.class, authority.assignmentId(), cap);
            if (!Boolean.TRUE.equals(retained)) throw new ConflictException("Нет подтверждённой истории замены назначения");
        }
        return cap;
    }

    private static byte[] payloadHash(CreateOneOffLessonRequest request) {
        StringBuilder value = new StringBuilder("ONE_OFF_CREATE_V1");
        append(value, String.valueOf(request.assignmentId()));
        append(value, String.valueOf(request.groupId()));
        append(value, String.valueOf(request.subjectId()));
        append(value, String.valueOf(request.date()));
        append(value, String.valueOf(request.lessonNumber()));
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS");
        append(value, request.startTime().format(format));
        append(value, request.endTime().format(format));
        append(value, request.classroom());
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append('|').append(value == null ? -1 : value.length()).append(':');
        if (value != null) target.append(value);
    }

    private static long number(Object value) { return ((Number) value).longValue(); }
    private static LocalDate date(Object value) {
        return value instanceof LocalDate date ? date : ((java.sql.Date) value).toLocalDate();
    }
    private static LocalTime time(Object value) {
        return value instanceof LocalTime time ? time : ((java.sql.Time) value).toLocalTime();
    }
}
