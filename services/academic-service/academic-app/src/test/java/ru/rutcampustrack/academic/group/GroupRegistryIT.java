package ru.rutcampustrack.academic.group;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

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
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupService groupService;
    @Autowired private DashboardService dashboardService;
    @PersistenceContext private EntityManager entityManager;

    @Autowired private ru.rutcampustrack.shared.security.InternalJwtTestFactory jwtFactory;
    private String adminToken;

    @org.junit.jupiter.api.BeforeEach
    void selectDurableFullAdminSession() {
        Long adminId = jdbc.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
        jdbc.update("INSERT INTO admin_access_profiles(user_id, assigned, mode, permissions, version) "
                + "VALUES (?, true, 'FULL', '{}', 1) ON CONFLICT(user_id) DO UPDATE "
                + "SET assigned = true, mode = 'FULL', permissions = '{}', "
                + "version = admin_access_profiles.version + 1, updated_at = NOW()", adminId);
        Long grantId = jdbc.queryForObject("SELECT id FROM user_role_grants "
                + "WHERE user_id = ? AND role = 'admin' AND status = 'active'", Long.class, adminId);
        java.util.UUID sid = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO auth_sessions(sid, user_id, active_role_grant_id, session_version, "
                + "current_refresh_jti, refresh_expires_at, created_at, last_seen_at, auth_method) "
                + "VALUES (?, ?, ?, 1, ?, NOW() + interval '1 day', NOW(), NOW(), 'PASSWORD')",
                sid, adminId, grantId, java.util.UUID.randomUUID());
        Long rolesVersion = jdbc.queryForObject("SELECT roles_version FROM users WHERE id = ?", Long.class, adminId);
        adminToken = jwtFactory.validToken(adminId, sid, 1, rolesVersion, "ADMIN", "ACTIVE", null, false, false);
    }

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
                Long.class, "reg_h_" + System.nanoTime());
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

    @Test
    void registryFiltersBeforePaginationAndUsesIdenticalFiltersForEveryTabCount() throws Exception {
        Long adminId = jdbc.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
        addRegistryGroup("ФЛТ-251", "ФЛТ", "251", 2, true);
        addRegistryGroup("ФЛТ-252", "ФЛТ", "252", 2, true);
        Long thirdDraft = addRegistryGroup("ФЛТ-253", "ФЛТ", "253", 2, true);
        Long active = addRegistryGroup("ФЛТ-254", "ФЛТ", "254", 2, true);
        addGrant(createUser("filter_headman_" + System.nanoTime(), "student"), "headman", "active", active);
        Long archived = addRegistryGroup("ФЛТ-255", "ФЛТ", "255", 2, false);
        addRegistryGroup("ФЛТ-151", "ФЛТ", "151", 1, true);
        addRegistryGroup("ФЛТ-241", "ФЛТ", "241", 2, true);
        addRegistryGroup("ФЛТ-281", "ФЛТ", "281", 2, true);
        addGroup("ФЛТ-256", true); // A parseable display name is not metadata.

        mockMvc.perform(registryRequest(adminId, "ФЛТ", "DRAFT")
                        .param("course", "2").param("studyMode", "PART_TIME")
                        .param("educationLevel", "SPECIALIST").param("sort", "name,asc")
                        .param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", equalTo(Math.toIntExact(thirdDraft))))
                .andExpect(jsonPath("$.totalElements", equalTo(3)))
                .andExpect(jsonPath("$.totalPages", equalTo(2)))
                .andExpect(jsonPath("$.draftCount", equalTo(3)))
                .andExpect(jsonPath("$.activeCount", equalTo(1)))
                .andExpect(jsonPath("$.archivedCount", equalTo(1)));
        mockMvc.perform(registryRequest(adminId, "ФЛТ", "ARCHIVED")
                        .param("course", "2").param("studyMode", "PART_TIME")
                        .param("educationLevel", "SPECIALIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", equalTo(Math.toIntExact(archived))))
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.draftCount", equalTo(3)))
                .andExpect(jsonPath("$.activeCount", equalTo(1)))
                .andExpect(jsonPath("$.archivedCount", equalTo(1)));
    }

    @Test
    void registrySortsWholeResultWithStableTiesAndUnknownMetadataLastInBothDirections() throws Exception {
        Long adminId = jdbc.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
        Long a = addRegistryGroup("ОРД-311", "ОРД", "311", 3, true);
        Long b = addRegistryGroup("ОРД-191", "ОРД", "191", 1, true);
        Long c = addRegistryGroup("ОРД-251", "ОРД", "251", 2, true);
        Long d = addRegistryGroup("ОРД-211", "ОРД", "211", 2, true);
        Long e = addGroup("ОРД-111", true);
        Long f = addRegistryGroup("ОРД-411", "ОРД", "411", 3, true); // contradictory course
        Long g = addRegistryGroup("ОРД-mixed", "Увп", "110", 1, true); // stored tuple wins over name

        assertThat(registryIds(adminId, "currentCourse,asc")).containsExactly(b, g, c, d, a, e, f);
        assertThat(registryIds(adminId, "currentCourse,desc")).containsExactly(a, c, d, b, g, e, f);
        assertThat(registryIds(adminId, "programType,asc")).containsExactly(a, d, g, c, b, e, f);
        assertThat(registryIds(adminId, "programType,desc")).containsExactly(b, c, a, d, g, e, f);
        assertThat(registryIds(adminId, "name,asc")).containsExactly(e, b, d, c, a, f, g);
        assertThat(registryIds(adminId, "name,desc")).containsExactly(g, f, a, c, d, b, e);
        assertThat(registryIds(adminId, "currentCourse,desc", "programType,asc"))
                .containsExactly(a, d, c, g, b, e, f);
        mockMvc.perform(registryRequest(adminId, "ОРД-411", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].currentCourse", nullValue()))
                .andExpect(jsonPath("$.items[0].programTypeDigit", nullValue()))
                .andExpect(jsonPath("$.items[0].studyMode", nullValue()))
                .andExpect(jsonPath("$.items[0].educationLevel", nullValue()))
                .andExpect(jsonPath("$.items[0].durationStatus", equalTo("LEGACY_UNKNOWN")));
        mockMvc.perform(registryRequest(adminId, "ОРД-111", "DRAFT").param("course", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    @Test
    void registryCreatesAllNineProgramTypesWithExplicitDurationAndRejectsInvalidQueryValues() throws Exception {
        Long adminId = jdbc.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
        jdbc.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbc.update("INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, true, NOW())", "types-it-" + System.nanoTime(),
                LocalDate.of(2045, 9, 1), LocalDate.of(2046, 6, 30));
        String[] modes = {"FULL_TIME", "PART_TIME", "EXTRAMURAL"};
        String[] levels = {"BACHELOR", "SPECIALIST", "MASTER"};
        for (int digit = 1; digit <= 9; digit++) {
            String numeric = "1" + digit + "0"; // Existing ordinal zero remains valid.
            String mode = modes[(digit - 1) % 3];
            String level = levels[(digit - 1) / 3];
            mockMvc.perform(post("/academic/groups/registry")
                            .header("X-Internal-Token", adminToken).header("X-User-Id", adminId).header("X-User-Role", "ADMIN")
                            .header("X-Group-Id", "").header("X-Is-Headman", "false")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(new CreateAdminGroupRequest("КТ", numeric, 6))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.programTypeDigit", equalTo(digit)))
                    .andExpect(jsonPath("$.studyMode", equalTo(mode)))
                    .andExpect(jsonPath("$.educationLevel", equalTo(level)))
                    .andExpect(jsonPath("$.trainingDurationYears", equalTo(6)));
            entityManager.flush();
            mockMvc.perform(registryRequest(adminId, "КТ-" + numeric, "DRAFT")
                            .param("course", "1").param("studyMode", mode).param("educationLevel", level))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements", equalTo(1)))
                    .andExpect(jsonPath("$.items[0].programTypeDigit", equalTo(digit)))
                    .andExpect(jsonPath("$.items[0].studyMode", equalTo(mode)))
                    .andExpect(jsonPath("$.items[0].educationLevel", equalTo(level)));
        }
        // The name-only compatibility writer cannot invent a duration for type 5.
        mockMvc.perform(post("/academic/groups").header("X-Internal-Token", adminToken).header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN").header("X-Group-Id", "")
                        .header("X-Is-Headman", "false").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"КТ-151\"}"))
                .andExpect(status().isBadRequest());
        for (String course : List.of("0", "10", "abc")) {
            mockMvc.perform(registryRequest(adminId, "КТ", "DRAFT").param("course", course))
                    .andExpect(status().isBadRequest());
        }
        for (String sort : List.of("id,asc", "name;DROP TABLE groups,asc", "currentCourse,sideways")) {
            mockMvc.perform(registryRequest(adminId, "КТ", "DRAFT").param("sort", sort))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(registryRequest(adminId, "КТ", "DRAFT").param("studyMode", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(registryRequest(adminId, "КТ", "DRAFT").param("educationLevel", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    private Long addRegistryGroup(String name, String prefix, String numeric, int course, boolean active) {
        return jdbc.queryForObject("INSERT INTO groups (name, is_active, created_at, alphabetic_code, "
                        + "numeric_code, current_course, training_duration_years, duration_status) "
                        + "VALUES (?, ?, NOW(), ?, ?, ?, 6, 'KNOWN') RETURNING id",
                Long.class, name, active, prefix, numeric, course);
    }

    private List<Long> registryIds(Long adminId, String... sort) throws Exception {
        List<Long> ids = new ArrayList<>();
        int pages = 1;
        for (int page = 0; page < pages; page++) {
            JsonNode response = objectMapper.readTree(mockMvc.perform(registryRequest(adminId, "ОРД", "DRAFT")
                            .param("size", "2").param("page", Integer.toString(page)).param("sort", sort))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            assertThat(response.path("totalElements").asLong()).isEqualTo(7);
            pages = response.path("totalPages").asInt();
            response.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        }
        return ids;
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
                .header("X-Internal-Token", adminToken).header("X-User-Id", adminId)
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registryRequest(
            Long adminId, String search, String status) {
        return get("/academic/groups/registry")
                .header("X-Internal-Token", adminToken).header("X-User-Id", adminId)
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false")
                .param("status", status)
                .param("search", search);
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
        var request = post("/academic/groups/{id}/restore", groupId)
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false");
        return "ADMIN".equals(role) ? request.header("X-Internal-Token", adminToken) : request;
    }
}
