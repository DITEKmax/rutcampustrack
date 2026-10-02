package ru.rutcampustrack.schedule.oneoff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceImpl;
import ru.rutcampustrack.schedule.grpc.LessonByIdRequest;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;
import ru.rutcampustrack.schedule.recurring.RecurringScheduleItemWriter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Public one-off creation, replay, physical-slot lifecycle and exact database authority. */
@AutoConfigureMockMvc
class OneOffLessonControllerIT extends AbstractScheduleIntegrationTest {
    private static final long GROUP = 1, SUBJECT = 100, SEMESTER = 10, ACTOR = 42, ASSIGNMENT = 501, TEACHER = 700;
    private static final LocalDate DATE = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(7);
    private static final LocalDate FROM = DATE.minusDays(2), UNTIL = DATE.plusDays(2);
    @MockitoBean AcademicGrpcClient academic;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RecurringScheduleItemWriter recurring;
    @Autowired ScheduleGrpcServiceImpl grpc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void setup() {
        resetScheduleData();
        when(academic.getActiveSemester()).thenReturn(SemesterResponse.newBuilder().setId(SEMESTER)
                .setDateFrom(FROM.toString()).setDateTo(UNTIL.minusDays(1).toString()).build());
        when(academic.validateGroup(GROUP)).thenReturn(GroupResponse.newBuilder().setId(GROUP).setIsActive(true).build());
        when(academic.isHeadman(ACTOR, GROUP)).thenReturn(true);
        when(academic.getSemesterArchiveAuthorityState(SEMESTER))
                .thenReturn(SemesterStateResponse.newBuilder().setId(SEMESTER).setActive(true).build());
        mockAssignment(ASSIGNMENT, TEACHER, FROM, UNTIL);
    }
    private void mockAssignment(long assignment, long teacher, LocalDate from, LocalDate until) {
        when(academic.getAssignmentsByIds(List.of(assignment))).thenReturn(List.of(AssignmentInfo.newBuilder()
                .setId(assignment).setTeacherId(teacher).setGroupId(GROUP).setSubjectId(SUBJECT).setSemesterId(SEMESTER)
                .setLessonType("lecture").setValidFrom(from.toString()).setValidUntilExclusive(until.toString()).build()));
    }
    private MockHttpServletRequestBuilder actor(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", ACTOR).header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP).header("X-Is-Headman", "true");
    }
    private String body(short number, String room) throws Exception {
        return json.writeValueAsString(new CreateOneOffLessonRequest(GROUP, SUBJECT, ASSIGNMENT, DATE, number,
                LocalTime.of(8,30), LocalTime.of(10,0), room));
    }
    private JsonNode create(UUID key, short number, String room) throws Exception {
        return json.readTree(mvc.perform(actor(post("/schedule/one-off-lessons")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(number, room))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
    private long occurrence(long physical) {
        return jdbcTemplate.queryForObject("SELECT occurrence_id FROM lessons WHERE id = ?", Long.class, physical);
    }
    private void cancel(long physical) throws Exception {
        mvc.perform(actor(patch("/schedule/lessons/{id}/cancel", physical)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Пара отменена\"}")).andExpect(status().isOk());
    }

    @Test void create_canonicalSnapshotFeedsLookupAndList_replaysOnce() throws Exception {
        UUID key = UUID.randomUUID();
        JsonNode first = create(key, (short)1, null);
        long physical = first.path("physicalLessonId").asLong();
        assertThat(physical).isPositive();
        @SuppressWarnings("unchecked")
        StreamObserver<ru.rutcampustrack.schedule.grpc.LessonResponse> observer = mock(StreamObserver.class);
        grpc.getLessonById(LessonByIdRequest.newBuilder().setLessonId(physical).build(), observer);
        ArgumentCaptor<ru.rutcampustrack.schedule.grpc.LessonResponse> response =
                ArgumentCaptor.forClass(ru.rutcampustrack.schedule.grpc.LessonResponse.class);
        verify(observer).onNext(response.capture());
        verify(observer).onCompleted();
        assertThat(response.getValue().getId()).isEqualTo(physical);
        assertThat(response.getValue().getAssignedTeacherId()).isEqualTo(TEACHER);
        assertThat(response.getValue().getRoom()).isEmpty();
        assertThat(create(key, (short)1, null).path("id").asLong()).isEqualTo(first.path("id").asLong());
        assertThat(jdbcTemplate.queryForMap("SELECT assignment_id, assigned_teacher_id, schedule_item_id, generation, revision FROM lessons WHERE id = ?", physical))
                .containsEntry("assignment_id", ASSIGNMENT).containsEntry("assigned_teacher_id", TEACHER)
                .containsEntry("schedule_item_id", null).containsEntry("generation", 1L).containsEntry("revision", 1L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_lifecycle_entries WHERE occurrence_id = ? AND action = 'CREATED'", Long.class, occurrence(physical))).isEqualTo(1);
        assertThat(outboxStorage.findPending(20).stream().filter(row -> row.eventType().equals("lesson.one_off.created")).count()).isEqualTo(1);
        mvc.perform(actor(get("/schedule/lessons/next")).param("groupId", "1").param("semesterId", "10")
                .param("subjectId", "100").param("lessonType", "LECTURE").param("fromDate", DATE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lessonId").value(physical));
        mvc.perform(actor(get("/schedule/groups/1/lessons")).param("groupId", "1").param("dateFrom", DATE.toString()).param("dateTo", DATE.toString()))
                .andExpect(status().isOk()); // null room/template projection must be safe
        mvc.perform(actor(post("/schedule/one-off-lessons")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body((short)1, "changed"))).andExpect(status().isConflict());
    }

    @Test void create_rejectsForeignAuthorityAndHeadmanScope_atomically() throws Exception {
        when(academic.getAssignmentsByIds(List.of(ASSIGNMENT))).thenReturn(List.of(AssignmentInfo.newBuilder()
                .setId(ASSIGNMENT).setTeacherId(TEACHER).setGroupId(2).setSubjectId(SUBJECT).setSemesterId(SEMESTER)
                .setLessonType("lecture").setValidFrom(FROM.toString()).setValidUntilExclusive(UNTIL.toString()).build()));
        mvc.perform(actor(post("/schedule/one-off-lessons")).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body((short)1, null))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.rutcampustrack.ru/problems/one-off-create-rejected"));
        when(academic.isHeadman(ACTOR, GROUP)).thenReturn(false);
        mvc.perform(actor(post("/schedule/one-off-lessons")).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body((short)1, null))).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_lessons", Long.class)).isZero();
        when(academic.isHeadman(ACTOR, GROUP)).thenReturn(true);
        mockAssignment(ASSIGNMENT, TEACHER, DATE.plusDays(1), UNTIL);
        UUID rejectedKey = UUID.randomUUID();
        mvc.perform(actor(post("/schedule/one-off-lessons")).header("Idempotency-Key", rejectedKey)
                .contentType(MediaType.APPLICATION_JSON).content(body((short)1, null))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.rutcampustrack.ru/problems/one-off-create-rejected"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_create_replay", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_lessons", Long.class)).isZero();
        assertThat(outboxStorage.findPending(20)).isEmpty();
        LocalDate finalSemesterDay = UNTIL.minusDays(1);
        String corrected = json.writeValueAsString(new CreateOneOffLessonRequest(GROUP, SUBJECT, ASSIGNMENT,
                finalSemesterDay, (short)1, LocalTime.of(8,30), LocalTime.of(10,0), null));
        UUID correctedKey = UUID.randomUUID();
        mvc.perform(actor(post("/schedule/one-off-lessons")).header("Idempotency-Key", correctedKey)
                .contentType(MediaType.APPLICATION_JSON).content(corrected)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(finalSemesterDay.toString()))
                .andExpect(jsonPath("$.physicalLessonId").isNumber());
        // A conflicting replay has durable acceptance and must not carry the refusal proof type.
        mvc.perform(actor(post("/schedule/one-off-lessons")).header("Idempotency-Key", correctedKey)
                .contentType(MediaType.APPLICATION_JSON).content(body((short)1, null))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.rutcampustrack.ru/problems/conflict"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_lessons", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_create_replay", Long.class)).isEqualTo(1);
    }

    @Test void cancelledRecurringPhysical_releasesSlotAndRetainsBothHistories() throws Exception {
        recurring.write(new CreateScheduleItemRequest(ASSIGNMENT, GROUP, SUBJECT, SEMESTER,
                (short)DATE.getDayOfWeek().getValue(), (short)1, LocalTime.of(8,30), LocalTime.of(10,0), WeekType.ALL, null),
                UUID.randomUUID(), ACTOR, new RecurringAssignmentAuthority(ASSIGNMENT, TEACHER, SUBJECT, GROUP, SEMESTER, "lecture", FROM, UNTIL), FROM, UNTIL.minusDays(1));
        long physical = jdbcTemplate.queryForObject("SELECT id FROM lessons WHERE date = ?", Long.class, DATE);
        mvc.perform(actor(post("/schedule/one-off-lessons")).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body((short)1, null))).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_lessons", Long.class)).isZero();
        cancel(physical);
        long replacement = create(UUID.randomUUID(), (short)1, null).path("physicalLessonId").asLong();
        assertThat(replacement).isNotEqualTo(physical);
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, physical)).isEqualTo("cancelled");
    }

    @Test void delete_canonicalCancelArchivesBinding_retainsOriginAndDoesNotCancelReplacement() throws Exception {
        JsonNode first = create(UUID.randomUUID(), (short)1, null);
        long old = first.path("physicalLessonId").asLong(), origin = first.path("id").asLong();
        jdbcTemplate.update("""
                INSERT INTO lesson_homework_bindings (occurrence_id, current_lesson_id, actor_id, request_key, payload_hash, state, homework_id)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 9901)
                """, occurrence(old), old, ACTOR, UUID.randomUUID(), new byte[32]);
        mvc.perform(actor(delete("/schedule/one-off-lessons/{id}", origin))).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM lesson_homework_bindings WHERE current_lesson_id = ?", String.class, old)).isEqualTo("ARCHIVED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_lessons WHERE id = ?", Long.class, origin)).isEqualTo(1);
        long replacement = create(UUID.randomUUID(), (short)1, null).path("physicalLessonId").asLong();
        mvc.perform(actor(delete("/schedule/one-off-lessons/{id}", origin))).andExpect(status().isUnprocessableEntity());
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, replacement)).isEqualTo("planned");
        assertThat(outboxStorage.findPending(20).stream().filter(row -> row.eventType().equals("lesson.cancelled")).count()).isEqualTo(1);
        assertThat(outboxStorage.findPending(20)).noneMatch(row -> row.eventType().equals("lesson.one_off.cancelled"));
    }

    @Test void restore_sameAssignment_reusesPhysicalAndRejectsOccupiedSlot() throws Exception {
        long physical = create(UUID.randomUUID(), (short)1, "C-303").path("physicalLessonId").asLong();
        cancel(physical);
        mvc.perform(actor(patch("/schedule/lessons/{id}/restore", physical))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(physical));
        assertThat(jdbcTemplate.queryForObject("SELECT revision FROM lesson_occurrences WHERE id = ?", Long.class, occurrence(physical))).isEqualTo(3);
        cancel(physical);
        create(UUID.randomUUID(), (short)1, "C-303");
        mvc.perform(actor(patch("/schedule/lessons/{id}/restore", physical))).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, physical)).isEqualTo("cancelled");
    }

    @Test void blockOneOff_persistsAndEmitsExactPhysicalSnapshotWithNullableRoom() throws Exception {
        long physical = create(UUID.randomUUID(), (short)1, null).path("physicalLessonId").asLong();
        mvc.perform(actor(post("/schedule/lessons/{id}/blockage", physical))).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForMap("SELECT is_blocked_by_headman, blocked_by_user_id FROM lessons WHERE id = ?", physical))
                .containsEntry("is_blocked_by_headman", true).containsEntry("blocked_by_user_id", ACTOR);
        var blocked = outboxStorage.findPending(20).stream().filter(row -> row.eventType().equals("lesson.blocked")).toList();
        assertThat(blocked).hasSize(1);
        assertThat(ru.rutcampustrack.schedule.events.EventSchemaValidator.validate("lesson.blocked.json", blocked.get(0).payload())).isEmpty();
        JsonNode payload = json.readTree(blocked.get(0).payload()).path("payload");
        assertThat(payload.path("lesson_id").asLong()).isEqualTo(physical);
        assertThat(payload.path("group_id").asLong()).isEqualTo(GROUP);
        assertThat(payload.path("subject_id").asLong()).isEqualTo(SUBJECT);
        assertThat(payload.path("date").asText()).isEqualTo(DATE.toString());
        assertThat(payload.path("start_time").asText()).isEqualTo("08:30");
        assertThat(payload.path("end_time").asText()).isEqualTo("10:00");
        assertThat(payload.path("lesson_number").asInt()).isEqualTo(1);
        assertThat(payload.path("blocked_by").asLong()).isEqualTo(ACTOR);
        assertThat(payload.path("room").isNull() || payload.path("room").isMissingNode()).isTrue();
    }

    @Test void restore_foreignCommittedReplacementChainIsRejectedWithoutMutation() throws Exception {
        long physical = create(UUID.randomUUID(), (short)1, null).path("physicalLessonId").asLong();
        cancel(physical);
        jdbcTemplate.update("""
                INSERT INTO schedule_assignment_replacement_operations (operation_id, payload_hash, source_assignment_id,
                    target_assignment_id, source_teacher_id, target_teacher_id, group_id, subject_id, semester_id,
                    lesson_type, source_valid_from, valid_until_exclusive, effective_from, state)
                VALUES (?, ?, ?, 502, ?, 701, 2, ?, ?, 'lecture', ?, ?, ?, 'COMMITTED')
                """, UUID.randomUUID(), new byte[32], ASSIGNMENT, TEACHER, SUBJECT, SEMESTER, FROM, UNTIL, DATE);
        mvc.perform(actor(patch("/schedule/lessons/{id}/restore", physical))).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForMap("SELECT status::text AS status, generation FROM lessons WHERE id = ?", physical))
                .containsEntry("status", "cancelled").containsEntry("generation", 1L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM one_off_lesson_restore_authorities", Long.class)).isZero();
    }

    @Test void restore_twoHopTeacherReturnCreatesExactNewAssignmentGeneration_oldPhysicalRemainsImmutable() throws Exception {
        UUID key = UUID.randomUUID();
        JsonNode created = create(key, (short)1, "C-303");
        long old = created.path("physicalLessonId").asLong();
        cancel(old);
        long revision = jdbcTemplate.queryForObject("SELECT revision FROM lessons WHERE id = ?", Long.class, old);
        long middleAssignment = 502, middleTeacher = 701, targetAssignment = 503, targetTeacher = TEACHER;
        mockAssignment(targetAssignment, targetTeacher, DATE, UNTIL);
        jdbcTemplate.update("""
                INSERT INTO schedule_assignment_fences (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                    lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive) VALUES (?, ?, ?, ?, ?, 'lecture', ?, ?, ?)
                """, middleAssignment, GROUP, SUBJECT, SEMESTER, middleTeacher, DATE, UNTIL, UNTIL);
        jdbcTemplate.update("""
                INSERT INTO schedule_assignment_fences (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                    lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive) VALUES (?, ?, ?, ?, ?, 'lecture', ?, ?, ?)
                """, targetAssignment, GROUP, SUBJECT, SEMESTER, targetTeacher, DATE, UNTIL, UNTIL);
        jdbcTemplate.update("""
                INSERT INTO schedule_assignment_replacement_operations (operation_id, payload_hash, source_assignment_id,
                    target_assignment_id, source_teacher_id, target_teacher_id, group_id, subject_id, semester_id,
                    lesson_type, source_valid_from, valid_until_exclusive, effective_from, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'lecture', ?, ?, ?, 'COMMITTED')
                """, UUID.randomUUID(), new byte[32], ASSIGNMENT, middleAssignment, TEACHER, middleTeacher, GROUP, SUBJECT, SEMESTER, FROM, UNTIL, DATE);
        jdbcTemplate.update("""
                INSERT INTO schedule_assignment_replacement_operations (operation_id, payload_hash, source_assignment_id,
                    target_assignment_id, source_teacher_id, target_teacher_id, group_id, subject_id, semester_id,
                    lesson_type, source_valid_from, valid_until_exclusive, effective_from, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'lecture', ?, ?, ?, 'COMMITTED')
                """, UUID.randomUUID(), new byte[32], middleAssignment, targetAssignment, middleTeacher, targetTeacher, GROUP, SUBJECT, SEMESTER, DATE, UNTIL, DATE);
        JsonNode restored = json.readTree(mvc.perform(actor(patch("/schedule/lessons/{id}/restore", old)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long current = restored.path("id").asLong();
        assertThat(current).isNotEqualTo(old);
        assertThat(occurrence(current)).isEqualTo(occurrence(old));
        assertThat(jdbcTemplate.queryForMap("SELECT assignment_id, assigned_teacher_id, generation, room_snapshot FROM lessons WHERE id = ?", current))
                .containsEntry("assignment_id", targetAssignment).containsEntry("assigned_teacher_id", targetTeacher)
                .containsEntry("generation", 2L).containsEntry("room_snapshot", "C-303");
        assertThat(jdbcTemplate.queryForMap("SELECT status::text AS status, assigned_teacher_id, revision FROM lessons WHERE id = ?", old))
                .containsEntry("status", "cancelled").containsEntry("assigned_teacher_id", TEACHER).containsEntry("revision", revision);
        mvc.perform(actor(patch("/schedule/lessons/{id}/restore", old))).andExpect(status().isConflict());
        assertThat(create(key, (short)1, "C-303").path("physicalLessonId").asLong()).isEqualTo(current);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE lesson_occurrences SET assigned_teacher_id = 999 WHERE id = ?", occurrence(current)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM one_off_lesson_restore_authorities"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        // V23's exact authorized final-semester order must remove new receipts/authorities through FKs.
        UUID deletion = UUID.randomUUID();
        String digest = "0".repeat(64);
        jdbcTemplate.update("""
                INSERT INTO schedule_semester_archive_barriers (semester_id, operation_id, state_version, participant_state,
                    expected_participant_digest, participant_digest, schedule_templates_count, one_off_lessons_count, lessons_count)
                VALUES (?, ?, 1, 'DELETE_SEALED', ?, ?, 0, 1, 2)
                """, SEMESTER, deletion, digest, digest);
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbcTemplate.queryForObject("SELECT set_config('rutcampustrack.schedule_delete_operation_id', ?, true)", String.class, deletion.toString());
                jdbcTemplate.queryForObject("SELECT set_config('rutcampustrack.schedule_delete_state_version', '1', true)", String.class);
                jdbcTemplate.queryForObject("SELECT set_config('rutcampustrack.schedule_delete_participant_digest', ?, true)", String.class, digest);
                jdbcTemplate.update("UPDATE schedule_one_off_lessons SET physical_lesson_id = NULL WHERE semester_id = ?", SEMESTER);
                jdbcTemplate.update("DELETE FROM lesson_lifecycle_entries WHERE occurrence_id = ?", occurrence(current));
                jdbcTemplate.update("DELETE FROM lessons WHERE semester_id = ?", SEMESTER);
                jdbcTemplate.update("DELETE FROM lesson_occurrences WHERE semester_id = ?", SEMESTER);
                jdbcTemplate.update("DELETE FROM schedule_items WHERE semester_id = ?", SEMESTER);
                jdbcTemplate.update("DELETE FROM schedule_one_off_lessons WHERE semester_id = ?", SEMESTER);
            });
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_create_replay", Long.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM one_off_lesson_restore_authorities", Long.class)).isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM schedule_semester_archive_barriers WHERE semester_id = ?", SEMESTER);
        }
    }

    @Test void concurrentSameKeyAndChangedPayload_haveOneAtomicWinner() throws Exception {
        UUID key = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return mvc.perform(actor(post("/schedule/one-off-lessons"))
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body((short)1, "A"))).andReturn().getResponse().getStatus(); });
            var second = executor.submit(() -> { start.await(); return mvc.perform(actor(post("/schedule/one-off-lessons"))
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body((short)2, "B"))).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lessons", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_occurrences", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_one_off_create_replay", Long.class)).isEqualTo(1);
    }
}
