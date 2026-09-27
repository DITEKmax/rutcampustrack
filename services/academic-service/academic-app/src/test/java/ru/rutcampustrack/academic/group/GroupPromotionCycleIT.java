package ru.rutcampustrack.academic.group;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL proof for the completed-spring promotion cycle ledger. */
@AutoConfigureMockMvc
@Transactional
class GroupPromotionCycleIT extends AbstractAcademicIntegrationTest {

    private static final LocalDate CYCLE_END = LocalDate.of(2026, 6, 30);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void singleThenMassPromotionRejectsStaleAndRetriedPlansWithoutChangingIdentityOrHistory() throws Exception {
        Long cycleId = prepareCompletedSpringCycle();
        Long adminId = userId("admin");
        Long teacherId = userId("teacher");

        Long firstGroup = addGroup("УИТ-111", beforeCycleEnd());
        Long archiveGroup = addGroup("УИТ-411", beforeCycleEnd());
        Long massGroup = addGroup("УВП-111", beforeCycleEnd());
        Long lateGroup = addGroup("РЕГ-111", afterCycleEnd());
        Long studentId = addStudent(firstGroup, "promotion_student_" + System.nanoTime());
        jdbc.update("INSERT INTO student_group_history (user_id, group_id, joined_at, reason) "
                        + "VALUES (?, ?, ?, ?)",
                studentId, firstGroup, CYCLE_END.minusDays(20), "promotion-cycle-it");

        mockMvc.perform(previewRequest(teacherId, "TEACHER", "{}"))
                .andExpect(status().isForbidden());

        JsonNode singlePreview = response(previewRequest(
                adminId, "ADMIN", "{\"groupId\":" + firstGroup + "}"));
        assertThat(singlePreview.path("dryRun").asBoolean()).isTrue();
        assertThat(singlePreview.path("executed").asBoolean()).isFalse();
        assertThat(singlePreview.path("cycleSemesterId").asLong()).isEqualTo(cycleId);
        assertThat(singlePreview.path("groupId").asLong()).isEqualTo(firstGroup);
        assertThat(singlePreview.path("promoteCount").asInt()).isEqualTo(1);
        assertThat(singlePreview.path("promotedStudentCount").asLong()).isEqualTo(1L);
        assertThat(singlePreview.path("toPromote").get(0).path("id").asLong()).isEqualTo(firstGroup);

        String singleVersion = singlePreview.path("previewVersion").asText();
        JsonNode singleResult = response(executeRequest(
                adminId, cycleId, singleVersion, firstGroup));
        assertThat(singleResult.path("executed").asBoolean()).isTrue();
        assertThat(singleResult.path("dryRun").asBoolean()).isFalse();
        assertThat(groupName(firstGroup)).isEqualTo("УИТ-211");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM student_group_history WHERE user_id = ? AND group_id = ?",
                Long.class, studentId, firstGroup)).isEqualTo(1L);

        JsonNode massPreview = response(previewRequest(adminId, "ADMIN", "{}"));
        assertThat(massPreview.path("groupId").isNull()).isTrue();
        assertThat(massPreview.path("skipped").findValuesAsText("reason"))
                .contains("ALREADY_PROCESSED", "CREATED_AFTER_CYCLE_END");
        assertThat(massPreview.path("skipped").findValuesAsText("id"))
                .contains(Long.toString(firstGroup), Long.toString(lateGroup));
        assertThat(massPreview.path("archiveCount").asInt()).isEqualTo(1);
        assertThat(massPreview.path("toArchive").get(0).path("id").asLong()).isEqualTo(archiveGroup);

        addGroup("УР-111", beforeCycleEnd());
        mockMvc.perform(executeRequest(adminId, cycleId, massPreview.path("previewVersion").asText(), null))
                .andExpect(status().isConflict());
        assertThat(groupName(massGroup)).isEqualTo("УВП-111");
        assertThat(groupName(archiveGroup)).isEqualTo("УИТ-411");

        JsonNode refreshedMassPreview = response(previewRequest(adminId, "ADMIN", "{}"));
        assertThat(refreshedMassPreview.path("skipped").findValuesAsText("id"))
                .contains(Long.toString(firstGroup), Long.toString(lateGroup));
        assertThat(refreshedMassPreview.path("toArchive").get(0).path("id").asLong()).isEqualTo(archiveGroup);
        JsonNode massResult = response(executeRequest(
                adminId, cycleId, refreshedMassPreview.path("previewVersion").asText(), null));
        assertThat(massResult.path("executed").asBoolean()).isTrue();
        assertThat(groupName(firstGroup)).isEqualTo("УИТ-211");
        assertThat(groupName(massGroup)).isEqualTo("УВП-211");
        assertThat(groupName(archiveGroup)).isEqualTo("УИТ-411 (выпуск 2026)");
        assertThat(groupIdByName("УИТ-211")).isEqualTo(firstGroup);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM student_group_history WHERE user_id = ? AND group_id = ?",
                Long.class, studentId, firstGroup)).isEqualTo(1L);

        mockMvc.perform(executeRequest(
                        adminId, cycleId, refreshedMassPreview.path("previewVersion").asText(), null))
                .andExpect(status().isConflict());
        assertThat(groupName(firstGroup)).isEqualTo("УИТ-211");
        assertThat(groupName(massGroup)).isEqualTo("УВП-211");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM group_promotion_cycle_record WHERE cycle_semester_id = ? AND group_id = ?",
                Long.class, cycleId, firstGroup)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM group_promotion_cycle_record WHERE cycle_semester_id = ? AND group_id = ?",
                Long.class, cycleId, massGroup)).isEqualTo(1L);
    }

    private Long prepareCompletedSpringCycle() {
        Long cycleId = jdbc.queryForObject(
                "SELECT id FROM semesters WHERE name = 'Spring 2026' AND date_to = ? ORDER BY id LIMIT 1",
                Long.class, CYCLE_END);
        jdbc.update("UPDATE semesters SET semester_type = 'SPRING', academic_year = 2025, "
                        + "is_active = false WHERE id = ?", cycleId);
        return cycleId;
    }

    private Long addGroup(String name, OffsetDateTime createdAt) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name, is_active, created_at, duration_status) "
                        + "VALUES (?, true, ?, 'LEGACY_UNKNOWN') RETURNING id",
                Long.class, name, createdAt);
    }

    private Long addStudent(Long groupId, String login) {
        Long studentId = jdbc.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                        + "is_headman, password_changed, created_at, updated_at) "
                        + "VALUES (?, 'x', 'Тестов', 'Студент', 'student', 'active', false, false, NOW(), NOW()) "
                        + "RETURNING id",
                Long.class, login);
        jdbc.update("INSERT INTO user_role_grants "
                        + "(user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'student', 'active', ?, NOW(), NOW())",
                studentId, groupId);
        return studentId;
    }

    private Long userId(String login) {
        return jdbc.queryForObject("SELECT id FROM users WHERE login = ?", Long.class, login);
    }

    private String groupName(Long id) {
        return jdbc.queryForObject("SELECT name FROM groups WHERE id = ?", String.class, id);
    }

    private Long groupIdByName(String name) {
        return jdbc.queryForObject("SELECT id FROM groups WHERE name = ?", Long.class, name);
    }

    private OffsetDateTime beforeCycleEnd() {
        return CYCLE_END.minusDays(10).atTime(12, 0).atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime afterCycleEnd() {
        return CYCLE_END.plusDays(10).atTime(12, 0).atOffset(ZoneOffset.UTC);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder previewRequest(
            Long userId, String role, String body) {
        return post("/academic/groups/promote/preview")
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false")
                .contentType(APPLICATION_JSON)
                .content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder executeRequest(
            Long userId, Long cycleId, String version, Long groupId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cycleSemesterId", cycleId);
        body.put("previewVersion", version);
        body.put("groupId", groupId);
        return post("/academic/groups/promote")
                .header("X-User-Id", userId)
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
