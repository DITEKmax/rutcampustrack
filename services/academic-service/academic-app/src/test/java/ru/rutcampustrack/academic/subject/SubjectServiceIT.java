package ru.rutcampustrack.academic.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;

/** Legacy subject routes exercised against the canonical V25 assignment model. */
@AutoConfigureMockMvc
class SubjectServiceIT extends AbstractAssignmentAuthorityIT {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired private MockMvc mockMvc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private ru.rutcampustrack.academic.grpc.ScheduleGrpcClient scheduleGrpcClient;

    private Fixture fixture;
    private Fixture otherFixture;
    private List<Long> priorActiveSemesters;

    @BeforeEach
    void createFixtures() {
        fixture = newFixture("legacy-subject");
        successfulAdoptionReceipts(scheduleGrpcClient);
        otherFixture = newFixture("legacy-other");
        priorActiveSemesters = activeSemesterIds();
    }

    @AfterEach
    void restoreActiveSemester() {
        restoreActiveSemesterIds(priorActiveSemesters);
    }

    @Test
    void createSubject_withTwoTeachers_atomicInsert() throws Exception {
        long semesterId = ensureCurrentActiveSemester();
        LocalDate from = LocalDate.now(MOSCOW).minusDays(1);
        JsonNode response =
                createSubject(
                        "L5A legacy two teachers " + UUID.randomUUID(),
                        "LECTURE",
                        List.of("LECTURE", "PRACTICE"),
                        List.of(
                                assignment(fixture.teacher1Id(), semesterId, "LECTURE", from, null),
                                assignment(
                                        fixture.teacher2Id(), semesterId, "PRACTICE", from, null)));

        assertThat(response.get("groupId").asLong()).isEqualTo(fixture.groupId());
        assertThat(response.get("createdAssignmentIds")).hasSize(2);
        JsonNode sameType =
                createSubject(
                        "L5A same type teachers " + UUID.randomUUID(),
                        "LECTURE",
                        List.of("LECTURE"),
                        List.of(
                                assignment(fixture.teacher1Id(), semesterId, "LECTURE", from, null),
                                assignment(
                                        fixture.teacher2Id(), semesterId, "LECTURE", from, null)));
        assertThat(sameType.get("createdAssignmentIds")).hasSize(2);
        assertThat(sameType.get("assignments")).hasSize(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM assignment_replacement_operations operation JOIN assignments target ON target.id=operation.target_assignment_id WHERE target.subject_id=? AND operation.operation_kind='ADOPT_UNASSIGNED' AND operation.state='COMMITTED'",
                                Long.class,
                                sameType.get("id").asLong()))
                .isEqualTo(2L);
        assertThat(assignmentCount(response.get("id").asLong())).isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM teacher_subject_groups WHERE subject_id = ?",
                                Integer.class,
                                response.get("id").asLong()))
                .isZero();
    }

    @Test
    void createSubject_rollbackOnTeacherSaveFail() throws Exception {
        long semesterId = ensureCurrentActiveSemester();
        String name = "L5A legacy rollback " + UUID.randomUUID();
        LocalDate from = LocalDate.now(MOSCOW).minusDays(1);
        String body =
                """
                {
                  "name":"%s",
                  "type":"PRACTICE",
                  "lessonTypes":["PRACTICE"],
                  "initialAssignments":[
                    %s,
                    {"teacherId":999999999,"semesterId":%d,"lessonType":"PRACTICE", "validFrom":"%s"}
                  ]
                }
                """
                        .formatted(
                                name,
                                assignment(
                                        fixture.teacher1Id(), semesterId, "PRACTICE", from, null),
                                semesterId,
                                from);

        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post("/academic/subjects")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body)))
                .andExpect(status().isNotFound());

        assertThat(countSubjectsByName(name)).isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM assignments WHERE teacher_id = ?",
                                Integer.class,
                                fixture.teacher1Id()))
                .isZero();
    }

    @Test
    void addTeacher_and_removeTeacher() throws Exception {
        long semesterId = ensureCurrentActiveSemester();
        JsonNode created =
                createSubject(
                        "L5A legacy add teacher " + UUID.randomUUID(),
                        "LECTURE",
                        List.of("LECTURE"),
                        List.of());
        long subjectId = created.get("id").asLong();
        LocalDate from = LocalDate.now(MOSCOW).minusDays(1);
        String request =
                """
                {"semesterId":%d,"lessonType":"LECTURE","validFrom":"%s","validUntilExclusive":null}
                """
                        .formatted(semesterId, from);

        MvcResult added =
                mockMvc.perform(
                                headman(
                                        MockMvcRequestBuilders.post(
                                                        "/academic/subjects/{id}/teachers/{teacherId}",
                                                        subjectId,
                                                        fixture.teacher1Id())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(request)))
                        .andExpect(status().isCreated())
                        .andReturn();
        long assignmentId =
                objectMapper.readTree(added.getResponse().getContentAsString()).get("id").asLong();
        assertThat(assignmentCount(subjectId)).isEqualTo(1);

        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post(
                                                "/academic/subjects/{id}/teachers/{teacherId}",
                                                subjectId,
                                                fixture.teacher1Id())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.delete(
                                                "/academic/subjects/{id}/teachers/{teacherId}",
                                                subjectId,
                                                fixture.teacher1Id())
                                        .param("assignmentId", Long.toString(assignmentId))
                                        .header("Idempotency-Key", UUID.randomUUID())
                                        .param("validUntilExclusive", from.plusDays(5).toString())))
                .andExpect(status().isNoContent());
        assertThat(assignmentCount(subjectId)).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT valid_until_exclusive FROM assignments WHERE id=?",
                                LocalDate.class,
                                assignmentId))
                .isEqualTo(from.plusDays(5));
        JsonNode closedProjection =
                objectMapper.readTree(
                        mockMvc.perform(
                                        headman(
                                                MockMvcRequestBuilders.get(
                                                        "/academic/subjects/{id}", subjectId)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(closedProjection.get("teacherIds")).isEmpty();
        assertThat(closedProjection.get("assignments")).isEmpty();
    }

    @Test
    void listSubjects_filteredByGroup() throws Exception {
        String ownName = "L5A own list " + UUID.randomUUID();
        String otherName = "L5A other list " + UUID.randomUUID();
        insertSubject(fixture.groupId(), ownName, "lecture");
        insertSubject(otherFixture.groupId(), otherName, "lecture");

        String body =
                mockMvc.perform(headman(MockMvcRequestBuilders.get("/academic/subjects")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).contains(ownName).doesNotContain(otherName);

        long adminId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE login = 'admin'", Long.class);
        MockHttpServletRequestBuilder adminRequest =
                MockMvcRequestBuilders.get("/academic/subjects")
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .with(signedAdmin())
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false");
        JsonNode firstPage =
                objectMapper.readTree(
                        mockMvc.perform(adminRequest)
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        int totalPages = firstPage.path("page").path("totalPages").asInt();
        Set<String> adminNames = new HashSet<>();
        collectSubjectNames(firstPage, adminNames);
        for (int page = 1; page < totalPages; page++) {
            JsonNode pageBody =
                    objectMapper.readTree(
                            mockMvc.perform(
                                            MockMvcRequestBuilders.get("/academic/subjects")
                                                    .param("page", Integer.toString(page))
                                                    .header("X-User-Id", adminId)
                                                    .header("X-User-Role", "ADMIN")
                                                    .with(signedAdmin())
                                                    .header("X-Group-Id", "")
                                                    .header("X-Is-Headman", "false"))
                                    .andExpect(status().isOk())
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString());
            collectSubjectNames(pageBody, adminNames);
        }
        assertThat(adminNames).contains(ownName, otherName);
    }

    @Test
    void listSubjects_forTeacher_returnsAssignedSubjects() throws Exception {
        long semesterId = ensureCurrentActiveSemester();
        LocalDate from = LocalDate.now(MOSCOW).minusDays(1);
        String assignedName = "L5A assigned list " + UUID.randomUUID();
        String unassignedName = "L5A unassigned list " + UUID.randomUUID();
        String otherName = "L5A other teacher list " + UUID.randomUUID();
        long assignedSubjectId = insertSubject(fixture.groupId(), assignedName, "lecture");
        insertSubject(fixture.groupId(), unassignedName, "lecture");
        insertSubject(otherFixture.groupId(), otherName, "lecture");

        mockMvc.perform(
                        headman(
                                MockMvcRequestBuilders.post("/academic/assignments")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"employeeNumber":"%s","subjectId":%d,"groupId":%d,
                                 "semesterId":%d,"lessonType":"LECTURE","validFrom":"%s",
                                 "validUntilExclusive":null}
                                """
                                                        .formatted(
                                                                fixture.employee1(),
                                                                assignedSubjectId,
                                                                fixture.groupId(),
                                                                semesterId,
                                                                from))))
                .andExpect(status().isCreated());

        String body =
                mockMvc.perform(
                                MockMvcRequestBuilders.get("/academic/subjects")
                                        .header(
                                                "X-Internal-Token",
                                                internalTokens.validToken(
                                                        fixture.teacher1Id(),
                                                        UUID.randomUUID(),
                                                        1,
                                                        1,
                                                        "TEACHER",
                                                        "ACTIVE",
                                                        null,
                                                        false,
                                                        false))
                                        .header("X-User-Role", "TEACHER")
                                        .header("X-Group-Id", "")
                                        .header("X-Is-Headman", "false"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body)
                .contains(assignedName)
                .doesNotContain(unassignedName)
                .doesNotContain(otherName);
    }

    private void collectSubjectNames(JsonNode page, Set<String> names) {
        page.path("_embedded")
                .path("subjectResponseList")
                .forEach(subject -> names.add(subject.path("name").asText()));
    }

    private JsonNode createSubject(
            String name, String scalarType, List<String> lessonTypes, List<String> assignments)
            throws Exception {
        String body =
                """
                {
                  "name":"%s",
                  "type":"%s",
                  "lessonTypes":%s,
                  "initialAssignments":[%s]
                }
                """
                        .formatted(
                                name,
                                scalarType,
                                quotedArray(lessonTypes),
                                String.join(",", assignments));
        MvcResult result =
                mockMvc.perform(
                                headman(
                                        MockMvcRequestBuilders.post("/academic/subjects")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder headman(MockHttpServletRequestBuilder request) {
        return signedHeadman(request, fixture.headmanId(), fixture.groupId());
    }

    private String assignment(
            long teacherId,
            long semesterId,
            String lessonType,
            LocalDate validFrom,
            LocalDate validUntilExclusive) {
        String end = validUntilExclusive == null ? "null" : "\"" + validUntilExclusive + "\"";
        return "{\"teacherId\":%d,\"semesterId\":%d,\"lessonType\":\"%s\","
                        .formatted(teacherId, semesterId, lessonType)
                + "\"validFrom\":\""
                + validFrom
                + "\",\"validUntilExclusive\":"
                + end
                + "}";
    }

    private String quotedArray(List<String> values) {
        return "["
                + values.stream()
                        .map(value -> "\"" + value + "\"")
                        .reduce((left, right) -> left + "," + right)
                        .orElse("")
                + "]";
    }

    private int countSubjectsByName(String name) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM subjects WHERE name = ?", Integer.class, name);
        return count == null ? 0 : count;
    }
}
