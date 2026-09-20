package ru.rutcampustrack.academic.subject;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.MvcResult;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.schedule.grpc.CountSubjectReferencesResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL and MockMvc proof for the headman subject authority. */
@AutoConfigureMockMvc
class SubjectAssignmentAuthorityIT extends AbstractAssignmentAuthorityIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleGrpcClient scheduleGrpcClient;

    private Fixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = newFixture("subject-authority");
        when(scheduleGrpcClient.countSubjectReferences(anyLong()))
                .thenReturn(CountSubjectReferencesResponse.getDefaultInstance());
    }

    @Test
    void headmanCreateSubjectWithOneTwoThreeLessonTypesAndInitialAssignmentsPersistsExactIds()
            throws Exception {
        JsonNode one = createSubject("L5A one type", "LECTURE", List.of("LECTURE"), List.of(
                assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                        fixture.dateFrom().plusDays(1), null)));
        JsonNode two = createSubject("L5A two types", "LECTURE", List.of("LECTURE", "PRACTICE"), List.of(
                assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                        fixture.dateFrom().plusDays(2), fixture.dateFrom().plusDays(8)),
                assignment(fixture.teacher2Id(), fixture.semesterId(), "PRACTICE",
                        fixture.dateFrom().plusDays(2), null)));
        JsonNode three = createSubject("L5A three types", "LECTURE",
                List.of("LECTURE", "PRACTICE", "LAB"), List.of(
                        assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                                fixture.dateFrom().plusDays(3), fixture.dateFrom().plusDays(9)),
                        assignment(fixture.teacher2Id(), fixture.semesterId(), "PRACTICE",
                                fixture.dateFrom().plusDays(3), fixture.dateFrom().plusDays(9)),
                        assignment(fixture.teacher1Id(), fixture.semesterId(), "LAB",
                                fixture.dateFrom().plusDays(3), null)));

        assertSubjectAndAssignments(one, 1, 1);
        assertSubjectAndAssignments(two, 2, 2);
        assertSubjectAndAssignments(three, 3, 3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assignments WHERE subject_id IN (?, ?, ?)", Integer.class,
                one.get("id").asLong(), two.get("id").asLong(), three.get("id").asLong()))
                .isEqualTo(6);
    }

    @Test
    void nullInitialAssignmentIsRejectedWithoutPersistingAnyAuthorityRows() throws Exception {
        String name = "L5A null initial assignment";
        RowCounts before = rowCounts();

        mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "type": "LECTURE",
                                  "lessonTypes": ["LECTURE"],
                                  "initialAssignments": [null]
                                }
                                """.formatted(name)), fixture))
                .andExpect(status().isBadRequest());

        assertThat(rowCounts()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subjects WHERE name = ?", Integer.class, name)).isZero();
    }

    @Test
    void legacyTeacherIdsAreRejectedAtomically() throws Exception {
        String name = "L5A legacy teacherIds";
        mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "type": "LECTURE",
                                  "teacherIds": [%d]
                                }
                                """.formatted(name, fixture.teacher1Id())), fixture))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subjects WHERE name = ?", Integer.class, name)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assignments WHERE teacher_id = ?", Integer.class,
                fixture.teacher1Id())).isZero();
    }

    @Test
    void foreignGroupAndNonHeadmanRoutesDenyWithoutCreatingSubject() throws Exception {
        Fixture otherFixture = newFixture("subject-authority-other");
        long subjectId = insertSubject(otherFixture.groupId(), "L5A foreign target", "lecture");
        Snapshot before = snapshot(subjectId);
        String name = "L5A denied subject";
        String body = """
                {
                  "name": "%s",
                  "type": "LECTURE"
                }
                """.formatted(name);

        mockMvc.perform(MockMvcRequestBuilders.put("/academic/subjects/{id}", subjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", fixture.headmanId())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", fixture.groupId())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.post("/academic/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", fixture.headmanId())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", fixture.groupId())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden());

        assertThat(snapshot(subjectId)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subjects WHERE name = ?", Integer.class, name)).isZero();
    }

    @Test
    void fullPutWithoutLessonTypesUsesSingletonAndReferencedTypeRemovalIsRejected()
            throws Exception {
        JsonNode referenced = createSubject("L5A full PUT referenced", "LECTURE",
                List.of("LECTURE", "PRACTICE"), List.of(assignment(
                        fixture.teacher1Id(), fixture.semesterId(), "PRACTICE",
                        fixture.dateFrom().plusDays(1), null)));
        long referencedId = referenced.get("id").asLong();

        mockMvc.perform(headman(MockMvcRequestBuilders.put("/academic/subjects/{id}", referencedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"L5A full PUT referenced renamed","type":"LECTURE"}
                                """), fixture))
                .andExpect(status().isConflict());

        assertThat(lessonTypes(referencedId)).containsExactly("lecture", "practice");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM subjects WHERE id = ?", String.class, referencedId))
                .isEqualTo("L5A full PUT referenced");
        assertThat(assignmentCount(referencedId)).isEqualTo(1);

        JsonNode unreferenced = createSubject("L5A full PUT singleton", "LECTURE",
                List.of("LECTURE", "PRACTICE"), List.of());
        long unreferencedId = unreferenced.get("id").asLong();
        mockMvc.perform(headman(MockMvcRequestBuilders.put("/academic/subjects/{id}", unreferencedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"L5A singleton renamed","type":"LECTURE"}
                                """), fixture))
                .andExpect(status().isOk());
        assertThat(lessonTypes(unreferencedId)).containsExactly("lecture");
    }

    @Test
    void sameNameSubjectsRemainDistinctAndRenamePreservesAssignmentIdentity()
            throws Exception {
        JsonNode first = createSubject("L5A same name", "LECTURE", List.of("LECTURE"), List.of(
                assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                        fixture.dateFrom().plusDays(1), null)));
        JsonNode second = createSubject("L5A same name", "LECTURE", List.of("LECTURE"), List.of());
        long firstId = first.get("id").asLong();
        long secondId = second.get("id").asLong();
        long assignmentId = first.get("createdAssignmentIds").get(0).asLong();

        assertThat(firstId).isNotEqualTo(secondId);
        mockMvc.perform(headman(MockMvcRequestBuilders.put("/academic/subjects/{id}", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"L5A same name renamed","type":"LECTURE"}
                                """), fixture))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT subject_id FROM assignments WHERE id = ?", Long.class, assignmentId))
                .isEqualTo(firstId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM subjects WHERE id = ?", String.class, firstId))
                .isEqualTo("L5A same name renamed");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM subjects WHERE id = ?", String.class, secondId))
                .isEqualTo("L5A same name");
    }

    @Test
    void closureRoutesReturnTyped409AndLeaveAssignmentHistoryAndOutboxUnchanged()
            throws Exception {
        JsonNode created = createSubject("L5A closure", "LECTURE", List.of("LECTURE"), List.of(
                assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                        fixture.dateFrom().plusDays(1), null)));
        long subjectId = created.get("id").asLong();
        long assignmentId = created.get("createdAssignmentIds").get(0).asLong();
        Snapshot before = snapshot(subjectId);
        String requestedEnd = fixture.dateFrom().plusDays(10).toString();

        mockMvc.perform(headman(MockMvcRequestBuilders.delete("/academic/assignments/{id}", assignmentId)
                        .param("validUntilExclusive", requestedEnd), fixture))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://api.rutcampustrack.ru/problems/assignment-closure-not-ready"));
        assertThat(snapshot(subjectId)).isEqualTo(before);

        mockMvc.perform(headman(MockMvcRequestBuilders.delete(
                                "/academic/subjects/{subjectId}/teachers/{teacherId}",
                                subjectId, fixture.teacher1Id())
                        .param("assignmentId", Long.toString(assignmentId))
                        .param("validUntilExclusive", requestedEnd), fixture))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://api.rutcampustrack.ru/problems/assignment-closure-not-ready"));
        assertThat(snapshot(subjectId)).isEqualTo(before);

        mockMvc.perform(headman(MockMvcRequestBuilders.delete("/academic/subjects/{id}", subjectId)
                        .param("force", "true"), fixture))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://api.rutcampustrack.ru/problems/assignment-closure-not-ready"));
        assertThat(snapshot(subjectId)).isEqualTo(before);
    }

    @Test
    void unreferencedSubjectDeletionSucceedsAfterZeroScheduleCheck() throws Exception {
        JsonNode created = createSubject("L5A unreferenced deletion", "LECTURE",
                List.of("LECTURE"), List.of());
        long subjectId = created.get("id").asLong();

        mockMvc.perform(headman(MockMvcRequestBuilders.delete("/academic/subjects/{id}", subjectId)
                        .param("force", "true"), fixture))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subjects WHERE id = ?", Integer.class, subjectId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subject_lesson_types WHERE subject_id = ?", Integer.class, subjectId))
                .isZero();
    }

    @Test
    void scheduleReferenceAndScheduleFailureKeepUnreferencedSubject() throws Exception {
        JsonNode referenced = createSubject("L5A schedule reference", "LECTURE",
                List.of("LECTURE"), List.of());
        long referencedId = referenced.get("id").asLong();
        Snapshot referencedBefore = snapshot(referencedId);
        when(scheduleGrpcClient.countSubjectReferences(referencedId))
                .thenReturn(CountSubjectReferencesResponse.newBuilder()
                        .setScheduleItemsCount(1)
                        .build());

        mockMvc.perform(headman(MockMvcRequestBuilders.delete("/academic/subjects/{id}", referencedId)
                        .param("force", "true"), fixture))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subjects WHERE id = ?", Integer.class, referencedId)).isEqualTo(1);
        assertThat(snapshot(referencedId)).isEqualTo(referencedBefore);

        JsonNode unavailable = createSubject("L5A schedule unavailable", "LECTURE",
                List.of("LECTURE"), List.of());
        long unavailableId = unavailable.get("id").asLong();
        Snapshot unavailableBefore = snapshot(unavailableId);
        when(scheduleGrpcClient.countSubjectReferences(unavailableId))
                .thenThrow(new ScheduleServiceUnavailableException("schedule unavailable"));

        mockMvc.perform(headman(MockMvcRequestBuilders.delete("/academic/subjects/{id}", unavailableId)
                        .param("force", "true"), fixture))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subjects WHERE id = ?", Integer.class, unavailableId)).isEqualTo(1);
        assertThat(snapshot(unavailableId)).isEqualTo(unavailableBefore);
    }

    private JsonNode createSubject(String name,
                                   String scalarType,
                                   List<String> lessonTypes,
                                   List<String> assignments) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "type": "%s",
                  "lessonTypes": %s,
                  "initialAssignments": [%s]
                }
                """.formatted(name, scalarType, quotedArray(lessonTypes), String.join(",", assignments));
        MvcResult result = mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body), fixture))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String assignment(long teacherId,
                              long semesterId,
                              String lessonType,
                              LocalDate validFrom,
                              LocalDate validUntilExclusive) {
        String end = validUntilExclusive == null ? "null" : "\"" + validUntilExclusive + "\"";
        return "{\"teacherId\":%d,\"semesterId\":%d,\"lessonType\":\"%s\","
                .formatted(teacherId, semesterId, lessonType)
                + "\"validFrom\":\"" + validFrom + "\",\"validUntilExclusive\":" + end + "}";
    }

    private void assertSubjectAndAssignments(JsonNode subject, int expectedTypes, int expectedAssignments) {
        long subjectId = subject.get("id").asLong();
        assertThat(subject.get("lessonTypes").size()).isEqualTo(expectedTypes);
        assertThat(subject.get("createdAssignmentIds").size()).isEqualTo(expectedAssignments);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subject_lesson_types WHERE subject_id = ?", Integer.class, subjectId))
                .isEqualTo(expectedTypes);
        assertThat(assignmentCount(subjectId)).isEqualTo(expectedAssignments);
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM assignments WHERE subject_id = ? ORDER BY id",
                (rs, rowNum) -> rs.getLong(1), subjectId);
        List<Long> responseIds = new ArrayList<>();
        subject.get("createdAssignmentIds").forEach(node -> responseIds.add(node.asLong()));
        assertThat(ids).containsExactlyInAnyOrderElementsOf(responseIds);

        List<Long> summaryIds = new ArrayList<>();
        subject.get("assignments").forEach(node -> summaryIds.add(node.get("id").asLong()));
        List<Long> canonicalIds = jdbcTemplate.query(
                "SELECT id FROM assignments WHERE subject_id = ? "
                        + "ORDER BY semester_id, lesson_type::text, teacher_id, valid_from, id",
                (rs, rowNum) -> rs.getLong(1), subjectId);
        assertThat(summaryIds).containsExactlyElementsOf(canonicalIds);
    }

    private List<String> lessonTypes(long subjectId) {
        return jdbcTemplate.query(
                "SELECT lesson_type::text FROM subject_lesson_types WHERE subject_id = ? ORDER BY lesson_type::text",
                (rs, rowNum) -> rs.getString(1), subjectId);
    }

    private RowCounts rowCounts() {
        return new RowCounts(
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subjects", Integer.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subject_lesson_types", Integer.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM assignments", Integer.class));
    }

    private Snapshot snapshot(long subjectId) {
        String subjectName = jdbcTemplate.queryForObject(
                "SELECT name FROM subjects WHERE id = ?", String.class, subjectId);
        List<AssignmentRow> assignments = jdbcTemplate.query(
                "SELECT id, teacher_id, subject_id, group_id, semester_id, lesson_type::text, valid_from, "
                        + "valid_until_exclusive FROM assignments WHERE subject_id = ? ORDER BY id",
                (rs, rowNum) -> new AssignmentRow(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rs.getString(6), rs.getObject(7, LocalDate.class),
                        rs.getObject(8, LocalDate.class)), subjectId);
        List<LessonTypeRow> lessonTypes = jdbcTemplate.query(
                "SELECT subject_id, lesson_type::text FROM subject_lesson_types "
                        + "WHERE subject_id = ? ORDER BY lesson_type::text",
                (rs, rowNum) -> new LessonTypeRow(rs.getLong(1), rs.getString(2)), subjectId);
        List<Long> semesterIds = assignments.stream().map(AssignmentRow::semesterId).distinct().toList();
        List<SemesterRow> semesters = semesterIds.isEmpty() ? List.of() : jdbcTemplate.query(
                "SELECT id, name, date_from, date_to, is_active FROM semesters "
                        + "WHERE id IN (" + semesterIds.stream().map(value -> "?").reduce((a, b) -> a + "," + b).orElse("")
                        + ") ORDER BY id",
                (rs, rowNum) -> new SemesterRow(rs.getLong(1), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class), rs.getBoolean(5)),
                semesterIds.toArray());
        List<OutboxRow> outbox = jdbcTemplate.query(
                "SELECT id, event_type, payload::text, status, retry_count, last_error, "
                        + "created_at::text, sent_at::text FROM academic_outbox ORDER BY id",
                (rs, rowNum) -> new OutboxRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8)));
        return new Snapshot(subjectName, assignments, lessonTypes, semesters, outbox);
    }

    private static String quotedArray(List<String> values) {
        return "[" + values.stream().map(value -> "\"" + value + "\"").reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private MockHttpServletRequestBuilder headman(MockHttpServletRequestBuilder request, Fixture value) {
        return request.header("X-User-Id", value.headmanId())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", value.groupId())
                .header("X-Is-Headman", "true");
    }

    private record AssignmentRow(long id,
                                 long teacherId,
                                 long subjectId,
                                 long groupId,
                                 long semesterId,
                                 String lessonType,
                                 LocalDate validFrom,
                                 LocalDate validUntilExclusive) {
    }

    private record LessonTypeRow(long subjectId, String lessonType) {
    }

    private record SemesterRow(long id,
                               String name,
                               LocalDate dateFrom,
                               LocalDate dateTo,
                               boolean active) {
    }

    private record OutboxRow(long id,
                             String eventType,
                             String payload,
                             String status,
                             int retryCount,
                             String lastError,
                             String createdAt,
                             String sentAt) {
    }

    private record Snapshot(String subjectName,
                            List<AssignmentRow> assignments,
                            List<LessonTypeRow> lessonTypes,
                            List<SemesterRow> semesters,
                            List<OutboxRow> outbox) {
    }

    private record RowCounts(int subjects, int lessonTypes, int assignments) {
    }
}
