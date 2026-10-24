package ru.rutcampustrack.academic.user;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.RestoreRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;
import ru.rutcampustrack.auth.session.model.AuthRole;

/**
 * End-to-end тест BUG-006-1: GET /academic/users?search=... case-insensitive фильтрация по
 * login/ФИО/telegramId.
 *
 * <p>Запускается против Testcontainers PostgreSQL (см. AbstractAcademicIntegrationTest). Каждый
 * тест использует canonical two-DB reset и реальные identity/lifecycle операции. Attendance impact
 * transport is doubled; Auth/Academic search and retained history are real.
 */
@AutoConfigureMockMvc
class UserSearchIT extends AbstractAcademicIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Autowired UserService users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired UserArchiveService archives;
    @MockitoBean AttendanceUserImpactGrpcClient attendance;

    private Long seedAdminId;
    private AcademicIdentityIntegrationFixture.SelectedActor adminActor;
    private String ivanovLogin, ivanovaLogin, petrovLogin, sidorovLogin;

    @BeforeEach
    void setUp() {
        seedAdminId = identityFixture.canonical().adminId();
        adminActor = identityFixture.select(seedAdminId, AuthRole.ADMIN);

        // Сидируем предсказуемых пользователей — имена выбраны так, чтобы НЕ
        // пересекаться с canonical fixture. Common Auth naming stems survive the profile rename.
        ivanovLogin =
                seedUser(
                                "Srch-Ivn1",
                                "Иванов",
                                "Иван",
                                "Иванович",
                                UserRole.STUDENT,
                                null,
                                611000001001L)
                        .login();
        ivanovaLogin =
                seedUser(
                                "Srch-Ivn2",
                                "Иванова",
                                "Ирина",
                                "Петровна",
                                UserRole.STUDENT,
                                null,
                                611000001002L)
                        .login();
        petrovLogin =
                seedUser(
                                "Srch-Ptrv",
                                "Петров",
                                "Пётр",
                                "Петрович",
                                UserRole.STUDENT,
                                null,
                                611000001003L)
                        .login();
        sidorovLogin =
                seedUser(
                                "Srch-Sdrv",
                                "Сидоров",
                                "Семён",
                                null,
                                UserRole.STUDENT,
                                null,
                                611000001004L)
                        .login();
        var activeTeacher =
                seedUser(
                        "Srch-Teacher-Active",
                        "Иванов",
                        "Иван",
                        "Преподаватель",
                        UserRole.STUDENT,
                        null,
                        611000001005L);
        var suspendedTeacher =
                seedUser(
                        "Srch-Teacher-Suspended",
                        "Иванов",
                        "Иван",
                        "Скрытый",
                        UserRole.TEACHER,
                        "EMP-SRCH-02",
                        null);
        try (var ignored = identityFixture.request(adminActor)) {
            users.updateRoleGrant(
                    activeTeacher.userId(),
                    "TEACHER",
                    new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE, null, "EMP-SRCH-01", null));
        }
        try (var ignored = identityFixture.request(adminActor)) {
            users.updateRoleGrant(
                    suspendedTeacher.userId(),
                    "TEACHER",
                    new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
        }
    }

    private org.springframework.test.web.servlet.ResultActions listAs(String searchParam)
            throws Exception {
        var rb =
                get("/academic/users")
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .with(signedAdmin())
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false")
                        .param("size", "100");
        if (searchParam != null) {
            rb = rb.param("search", searchParam);
        }
        return mockMvc.perform(rb);
    }

    @Test
    void archivedRegistryFilterIncludesArchivedAccountsAndRestoredRoleHistory() throws Exception {
        var archived =
                seedUser(
                        "Arclist-Match",
                        "АрхивныйПоиск",
                        "Синтетика",
                        null,
                        UserRole.STUDENT,
                        null,
                        611000002001L);
        var restored =
                seedUser(
                        "Arclist-Match",
                        "АрхивныйПоиск",
                        "Синтетика",
                        null,
                        UserRole.STUDENT,
                        null,
                        611000002002L);
        var teacher =
                seedUser(
                        "Arclist-Match",
                        "АрхивныйПоиск",
                        "Синтетика",
                        null,
                        UserRole.TEACHER,
                        "EMP-ARCLIST-2003",
                        null);
        var suspended =
                seedUser(
                        "Arclist-Match",
                        "АрхивныйПоиск",
                        "Синтетика",
                        null,
                        UserRole.STUDENT,
                        null,
                        611000002004L);
        archive(archived.userId());
        archive(restored.userId());
        var restore = new RestoreRequest(UUID.randomUUID());
        try (var ignored = identityFixture.request(adminActor, restore.operationId())) {
            archives.restore(restored.userId(), restore);
        }
        archive(teacher.userId());
        try (var ignored = identityFixture.request(adminActor)) {
            users.updateRoleGrant(
                    suspended.userId(),
                    "STUDENT",
                    new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
        }

        // Both an archived account and a restored ACTIVE account retain archived grants.
        // Equal names require the ID tie-breaker; the unrelated role/status must not inflate count.
        for (int page = 0; page < 2; page++) {
            mockMvc.perform(
                            get("/academic/users")
                                    .header("X-User-Id", seedAdminId)
                                    .header("X-User-Role", "ADMIN")
                                    .with(signedAdmin())
                                    .param("search", "ARCLIST-MATCH")
                                    .param("role", "STUDENT")
                                    .param("roleStatus", "ARCHIVED")
                                    .param("sort", "lastName,asc", "firstName,asc")
                                    .param("page", String.valueOf(page))
                                    .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements", is(2)))
                    .andExpect(jsonPath("$.page.totalPages", is(2)))
                    .andExpect(
                            jsonPath(
                                    "$._embedded.userResponseList[0].login",
                                    is(page == 0 ? archived.login() : restored.login())));
        }
        mockMvc.perform(
                        get("/academic/users")
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(signedAdmin())
                                .param("search", "arclist-match")
                                .param("status", "ARCHIVED")
                                .param("sort", "login,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(2)))
                .andExpect(
                        jsonPath(
                                "$._embedded.userResponseList[*].login",
                                contains(archived.login(), teacher.login())));
        listAs("arclist-match")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(2)))
                .andExpect(
                        jsonPath(
                                "$._embedded.userResponseList[*].login",
                                containsInAnyOrder(restored.login(), suspended.login())));
        mockMvc.perform(
                        get("/academic/users")
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(signedAdmin())
                                .param("search", "arclist%match")
                                .param("role", "STUDENT")
                                .param("roleStatus", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(0)));
        var restoredTeacher =
                seedUser(
                        "Arclist-Match",
                        "АрхивныйПоиск",
                        "Синтетика",
                        null,
                        UserRole.TEACHER,
                        "EMP-ARCLIST-2005",
                        null);
        var activeTeacher =
                seedUser(
                        "Arclist-Match",
                        "АрхивныйПоиск",
                        "Синтетика",
                        null,
                        UserRole.TEACHER,
                        "EMP-ARCLIST-2006",
                        null);
        var suspendedTeacher =
                seedUser(
                        "Arclist-Match",
                        "АрхивныйПоиск",
                        "Синтетика",
                        null,
                        UserRole.TEACHER,
                        "EMP-ARCLIST-2007",
                        null);
        archive(restoredTeacher.userId());
        var teacherRestore = new RestoreRequest(UUID.randomUUID());
        try (var ignored = identityFixture.request(adminActor, teacherRestore.operationId())) {
            archives.restore(restoredTeacher.userId(), teacherRestore);
        }
        try (var ignored = identityFixture.request(adminActor)) {
            users.updateRoleGrant(
                    suspendedTeacher.userId(),
                    "TEACHER",
                    new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
        }
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", adminActor.internalToken());
        var seen = new java.util.ArrayList<Long>();
        for (int page = 0; page < 2; page++) {
            var response =
                    http.exchange(
                            "/academic/users?search=ARCLIST-MATCH&role=TEACHER&roleStatus=ARCHIVED&sort=id,asc&size=1&page="
                                    + page,
                            org.springframework.http.HttpMethod.GET,
                            new org.springframework.http.HttpEntity<>(headers),
                            String.class);
            org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                    .isEqualTo(org.springframework.http.HttpStatus.OK);
            var json = mapper.readTree(response.getBody());
            org.assertj.core.api.Assertions.assertThat(
                            json.path("page").path("totalElements").asLong())
                    .isEqualTo(2);
            org.assertj.core.api.Assertions.assertThat(json.path("page").path("totalPages").asInt())
                    .isEqualTo(2);
            var rows = json.path("_embedded").path("userResponseList");
            org.assertj.core.api.Assertions.assertThat(rows.size()).isEqualTo(1);
            seen.add(rows.get(0).path("id").asLong());
        }
        org.assertj.core.api.Assertions.assertThat(seen)
                .containsExactly(teacher.userId(), restoredTeacher.userId())
                .doesNotContain(
                        activeTeacher.userId(),
                        suspendedTeacher.userId(),
                        archived.userId(),
                        restored.userId());
    }

    @Test
    void groupFilterCouplesStudentGrantAndPagesMixedRoleAccountsBeforeCounting() throws Exception {
        long group = identityFixture.canonical().groupId();
        long otherGroup = newGroup();
        var mixed =
                seedUser(
                        "Groupfilter-Mixed",
                        "Одинаковый",
                        "Пользователь",
                        null,
                        UserRole.STUDENT,
                        null,
                        611000004001L);
        var transferred =
                seedUser(
                        "Groupfilter-Transferred",
                        "Одинаковый",
                        "Пользователь",
                        null,
                        UserRole.STUDENT,
                        null,
                        611000004002L);
        var suspended =
                seedUser(
                        "Groupfilter-Suspended",
                        "Одинаковый",
                        "Пользователь",
                        null,
                        UserRole.STUDENT,
                        null,
                        611000004003L);
        asAdmin(
                () ->
                        users.patchUser(
                                mixed.userId(),
                                new PatchUserRequest(
                                        null, null, null, true, null, null, null, null)));
        asAdmin(
                () ->
                        users.updateRoleGrant(
                                mixed.userId(),
                                "TEACHER",
                                new RoleGrantUpdateRequest(
                                        RoleGrantStatus.ACTIVE, null, "GF-MIXED", null)));
        asAdmin(
                () ->
                        users.updateRoleGrant(
                                transferred.userId(),
                                "TEACHER",
                                new RoleGrantUpdateRequest(
                                        RoleGrantStatus.ACTIVE, null, "GF-TRANSFER", null)));
        asAdmin(
                () ->
                        users.transferStudent(
                                transferred.userId(),
                                new ru.rutcampustrack.academic.contract.dto.user
                                        .TransferStudentRequest(
                                        otherGroup, "Проверка актуального фильтра")));
        asAdmin(
                () ->
                        users.updateRoleGrant(
                                suspended.userId(),
                                "STUDENT",
                                new RoleGrantUpdateRequest(
                                        RoleGrantStatus.SUSPENDED, null, null, null)));
        asAdmin(
                () ->
                        users.updateRoleGrant(
                                suspended.userId(),
                                "TEACHER",
                                new RoleGrantUpdateRequest(
                                        RoleGrantStatus.ACTIVE, null, "GF-SUSPENDED", null)));
        // The old group remains in dated history; it must not satisfy this registry selector.
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id=? AND"
                                        + " group_id=?",
                                Long.class,
                                transferred.userId(),
                                group))
                .isPositive();
        org.assertj.core.api.Assertions.assertThat(
                        rowIds(
                                groupGet(
                                        adminActor,
                                        "search=Groupfilter&groupId="
                                                + group
                                                + "&role=HEADMAN&roleStatus=ACTIVE")))
                .containsExactly(mixed.userId());
        var active =
                groupGet(
                        adminActor,
                        "search=Groupfilter&groupId=" + group + "&role=STUDENT&roleStatus=ACTIVE");
        org.assertj.core.api.Assertions.assertThat(rowIds(active)).containsExactly(mixed.userId());
        org.assertj.core.api.Assertions.assertThat(
                        rowIds(
                                groupGet(
                                        adminActor,
                                        "search=Groupfilter&groupId="
                                                + group
                                                + "&role=TEACHER&roleStatus=ACTIVE")))
                .containsExactly(mixed.userId(), suspended.userId());
        org.assertj.core.api.Assertions.assertThat(
                        rowIds(
                                groupGet(
                                        adminActor,
                                        "search=Groupfilter&groupId="
                                                + otherGroup
                                                + "&role=STUDENT&roleStatus=ACTIVE")))
                .containsExactly(transferred.userId());
        for (int page = 0; page < 2; page++) {
            var json =
                    groupGet(
                            adminActor,
                            "search=Groupfilter&groupId=" + group + "&size=1&page=" + page);
            org.assertj.core.api.Assertions.assertThat(
                            json.path("page").path("totalElements").asLong())
                    .isEqualTo(2);
            org.assertj.core.api.Assertions.assertThat(json.path("page").path("totalPages").asInt())
                    .isEqualTo(2);
            org.assertj.core.api.Assertions.assertThat(rowIds(json))
                    .containsExactly(page == 0 ? mixed.userId() : suspended.userId());
        }
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", adminActor.internalToken());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var exported =
                http.exchange(
                        "/academic/registry-exports/users",
                        org.springframework.http.HttpMethod.POST,
                        new org.springframework.http.HttpEntity<>(
                                "{\"search\":\"Groupfilter\",\"groupId\":"
                                        + group
                                        + ",\"role\":\"STUDENT\",\"roleStatus\":\"ACTIVE\",\"format\":\"html\"}",
                                headers),
                        String.class);
        org.assertj.core.api.Assertions.assertThat(exported.getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.OK);
        org.assertj.core.api.Assertions.assertThat(
                        exported.getHeaders().getFirst("X-Export-Row-Count"))
                .isEqualTo("1");
        org.assertj.core.api.Assertions.assertThat(exported.getBody())
                .contains(mixed.login())
                .doesNotContain(transferred.login(), suspended.login());
    }

    @Test
    void groupFilterRetainsArchivedLinksWithSameGrantStatusAndAccountStatus() throws Exception {
        long group = identityFixture.canonical().groupId();
        var archived =
                seedUser(
                        "Groupretain-Archived",
                        "Архив",
                        "Связь",
                        null,
                        UserRole.STUDENT,
                        null,
                        611000004004L);
        var restored =
                seedUser(
                        "Groupretain-Restored",
                        "Архив",
                        "Связь",
                        null,
                        UserRole.STUDENT,
                        null,
                        611000004005L);
        archive(archived.userId());
        archive(restored.userId());
        var request = new RestoreRequest(UUID.randomUUID());
        try (var ignored = identityFixture.request(adminActor, request.operationId())) {
            archives.restore(restored.userId(), request);
        }
        org.assertj.core.api.Assertions.assertThat(
                        rowIds(
                                groupGet(
                                        adminActor,
                                        "search=Groupretain&groupId="
                                                + group
                                                + "&role=STUDENT&roleStatus=ARCHIVED&sort=id,asc")))
                .containsExactly(archived.userId(), restored.userId());
        org.assertj.core.api.Assertions.assertThat(
                        rowIds(
                                groupGet(
                                        adminActor,
                                        "search=Groupretain&groupId="
                                                + group
                                                + "&status=ARCHIVED")))
                .containsExactly(archived.userId());
        org.assertj.core.api.Assertions.assertThat(
                        rowIds(
                                groupGet(
                                        adminActor,
                                        "search=Groupretain&groupId="
                                                + newGroup()
                                                + "&role=STUDENT&roleStatus=ARCHIVED")))
                .isEmpty();
    }

    @Test
    void groupFilterRejectsInvalidIdsAndPreservesPermissionAndExistingRoleStatusGuard()
            throws Exception {
        for (String query :
                java.util.List.of(
                        "groupId=0",
                        "groupId=-1",
                        "groupId=1.5",
                        "groupId=nope",
                        "groupId=1&roleStatus=ACTIVE")) {
            org.assertj.core.api.Assertions.assertThat(
                            groupResponse(adminActor, query).getStatusCode())
                    .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        var unknown = groupGet(adminActor, "groupId=9223372036854775807");
        org.assertj.core.api.Assertions.assertThat(
                        unknown.path("page").path("totalElements").asLong())
                .isZero();
        var limitedId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Groupfilter",
                                        "Limited",
                                        null,
                                        UserRole.ADMIN,
                                        null,
                                        null,
                                        null,
                                        new ru.rutcampustrack.academic.contract.dto.user
                                                .AdminAccessInput(
                                                "LIMITED", java.util.List.of("GROUPS_VIEW"))),
                                adminActor)
                        .userId();
        var limited = identityFixture.select(limitedId, AuthRole.ADMIN);
        org.assertj.core.api.Assertions.assertThat(
                        groupResponse(limited, "groupId=" + identityFixture.canonical().groupId())
                                .getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        var student =
                identityFixture.select(identityFixture.canonical().studentId(), AuthRole.STUDENT);
        org.assertj.core.api.Assertions.assertThat(
                        groupResponse(student, "groupId=" + identityFixture.canonical().groupId())
                                .getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    private void asAdmin(Runnable command) {
        try (var ignored = identityFixture.request(adminActor)) {
            command.run();
        }
    }

    private long newGroup() {
        long group =
                jdbc.queryForObject(
                        "INSERT INTO"
                                + " groups(name,is_active,alphabetic_code,numeric_code,current_course,training_duration_years,duration_status)"
                                + " VALUES('ФЛТ-211',TRUE,'ФЛТ','211',2,4,'KNOWN') RETURNING id",
                        Long.class);
        jdbc.update(
                "INSERT INTO"
                        + " group_history_coverage(group_id,coverage_from,writer_version,created_at)"
                        + " VALUES(?,'2026-02-01',?,NOW())",
                group,
                ru.rutcampustrack.academic.history.HistoricalMembershipService.WRITER_VERSION);
        return group;
    }

    private org.springframework.http.ResponseEntity<String> groupResponse(
            AcademicIdentityIntegrationFixture.SelectedActor actor, String query) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", actor.internalToken());
        return http.exchange(
                "/academic/users?" + query,
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);
    }

    private com.fasterxml.jackson.databind.JsonNode groupGet(
            AcademicIdentityIntegrationFixture.SelectedActor actor, String query) throws Exception {
        var response = groupResponse(actor, query);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }

    private java.util.List<Long> rowIds(com.fasterxml.jackson.databind.JsonNode json) {
        var result = new java.util.ArrayList<Long>();
        json.path("_embedded")
                .path("userResponseList")
                .forEach(row -> result.add(row.path("id").asLong()));
        return result;
    }

    @Test
    void search_caseInsensitive_filtersByLastName() throws Exception {
        listAs("иван")
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$._embedded.userResponseList[*].login",
                                hasItems(ivanovLogin, ivanovaLogin)))
                .andExpect(
                        jsonPath(
                                "$._embedded.userResponseList[*].login",
                                not(hasItem(petrovLogin))));
    }

    @Test
    void search_uppercase_matchesSameResultsAsLowercase() throws Exception {
        listAs("ИВАН")
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$._embedded.userResponseList[*].login",
                                hasItems(ivanovLogin, ivanovaLogin)));
    }

    @Test
    void search_blank_returnsAllUsers() throws Exception {
        // Пустой search = без фильтра. Должны увидеть seed-админа плюс 4 наших
        // тестовых + другие seed-юзеров.
        listAs("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(5)));
    }

    @Test
    void search_noMatch_returnsEmptyPage() throws Exception {
        listAs("xyzNoSuchUserExists")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    void teacherLookup_usesFullNameActiveGrantAndSafePagedDto() throws Exception {
        var student =
                identityFixture.select(identityFixture.canonical().studentId(), AuthRole.STUDENT);

        mockMvc.perform(
                        get("/academic/users/teachers/search")
                                .with(identityFixture.signed(student))
                                .param("search", "Иванов Иван")
                                .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(
                        jsonPath(
                                "$._embedded.teacherLookupResponseList[0].fullName",
                                is("Иванов Иван Преподаватель")))
                .andExpect(
                        jsonPath(
                                "$._embedded.teacherLookupResponseList[0].employeeNumber",
                                is("EMP-SRCH-01")))
                .andExpect(
                        jsonPath("$._embedded.teacherLookupResponseList[0].login").doesNotExist())
                .andExpect(
                        jsonPath("$._embedded.teacherLookupResponseList[0].roles").doesNotExist());
    }

    @Test
    void search_byLoginPrefix_matchesCaseInsensitive() throws Exception {
        // Note: "_" is escaped as literal per T-58-01-01 (pattern injection guard).
        // Used "SRCH" (no underscore) → должен совпасть с префиксом всех четырёх seed-логинов.
        listAs("SRCH")
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$._embedded.userResponseList[*].login",
                                hasItems(ivanovLogin, ivanovaLogin, petrovLogin, sidorovLogin)));
    }

    @Test
    void search_underscoreIsEscaped_doesNotActAsWildcard() throws Exception {
        // "abc_" не должно матчить "abc" + любой символ — "_" экранирован.
        // Ни один seed-login не содержит литерального "xyz_" → пусто.
        // An actual stored name would match if '_' were treated as a wildcard.
        seedUser(
                "Wildcard-Control",
                "xyzXabc",
                "Контроль",
                null,
                UserRole.STUDENT,
                null,
                611000003001L);
        listAs("xyz_abc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    private AcademicIdentityIntegrationFixture.SeededUser seedUser(
            String stem,
            String lastName,
            String firstName,
            String middleName,
            UserRole role,
            String employee,
            Long telegram) {
        String[] naming = stem.split("-", 2);
        var created =
                identityFixture.seedUser(
                        new CreateUserRequest(
                                naming[0],
                                naming.length == 2 ? naming[1] : "Fixture",
                                null,
                                role,
                                role == UserRole.STUDENT
                                        ? identityFixture.canonical().groupId()
                                        : null,
                                employee,
                                telegram),
                        adminActor);
        try (var ignored = identityFixture.request(adminActor)) {
            users.patchUser(
                    created.userId(),
                    new PatchUserRequest(
                            lastName,
                            firstName,
                            middleName == null ? "" : middleName,
                            null,
                            null,
                            null,
                            null,
                            null));
        }
        return created;
    }

    private void archive(long user) {
        when(attendance.preview(user))
                .thenReturn(
                        UserImpactSnapshot.newBuilder()
                                .setUserId(user)
                                .setObservedAtEpochMs(Instant.now().toEpochMilli())
                                .setSnapshotDigest("b".repeat(64))
                                .build());
        var preview = preview(user);
        var request =
                new ArchiveRequest(
                        UUID.randomUUID(),
                        preview.previewDigest(),
                        preview.requiresPassword()
                                ? AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD
                                : null);
        try (var ignored = identityFixture.request(adminActor, request.operationId())) {
            archives.archive(user, request);
        }
    }

    private ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.Preview preview(
            long user) {
        try (var ignored = identityFixture.request(adminActor)) {
            return archives.preview(user);
        }
    }
}
