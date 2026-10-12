package ru.rutcampustrack.schedule.lesson;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.CommitAssignmentCloseRequest;
import ru.rutcampustrack.schedule.grpc.InstallAssignmentCloseCapRequest;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.replacement.AssignmentReplacementService;

/**
 * Real PostgreSQL/Flyway/HTTP proof of in-place room authorization, replay and retained bindings.
 */
@AutoConfigureMockMvc
class LessonRoomChangeIT extends AbstractScheduleIntegrationTest {
    @MockitoBean AcademicGrpcClient academic;
    @MockitoBean ru.rutcampustrack.schedule.grpc.AcademicAdminPermissionClient adminPermissions;
    @Autowired ru.rutcampustrack.shared.security.InternalJwtTestFactory signer;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired LessonTransferWriter transfer;
    @Autowired AssignmentReplacementService assignments;
    @Autowired LessonRepository lessons;
    @Autowired PlatformTransactionManager manager;
    private LocalDate date;
    private static final long GROUP = 10,
            SUBJECT = 20,
            SEMESTER = 30,
            ASSIGNMENT = 501,
            TEACHER = 700,
            ACTOR = 42;

    @BeforeEach
    void fixture() {
        resetScheduleData();
        date = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(30);
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) date = date.plusDays(1);
        when(academic.isHeadman(ACTOR, GROUP)).thenReturn(true);
        when(academic.getSemesterArchiveAuthorityState(SEMESTER))
                .thenReturn(
                        SemesterStateResponse.newBuilder().setId(SEMESTER).setActive(true).build());
        when(academic.validateGroup(GROUP))
                .thenReturn(GroupResponse.newBuilder().setId(GROUP).setIsActive(true).build());
        when(academic.getActiveSemester())
                .thenReturn(
                        SemesterResponse.newBuilder()
                                .setId(SEMESTER)
                                .setDateFrom(date.minusDays(40).toString())
                                .setDateTo(date.plusDays(39).toString())
                                .build());
        when(academic.getAssignmentsByIds(List.of(ASSIGNMENT)))
                .thenReturn(
                        List.of(
                                AssignmentInfo.newBuilder()
                                        .setId(ASSIGNMENT)
                                        .setTeacherId(TEACHER)
                                        .setGroupId(GROUP)
                                        .setSubjectId(SUBJECT)
                                        .setSemesterId(SEMESTER)
                                        .setLessonType("lecture")
                                        .setValidFrom(date.minusDays(40).toString())
                                        .setValidUntilExclusive(date.plusDays(40).toString())
                                        .build()));
    }

    @Test
    void recurringAndOneOffChangeInPlaceReplayNoOpAndReloadRetainEverything() throws Exception {
        long lesson = recurring(date, "planned");
        long neighbour = recurring(date.plusDays(7), "planned");
        long occurrence = scalar("SELECT occurrence_id FROM lessons WHERE id=?", lesson);
        long binding =
                jdbc.queryForObject(
                        """
                INSERT INTO lesson_homework_bindings(occurrence_id,current_lesson_id,homework_id,actor_id,request_key,payload_hash,state,revision)
                VALUES(?,?,991,?,?,?,'ACTIVE',1) RETURNING binding_id
                """,
                        Long.class,
                        occurrence,
                        lesson,
                        ACTOR,
                        UUID.randomUUID(),
                        new byte[32]);
        var before = row("lessons", lesson);
        var template =
                row(
                        "schedule_items",
                        scalar("SELECT schedule_item_id FROM lessons WHERE id=?", lesson));
        String bindingBefore =
                jdbc.queryForObject(
                        "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings binding WHERE binding_id=?",
                        String.class,
                        binding);
        var neighbourBefore = row("lessons", neighbour);
        UUID key = UUID.randomUUID();
        JsonNode receipt = change(lesson, key, " B-205 ", "1", 200);
        assertThat(receipt.path("state").asText()).isEqualTo("COMPLETED");
        assertThat(receipt.path("lessonId").asText()).isEqualTo(Long.toString(lesson));
        assertThat(receipt.path("occurrenceId").asText()).isEqualTo(Long.toString(occurrence));
        assertThat(receipt.path("revision").asText()).isEqualTo("2");
        assertThat(change(lesson, key, "B-205", "1", 200)).isEqualTo(receipt);
        change(lesson, key, "different", "1", 409);
        change(neighbour, key, "B-205", "1", 409);
        change(lesson, UUID.randomUUID(), "stale", "1", 409);
        var after = row("lessons", lesson);
        assertThat(without(after, "room_snapshot", "revision"))
                .isEqualTo(without(before, "room_snapshot", "revision"));
        assertThat(after.get("room_snapshot")).isEqualTo("B-205");
        assertThat(row("lessons", neighbour)).isEqualTo(neighbourBefore);
        assertThat(row("schedule_items", ((Number) template.get("id")).longValue()))
                .isEqualTo(template);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings binding WHERE binding_id=?",
                                String.class,
                                binding))
                .isEqualTo(bindingBefore);
        var reload = reload(lesson);
        assertThat(reload.path("room").asText()).isEqualTo("B-205");
        assertThat(reload.path("occurrenceRevision").asText()).isEqualTo("2");
        assertThat(change(lesson, UUID.randomUUID(), "B-205", "2", 200).path("revision").asText())
                .isEqualTo("2");
        assertThat(
                        scalar(
                                "SELECT count(*) FROM schedule_outbox WHERE event_type='schedule.action-recorded'"))
                .isEqualTo(1);
        JsonNode cleared = change(lesson, UUID.randomUUID(), "   ", "2", 200);
        assertThat(cleared.path("room").isNull()).isTrue();
        assertThat(reload(lesson).path("room").isNull()).isTrue();
        assertThat(change(lesson, key, "B-205", "1", 200))
                .isEqualTo(receipt); // original receipt after later mutation
        var actions =
                jdbc.queryForList(
                        "SELECT payload FROM schedule_outbox WHERE event_type='schedule.action-recorded'",
                        String.class);
        assertThat(actions).hasSize(2);
        for (String action : actions) {
            assertThat(
                            ru.rutcampustrack.schedule.events.EventSchemaValidator.validate(
                                    "schedule.action-recorded.json", action))
                    .isEmpty();
            var payload = json.readTree(action).path("payload");
            assertThat(payload.path("action").asText()).isEqualTo("ROOM_CHANGED");
            assertThat(payload.path("actor").path("user_id").asLong()).isEqualTo(ACTOR);
            assertThat(payload.path("before").path("lesson_id"))
                    .isEqualTo(payload.path("after").path("lesson_id"));
        }
        assertThat(
                        json.readTree(actions.getFirst())
                                .path("payload")
                                .path("before")
                                .path("room_snapshot")
                                .asText())
                .isEqualTo("A-204");
        assertThat(
                        json.readTree(actions.getFirst())
                                .path("payload")
                                .path("after")
                                .path("room_snapshot")
                                .asText())
                .isEqualTo("B-205");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE lessons SET room_snapshot='forged' WHERE id=?",
                                        lesson))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () -> jdbc.update("UPDATE lessons SET lesson_number=3 WHERE id=?", lesson))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE lesson_room_change_ledger SET after_room='forged' WHERE lesson_id=?",
                                        lesson))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "DELETE FROM lesson_room_change_ledger WHERE lesson_id=?",
                                        lesson))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(
                                                tx -> {
                                                    jdbc.queryForObject(
                                                            "SELECT set_config('rutcampustrack.lesson_room_operation_id',?,true)",
                                                            String.class,
                                                            receipt.path("operationId").asText());
                                                    jdbc.update(
                                                            "UPDATE lessons SET room_snapshot='forged',revision=revision+1 WHERE id=?",
                                                            lesson);
                                                }))
                .isInstanceOf(DataAccessException.class);

        JsonNode oneOff =
                json.readTree(
                        http.perform(
                                        post("/schedule/one-off-lessons")
                                                .header("X-User-Id", ACTOR)
                                                .header("X-User-Role", "STUDENT")
                                                .header("X-Group-Id", GROUP)
                                                .header("X-Is-Headman", "true")
                                                .header("Idempotency-Key", UUID.randomUUID())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        json.writeValueAsString(
                                                                new CreateOneOffLessonRequest(
                                                                        GROUP,
                                                                        SUBJECT,
                                                                        ASSIGNMENT,
                                                                        date,
                                                                        (short) 3,
                                                                        null,
                                                                        null,
                                                                        "origin"))))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        long physical = oneOff.path("physicalLessonId").asLong();
        long origin = oneOff.path("id").asLong();
        var originBefore = row("schedule_one_off_lessons", origin);
        var physicalBefore = row("lessons", physical);
        change(physical, UUID.randomUUID(), null, "1", 200);
        http.perform(
                        actor(patch("/schedule/lessons/{id}/room", physical), ACTOR + 1, "ADMIN")
                                .header(
                                        "X-Internal-Token",
                                        signer.validToken(
                                                ACTOR + 1,
                                                UUID.randomUUID(),
                                                1,
                                                1,
                                                "ADMIN",
                                                "ACTIVE",
                                                null,
                                                false,
                                                false))
                                .header("Idempotency-Key", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"room\":null,\"expectedRevision\":\"2\"}"))
                .andExpect(status().isOk());
        assertThat(reload(physical).path("room").isNull()).isTrue();
        assertThat(row("schedule_one_off_lessons", origin)).isEqualTo(originBefore);
        assertThat(without(row("lessons", physical), "room_snapshot", "revision"))
                .isEqualTo(without(physicalBefore, "room_snapshot", "revision"));
        // New receipts obey the existing exact sealed final-deletion context in this isolated DB
        // only.
        UUID deletion = UUID.randomUUID();
        String digest = "a".repeat(64);
        jdbc.update(
                """
                INSERT INTO schedule_semester_archive_barriers(semester_id,operation_id,state_version,participant_state,
                    expected_participant_digest,participant_digest,schedule_templates_count,one_off_lessons_count,lessons_count)
                VALUES(?,?,1,'DELETE_SEALED',?,?,1,1,3)
                """,
                SEMESTER,
                deletion,
                digest,
                digest);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(
                                                tx -> {
                                                    deletionContext(deletion, 2, digest);
                                                    jdbc.update(
                                                            "DELETE FROM lesson_room_change_ledger WHERE semester_id=?",
                                                            SEMESTER);
                                                }))
                .isInstanceOf(DataAccessException.class);
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        tx -> {
                            deletionContext(deletion, 1, digest);
                            assertThat(
                                            jdbc.update(
                                                    "DELETE FROM lesson_room_change_ledger WHERE semester_id=?",
                                                    SEMESTER))
                                    .isEqualTo(5);
                        });
        assertThat(scalar("SELECT count(*) FROM lesson_room_change_ledger")).isZero();
    }

    @Test
    void currentAuthorityArchivePendingStartedAndConcurrentCasFailWithoutWrites() throws Exception {
        long lesson = recurring(date, "planned");
        UUID key = UUID.randomUUID();
        String body = json.writeValueAsString(Map.of("room", "B", "expectedRevision", "1"));
        // Assistants use STUDENT context; delegated rights do not make isHeadman true.
        for (String role : List.of("TEACHER", "STUDENT")) {
            http.perform(
                            actor(patch("/schedule/lessons/{id}/room", lesson), ACTOR + 1, role)
                                    .header("Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isForbidden());
        }
        http.perform(
                        actor(patch("/schedule/lessons/{id}/room", lesson), ACTOR, "STUDENT")
                                .header("Idempotency-Key", "invalid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
        http.perform(
                        actor(patch("/schedule/lessons/{id}/room", lesson), ACTOR, "STUDENT")
                                .header("Idempotency-Key", "1-1-1-1-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
        for (String malformed :
                List.of(
                        "{\"room\":123,\"expectedRevision\":\"1\"}",
                        "{\"room\":\"B\",\"expectedRevision\":1}",
                        "{\"expectedRevision\":\"1\"}")) {
            http.perform(
                            actor(patch("/schedule/lessons/{id}/room", lesson), ACTOR, "STUDENT")
                                    .header("Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(malformed))
                    .andExpect(status().isBadRequest());
        }
        change(lesson, key, "B", "9223372036854775808", 400);
        change(lesson, key, "X".repeat(65), "1", 400);
        change(999999, key, "B", "1", 404);
        when(academic.getSemesterArchiveAuthorityState(SEMESTER))
                .thenReturn(
                        SemesterStateResponse.newBuilder()
                                .setId(SEMESTER)
                                .setArchived(true)
                                .build());
        change(lesson, key, "B", "1", 409);
        when(academic.getSemesterArchiveAuthorityState(SEMESTER))
                .thenThrow(new AcademicServiceUnavailableException("synthetic authority outage"));
        change(lesson, key, "B", "1", 503);
        doReturn(SemesterStateResponse.newBuilder().setId(SEMESTER).build())
                .when(academic)
                .getSemesterArchiveAuthorityState(SEMESTER);
        assertThat(scalar("SELECT count(*) FROM lesson_room_change_ledger")).isZero();
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            Callable<Integer> command =
                    () -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS))
                            throw new AssertionError("race timeout");
                        return http.perform(
                                        actor(
                                                        patch(
                                                                "/schedule/lessons/{id}/room",
                                                                lesson),
                                                        ACTOR,
                                                        "STUDENT")
                                                .header("Idempotency-Key", UUID.randomUUID())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    };
            var a = pool.submit(command);
            var b = pool.submit(command);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(scalar("SELECT count(*) FROM lesson_room_change_ledger")).isEqualTo(1);
        assertThat(scalar("SELECT revision FROM lessons WHERE id=?", lesson)).isEqualTo(2);
        assertThat(
                        scalar(
                                "SELECT revision FROM lesson_occurrences WHERE current_lesson_id=?",
                                lesson))
                .isEqualTo(2);
        // A previously committed key still rechecks current authority before replay.
        UUID acceptedKey =
                (UUID)
                        jdbc.queryForObject(
                                "SELECT request_key FROM lesson_room_change_ledger", UUID.class);
        when(academic.isHeadman(ACTOR, GROUP)).thenReturn(false);
        change(lesson, acceptedKey, "B", "1", 403);
        when(academic.isHeadman(ACTOR, GROUP)).thenReturn(true);
        long active = recurring(date.plusDays(7), "active"),
                closed = recurring(date.plusDays(14), "closed");
        change(active, UUID.randomUUID(), "B", "1", 409);
        change(closed, UUID.randomUUID(), "B", "1", 409);
        long started = recurring(LocalDate.now(ZoneId.of("Europe/Moscow")).minusDays(1), "planned");
        change(started, UUID.randomUUID(), "B", "1", 409);
        long pending = recurring(date.plusDays(21), "planned");
        LocalDate target = date.plusDays(22);
        if (target.getDayOfWeek() == DayOfWeek.SUNDAY) target = target.plusDays(1);
        transfer.transfer(
                pending,
                ACTOR,
                new TransferLessonRequest(
                        target, 4, null, null, "transfer", "1", UUID.randomUUID()));
        change(pending, UUID.randomUUID(), "B", "1", 409);
        long cancelled = recurring(date.plusDays(28), "cancelled");
        change(cancelled, UUID.randomUUID(), "B", "1", 409);
        assertThat(scalar("SELECT count(*) FROM lesson_room_change_ledger")).isEqualTo(1);
    }

    @Test
    void clearingUnassignedCanonicalRoomRetainsNullThroughPublicReload() throws Exception {
        long lesson = recurring(date, "planned");
        UUID operation = UUID.randomUUID();
        ByteString hash = ByteString.copyFrom(new byte[32]);
        var authority =
                PreparedAssignmentCloseResponse.newBuilder()
                        .setOperationId(operation.toString())
                        .setOperationKind("CLOSE_UNASSIGN")
                        .setSourceAssignmentId(ASSIGNMENT)
                        .setSourceTeacherId(TEACHER)
                        .setGroupId(GROUP)
                        .setSubjectId(SUBJECT)
                        .setSemesterId(SEMESTER)
                        .setLessonType("lecture")
                        .setSourceValidFrom(date.minusDays(40).toString())
                        .setSourceValidUntilExclusive(date.plusDays(40).toString())
                        .setTargetValidUntilExclusive(date.plusDays(40).toString())
                        .setTargetLifecycleState("PREPARED")
                        .setEffectiveFrom(date.toString())
                        .setRequestedCutoff(date.toString())
                        .setPayloadHash(hash)
                        .setState("PREPARED");
        when(academic.getPreparedAssignmentLifecycle(operation, ASSIGNMENT))
                .thenReturn(authority.build());
        assignments.install(
                InstallAssignmentCloseCapRequest.newBuilder()
                        .setOperationId(operation.toString())
                        .setOperationKind("CLOSE_UNASSIGN")
                        .setSourceAssignmentId(ASSIGNMENT)
                        .setEffectiveFrom(date.toString())
                        .setPayloadHash(hash)
                        .build());
        when(academic.getPreparedAssignmentLifecycle(operation, ASSIGNMENT))
                .thenReturn(
                        authority.setState("APPLIED").setTargetLifecycleState("ACTIVE").build());
        assignments.commit(
                CommitAssignmentCloseRequest.newBuilder()
                        .setOperationId(operation.toString())
                        .setPayloadHash(hash)
                        .build());
        assertThat(lessons.findById(lesson).orElseThrow().getAssignmentId()).isNull();
        var before = row("lessons", lesson);
        assertThat(change(lesson, UUID.randomUUID(), null, "2", 200).path("revision").asText())
                .isEqualTo("3");
        assertThat(reload(lesson).path("room").isNull()).isTrue();
        assertThat(without(row("lessons", lesson), "room_snapshot", "revision"))
                .isEqualTo(without(before, "room_snapshot", "revision"));
    }

    private long recurring(LocalDate on, String state) {
        jdbc.update(
                """
                INSERT INTO schedule_assignment_fences(assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,
                    lesson_type,valid_from,cap_until_exclusive,creation_cap_until_exclusive)
                VALUES(501,10,20,30,700,'lecture',?,?,?) ON CONFLICT DO NOTHING
                """,
                date.minusDays(40),
                date.plusDays(40),
                date.plusDays(40));
        var items =
                jdbc.queryForList(
                        "SELECT id FROM schedule_items WHERE day_of_week=? AND is_active",
                        Long.class,
                        on.getDayOfWeek().getValue());
        long item =
                items.isEmpty()
                        ? jdbc.queryForObject(
                                """
                INSERT INTO schedule_items(assignment_id,group_id,subject_id,semester_id,day_of_week,lesson_number,start_time,end_time,week_type,room)
                VALUES(501,10,20,30,?,2,'10:05','11:25','all','A-204') RETURNING id
                """,
                                Long.class,
                                on.getDayOfWeek().getValue())
                        : items.getFirst();
        long occurrence =
                jdbc.queryForObject(
                        """
                INSERT INTO lesson_occurrences(schedule_item_id,occurrence_date,assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,lesson_type)
                VALUES(?,?,501,10,20,30,700,'lecture') RETURNING id
                """,
                        Long.class,
                        item,
                        on);
        long lesson =
                jdbc.queryForObject(
                        """
                INSERT INTO lessons(schedule_item_id,occurrence_id,assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,lesson_type,
                    lesson_number,day_of_week,start_time,end_time,room_snapshot,week_type_snapshot,generation,revision,date,status,is_geo_blocked)
                VALUES(?,?,501,10,20,30,700,'lecture',2,?,'10:05','11:25','A-204','all',1,1,?,?::lesson_status,false) RETURNING id
                """,
                        Long.class,
                        item,
                        occurrence,
                        on.getDayOfWeek().getValue(),
                        on,
                        state);
        jdbc.update(
                "UPDATE lesson_occurrences SET current_lesson_id=? WHERE id=?", lesson, occurrence);
        jdbc.update(
                "INSERT INTO lesson_lifecycle_entries(occurrence_id,revision,action,lesson_id,generation,actor_id,occurred_at) VALUES(?,1,'CREATED',?,1,42,NOW())",
                occurrence,
                lesson);
        return lesson;
    }

    private JsonNode change(long lesson, UUID key, String room, String revision, int status)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("room", room);
        body.put("expectedRevision", revision);
        String response =
                http.perform(
                                actor(
                                                patch("/schedule/lessons/{id}/room", lesson),
                                                ACTOR,
                                                "STUDENT")
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().is(status))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response);
    }

    private JsonNode reload(long lesson) throws Exception {
        String body =
                http.perform(
                                actor(
                                                get("/schedule/groups/{groupId}/lessons", GROUP),
                                                ACTOR,
                                                "STUDENT")
                                        .param("dateFrom", date.minusDays(2).toString())
                                        .param("dateTo", date.plusDays(35).toString()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        for (JsonNode row : json.readTree(body).path("_embedded").path("lessonResponseList"))
            if (row.path("id").asLong() == lesson) return row;
        throw new AssertionError("missing lesson on reload " + lesson);
    }

    private MockHttpServletRequestBuilder actor(
            MockHttpServletRequestBuilder request, long actor, String role) {
        return request.header("X-User-Id", actor)
                .header("X-User-Role", role)
                .header("X-Group-Id", GROUP)
                .header("X-Is-Headman", "false");
    }

    private Map<String, Object> row(String table, long id) {
        return jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?", id);
    }

    private long scalar(String query, Object... args) {
        return jdbc.queryForObject(query, Long.class, args);
    }

    private Map<String, Object> without(Map<String, Object> row, String... fields) {
        var copy = new LinkedHashMap<>(row);
        for (String field : fields) copy.remove(field);
        return copy;
    }

    private void deletionContext(UUID operation, long version, String digest) {
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.schedule_delete_operation_id',?,true)",
                String.class,
                operation.toString());
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.schedule_delete_state_version',?,true)",
                String.class,
                Long.toString(version));
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.schedule_delete_participant_digest',?,true)",
                String.class,
                digest);
    }
}
