package ru.rutcampustrack.schedule.homework;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingActorContext;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingState;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.ResolveLessonRequest;
import ru.rutcampustrack.schedule.grpc.ReserveHomeworkBindingRequest;
import ru.rutcampustrack.schedule.grpc.ConfirmHomeworkBindingRequest;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingsRequest;
import ru.rutcampustrack.schedule.grpc.ArchiveHomeworkBindingRequest;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Schedule-owned implementation of the homework binding protocol.
 *
 * <p>The repository deliberately uses JDBC because the V17 tables represent
 * immutable physical snapshots and composite foreign keys which are not part
 * of the legacy JPA model. Every writer follows one lock graph:
 * origin, occurrence/current physical, bindings ordered by binding_id. The
 * Academic service owns homework content; this service owns only the binding
 * and its lifecycle.</p>
 */
@Service
public class HomeworkBindingService {

    private static final String BINDING_COLUMNS = """
            binding_id, occurrence_id, current_lesson_id, homework_id, actor_id,
            request_key, payload_hash, state, revision
            """;

    private final JdbcTemplate jdbcTemplate;

    public HomeworkBindingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public HomeworkBindingResponse reserve(ReserveHomeworkBindingRequest request) {
        InternalJwtClaims claims = HomeworkBindingActorContext.requireClaims();
        long actorId = claims.userId();
        long occurrenceId = positive(request.getOccurrenceId(), "occurrence_id");
        long expectedRevision = positive(request.getExpectedRevision(), "expected_revision");
        UUID requestKey = parseRequestKey(request.getRequestKey());
        byte[] payloadHash = request.getPayloadHash().toByteArray();
        if (payloadHash.length != 32) {
            throw new IllegalArgumentException("payload_hash must contain exactly 32 bytes");
        }

        // An idempotent retry is routed by its durable actor/key identity
        // before consulting the mutable natural slot.  A transfer may have
        // moved the current physical lesson since the original reservation;
        // replay must return that current pointer instead of re-resolving the
        // old date/number or rejecting a valid same-occurrence retry.
        BindingRow replay = findBindingByActorAndKey(actorId, requestKey);
        if (replay != null) {
            if (replay.occurrenceId() != occurrenceId
                    || !Arrays.equals(replay.payloadHash(), payloadHash)) {
                throw new ConflictException("request_key was already used for another homework binding");
            }
            OccurrenceSnapshot current = lockCurrentOccurrence(replay.occurrenceId());
            assertWriteAccess(claims, current.groupId());
            List<BindingRow> bindings = lockBindings(List.of(replay.occurrenceId()));
            BindingRow fresh = bindings.stream()
                    .filter(row -> row.bindingId() == replay.bindingId())
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "HomeworkBinding", "binding_id", replay.bindingId()));
            if (fresh.actorId() != actorId
                    || !fresh.requestKey().equals(requestKey)
                    || !Arrays.equals(fresh.payloadHash(), payloadHash)) {
                throw new ConflictException("homework binding replay identity changed");
            }
            return toResponse(fresh, current);
        }

        // Required lock order: origin -> occurrence/current physical -> bindings.
        OccurrenceSnapshot current = lockCurrentOccurrence(occurrenceId);
        assertWriteAccess(claims, current.groupId());
        if (current.occurrenceRevision() != expectedRevision) {
            throw new ConflictException("occurrence revision changed before homework reservation");
        }
        List<BindingRow> bindings = lockBindings(List.of(occurrenceId));
        for (BindingRow existing : bindings) {
            if (existing.actorId() != actorId || !existing.requestKey().equals(requestKey)) {
                continue;
            }
            if (Arrays.equals(existing.payloadHash(), payloadHash)) {
                return toResponse(existing, current);
            }
            throw new ConflictException("request_key was already used for another homework binding payload");
        }

        Long bindingId;
        try {
            bindingId = jdbcTemplate.queryForObject("""
                INSERT INTO lesson_homework_bindings
                    (occurrence_id, current_lesson_id, actor_id, request_key,
                     payload_hash, state, revision)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 1)
                ON CONFLICT (actor_id, request_key) DO NOTHING
                RETURNING binding_id
                """, Long.class, occurrenceId, current.lessonId(), actorId,
                requestKey, payloadHash);
        } catch (EmptyResultDataAccessException ignored) {
            BindingRow existing = findBindingByActorAndKey(actorId, requestKey);
            if (existing != null && existing.occurrenceId() == occurrenceId
                    && Arrays.equals(existing.payloadHash(), payloadHash)
                    && existing.currentLessonId() == current.lessonId()) {
                return toResponse(existing, current);
            }
            throw new ConflictException("request_key was already used for another homework binding");
        }
        if (bindingId == null || bindingId <= 0) {
            throw new IllegalStateException("schedule returned an invalid homework binding id");
        }
        BindingRow inserted = findBinding(bindingId);
        return toResponse(inserted, current);
    }

    @Transactional
    public HomeworkBindingResponse confirm(ConfirmHomeworkBindingRequest request) {
        InternalJwtClaims claims = HomeworkBindingActorContext.requireClaims();
        long actorId = claims.userId();
        long bindingId = positive(request.getBindingId(), "binding_id");
        long homeworkId = positive(request.getHomeworkId(), "homework_id");
        UUID requestKey = parseRequestKey(request.getRequestKey());

        // The first read only discovers routing. It is never used as the
        // update source: the row is re-read after the ordered locks below.
        BindingRow routed = findBinding(bindingId);
        OccurrenceSnapshot current = lockCurrentOccurrence(routed.occurrenceId());
        List<BindingRow> bindings = lockBindings(List.of(routed.occurrenceId()));
        BindingRow binding = bindings.stream()
                .filter(row -> row.bindingId() == bindingId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HomeworkBinding", "binding_id", bindingId));
        assertWriteAccess(claims, current.groupId());

        if (binding.actorId() != actorId) {
            throw new ConflictException("homework binding belongs to another actor");
        }
        if (!binding.requestKey().equals(requestKey)) {
            throw new ConflictException("request_key does not match the reserved binding");
        }
        if ("ARCHIVED".equals(binding.state())) {
            throw new ConflictException("archived homework binding cannot be confirmed");
        }
        if ("ACTIVE".equals(binding.state())) {
            if (Objects.equals(binding.homeworkId(), homeworkId)) {
                return toResponse(binding, current);
            }
            throw new ConflictException("active homework binding cannot be replaced");
        }
        if (!"PENDING".equals(binding.state()) || binding.homeworkId() != null) {
            throw new ConflictException("homework binding is not confirmable");
        }

        // Persist only the intended transition. The revision predicate makes
        // lost responses/retries deterministic and protects against stale
        // detached routing state.
        int updated = jdbcTemplate.update("""
                UPDATE lesson_homework_bindings
                   SET homework_id = ?, state = 'ACTIVE', revision = revision + 1,
                       updated_at = NOW()
                 WHERE binding_id = ?
                   AND revision = ?
                   AND state = 'PENDING'
                   AND homework_id IS NULL
                """, homeworkId, bindingId, binding.revision());
        if (updated != 1) {
            throw new ConflictException("homework binding revision changed before confirmation");
        }

        BindingRow confirmed = findBinding(bindingId);
        return toResponse(confirmed, current);
    }

    /**
     * Terminally archives a binding as part of Academic's coordinated
     * homework deletion.  The row and its FK chain remain durable; retries
     * with the same actor/key/homework identity return ARCHIVED.
     */
    @Transactional
    public HomeworkBindingResponse archive(ArchiveHomeworkBindingRequest request) {
        InternalJwtClaims claims = HomeworkBindingActorContext.requireClaims();
        long actorId = claims.userId();
        long bindingId = positive(request.getBindingId(), "binding_id");
        long homeworkId = positive(request.getHomeworkId(), "homework_id");
        UUID requestKey = parseRequestKey(request.getRequestKey());

        // Routing is read only. All mutable state is re-read after the common
        // origin -> occurrence/current physical -> bindings lock order.
        BindingRow routed = findBinding(bindingId);
        OccurrenceSnapshot current = lockCurrentOccurrence(routed.occurrenceId());
        List<BindingRow> bindings = lockBindings(List.of(routed.occurrenceId()));
        BindingRow binding = bindings.stream()
                .filter(row -> row.bindingId() == bindingId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HomeworkBinding", "binding_id", bindingId));
        assertWriteAccess(claims, current.groupId());
        // Archive authority is checked by Academic's signed permission
        // lookup (headman or manage_homework assistant).  The actor who
        // publishes/edits may differ from the actor who originally reserved
        // the binding, so archive is deliberately not creator-scoped here.
        if (!binding.requestKey().equals(requestKey)) {
            throw new ConflictException("request_key does not match the binding");
        }
        if (binding.homeworkId() != null && binding.homeworkId() != homeworkId) {
            throw new ConflictException("homework binding belongs to another homework");
        }
        if ("ARCHIVED".equals(binding.state())) {
            if (binding.homeworkId() == null || binding.homeworkId() == homeworkId) {
                return toResponse(binding, current);
            }
            throw new ConflictException("archived homework binding has another homework");
        }
        if (!"PENDING".equals(binding.state()) && !"ACTIVE".equals(binding.state())) {
            throw new ConflictException("homework binding is not archivable");
        }

        int updated = jdbcTemplate.update("""
                UPDATE lesson_homework_bindings
                   SET homework_id = COALESCE(homework_id, ?), state = 'ARCHIVED',
                       revision = revision + 1, updated_at = NOW()
                 WHERE binding_id = ?
                   AND revision = ?
                   AND state IN ('PENDING', 'ACTIVE')
                   AND (homework_id IS NULL OR homework_id = ?)
                """, homeworkId, bindingId, binding.revision(), homeworkId);
        if (updated != 1) {
            throw new ConflictException("homework binding revision changed before archive");
        }
        return toResponse(findBinding(bindingId), current);
    }

    @Transactional
    public List<HomeworkBindingResponse> getBindings(HomeworkBindingsRequest request) {
        InternalJwtClaims claims = HomeworkBindingActorContext.requireClaims();
        List<Long> occurrenceIds = request.getOccurrenceIdsList().stream()
                .map(id -> positive(id, "occurrence_id"))
                .distinct()
                .sorted()
                .toList();
        if (occurrenceIds.isEmpty()) {
            return List.of();
        }

        // Lock all occurrence/current rows first, in deterministic order.
        List<OccurrenceSnapshot> snapshots = new ArrayList<>();
        for (Long occurrenceId : occurrenceIds) {
            snapshots.add(lockCurrentOccurrence(occurrenceId));
        }
        List<BindingRow> bindings = lockBindings(occurrenceIds);
        return bindings.stream()
                .map(binding -> {
                    OccurrenceSnapshot current = snapshots.stream()
                            .filter(snapshot -> snapshot.occurrenceId() == binding.occurrenceId())
                            .findFirst()
                            .orElseThrow();
                    assertReadAccess(claims, current.groupId());
                    return toResponse(binding, current);
                })
                .toList();
    }

    /**
     * Academic has already checked the application permission (headman or an
     * active assistant).  Schedule still verifies the signed identity and its
     * group because neither the protobuf actor nor the HTTP legacy headers are
     * authoritative for a binding write.
     */
    private static void assertWriteAccess(InternalJwtClaims claims, long lessonGroupId) {
        if (!"ACTIVE".equals(claims.status()) || claims.readOnly()
                || (!"STUDENT".equals(claims.role()) && !"HEADMAN".equals(claims.role()))) {
            throw new AccessDeniedException("homework binding requires an active student authority");
        }
        assertGroupAccess(claims, lessonGroupId);
    }

    private static void assertReadAccess(InternalJwtClaims claims, long lessonGroupId) {
        if (!"ACTIVE".equals(claims.status()) && !claims.readOnly()) {
            throw new AccessDeniedException("homework binding read requires a valid user authority");
        }
        if (!"STUDENT".equals(claims.role()) && !"HEADMAN".equals(claims.role())) {
            throw new AccessDeniedException("homework binding read is limited to student authority");
        }
        assertGroupAccess(claims, lessonGroupId);
    }

    private static void assertGroupAccess(InternalJwtClaims claims, long lessonGroupId) {
        if (claims.groupId() == null || claims.groupId() != lessonGroupId) {
            throw new AccessDeniedException("homework binding belongs to another group");
        }
    }

    /**
     * Resolve the current physical lesson from the authoritative V17
     * snapshot. The legacy JPA path cannot expose occurrence/revision data.
     */
    @Transactional(readOnly = true)
    public LessonResponse resolveLesson(ResolveLessonRequest request) {
        LocalDate date;
        try {
            date = LocalDate.parse(request.getDate());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("date must be an ISO-8601 local date", e);
        }
        List<OccurrenceSnapshot> rows = jdbcTemplate.query("""
                SELECT lo.id AS occurrence_id, lo.current_lesson_id,
                       lo.revision AS occurrence_revision,
                       l.id AS lesson_id, l.schedule_item_id, l.group_id, l.subject_id,
                       l.semester_id, l.assigned_teacher_id, l.assignment_id,
                       l.lesson_type, l.generation, l.revision AS lesson_revision,
                       l.date, l.lesson_number, l.start_time, l.end_time,
                       l.room_snapshot, l.status::text AS lesson_status
                  FROM lesson_occurrences lo
                  JOIN lessons l
                    ON l.id = lo.current_lesson_id
                   AND l.occurrence_id = lo.id
                 WHERE l.group_id = ?
                   AND l.date = ?
                   AND l.lesson_number = ?
                   AND l.status::text IN ('planned', 'active', 'closed')
                 ORDER BY l.id
                 LIMIT 1
                """, HomeworkBindingService::mapOccurrence,
                request.getGroupId(), date, request.getLessonNumber());
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Lesson", "group_id/date/lesson_number",
                    request.getGroupId() + "/" + date + "/" + request.getLessonNumber());
        }
        return toLessonResponse(rows.get(0));
    }

    private OccurrenceSnapshot lockCurrentOccurrence(long occurrenceId) {
        OriginRef origin = queryOne("""
                SELECT schedule_item_id, one_off_lesson_id
                  FROM lesson_occurrences
                 WHERE id = ?
                """, HomeworkBindingService::mapOrigin, occurrenceId,
                () -> new ResourceNotFoundException("LessonOccurrence", "id", occurrenceId));

        // Lock the immutable origin before the occurrence/current physical.
        if (origin.scheduleItemId() != null) {
            queryOne("SELECT id FROM schedule_items WHERE id = ? FOR UPDATE",
                    (rs, rowNum) -> rs.getLong(1), origin.scheduleItemId(),
                    () -> new ResourceNotFoundException("ScheduleItem", "id", origin.scheduleItemId()));
        } else if (origin.oneOffLessonId() != null) {
            queryOne("SELECT id FROM schedule_one_off_lessons WHERE id = ? FOR UPDATE",
                    (rs, rowNum) -> rs.getLong(1), origin.oneOffLessonId(),
                    () -> new ResourceNotFoundException("OneOffLesson", "id", origin.oneOffLessonId()));
        } else {
            throw new ConflictException("lesson occurrence has no immutable origin");
        }

        OccurrenceSnapshot current = queryOne("""
                SELECT lo.id AS occurrence_id, lo.current_lesson_id,
                       lo.revision AS occurrence_revision,
                       l.id AS lesson_id, l.schedule_item_id, l.group_id, l.subject_id,
                       l.semester_id, l.assigned_teacher_id, l.assignment_id,
                       l.lesson_type, l.generation, l.revision AS lesson_revision,
                       l.date, l.lesson_number, l.start_time, l.end_time,
                       l.room_snapshot, l.status::text AS lesson_status
                  FROM lesson_occurrences lo
                  JOIN lessons l
                    ON l.id = lo.current_lesson_id
                   AND l.occurrence_id = lo.id
                 WHERE lo.id = ?
                 FOR UPDATE OF lo, l
                """, HomeworkBindingService::mapOccurrence, occurrenceId,
                () -> new ConflictException("lesson occurrence has no current physical lesson"));
        if (!List.of("planned", "active", "closed").contains(current.status())) {
            throw new ConflictException("homework cannot be bound to a cancelled or transferred lesson");
        }
        return current;
    }

    private List<BindingRow> lockBindings(List<Long> occurrenceIds) {
        String placeholders = occurrenceIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbcTemplate.query("""
                SELECT %s
                  FROM lesson_homework_bindings
                 WHERE occurrence_id IN (%s)
                 ORDER BY binding_id
                 FOR UPDATE
                """.formatted(BINDING_COLUMNS, placeholders),
                HomeworkBindingService::mapBinding, occurrenceIds.toArray());
    }

    private BindingRow findBinding(long bindingId) {
        return queryOne("SELECT " + BINDING_COLUMNS
                        + " FROM lesson_homework_bindings WHERE binding_id = ?",
                        HomeworkBindingService::mapBinding, bindingId,
                () -> new ResourceNotFoundException("HomeworkBinding", "binding_id", bindingId));
    }

    private BindingRow findBindingByActorAndKey(long actorId, UUID requestKey) {
        List<BindingRow> rows = jdbcTemplate.query(
                "SELECT " + BINDING_COLUMNS
                        + " FROM lesson_homework_bindings WHERE actor_id = ? AND request_key = ?",
                HomeworkBindingService::mapBinding, actorId, requestKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private HomeworkBindingResponse toResponse(BindingRow binding, OccurrenceSnapshot current) {
        HomeworkBindingState state = switch (binding.state()) {
            case "PENDING" -> HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING;
            case "ACTIVE" -> HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE;
            case "ARCHIVED" -> HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED;
            default -> throw new IllegalStateException("unknown homework binding state: " + binding.state());
        };
        HomeworkBindingResponse.Builder response = HomeworkBindingResponse.newBuilder()
                .setBindingId(binding.bindingId())
                .setOccurrenceId(binding.occurrenceId())
                .setCurrentLesson(toLessonInfo(current))
                .setState(state)
                .setRevision(binding.revision())
                .setGroupId(current.groupId())
                .setSubjectId(current.subjectId())
                .setSemesterId(current.semesterId())
                .setDate(current.date().toString())
                .setLessonNumber(current.lessonNumber());
        if (binding.homeworkId() != null) {
            response.setHomeworkId(binding.homeworkId());
        }
        return response.build();
    }

    private LessonResponse toLessonResponse(OccurrenceSnapshot snapshot) {
        return LessonResponse.newBuilder()
                .setId(snapshot.lessonId())
                .setScheduleItemId(snapshot.scheduleItemId() == null ? 0 : snapshot.scheduleItemId())
                .setGroupId(snapshot.groupId())
                .setSubjectId(snapshot.subjectId())
                .setDate(snapshot.date().toString())
                .setLessonNumber(snapshot.lessonNumber())
                .setStartTime(snapshot.startTime().toString())
                .setEndTime(snapshot.endTime().toString())
                .setStatus(snapshot.status())
                .setRoom(snapshot.room() == null ? "" : snapshot.room())
                .setOccurrenceId(snapshot.occurrenceId())
                .setAssignmentId(snapshot.assignmentId())
                .setSemesterId(snapshot.semesterId())
                .setAssignedTeacherId(snapshot.assignedTeacherId())
                .setLessonType(snapshot.lessonType())
                .setGeneration(snapshot.generation())
                .setRevision(snapshot.lessonRevision())
                .setCurrent(true)
                .build();
    }

    private LessonInfo toLessonInfo(OccurrenceSnapshot snapshot) {
        return LessonInfo.newBuilder()
                .setLessonId(snapshot.lessonId())
                .setGroupId(snapshot.groupId())
                .setSubjectId(snapshot.subjectId())
                .setStartsAt(snapshot.date() + "T" + snapshot.startTime())
                .setLessonNumber(snapshot.lessonNumber())
                .setDate(snapshot.date().toString())
                .setOccurrenceId(snapshot.occurrenceId())
                .setAssignmentId(snapshot.assignmentId())
                .setSemesterId(snapshot.semesterId())
                .setTeacherId(snapshot.assignedTeacherId())
                .setLessonType(snapshot.lessonType())
                .setGeneration(snapshot.generation())
                .setRevision(snapshot.lessonRevision())
                .setStatus(snapshot.status())
                .build();
    }

    private static long positive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static UUID parseRequestKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("request_key must be a UUID");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("request_key must be a UUID", e);
        }
    }

    private static OriginRef mapOrigin(ResultSet rs, int rowNum) throws SQLException {
        Long scheduleItemId = rs.getObject("schedule_item_id", Long.class);
        Long oneOffLessonId = rs.getObject("one_off_lesson_id", Long.class);
        return new OriginRef(scheduleItemId, oneOffLessonId);
    }

    private static OccurrenceSnapshot mapOccurrence(ResultSet rs, int rowNum) throws SQLException {
        return new OccurrenceSnapshot(
                rs.getLong("occurrence_id"),
                rs.getLong("current_lesson_id"),
                rs.getLong("occurrence_revision"),
                rs.getLong("lesson_id"),
                rs.getObject("schedule_item_id", Long.class),
                rs.getLong("group_id"),
                rs.getLong("subject_id"),
                rs.getLong("semester_id"),
                rs.getLong("assigned_teacher_id"),
                rs.getLong("assignment_id"),
                rs.getString("lesson_type"),
                rs.getLong("generation"),
                rs.getLong("lesson_revision"),
                rs.getObject("date", LocalDate.class),
                rs.getInt("lesson_number"),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                rs.getString("room_snapshot"),
                rs.getString("lesson_status"));
    }

    private static BindingRow mapBinding(ResultSet rs, int rowNum) throws SQLException {
        return new BindingRow(
                rs.getLong("binding_id"),
                rs.getLong("occurrence_id"),
                rs.getLong("current_lesson_id"),
                rs.getObject("homework_id", Long.class),
                rs.getLong("actor_id"),
                rs.getObject("request_key", UUID.class),
                rs.getBytes("payload_hash"),
                rs.getString("state"),
                rs.getLong("revision"));
    }

    private <T> T queryOne(String sql,
                           org.springframework.jdbc.core.RowMapper<T> mapper,
                           Object argument,
                           java.util.function.Supplier<? extends RuntimeException> missing) {
        List<T> rows = jdbcTemplate.query(sql, mapper, argument);
        if (rows.isEmpty()) {
            throw missing.get();
        }
        return rows.get(0);
    }

    private record OriginRef(Long scheduleItemId, Long oneOffLessonId) {}

    private record BindingRow(long bindingId, long occurrenceId, long currentLessonId,
                              Long homeworkId, long actorId, UUID requestKey,
                              byte[] payloadHash, String state, long revision) {
        private BindingRow {
            payloadHash = payloadHash == null ? null : payloadHash.clone();
        }
    }

    private record OccurrenceSnapshot(long occurrenceId, long currentLessonId,
                                      long occurrenceRevision, long lessonId,
                                      Long scheduleItemId, long groupId, long subjectId,
                                      long semesterId, long assignedTeacherId,
                                      long assignmentId, String lessonType,
                                      long generation, long lessonRevision,
                                      LocalDate date, int lessonNumber,
                                      LocalTime startTime, LocalTime endTime,
                                      String room, String status) {}
}
