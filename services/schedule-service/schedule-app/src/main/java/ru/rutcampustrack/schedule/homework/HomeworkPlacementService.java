package ru.rutcampustrack.schedule.homework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.schedule.event.HomeworkBindingArchivedEvent;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Guarded placement commands; the existing binding remains the publication identity. */
@Service
public class HomeworkPlacementService {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ScheduleSemesterArchiveWriteFence fence;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public HomeworkPlacementService(JdbcTemplate jdbc, ObjectMapper mapper,
                                    ScheduleSemesterArchiveWriteFence fence,
                                    ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc; this.mapper = mapper; this.fence = fence; this.events = events; this.clock = clock;
    }

    @Transactional
    public HomeworkBindingResponse reserveDate(ReserveHomeworkBindingRequest request) {
        InternalJwtClaims claims = HomeworkBindingActorContext.requireClaims();
        requireWrite(claims, request.getGroupId());
        UUID key = UUID.fromString(request.getRequestKey());
        LocalDate date = LocalDate.parse(request.getDate());
        if (date.isBefore(today()) || request.getPayloadHash().size() != 32 || request.getOccurrenceId() != 0) {
            throw new IllegalArgumentException("DATE requires a non-past date and no lesson occurrence");
        }
        fence.lockForBusinessWrite(request.getSemesterId());
        lockKey(claims.userId(), key);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT * FROM lesson_homework_bindings WHERE actor_id = ? AND request_key = ? FOR UPDATE", claims.userId(), key);
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.getFirst();
            if (!"DATE".equals(row.get("original_binding_mode"))
                    || !Arrays.equals((byte[]) row.get("payload_hash"), request.getPayloadHash().toByteArray())
                    || !date.equals(date(row.get("original_date")))
                    || number(row, "group_id") != request.getGroupId()
                    || number(row, "subject_id") != request.getSubjectId()
                    || number(row, "semester_id") != request.getSemesterId()) {
                throw new ConflictException("request_key resolves to another DATE create intent");
            }
            return response(row);
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO lesson_homework_bindings
                    (binding_mode, group_id, subject_id, semester_id, placement_date,
                     actor_id, request_key, payload_hash, state, revision)
                VALUES ('DATE', ?, ?, ?, ?, ?, ?, ?, 'PENDING', 1) RETURNING binding_id
                """, Long.class, request.getGroupId(), request.getSubjectId(), request.getSemesterId(), date,
                claims.userId(), key, request.getPayloadHash().toByteArray());
        return response(binding(id, false));
    }

    @Transactional
    public HomeworkBindingResponse confirmDate(ConfirmHomeworkBindingRequest request) {
        Map<String, Object> routed = binding(request.getBindingId(), false);
        fence.lockForPendingBindingConfirmation(number(routed, "semester_id"));
        Map<String, Object> row = binding(request.getBindingId(), true);
        InternalJwtClaims claims = HomeworkBindingActorContext.requireClaims();
        requireWrite(claims, number(row, "group_id"));
        if (number(row, "actor_id") != claims.userId()
                || !row.get("request_key").toString().equals(request.getRequestKey())) {
            throw new ConflictException("confirmation does not match create identity");
        }
        if (expire(row)) return response(binding(request.getBindingId(), false));
        if ("ARCHIVED".equals(row.get("state"))) throw new ConflictException("archived binding cannot be confirmed");
        if (row.get("homework_id") != null) {
            if (number(row, "homework_id") != request.getHomeworkId()) throw new ConflictException("another homework identity");
            return response(row);
        }
        jdbc.update("UPDATE lesson_homework_bindings SET homework_id = ?, state = 'ACTIVE', revision = revision + 1, updated_at = now() WHERE binding_id = ?",
                request.getHomeworkId(), request.getBindingId());
        return response(binding(request.getBindingId(), false));
    }

    @Transactional
    public HomeworkBindingResponse archiveDate(ArchiveHomeworkBindingRequest request) {
        Map<String, Object> routed = binding(request.getBindingId(), false);
        if (!"ARCHIVED".equals(routed.get("state"))) fence.lockForBusinessWrite(number(routed, "semester_id"));
        Map<String, Object> row = binding(request.getBindingId(), true);
        requireWrite(HomeworkBindingActorContext.requireClaims(), number(row, "group_id"));
        if (!row.get("request_key").toString().equals(request.getRequestKey())
                || row.get("homework_id") != null && number(row, "homework_id") != request.getHomeworkId()) {
            throw new ConflictException("archive does not match binding identity");
        }
        if (!"ARCHIVED".equals(row.get("state"))) jdbc.update("""
                UPDATE lesson_homework_bindings SET homework_id = coalesce(homework_id, ?), state = 'ARCHIVED',
                    revision = revision + 1, updated_at = now() WHERE binding_id = ?
                """, request.getHomeworkId(), request.getBindingId());
        return response(binding(request.getBindingId(), false));
    }

    @Transactional(readOnly = true)
    public HomeworkBindingResponse get(long bindingId) {
        Map<String, Object> row = binding(bindingId, false);
        requireRead(HomeworkBindingActorContext.requireClaims(), number(row, "group_id"));
        return response(row);
    }

    @Transactional
    public HomeworkEditReceipt move(MoveHomeworkBindingRequest request) {
        HomeworkEditIdentity identity = request.getIdentity();
        validate(identity);
        requireWrite(HomeworkBindingActorContext.requireClaims(), identity.getGroupId());
        if (HomeworkBindingActorContext.requireClaims().userId() != identity.getActorId()) {
            throw new AccessDeniedException("edit actor does not match signed identity");
        }
        fence.lockForBusinessWrite(identity.getSemesterId());
        // The operation key lock is shared with NOT_ACCEPTED tombstone creation.
        lockKey(identity.getActorId(), UUID.fromString(identity.getRequestKey()));
        Map<String, Object> prior = operation(identity, false);
        if (prior != null) return receipt(prior, identity);
        Map<String, Object> routed = binding(identity.getBindingId(), false);
        List<Long> occurrenceIds = new ArrayList<>();
        if (routed.get("occurrence_id") != null) occurrenceIds.add(number(routed, "occurrence_id"));
        if ("LESSON".equals(request.getBindingMode())) occurrenceIds.add(request.getTargetOccurrenceId());
        lockOriginsAndOccurrences(occurrenceIds);
        Map<String, Object> row = binding(identity.getBindingId(), true);
        requireIdentity(row, identity);
        if (!"ACTIVE".equals(row.get("state")) || expire(row)) throw new ConflictException("terminal homework cannot move");
        if (row.get("pending_edit_operation_id") != null) throw new ConflictException("another edit awaits Academic acknowledgement");
        if (number(row, "revision") != request.getExpectedBindingRevision()) throw new ConflictException("binding revision changed");
        LocalDate targetDate = LocalDate.parse(request.getDate());
        if (targetDate.isBefore(today())) throw new IllegalArgumentException("placement date cannot be in the past");
        Long targetOccurrence = null, targetLesson = null;
        Integer targetNumber = null;
        if ("LESSON".equals(request.getBindingMode())) {
            Map<String, Object> target = jdbc.queryForMap("""
                    SELECT lesson.* FROM lesson_occurrences occurrence
                     JOIN lessons lesson ON lesson.id = occurrence.current_lesson_id AND lesson.occurrence_id = occurrence.id
                     WHERE occurrence.id = ?
                    """, request.getTargetOccurrenceId());
            if (number(target, "group_id") != identity.getGroupId()
                    || number(target, "subject_id") != identity.getSubjectId()
                    || number(target, "semester_id") != identity.getSemesterId()
                    || number(target, "revision") != request.getExpectedLessonRevision()
                    || !targetDate.equals(date(target.get("date")))
                    || number(target, "lesson_number") != request.getLessonNumber()
                    || !List.of("planned", "active", "closed").contains(target.get("status").toString())) {
                throw new ConflictException("target lesson identity/revision changed");
            }
            rejectTransfer(request.getTargetOccurrenceId());
            targetOccurrence = request.getTargetOccurrenceId(); targetLesson = number(target, "id");
            targetNumber = request.getLessonNumber();
        } else if (!"DATE".equals(request.getBindingMode()) || request.getLessonNumber() != 0
                || request.getTargetOccurrenceId() != 0) throw new IllegalArgumentException("invalid DATE placement");
        if (routed.get("occurrence_id") != null) rejectTransfer(number(routed, "occurrence_id"));
        long revision = number(row, "revision") + 1;
        Placement accepted = new Placement(identity.getBindingId(), targetOccurrence, targetLesson,
                identity.getHomeworkId(), identity.getGroupId(), identity.getSubjectId(), identity.getSemesterId(),
                request.getBindingMode(), targetDate.toString(), targetNumber, revision, "ACTIVE");
        jdbc.update("""
                INSERT INTO homework_placement_operations
                    (operation_id, binding_id, homework_id, actor_id, request_key, command_hash,
                     group_id, subject_id, semester_id, state, accepted_binding, accepted_revision,
                     target_mode, target_occurrence_id, target_lesson_id, target_date, target_lesson_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPLIED_AWAITING_ACK', ?::jsonb, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString(identity.getOperationId()), identity.getBindingId(), identity.getHomeworkId(),
                identity.getActorId(), UUID.fromString(identity.getRequestKey()), identity.getCommandHash().toByteArray(),
                identity.getGroupId(), identity.getSubjectId(), identity.getSemesterId(), json(accepted), revision,
                request.getBindingMode(), targetOccurrence, targetLesson, targetDate, targetNumber);
        jdbc.queryForObject("SELECT set_config('rutcampustrack.homework_edit_operation_id', ?, true)",
                String.class, identity.getOperationId());
        jdbc.update("""
                UPDATE lesson_homework_bindings SET binding_mode = ?, occurrence_id = ?, current_lesson_id = ?,
                    placement_date = ?, placement_lesson_number = ?, revision = ?, pending_edit_operation_id = ?, updated_at = now()
                 WHERE binding_id = ?
                """, request.getBindingMode(), targetOccurrence, targetLesson, targetDate, targetNumber, revision,
                UUID.fromString(identity.getOperationId()), identity.getBindingId());
        return receipt(operation(identity, false), identity);
    }

    /** Exact service continuation never creates or changes a placement. */
    @Transactional(readOnly = true)
    public HomeworkEditReceipt continuation(HomeworkEditIdentity identity) {
        validate(identity);
        Map<String, Object> prior = operation(identity, false);
        if (prior == null) throw new ResourceNotFoundException("HomeworkEdit", "operation_id", identity.getOperationId());
        return receipt(prior, identity);
    }

    /** CAS tombstone prevents a delayed initial Move after a missing recovery lookup. */
    @Transactional
    public HomeworkEditReceipt abortUnaccepted(HomeworkEditIdentity identity) {
        validate(identity);
        ScheduleSemesterArchiveWriteFence.lockSemester(jdbc, identity.getSemesterId());
        lockKey(identity.getActorId(), UUID.fromString(identity.getRequestKey()));
        Map<String, Object> prior = operation(identity, false);
        if (prior != null) return receipt(prior, identity);
        requireIdentity(binding(identity.getBindingId(), true), identity);
        jdbc.update("""
                INSERT INTO homework_placement_operations
                    (operation_id, binding_id, homework_id, actor_id, request_key, command_hash,
                     group_id, subject_id, semester_id, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_ACCEPTED')
                """, UUID.fromString(identity.getOperationId()), identity.getBindingId(), identity.getHomeworkId(),
                identity.getActorId(), UUID.fromString(identity.getRequestKey()), identity.getCommandHash().toByteArray(),
                identity.getGroupId(), identity.getSubjectId(), identity.getSemesterId());
        return receipt(operation(identity, false), identity);
    }

    @Transactional
    public HomeworkEditReceipt acknowledge(HomeworkEditIdentity identity) {
        validate(identity);
        ScheduleSemesterArchiveWriteFence.lockSemester(jdbc, identity.getSemesterId());
        lockKey(identity.getActorId(), UUID.fromString(identity.getRequestKey()));
        Map<String, Object> prior = operation(identity, true);
        if (prior == null) throw new ResourceNotFoundException("HomeworkEdit", "operation_id", identity.getOperationId());
        HomeworkEditReceipt result = receipt(prior, identity);
        if (!"APPLIED_AWAITING_ACK".equals(result.getState())) return result;
        Map<String, Object> row = binding(identity.getBindingId(), true);
        requireIdentity(row, identity);
        if (!UUID.fromString(identity.getOperationId()).equals(row.get("pending_edit_operation_id"))) {
            throw new ConflictException("acknowledgement does not own the pending placement gate");
        }
        jdbc.update("UPDATE homework_placement_operations SET state = 'ACKNOWLEDGED', acknowledged_at = now() WHERE operation_id = ?",
                UUID.fromString(identity.getOperationId()));
        jdbc.update("UPDATE lesson_homework_bindings SET pending_edit_operation_id = NULL WHERE binding_id = ?", identity.getBindingId());
        return receipt(operation(identity, false), identity);
    }

    @Transactional
    public int archiveExpiredDates() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT binding_id, semester_id FROM lesson_homework_bindings
                 WHERE binding_mode = 'DATE' AND state <> 'ARCHIVED' AND placement_date < ?
                 ORDER BY semester_id, binding_id LIMIT 256
                """, today());
        Set<Long> writable = fence.lockWritableSemesters(due.stream().map(row -> number(row, "semester_id")).toList());
        int count = 0;
        for (Map<String, Object> candidate : due) {
            if (writable.contains(number(candidate, "semester_id")) && expire(binding(number(candidate, "binding_id"), true))) count++;
        }
        return count;
    }

    private boolean expire(Map<String, Object> row) {
        if (!"DATE".equals(row.get("binding_mode")) || "ARCHIVED".equals(row.get("state"))
                || !date(row.get("placement_date")).isBefore(today())) return false;
        jdbc.update("UPDATE lesson_homework_bindings SET state = 'ARCHIVED', revision = revision + 1, updated_at = now() WHERE binding_id = ?",
                number(row, "binding_id"));
        events.publishEvent(new HomeworkBindingArchivedEvent(this, number(row, "binding_id"),
                number(row, "actor_id"), (UUID) row.get("request_key"), null, null,
                nullableNumber(row, "homework_id"), number(row, "revision") + 1, number(row, "semester_id")));
        return true;
    }

    private Map<String, Object> binding(long id, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM lesson_homework_bindings WHERE binding_id = ?" + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("HomeworkBinding", "binding_id", id);
        return rows.getFirst();
    }

    private Map<String, Object> operation(HomeworkEditIdentity identity, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM homework_placement_operations WHERE operation_id = ?" + (lock ? " FOR UPDATE" : ""),
                UUID.fromString(identity.getOperationId()));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private HomeworkEditReceipt receipt(Map<String, Object> row, HomeworkEditIdentity identity) {
        if (number(row, "binding_id") != identity.getBindingId() || number(row, "homework_id") != identity.getHomeworkId()
                || number(row, "actor_id") != identity.getActorId() || !row.get("request_key").toString().equals(identity.getRequestKey())
                || !Arrays.equals((byte[]) row.get("command_hash"), identity.getCommandHash().toByteArray())
                || number(row, "group_id") != identity.getGroupId() || number(row, "subject_id") != identity.getSubjectId()
                || number(row, "semester_id") != identity.getSemesterId()) throw new ConflictException("edit recovery tuple differs from receipt");
        HomeworkEditReceipt.Builder result = HomeworkEditReceipt.newBuilder().setIdentity(identity).setState(row.get("state").toString());
        if (row.get("accepted_binding") != null) {
            try { result.setAcceptedBinding(readPlacement(row.get("accepted_binding").toString()).response()); }
            catch (JsonProcessingException invalid) { throw new IllegalStateException("invalid durable placement receipt", invalid); }
        }
        return result.build();
    }

    private HomeworkBindingResponse response(Map<String, Object> row) {
        return new Placement(number(row, "binding_id"), nullableNumber(row, "occurrence_id"), nullableNumber(row, "current_lesson_id"),
                nullableNumber(row, "homework_id"), number(row, "group_id"), number(row, "subject_id"), number(row, "semester_id"),
                row.get("binding_mode").toString(), date(row.get("placement_date")).toString(),
                row.get("placement_lesson_number") == null ? null : ((Number) row.get("placement_lesson_number")).intValue(),
                number(row, "revision"), row.get("state").toString()).response();
    }

    private void lockKey(long actor, UUID key) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 7351)) IS NULL", Boolean.class, actor + ":" + key);
    }

    private void lockOriginsAndOccurrences(List<Long> ids) {
        List<Long> ordered = ids.stream().filter(id -> id != null && id > 0).distinct().sorted().toList();
        List<Map<String, Object>> origins = new ArrayList<>();
        for (Long id : ordered) origins.add(jdbc.queryForMap("SELECT schedule_item_id, one_off_lesson_id FROM lesson_occurrences WHERE id = ?", id));
        for (String column : List.of("schedule_item_id", "one_off_lesson_id")) {
            for (Long origin : origins.stream().map(row -> nullableNumber(row, column)).filter(Objects::nonNull).distinct().sorted().toList()) {
                String table = column.equals("schedule_item_id") ? "schedule_items" : "schedule_one_off_lessons";
                jdbc.queryForObject("SELECT id FROM " + table + " WHERE id = ? FOR UPDATE", Long.class, origin);
            }
        }
        for (Long id : ordered) jdbc.queryForList("""
                SELECT occurrence.id FROM lesson_occurrences occurrence JOIN lessons lesson
                  ON lesson.id = occurrence.current_lesson_id AND lesson.occurrence_id = occurrence.id
                 WHERE occurrence.id = ? FOR UPDATE OF occurrence, lesson
                """, id);
    }

    private void rejectTransfer(long occurrence) {
        Boolean pending = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM lesson_transfer_operations WHERE occurrence_id = ? AND state <> 'COMPLETED')",
                Boolean.class, occurrence);
        if (Boolean.TRUE.equals(pending)) throw new ConflictException("placement is blocked until lesson transfer completes");
    }

    private static void requireIdentity(Map<String, Object> row, HomeworkEditIdentity identity) {
        if (number(row, "homework_id") != identity.getHomeworkId() || number(row, "group_id") != identity.getGroupId()
                || number(row, "subject_id") != identity.getSubjectId() || number(row, "semester_id") != identity.getSemesterId()) {
            throw new ConflictException("edit identity does not match publication scope");
        }
    }
    private static void validate(HomeworkEditIdentity identity) {
        UUID.fromString(identity.getOperationId()); UUID.fromString(identity.getRequestKey());
        if (identity.getBindingId() <= 0 || identity.getHomeworkId() <= 0 || identity.getActorId() <= 0
                || identity.getGroupId() <= 0 || identity.getSubjectId() <= 0 || identity.getSemesterId() <= 0
                || identity.getCommandHash().size() != 32) throw new IllegalArgumentException("invalid persisted edit tuple");
    }
    private static void requireWrite(InternalJwtClaims claims, long group) {
        if (!"ACTIVE".equals(claims.status()) || claims.readOnly()) throw new AccessDeniedException("active homework authority required");
        requireRead(claims, group);
    }
    private static void requireRead(InternalJwtClaims claims, long group) {
        if ((!"ACTIVE".equals(claims.status()) && !claims.readOnly())
                || (!"STUDENT".equals(claims.role()) && !"HEADMAN".equals(claims.role()))
                || claims.groupId() == null || claims.groupId() != group) throw new AccessDeniedException("homework belongs to another group");
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(MOSCOW)); }
    private static long number(Map<String, Object> row, String field) { return ((Number) row.get(field)).longValue(); }
    private static Long nullableNumber(Map<String, Object> row, String field) { return row.get(field) == null ? null : number(row, field); }
    private static LocalDate date(Object value) { return value instanceof LocalDate local ? local : LocalDate.parse(value.toString()); }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException invalid) { throw new IllegalStateException(invalid); }
    }
    private Placement readPlacement(String json) throws JsonProcessingException { return mapper.readValue(json, Placement.class); }

    private record Placement(long bindingId, Long occurrenceId, Long lessonId, Long homeworkId,
                             long groupId, long subjectId, long semesterId, String mode,
                             String date, Integer lessonNumber, long revision, String state) {
        HomeworkBindingResponse response() {
            HomeworkBindingResponse.Builder result = HomeworkBindingResponse.newBuilder().setBindingId(bindingId)
                    .setGroupId(groupId).setSubjectId(subjectId).setSemesterId(semesterId).setBindingMode(mode)
                    .setDate(date).setLessonNumber(lessonNumber == null ? 0 : lessonNumber).setRevision(revision)
                    .setState(HomeworkBindingState.valueOf("HOMEWORK_BINDING_STATE_" + state));
            if (homeworkId != null) result.setHomeworkId(homeworkId);
            if (occurrenceId != null) result.setOccurrenceId(occurrenceId).setCurrentLesson(LessonInfo.newBuilder()
                    .setLessonId(lessonId).setOccurrenceId(occurrenceId).setGroupId(groupId).setSubjectId(subjectId)
                    .setSemesterId(semesterId).setDate(date).setLessonNumber(lessonNumber).build());
            return result.build();
        }
    }
}
