package ru.rutcampustrack.academic.user;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import java.time.Instant;
import java.util.UUID;

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
    @Autowired UserService users;
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
