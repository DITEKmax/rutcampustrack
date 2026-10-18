package ru.rutcampustrack.academic.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;
import ru.rutcampustrack.academic.contract.dto.group.CreateAdminGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.dashboard.DashboardService;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.user.UserArchiveService;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One real PostgreSQL proof for the additive ADMIN registry read model.
 *
 * <p>The test deliberately goes through the canonical service writer and the REST registry read
 * path. It covers the coupled rules that a unit mapping test cannot prove: current-semester
 * coverage, grant-derived DRAFT/ACTIVE, tab counts and an unknown legacy programme duration.
 */
@AutoConfigureMockMvc
class GroupRegistryIT extends AbstractAcademicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupService groupService;
    @Autowired private DashboardService dashboardService;
    @Autowired private AcademicUserIdentityOperations identityOperations;
    @Autowired private UserArchiveService userArchives;
    @MockitoBean private AttendanceUserImpactGrpcClient attendanceImpact;
    private SelectedActor admin;
    private SelectedActor teacher;

    @org.junit.jupiter.api.BeforeEach
    void selectDurableFullAdminSession() {
        admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        teacher = identityFixture.select(identityFixture.canonical().teacherId(), AuthRole.TEACHER);
    }

    @Test
    void registryCreateCoverageStatusCountsAndLegacyDurationAreServerDerived() throws Exception {
        Long adminId = admin.userId();

        jdbc.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbc.update(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, true, NOW())",
                "registry-it-" + System.nanoTime(),
                LocalDate.of(2045, 9, 1),
                LocalDate.of(2046, 6, 30));

        Group created = groupService.createAdminGroup(new CreateAdminGroupRequest("РЕГ", "311", 3));

        Long coverageRows =
                jdbc.queryForObject(
                        "SELECT count(*) FROM group_history_coverage WHERE group_id = ?",
                        Long.class,
                        created.getId());
        LocalDate coverageFrom =
                jdbc.queryForObject(
                        "SELECT coverage_from FROM group_history_coverage WHERE group_id = ?",
                        LocalDate.class,
                        created.getId());
        // The count is the atomic provenance marker; the date proves that the
        // marker belongs to the active semester used by the writer.
        org.assertj.core.api.Assertions.assertThat(coverageRows).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(coverageFrom)
                .isEqualTo(LocalDate.of(2045, 9, 1));

        jdbc.update(
                "INSERT INTO groups (name, is_active, created_at) VALUES (?, true, NOW())",
                "ЛЕГ-351");
        jdbc.update(
                "INSERT INTO groups (name, is_active, created_at, alphabetic_code, numeric_code, "
                        + "current_course, training_duration_years, duration_status) "
                        + "VALUES (?, false, NOW(), ?, ?, ?, ?, 'KNOWN')",
                "СТА-311 (выпуск 2024)",
                "СТА",
                "311",
                3,
                4);
        Group reusedArchivedCode =
                groupService.createAdminGroup(new CreateAdminGroupRequest("СТА", "311", 4));
        org.assertj.core.api.Assertions.assertThat(reusedArchivedCode.getName())
                .isEqualTo("СТА-311");

        jdbc.update(
                "INSERT INTO groups (name, is_active, created_at) VALUES (?, true, NOW())",
                "ИВТ-011");
        jdbc.update(
                "INSERT INTO groups (name, is_active, created_at) VALUES (?, true, NOW())",
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

        long headmanId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Тестов",
                                        "Староста",
                                        null,
                                        UserRole.STUDENT,
                                        created.getId(),
                                        null,
                                        null),
                                admin)
                        .userId();
        assignHeadman(headmanId, created.getId());

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
        Long adminId = admin.userId();
        String suffix = Long.toUnsignedString(System.nanoTime());
        String letters = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ";
        String alphabeticCode =
                "Р" + letters.charAt(Math.floorMod((int) System.nanoTime(), letters.length()));
        String numericCode = "351";
        String canonicalName = alphabeticCode + "-" + numericCode;
        Long canonicalGroupId =
                addArchivedGroup(
                        canonicalName + " (выпуск 2026)",
                        alphabeticCode,
                        numericCode,
                        3,
                        5,
                        "KNOWN");

        String legacyName = "ЛГ-111";
        Long legacyGroupId =
                addArchivedGroup(
                        legacyName + " (выпуск 2025)", null, null, null, null, "LEGACY_UNKNOWN");

        String occupiedName = "ЗГ-111";
        Long occupiedGroupId =
                addArchivedGroup(
                        occupiedName + " (выпуск 2024)", null, null, null, null, "LEGACY_UNKNOWN");
        addGroup(occupiedName, true);

        mockMvc.perform(restoreRequest(adminId, legacyGroupId, "TEACHER"))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM groups WHERE id = ?",
                                String.class,
                                legacyGroupId))
                .isEqualTo(legacyName + " (выпуск 2025)");

        mockMvc.perform(restoreRequest(adminId, canonicalGroupId, "ADMIN"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(restoreRequest(adminId, canonicalGroupId, "ADMIN"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM groups WHERE id = ?",
                                String.class,
                                canonicalGroupId))
                .isEqualTo(canonicalName);
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM groups WHERE id = ?",
                                Boolean.class,
                                canonicalGroupId))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT archived_at IS NULL FROM groups WHERE id = ?",
                                Boolean.class,
                                canonicalGroupId))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT current_course FROM groups WHERE id = ?",
                                Integer.class,
                                canonicalGroupId))
                .isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT training_duration_years FROM groups WHERE id = ?",
                                Integer.class,
                                canonicalGroupId))
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
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM groups WHERE id = ?",
                                String.class,
                                legacyGroupId))
                .isEqualTo(legacyName);

        String occupiedArchivedName = occupiedName + " (выпуск 2024)";
        mockMvc.perform(restoreRequest(adminId, occupiedGroupId, "ADMIN"))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM groups WHERE id = ?",
                                String.class,
                                occupiedGroupId))
                .isEqualTo(occupiedArchivedName);
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM groups WHERE id = ?",
                                Boolean.class,
                                occupiedGroupId))
                .isFalse();
    }

    @Test
    void dashboardCountsOnlyActiveRoleGrantsAndRegistryActiveGroups() throws Exception {
        Long adminId = admin.userId();
        DashboardStatsResponse before = dashboardService.getStats().getContent();
        String suffix = Long.toString(System.nanoTime());

        Long activeGroup = addGroup("dg-a-" + suffix, true);
        addGroup("dg-d-" + suffix, true);
        addGroup("dg-x-" + suffix, false);
        Long activeStudent = createUser("ds_" + suffix, UserRole.STUDENT, activeGroup);
        createUser("dt_" + suffix, UserRole.TEACHER, null);
        Long suspendedStudent = createUser("dsp_" + suffix, UserRole.STUDENT, null);
        changeRole(suspendedStudent, "STUDENT", RoleGrantStatus.SUSPENDED, null);
        Long archivedStudent = createUser("da_" + suffix, UserRole.STUDENT, null);
        org.mockito.Mockito.when(attendanceImpact.preview(archivedStudent))
                .thenReturn(
                        UserImpactSnapshot.newBuilder()
                                .setUserId(archivedStudent)
                                .setObservedAtEpochMs(System.currentTimeMillis())
                                .setSnapshotDigest("0".repeat(64))
                                .build());
        try (var ignored = identityFixture.request(admin)) {
            var preview = userArchives.preview(archivedStudent);
            userArchives.archive(
                    archivedStudent,
                    new ArchiveRequest(UUID.randomUUID(), preview.previewDigest(), null));
        }
        Long multiRoleUser = createUser("dm_" + suffix, UserRole.STUDENT, null);
        changeRole(multiRoleUser, "TEACHER", RoleGrantStatus.ACTIVE, "dm_" + suffix);
        // The active student also holds HEADMAN; a derived headman does not count twice.
        assignHeadman(activeStudent, activeGroup);

        mockMvc.perform(dashboardRequest(adminId))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.totalStudents",
                                equalTo(Math.toIntExact(before.getTotalStudents() + 2))))
                .andExpect(
                        jsonPath(
                                "$.totalTeachers",
                                equalTo(Math.toIntExact(before.getTotalTeachers() + 2))))
                .andExpect(
                        jsonPath(
                                "$.totalGroups",
                                equalTo(Math.toIntExact(before.getActiveGroups() + 1))))
                .andExpect(
                        jsonPath(
                                "$.activeGroups",
                                equalTo(Math.toIntExact(before.getActiveGroups() + 1))));
    }

    @Test
    void registryFiltersBeforePaginationAndUsesIdenticalFiltersForEveryTabCount() throws Exception {
        Long adminId = admin.userId();
        addRegistryGroup("ФЛТ-251", "ФЛТ", "251", 2, true);
        addRegistryGroup("ФЛТ-252", "ФЛТ", "252", 2, true);
        Long thirdDraft = addRegistryGroup("ФЛТ-253", "ФЛТ", "253", 2, true);
        Long active = addRegistryGroup("ФЛТ-254", "ФЛТ", "254", 2, true);
        assignHeadman(
                createUser("filter_headman_" + System.nanoTime(), UserRole.STUDENT, active),
                active);
        Long archived = addRegistryGroup("ФЛТ-255", "ФЛТ", "255", 2, false);
        addRegistryGroup("ФЛТ-151", "ФЛТ", "151", 1, true);
        addRegistryGroup("ФЛТ-241", "ФЛТ", "241", 2, true);
        addRegistryGroup("ФЛТ-281", "ФЛТ", "281", 2, true);
        addGroup("ФЛТ-256", true); // A parseable display name is not metadata.

        mockMvc.perform(
                        registryRequest(adminId, "ФЛТ", "DRAFT")
                                .param("course", "2")
                                .param("studyMode", "PART_TIME")
                                .param("educationLevel", "SPECIALIST")
                                .param("sort", "name,asc")
                                .param("size", "2")
                                .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", equalTo(Math.toIntExact(thirdDraft))))
                .andExpect(jsonPath("$.totalElements", equalTo(3)))
                .andExpect(jsonPath("$.totalPages", equalTo(2)))
                .andExpect(jsonPath("$.draftCount", equalTo(3)))
                .andExpect(jsonPath("$.activeCount", equalTo(1)))
                .andExpect(jsonPath("$.archivedCount", equalTo(1)));
        mockMvc.perform(
                        registryRequest(adminId, "ФЛТ", "ARCHIVED")
                                .param("course", "2")
                                .param("studyMode", "PART_TIME")
                                .param("educationLevel", "SPECIALIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", equalTo(Math.toIntExact(archived))))
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.draftCount", equalTo(3)))
                .andExpect(jsonPath("$.activeCount", equalTo(1)))
                .andExpect(jsonPath("$.archivedCount", equalTo(1)));
    }

    @Test
    void registrySortsWholeResultWithStableTiesAndUnknownMetadataLastInBothDirections()
            throws Exception {
        Long adminId = admin.userId();
        Long a = addRegistryGroup("ОРД-311", "ОРД", "311", 3, true);
        Long b = addRegistryGroup("ОРД-191", "ОРД", "191", 1, true);
        Long c = addRegistryGroup("ОРД-251", "ОРД", "251", 2, true);
        Long d = addRegistryGroup("ОРД-211", "ОРД", "211", 2, true);
        Long e = addGroup("ОРД-111", true);
        Long f = addRegistryGroup("ОРД-411", "ОРД", "411", 3, true); // contradictory course
        Long g =
                addRegistryGroup("ОРД-mixed", "Увп", "110", 1, true); // stored tuple wins over name

        assertThat(registryIds(adminId)).containsExactly(b, d, c, a, f, e, g);
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    @Test
    void registryCreatesAllNineProgramTypesWithExplicitDurationAndRejectsInvalidQueryValues()
            throws Exception {
        Long adminId = admin.userId();
        jdbc.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbc.update(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, true, NOW())",
                "types-it-" + System.nanoTime(),
                LocalDate.of(2045, 9, 1),
                LocalDate.of(2046, 6, 30));
        String[] modes = {"FULL_TIME", "PART_TIME", "EXTRAMURAL"};
        String[] levels = {"BACHELOR", "SPECIALIST", "MASTER"};
        for (int digit = 1; digit <= 9; digit++) {
            String numeric = "1" + digit + "0"; // Existing ordinal zero remains valid.
            String mode = modes[(digit - 1) % 3];
            String level = levels[(digit - 1) / 3];
            mockMvc.perform(
                            post("/academic/groups/registry")
                                    .with(identityFixture.signed(admin))
                                    .contentType(APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsBytes(
                                                    new CreateAdminGroupRequest("КТ", numeric, 5))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.programTypeDigit", equalTo(digit)))
                    .andExpect(jsonPath("$.studyMode", equalTo(mode)))
                    .andExpect(jsonPath("$.educationLevel", equalTo(level)))
                    .andExpect(jsonPath("$.trainingDurationYears", equalTo(5)));
            mockMvc.perform(
                            registryRequest(adminId, "КТ-" + numeric, "DRAFT")
                                    .param("course", "1")
                                    .param("studyMode", mode)
                                    .param("educationLevel", level))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements", equalTo(1)))
                    .andExpect(jsonPath("$.items[0].programTypeDigit", equalTo(digit)))
                    .andExpect(jsonPath("$.items[0].studyMode", equalTo(mode)))
                    .andExpect(jsonPath("$.items[0].educationLevel", equalTo(level)));
        }
        // The name-only compatibility writer cannot invent a duration for type 5.
        mockMvc.perform(
                        post("/academic/groups")
                                .with(identityFixture.signed(admin))
                                .contentType(APPLICATION_JSON)
                                .content("{\"name\":\"КТ-151\"}"))
                .andExpect(status().isBadRequest());
        for (String course : List.of("0", "10", "abc")) {
            mockMvc.perform(registryRequest(adminId, "КТ", "DRAFT").param("course", course))
                    .andExpect(status().isBadRequest());
        }
        for (String sort :
                List.of("id,asc", "name;DROP TABLE groups,asc", "currentCourse,sideways")) {
            mockMvc.perform(registryRequest(adminId, "КТ", "DRAFT").param("sort", sort))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(registryRequest(adminId, "КТ", "DRAFT").param("studyMode", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(registryRequest(adminId, "КТ", "DRAFT").param("educationLevel", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    private Long addRegistryGroup(
            String name, String prefix, String numeric, int course, boolean active) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name, is_active, created_at, alphabetic_code, "
                        + "numeric_code, current_course, training_duration_years, duration_status) "
                        + "VALUES (?, ?, NOW(), ?, ?, ?, 6, 'KNOWN') RETURNING id",
                Long.class,
                name,
                active,
                prefix,
                numeric,
                course);
    }

    private List<Long> registryIds(Long adminId, String... sort) throws Exception {
        List<Long> ids = new ArrayList<>();
        int pages = 1;
        for (int page = 0; page < pages; page++) {
            var request =
                    registryRequest(adminId, "ОРД", "DRAFT")
                            .param("size", "2")
                            .param("page", Integer.toString(page));
            if (sort.length > 0) {
                request.param("sort", sort);
            }
            JsonNode response =
                    objectMapper.readTree(
                            mockMvc.perform(request)
                                    .andExpect(status().isOk())
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsByteArray());
            assertThat(response.path("totalElements").asLong()).isEqualTo(7);
            pages = response.path("totalPages").asInt();
            response.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        }
        return ids;
    }

    private Long createUser(String suffix, UserRole role, Long groupId) {
        if (role == UserRole.STUDENT && groupId != null) {
            jdbc.update(
                    "INSERT INTO"
                        + " group_history_coverage(group_id,coverage_from,writer_version,created_at)"
                        + " SELECT ?,date_from,'managed_v1',NOW() FROM semesters WHERE is_active ON"
                        + " CONFLICT(group_id) DO NOTHING",
                    groupId);
        }
        return identityFixture
                .seedUser(
                        new CreateUserRequest(
                                "Тестов",
                                "Сводки",
                                null,
                                role,
                                role == UserRole.STUDENT
                                        ? (groupId == null
                                                ? identityFixture.canonical().groupId()
                                                : groupId)
                                        : null,
                                role == UserRole.TEACHER ? suffix : null,
                                null),
                        admin)
                .userId();
    }

    private void changeRole(long userId, String role, RoleGrantStatus status, String employee) {
        try (var ignored = identityFixture.request(admin)) {
            identityOperations.role(
                    userId, role, new RoleGrantUpdateRequest(status, null, employee, null));
        }
    }

    private void assignHeadman(long userId, long groupId) {
        try (var ignored = identityFixture.request(admin)) {
            identityOperations.headman(groupId, userId, null, false, null);
        }
    }

    private Long addGroup(String name, boolean active) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name, is_active, created_at) VALUES (?, ?, NOW()) RETURNING"
                    + " id",
                Long.class,
                name,
                active);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            dashboardRequest(Long adminId) {
        return get("/academic/dashboard/stats").with(identityFixture.signed(admin));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            registryRequest(Long adminId, String search, String status) {
        return get("/academic/groups/registry")
                .with(identityFixture.signed(admin))
                .param("status", status)
                .param("search", search);
    }

    private Long addArchivedGroup(
            String name,
            String alphabeticCode,
            String numericCode,
            Integer currentCourse,
            Integer duration,
            String durationStatus) {
        return jdbc.queryForObject(
                "INSERT INTO groups (name, is_active, created_at, archived_at, alphabetic_code, "
                        + "numeric_code, current_course, training_duration_years, duration_status) "
                        + "VALUES (?, false, NOW(), NOW(), CAST(? AS varchar), CAST(? AS varchar), "
                        + "CAST(? AS integer), CAST(? AS integer), ?) RETURNING id",
                Long.class,
                name,
                alphabeticCode,
                numericCode,
                currentCourse,
                duration,
                durationStatus);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            restoreRequest(Long userId, Long groupId, String role) {
        return post("/academic/groups/{id}/restore", groupId)
                .with(identityFixture.signed("ADMIN".equals(role) ? admin : teacher));
    }
}
