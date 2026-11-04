package ru.rutcampustrack.schedule.constructor;

import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.constructor.grpc.PastLessonIdentity;
import ru.rutcampustrack.constructor.grpc.PastLessonMaterializationCommand;
import ru.rutcampustrack.schedule.event.LessonStartedEvent;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;

/** Local-only semester generation; exact delivery work commits with the new physical rows. */
@Service
public class SemesterMaterializationWriter {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final String SETTING = "rutcampustrack.constructor_materialization_operation_id";
    private static final int BATCH_SIZE = 128;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public SemesterMaterializationWriter(
            JdbcTemplate jdbc, Clock clock, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.events = events;
    }

    public UUID begin(
            long itemId,
            long actor,
            UUID key,
            RecurringAssignmentAuthority authority,
            LocalDate from,
            LocalDate until) {
        return begin(itemId, actor, key, authority, from, until, "USER", "CREATE");
    }

    public UUID replay(Long actor, UUID key, String source, String kind, long item) {
        var rows = jdbc.queryForList(
                "SELECT operation_id,requested_item_id,request_kind FROM constructor_semester_materializations "
                        + "WHERE actor_id IS NOT DISTINCT FROM ? AND request_key=? AND source_kind=?",
                actor, key, source);
        if (rows.isEmpty()) return null;
        var row = rows.getFirst();
        if (((Number) row.get("requested_item_id")).longValue() != item || !kind.equals(row.get("request_kind")))
            throw ConstructorLifecycleException.conflict("MATERIALIZATION_KEY_CONFLICT");
        return (UUID) row.get("operation_id");
    }

    public UUID begin(
            long itemId, Long actor, UUID key, RecurringAssignmentAuthority authority,
            LocalDate from, LocalDate until, String source, String kind) {
        requireTransaction();
        UUID operation = UUID.nameUUIDFromBytes(
                ("SEMESTER_MATERIALIZATION|" + source + "|" + actor + "|" + key).getBytes(StandardCharsets.UTF_8));
        List<Long> family = familyIds(itemId);
        jdbc.update(
                """
                INSERT INTO constructor_semester_materializations
                    (operation_id,actor_id,request_key,requested_item_id,group_id,subject_id,
                     semester_id,family_ids,semester_from,semester_until_exclusive,state,source_kind,request_kind)
                VALUES (?,?,?,?,?,?,?,?::bigint[],?,?,'GENERATING',?,?)
                """,
                operation, actor, key, itemId, authority.groupId(), authority.subjectId(),
                authority.semesterId(), array(family), from, until.plusDays(1), source, kind);
        jdbc.queryForObject("SELECT set_config(?,?,TRUE)", String.class, SETTING, operation.toString());
        return operation;
    }

    public boolean admitted() {
        return jdbc.queryForObject("SELECT NULLIF(current_setting(?,TRUE),'') IS NOT NULL",
                Boolean.class, SETTING);
    }

    public long insertOccurrence(
            long itemId, LocalDate date, RecurringAssignmentAuthority authority, OffsetDateTime at) {
        requireTransaction();
        UUID operation = activeOperation();
        var item = jdbc.queryForMap("SELECT *,week_type::text AS week_type FROM schedule_items WHERE id=?", itemId);
        var fence = jdbc.queryForMap(
                "SELECT valid_from,creation_cap_until_exclusive,assigned_teacher_id FROM schedule_assignment_fences WHERE assignment_id=?",
                authority.assignmentId());
        boolean assigned = !date.isBefore(date(fence.get("valid_from")))
                && date.isBefore(date(fence.get("creation_cap_until_exclusive")));
        Long assignment = assigned ? authority.assignmentId() : null;
        Long teacher = assigned ? ((Number) fence.get("assigned_teacher_id")).longValue() : null;
        long occurrence = nextId("lesson_occurrences"), lesson = nextId("lessons");
        byte[] snapshot = ConstructorHashes.json(immutableSnapshot(
                lesson, occurrence, authority.groupId(), authority.subjectId(), authority.semesterId(),
                authority.lessonType(), date, ((Number) item.get("lesson_number")).intValue(),
                time(item.get("start_time")), time(item.get("end_time")), 1));
        jdbc.update(
                """
                INSERT INTO constructor_generation_origins
                    (occurrence_id,lesson_id,operation_id,schedule_item_id,origin_assignment_id,
                     initial_assignment_id,initial_teacher_id,group_id,subject_id,semester_id,lesson_type,
                     occurrence_date,lesson_number,start_time,end_time,room_snapshot,week_type_snapshot,
                     snapshot_hash,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::week_type,?,?)
                """,
                occurrence, lesson, operation, itemId, authority.assignmentId(), assignment, teacher,
                authority.groupId(), authority.subjectId(), authority.semesterId(), authority.lessonType(),
                date, item.get("lesson_number"), item.get("start_time"), item.get("end_time"), item.get("room"),
                item.get("week_type"), snapshot, at);
        jdbc.update(
                """
                INSERT INTO lesson_occurrences
                    (id,schedule_item_id,occurrence_date,assignment_id,group_id,subject_id,semester_id,
                     assigned_teacher_id,lesson_type,generation,revision,created_at)
                OVERRIDING SYSTEM VALUE VALUES (?,?,?,?,?,?,?,?,?,1,1,?)
                """,
                occurrence, itemId, date, assignment, authority.groupId(), authority.subjectId(),
                authority.semesterId(), teacher, authority.lessonType(), at);
        return occurrence;
    }

    public long insertPhysicalLesson(long occurrence, OffsetDateTime at) {
        requireTransaction();
        var origin = jdbc.queryForMap(
                "SELECT *,week_type_snapshot::text AS week_type_snapshot FROM constructor_generation_origins WHERE occurrence_id=? AND operation_id=?",
                occurrence, activeOperation());
        LocalDate day = date(origin.get("occurrence_date"));
        LocalTime start = time(origin.get("start_time")), end = time(origin.get("end_time"));
        LocalDateTime now = at.atZoneSameInstant(MOSCOW).toLocalDateTime();
        String status = !day.atTime(end).plusMinutes(5).isAfter(now) ? "closed"
                : !day.atTime(start).isAfter(now) ? "active" : "planned";
        jdbc.update(
                """
                INSERT INTO lessons
                    (id,schedule_item_id,occurrence_id,assignment_id,group_id,subject_id,semester_id,
                     assigned_teacher_id,lesson_type,lesson_number,day_of_week,start_time,end_time,
                    room_snapshot,week_type_snapshot,generation,revision,date,status,is_geo_blocked,
                     created_at,closed_at)
                OVERRIDING SYSTEM VALUE VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,1,?,?::lesson_status,FALSE,?,?)
                """,
                origin.get("lesson_id"), origin.get("schedule_item_id"), occurrence,
                origin.get("initial_assignment_id"), origin.get("group_id"), origin.get("subject_id"),
                origin.get("semester_id"), origin.get("initial_teacher_id"), origin.get("lesson_type"),
                origin.get("lesson_number"), day.getDayOfWeek().getValue(), start, end,
                origin.get("room_snapshot"), origin.get("week_type_snapshot"), day, status, at,
                "closed".equals(status) ? at : null);
        long lesson = ((Number) origin.get("lesson_id")).longValue();
        if ("active".equals(status)) {
            events.publishEvent(new LessonStartedEvent(
                    this, lesson, ((Number) origin.get("group_id")).longValue(),
                    ((Number) origin.get("subject_id")).longValue(),
                    ((Number) origin.get("lesson_number")).shortValue(), start, end,
                    (String) origin.get("room_snapshot")));
        }
        // Historical CLOSED has no shared started/closed/reminder publication.
        return lesson;
    }

    public void finish(UUID operation) {
        requireTransaction();
        var scope = jdbc.queryForMap("SELECT * FROM constructor_semester_materializations WHERE operation_id=?", operation);
        var historical = jdbc.queryForList(
                """
                SELECT origin.lesson_id,origin.occurrence_id,origin.snapshot_hash,lesson.revision
                  FROM constructor_generation_origins origin JOIN lessons lesson ON lesson.id=origin.lesson_id
                 WHERE origin.operation_id=? AND lesson.status='closed' ORDER BY origin.lesson_id
                """, operation);
        int batches = (historical.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        for (int batch = 0; batch < batches; batch++) {
            var command = PastLessonMaterializationCommand.newBuilder()
                    .setOperationId(operation.toString())
                    .setGroupId(((Number) scope.get("group_id")).longValue())
                    .setSemesterId(((Number) scope.get("semester_id")).longValue())
                    .setBatchIndex(batch).setBatchCount(batches);
            for (var row : historical.subList(batch * BATCH_SIZE, Math.min(historical.size(), (batch + 1) * BATCH_SIZE))) {
                command.addLessons(PastLessonIdentity.newBuilder()
                        .setLessonId(((Number) row.get("lesson_id")).longValue())
                        .setOccurrenceId(((Number) row.get("occurrence_id")).longValue())
                        .setExpectedRevision(((Number) row.get("revision")).longValue())
                        .setSnapshotHash(ByteString.copyFrom((byte[]) row.get("snapshot_hash"))));
            }
            byte[] hash = ConstructorHashes.sha256(command.build().toByteArray());
            command.setBatchHash(ByteString.copyFrom(hash));
            jdbc.update(
                    "INSERT INTO constructor_materialization_work(operation_id,batch_index,command,batch_hash,lesson_count,lesson_ids) VALUES(?,?,?,?,?,?::bigint[])",
                    operation, batch, command.build().toByteArray(), hash, command.getLessonsCount(),
                    array(command.getLessonsList().stream().map(PastLessonIdentity::getLessonId).toList()));
        }
        jdbc.update("UPDATE constructor_semester_materializations SET state=? WHERE operation_id=?",
                batches == 0 ? "COMPLETED" : "PENDING", operation);
        jdbc.queryForObject("SELECT set_config(?,?,TRUE)", String.class, SETTING, "");
    }

    public List<Long> familyIds(long item) {
        return jdbc.query(
                """
                WITH RECURSIVE edges(a,b) AS (
                    SELECT source_schedule_item_id,target_schedule_item_id FROM schedule_assignment_replacement_templates
                    UNION SELECT target_schedule_item_id,source_schedule_item_id FROM schedule_assignment_replacement_templates
                ), family(id) AS (SELECT id FROM schedule_items WHERE id=?
                    UNION SELECT edges.b FROM edges JOIN family ON edges.a=family.id)
                SELECT id FROM family ORDER BY id
                """, (rs, row) -> rs.getLong(1), item);
    }

    public Set<LocalDate> coveredDates(List<Long> family) {
        return new java.util.HashSet<>(jdbc.query(
                """
                SELECT occurrence_date FROM lesson_occurrences WHERE schedule_item_id=ANY(?::bigint[])
                UNION SELECT date FROM lessons WHERE schedule_item_id=ANY(?::bigint[])
                """, (rs, row) -> rs.getDate(1).toLocalDate(), array(family), array(family)));
    }

    public static Map<String, Object> immutableSnapshot(
            long lesson, long occurrence, long group, long subject, long semester, String type,
            LocalDate date, int number, LocalTime start, LocalTime end, long generation) {
        var result = new LinkedHashMap<String, Object>();
        result.put("lesson_id", lesson);
        result.put("occurrence_id", occurrence);
        result.put("group_id", group);
        result.put("subject_id", subject);
        result.put("semester_id", semester);
        result.put("lesson_type", type.toLowerCase(java.util.Locale.ROOT));
        result.put("date", date.toString());
        result.put("lesson_number", number);
        result.put("start_time", start.toString());
        result.put("end_time", end.toString());
        result.put("generation", generation);
        return result;
    }

    private UUID activeOperation() {
        return UUID.fromString(jdbc.queryForObject("SELECT current_setting(?,TRUE)", String.class, SETTING));
    }

    private long nextId(String table) {
        return jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence(?, 'id'))", Long.class, table);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Semester origin and delivery work require the generation transaction");
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : LocalDate.parse(value.toString());
    }

    private static LocalTime time(Object value) {
        return value instanceof java.sql.Time time ? time.toLocalTime() : LocalTime.parse(value.toString());
    }

    private static String array(List<Long> values) {
        return "{" + values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "}";
    }
}
