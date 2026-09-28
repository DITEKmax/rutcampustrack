package ru.rutcampustrack.academic.group;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.group.CreateAdminGroupRequest;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.dashboard.DashboardService;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import java.time.LocalDate;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One real PostgreSQL proof for the additive ADMIN registry read model.
 *
 * <p>The test deliberately goes through the canonical service writer and the
 * REST registry read path. It covers the coupled rules that a unit mapping
 * test cannot prove: current-semester coverage, grant-derived DRAFT/ACTIVE,
 * tab counts and an unknown legacy programme duration.</p>
 */
@AutoConfigureMockMvc
@Transactional
class GroupRegistryIT extends AbstractAcademicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupService groupService;
    @Autowired private DashboardService dashboardService;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void registryCreateCoverageStatusCountsAndLegacyDurationAreServerDerived() throws Exception {
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM users WHERE login = 'admin'", Long.class);

        jdbc.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbc.update("INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                + "VALUES (?, ?, ?, true, NOW())",
                "registry-it-" + System.nanoTime(),
                LocalDate.of(2045, 9, 1), LocalDate.of(2046, 6, 30));

        Group created = groupService.createAdminGroup(
                new CreateAdminGroupRequest("РЕГ", "311", 3));
        entityManager.flush();

        Long coverageRows = jdbc.queryForObject(
                "SELECT count(*) FROM group_history_coverage WHERE group_id = ?",
                Long.class, created.getId());
        LocalDate coverageFrom = jdbc.queryForObject(
                "SELECT coverage_from FROM group_history_coverage WHERE group_id = ?",
                LocalDate.class, created.getId());
        // The count is the atomic provenance marker; the date proves that the
        // marker belongs to the active semester used by the writer.
        org.assertj.core.api.Assertions.assertThat(coverageRows).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(coverageFrom).isEqualTo(LocalDate.of(2045, 9, 1));

        jdbc.update("INSERT INTO groups (name, is_active, created_at) VALUES (?, true, NOW())",
                "ЛЕГ-351");
        jdbc.update("INSERT INTO groups (name, is_active, created_at, alphabetic_code, numeric_code, "
                        + "current_course, training_duration_years, duration_status) "
                        + "VALUES (?, false, NOW(), ?, ?, ?, ?, 'KNOWN')",
                "СТА-311 (выпуск 2024)", "СТА", "311", 3, 4);
        Group reusedArchivedCode = groupService.createAdminGroup(
                new CreateAdminGroupRequest("СТА", "311", 4));
        org.assertj.core.api.Assertions.assertThat(reusedArchivedCode.getName())
                .isEqualTo("СТА-311");

        jdbc.update("INSERT INTO groups (name, is_active, created_at) VALUES (?, true, NOW())",
                "ИВТ-011");
        jdbc.update("INSERT INTO groups (name, is_active, created_at) VALUES (?, true, NOW())",
                "УВПв-511");

        mockMvc.perform(registryRequest(adminId, "РЕГ-311", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name", equalTo("РЕГ-311")))
                .andExpect(jsonPath("$.items[0].currentCourse", equalTo(3)))
                .andExpect(jsonPath("$.items[0].trainingDurationYears", equalTo(3)))
                .andExpect(jsonPath("$.items[0].status", equalTo("DRAFT")))
                .andExpect(jsonPath("$.items[0].draftReason", equalTo("Староста не назначен")))
                .andExpect(jsonPath("$.draftCount", equalTo(1)))
                .andExpect(jsonPath("$.activeCount", equalTo(0)));

        Long headmanId = jdbc.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                        + "is_headman, password_changed, created_at, updated_at) "
                        + "VALUES (?, 'x', 'Тестов', 'Староста', 'student', 'active', false, false, NOW(), NOW()) "
                        + "RETURNING id",
                Long.class, "registry_headman_" + System.nanoTime());
        jdbc.update("INSERT INTO user_role_grants "
                        + "(user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'headman', 'active', ?, NOW(), NOW())",
                headmanId, created.getId());

        mockMvc.perform(registryRequest(adminId, "РЕГ-311", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.items[0].headmanFio", equalTo("Тестов Староста")))
                .andExpect(jsonPath("$.activeCount", equalTo(1)))
                .andExpect(jsonPath("$.draftCount", equalTo(0)));

        mockMvc.perform(registryRequest(adminId, "ЛЕГ-351", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].durationStatus", equalTo("LEGACY_UNKNOWN")))
                .andExpect(jsonPath("$.items[0].trainingDurationYears", nullValue()))
                .andExpect(jsonPath("$.items[0].currentCourse", nullValue()))
                .andExpect(jsonPath("$.items[0].status", equalTo("DRAFT")));

        mockMvc.perform(registryRequest(adminId, "ИВТ-011", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].alphabeticCode", nullValue()))
                .andExpect(jsonPath("$.items[0].numericCode", nullValue()))
                .andExpect(jsonPath("$.items[0].currentCourse", nullValue()))
                .andExpect(jsonPath("$.items[0].trainingDurationYears", nullValue()))
                .andExpect(jsonPath("$.items[0].durationStatus", equalTo("LEGACY_UNKNOWN")));

        mockMvc.perform(registryRequest(adminId, "УВПв-511", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].alphabeticCode", nullValue()))
                .andExpect(jsonPath("$.items[0].numericCode", nullValue()))
                .andExpect(jsonPath("$.items[0].currentCourse", nullValue()))
                .andExpect(jsonPath("$.items[0].trainingDurationYears", nullValue()))
                .andExpect(jsonPath("$.items[0].durationStatus", equalTo("LEGACY_UNKNOWN")));
    }

    @Test
    void restoreArchivedGroupsIsAdminOnlyIdempotentAndKeepsLegacyNamesAndRegistryCounts()
            throws Exception {
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM users WHERE login = 'admin'", Long.class);
        String suffix = Long.toUnsignedString(System.nanoTime());
        String letters = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ";
        String alphabeticCode = "Р" + letters.charAt(Math.floorMod((int) System.nanoTime(), letters.length()));
        String numericCode = "3" + suffix.substring(0, 2);
        String canonicalName = alphabeticCode + "-" + numericCode;
        Long canonicalGroupId = addArchivedGroup(
                canonicalName + " (выпуск 2026)", alphabeticCode, numericCode, 3, 5, "KNOWN");

        String legacyName = "Old-" + suffix.substring(2, 6);
        Long legacyGroupId = addArchivedGroup(
                legacyName + " (выпуск 2025)", null, null, null, null, "LEGACY_UNKNOWN");

        String occupiedName = "Old-" + suffix.substring(6, 10);
        Long occupiedGroupId = addArchivedGroup(
                occupiedName + " (выпуск 2024)", null, null, null, null, "LEGACY_UNKNOWN");
        addGroup(occupiedName, true);

        mockMvc.perform(restoreRequest(adminId, legacyGroupId, "TEACHER"))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT name FROM groups WHERE id = ?", String.class, legacyGroupId))
                .isEqualTo(legacyName + " (выпуск 2025)");

        mockMvc.perform(restoreRequest(adminId, canonicalGroupId, "ADMIN"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(restoreRequest(adminId, canonicalGroupId, "ADMIN"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT name FROM groups WHERE id = ?", String.class, canonicalGroupId))
                .isEqualTo(canonicalName);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT is_active FROM groups WHERE id = ?", Boolean.class, canonicalGroupId))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT archived_at IS NULL FROM groups WHERE id = ?", Boolean.class, canonicalGroupId))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT current_course FROM groups WHERE id = ?", Integer.class, canonicalGroupId))
                .isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT training_duration_years FROM groups WHERE id = ?", Integer.class, canonicalGroupId))
                .isEqualTo(5);

        mockMvc.perform(registryRequest(adminId, canonicalName, "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", equalTo(Math.toIntExact(canonicalGroupId))))
                .andExpect(jsonPath("$.items[0].status", equalTo("DRAFT")))
                .andExpect(jsonPath("$.draftCount", equalTo(1)))
                .andExpect(jsonPath("$.archivedCount", equalTo(0)))
                .andExpect(jsonPath("$.activeCount", equalTo(0)));

        mockMvc.perform(restoreRequest(adminId, legacyGroupId, "ADMIN"))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT name FROM groups WHERE id = ?", String.class, legacyGroupId))
                .isEqualTo(legacyName);

        String occupiedArchivedName = occupiedName + " (выпуск 2024)";
        mockMvc.perform(restoreRequest(adminId, occupiedGroupId, "ADMIN"))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT name FROM groups WHERE id = ?", String.class, occupiedGroupId))
                .isEqualTo(occupiedArchivedName);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT is_active FROM groups WHERE id = ?", Boolean.class, occupiedGroupId))
                .isFalse();
    }

    @Test
    void dashboardCountsOnlyActiveRoleGrantsAndRegistryActiveGroups() throws Exception {
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM users WHERE login = 'admin'", Long.class);
        DashboardStatsResponse before = dashboardService.getStats().getContent();
        String suffix = Long.toString(System.nanoTime());

        Long activeStudent = createUser("ds_" + suffix, "student");
        addGrant(activeStudent, "student", "active", null);
        Long activeTeacher = createUser("dt_" + suffix, "teacher");
        addGrant(activeTeacher, "teacher", "active", null);
        Long suspendedStudent = createUser("dsp_" + suffix, "student");
        addGrant(suspendedStudent, "student", "suspended", null);
        Long archivedStudent = createUser("da_" + suffix, "student");
        addGrant(archivedStudent, "student", "archived", null);
        Long multiRoleUser = createUser("dm_" + suffix, "student");
        addGrant(multiRoleUser, "student", "active", null);
        addGrant(multiRoleUser, "teacher", "active", null);

        Long activeGroup = addGroup("dg-a-" + suffix, true);
        addGroup("dg-d-" + suffix, true);
        addGroup("dg-x-" + suffix, false);
        Long headman = createUser("dh_" + suffix, "student");
        addGrant(headman, "headman", "active", activeGroup);

        mockMvc.perform(dashboardRequest(adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents", equalTo(Math.toIntExact(before.getTotalStudents() + 2))))
                .andExpect(jsonPath("$.totalTeachers", equalTo(Math.toIntExact(before.getTotalTeachers() + 2))))
                .andExpect(jsonPath("$.totalGroups", equalTo(Math.toIntExact(before.getActiveGroups() + 1))))
                .andExpect(jsonPath("$.activeGroups", equalTo(Math.toIntExact(before.getActiveGroups() + 1))));
    }

    private Long createUser(String login, String legacyRole) {
        return jdbc.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                        + "is_headman, password_changed, created_at, updated_at) "
                        + "VALUES (?, 'x', 'Тестов', 'Сводки', ?, 'active', false, false, NOW(), NOW()) "
                        + "RETURNING id",
                Long.class, login, legacyRole);
    }

    private void addGrant(Long userId, String role, String status, Long groupId) {
        jdbc.update("INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                userId, role, status, groupId);
    }

    private Long addGroup(String name, boolean active) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name, is_active, created_at) VALUES (?, ?, NOW()) RETURNING id",
                Long.class, name, active);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dashboardRequest(Long adminId) {
        return get("/academic/dashboard/stats")
                .header("X-User-Id", adminId)
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registryRequest(
            Long adminId, String search, String status) {
        return get("/academic/groups/registry")
                .header("X-User-Id", adminId)
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false")
                .param("status", status)
                .param("search", search)
                .param("size", "20");
    }

    private Long addArchivedGroup(String name, String alphabeticCode, String numericCode,
                                  Integer currentCourse, Integer duration, String durationStatus) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name, is_active, created_at, archived_at, alphabetic_code, "
                        + "numeric_code, current_course, training_duration_years, duration_status) "
                        + "VALUES (?, false, NOW(), NOW(), CAST(? AS varchar), CAST(? AS varchar), "
                        + "CAST(? AS integer), CAST(? AS integer), ?) RETURNING id",
                Long.class, name, alphabeticCode, numericCode, currentCourse, duration, durationStatus);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder restoreRequest(
            Long userId, Long groupId, String role) {
        return post("/academic/groups/{id}/restore", groupId)
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false");
    }
}
