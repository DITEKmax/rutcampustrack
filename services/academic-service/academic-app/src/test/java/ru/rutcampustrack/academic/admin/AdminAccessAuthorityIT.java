package ru.rutcampustrack.academic.admin;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.ErrorResponseException;

import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.*;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.repository.*;
import ru.rutcampustrack.academic.semester.SemesterArchiveService;
import ru.rutcampustrack.academic.user.*;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.security.admin.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Current authority cases use the common actual two-DB/Auth-TLS fixture; no shared authority SQL.
 */
@AutoConfigureMockMvc
class AdminAccessAuthorityIT extends AbstractAcademicIntegrationTest {
    @Autowired AdminAccessService service;
    @Autowired UserService users;
    @Autowired UserAssembler assembler;
    @Autowired UserRepository userRows;
    @Autowired GroupHeadmanAssignmentService headmen;
    @Autowired SemesterArchiveService semesters;
    @Autowired SemesterArchiveOperationRepository semesterOperations;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    AuthIdentityDataClient identityData;

    @Autowired AcademicIdentityCoordinator coordinator;
    @Autowired UserArchiveService archives;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AttendanceUserImpactGrpcClient attendanceImpact;
    private JdbcTemplate auth;
    private JdbcTemplate academic;
    private SelectedActor first;
    private long firstId, secondId, revokedId, teacherId, studentId, otherTeacherId, group;

    @BeforeEach
    void authorityFixture() {
        auth = identityFixture.authJdbc();
        academic = identityFixture.academicJdbc();
        var canonical = identityFixture.canonical();
        firstId = canonical.adminId();
        first = identityFixture.select(firstId, AuthRole.ADMIN);
        group = canonical.groupId();
        teacherId = canonical.teacherId();
        secondId = seed(UserRole.ADMIN, "Second", new AdminAccessInput("FULL", List.of()));
        revokedId =
                seed(
                        UserRole.ADMIN,
                        "Revoked",
                        new AdminAccessInput("LIMITED", List.of("USERS_VIEW")));
        revoke(first, revokedId, 1);
        studentId = seed(UserRole.STUDENT, "Student", null);
        otherTeacherId = seed(UserRole.TEACHER, "Other", null);
        // This existing suite does not claim Attendance effects; protected archive still uses
        // actual Academic preview/digest and actual Auth password receipt/identity operation.
        org.mockito.Mockito.when(attendanceImpact.preview(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(
                        call ->
                                ru.rutcampustrack.attendance.grpc.UserImpactSnapshot.newBuilder()
                                        .setUserId(call.<Long>getArgument(0))
                                        .setObservedAtEpochMs(System.currentTimeMillis())
                                        .setSnapshotDigest("11".repeat(32))
                                        .build());
    }

    private long seed(UserRole role, String suffix, AdminAccessInput profile) {
        return identityFixture
                .seedUser(
                        new CreateUserRequest(
                                "Synthetic",
                                suffix,
                                null,
                                role,
                                role == UserRole.STUDENT ? group : null,
                                role == UserRole.TEACHER
                                        ? "E"
                                                + UUID.randomUUID()
                                                        .toString()
                                                        .replace("-", "")
                                                        .substring(0, 12)
                                        : null,
                                role == UserRole.STUDENT ? 900000000222L : null,
                                profile),
                        first)
                .userId();
    }

    private <T> T as(SelectedActor actor, Supplier<T> action) {
        try (var ignored = identityFixture.request(actor)) {
            return action.get();
        }
    }

    private AdminAccessProfile put(
            long target, String mode, List<String> permissions, long version) {
        return as(
                first, () -> service.put(target, new AdminAccessInput(mode, permissions), version));
    }

    private void revoke(SelectedActor actor, long target, long version) {
        as(
                actor,
                () -> {
                    service.revoke(target, version);
                    return null;
                });
    }

    /**
     * An assertion-side read of Auth-owned storage, never a request permission/admission oracle.
     */
    private AdminAccessProfile storedProfile(long target) {
        var rows =
                auth.query(
                        "SELECT assigned,mode,permissions,version FROM admin_access_profiles WHERE"
                                + " user_id=?",
                        (rs, row) -> {
                            boolean assigned = rs.getBoolean("assigned");
                            String mode = rs.getString("mode");
                            String[] permissions = (String[]) rs.getArray("permissions").getArray();
                            return new AdminAccessProfile(
                                    target,
                                    assigned,
                                    mode,
                                    assigned && "FULL".equals(mode)
                                            ? AdminPermission.allCodes()
                                            : Arrays.asList(permissions),
                                    rs.getLong("version"));
                        },
                        target);
        return rows.isEmpty()
                ? new AdminAccessProfile(target, false, null, List.of(), 0)
                : rows.getFirst();
    }

    private long roleCount(long user, String role, String status) {
        return auth.queryForObject(
                "SELECT count(*) FROM user_role_grants WHERE user_id=? AND role=? AND status=?",
                Long.class,
                user,
                role,
                status);
    }

    private long rolesVersion(long user) {
        return auth.queryForObject(
                "SELECT roles_version FROM auth_identities WHERE id=?", Long.class, user);
    }

    private List<JsonNode> facts(long target) {
        return auth.query(
                "SELECT payload::text FROM auth_outbox WHERE event_type='admin.access.changed'"
                        + " AND (payload->'payload'->>'target_user_id')::bigint=? ORDER BY id",
                (rs, row) -> {
                    try {
                        return mapper.readTree(rs.getString(1));
                    } catch (java.io.IOException failure) {
                        throw new IllegalStateException(failure);
                    }
                },
                target);
    }

    @Test
    void limitedAssignmentRoundTripsValidatesDependenciesAndNeverDelegatesFull() {
        var limited = put(teacherId, "LIMITED", List.of("USERS_VIEW", "USERS_MANAGE"), 0);
        assertThat(limited.permissions()).containsExactly("USERS_VIEW", "USERS_MANAGE");
        assertThat(limited.full()).isFalse();
        assertThat(storedProfile(teacherId)).isEqualTo(limited);
        var event = facts(teacherId).getFirst();
        assertThat(event.path("source").asText()).isEqualTo("auth-service");
        var fact = event.path("payload");
        assertThat(fact.path("target_user_id").asLong()).isEqualTo(teacherId);
        assertThat(fact.path("permissions")).isEqualTo(mapper.valueToTree(limited.permissions()));
        assertThat(fact.path("version").asLong()).isEqualTo(limited.version());
        assertThat(fact.path("permissions_changed").asBoolean()).isTrue();
        assertThat(fact.path("actor_user_id").asLong()).isEqualTo(firstId);
        long beforeRv = rolesVersion(teacherId);
        put(teacherId, "LIMITED", List.of("USERS_VIEW", "USERS_MANAGE"), limited.version());
        assertThat(facts(teacherId)).hasSize(1);
        assertThat(rolesVersion(teacherId)).isEqualTo(beforeRv);
        assertThat(roleCount(teacherId, "teacher", "active")).isOne();
        var limitedActor = identityFixture.select(teacherId, AuthRole.ADMIN);
        denied(
                () ->
                        as(
                                limitedActor,
                                () ->
                                        service.put(
                                                otherTeacherId,
                                                new AdminAccessInput("FULL", List.of()),
                                                0L)));
        for (var permissions :
                List.of(
                        List.of("USERS_MANAGE"),
                        List.of("USERS_VIEW", "USERS_VIEW"),
                        List.of("ADMIN_ACCESS_MANAGE"),
                        List.<String>of()))
            assertThatThrownBy(() -> put(otherTeacherId, "LIMITED", permissions, 0))
                    .isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> put(otherTeacherId, "FULL", List.of("USERS_VIEW"), 0))
                .isInstanceOf(AdminAccessException.class);
    }

    @Test
    void staleCasAndRevokeRegrantDoNotResurrectSelectionOrOtherRoleLogin() {
        put(teacherId, "LIMITED", List.of("USERS_VIEW"), 0);
        var selected = identityFixture.select(teacherId, AuthRole.ADMIN);
        revoke(first, teacherId, 1);
        assertThat(storedProfile(teacherId).version()).isEqualTo(2);
        assertThat(
                        auth.queryForObject(
                                "SELECT active_role_grant_id IS NULL FROM auth_sessions WHERE"
                                        + " sid=?",
                                Boolean.class,
                                selected.sessionId()))
                .isTrue();
        assertThat(
                        auth.queryForObject(
                                "SELECT revoked_at IS NULL FROM auth_sessions WHERE sid=?",
                                Boolean.class,
                                selected.sessionId()))
                .isTrue();
        assertThat(roleCount(teacherId, "teacher", "active")).isOne();
        put(teacherId, "FULL", List.of(), 2);
        assertThat(storedProfile(teacherId).version()).isEqualTo(3);
        assertThatThrownBy(() -> revoke(first, teacherId, 1))
                .isInstanceOfSatisfying(
                        AdminAccessException.class,
                        failure ->
                                assertThat(failure.getBody().getProperties())
                                        .containsKey("currentProfile"));
        assertThat(
                        auth.queryForObject(
                                "SELECT active_role_grant_id IS NULL FROM auth_sessions WHERE"
                                        + " sid=?",
                                Boolean.class,
                                selected.sessionId()))
                .isTrue();
        stale(selected);
    }

    @Test
    void credentialsAndActiveAdminTargetProtectionApplyToMixedRoleAccountsAndSelf() {
        put(
                teacherId,
                "LIMITED",
                List.of("USERS_VIEW", "USERS_MANAGE", "USER_CREDENTIALS_MANAGE", "USERS_ARCHIVE"),
                0);
        var limited = identityFixture.select(teacherId, AuthRole.ADMIN);
        assertThat(
                        as(
                                limited,
                                () ->
                                        identityData
                                                .initialCredentials(List.of(studentId), deadline())
                                                .getCredentialsCount()))
                .isOne();
        denied(
                () ->
                        as(
                                limited,
                                () ->
                                        identityData.initialCredentials(
                                                List.of(secondId), deadline())));
        denied(
                () ->
                        as(
                                limited,
                                () ->
                                        identityData.initialCredentials(
                                                List.of(teacherId), deadline())));
        for (long target : List.of(teacherId, secondId))
            denied(
                    () ->
                            as(
                                    limited,
                                    () ->
                                            users.patchUser(
                                                    target,
                                                    new PatchUserRequest(
                                                            "Forbidden",
                                                            null,
                                                            null,
                                                            null,
                                                            null,
                                                            null,
                                                            null,
                                                            null))));
        denied(() -> put(firstId, "LIMITED", List.of("USERS_VIEW"), 1));
    }

    @Test
    void plainJsonApiRoundTripsAndRejectsDirectLimitedMutationAndStaleCas() throws Exception {
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/academic/users/{id}/admin-access", teacherId)
                                .with(identityFixture.signed(first, UUID.randomUUID()))
                                .contentType("application/json")
                                .content(
                                        "{\"mode\":\"LIMITED\",\"permissions\":[\"USERS_VIEW\"],\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(
                        get("/academic/users/{id}/admin-access", teacherId)
                                .with(identityFixture.signed(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("USERS_VIEW"));
        mvc.perform(
                        delete("/academic/users/{id}/admin-access", teacherId)
                                .with(identityFixture.signed(first, UUID.randomUUID()))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_CONFLICT"))
                .andExpect(jsonPath("$.currentProfile.version").value(1));
        var limited = identityFixture.select(teacherId, AuthRole.ADMIN);
        mvc.perform(get("/academic/admin-access/me").with(identityFixture.signed(limited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIMITED"));
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/academic/users/{id}/admin-access", otherTeacherId)
                                .with(identityFixture.signed(limited, UUID.randomUUID()))
                                .contentType("application/json")
                                .content(
                                        "{\"mode\":\"FULL\",\"permissions\":[],\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_FULL_REQUIRED"));
        assertThat(storedProfile(otherTeacherId).assigned()).isFalse();
    }

    @Test
    void unknownAndNullPermissionElementsAreRejectedWithoutAuthorityMutation() throws Exception {
        String before = authorityRows();
        for (String permissions :
                List.of("[\"USERS_VIEW\",\"UNKNOWN\"]", "[\"USERS_VIEW\",null]")) {
            mvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                            "/academic/users/{id}/admin-access", teacherId)
                                    .with(identityFixture.signed(first, UUID.randomUUID()))
                                    .contentType("application/json")
                                    .content(
                                            "{\"mode\":\"LIMITED\",\"permissions\":"
                                                    + permissions
                                                    + ",\"expectedVersion\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ADMIN_ACCESS"));
            assertThat(authorityRows())
                    .as("Invalid permission elements preserve both owners' authority rows")
                    .isEqualTo(before);
            assertThat(facts(teacherId)).isEmpty();
        }
        put(teacherId, "LIMITED", List.of("USERS_VIEW"), 0);
        assertThat(storedProfile(teacherId).permissions()).containsExactly("USERS_VIEW");
        assertThat(storedProfile(teacherId).version()).isEqualTo(1);
        assertThat(roleCount(teacherId, "admin", "active")).isOne();
        assertThat(authorityRows())
                .as("Positive control commits a durable authority transition")
                .isNotEqualTo(before);
        assertThat(facts(teacherId)).hasSize(1);
    }

    @Test
    void everyAssemblerCredentialProjectionUsesFreshCapabilityAndTargetProtection() {
        put(teacherId, "LIMITED", List.of("USERS_VIEW", "USERS_MANAGE"), 0);
        var student = users.findUserById(studentId);
        String initial =
                as(
                        first,
                        () ->
                                identityData
                                        .initialCredentials(List.of(studentId), deadline())
                                        .getCredentials(0)
                                        .getInitialPassword());
        assertThat(initial).isNotBlank();
        assertThat(
                        as(
                                first,
                                () ->
                                        users.createUser(
                                                        new CreateUserRequest(
                                                                "Synthetic",
                                                                "FullCreated",
                                                                null,
                                                                UserRole.TEACHER,
                                                                null,
                                                                "E-FULL-CREATED",
                                                                null))
                                                .getContent()
                                                .getInitialPassword()))
                .isNotBlank();
        assertThat(
                        as(
                                first,
                                () ->
                                        assembler
                                                .toAdminModel(student)
                                                .getContent()
                                                .getInitialPassword()))
                .isEqualTo(initial);
        var unprivileged = identityFixture.select(teacherId, AuthRole.ADMIN);
        assertThat(
                        as(
                                unprivileged,
                                () ->
                                        assembler
                                                .toAdminModel(student)
                                                .getContent()
                                                .getInitialPassword()))
                .isNull();
        var create =
                new CreateUserRequest(
                        "Synthetic",
                        "LimitedCreated",
                        null,
                        UserRole.TEACHER,
                        null,
                        "E-LIMITED",
                        null);
        assertThat(
                        as(
                                unprivileged,
                                () -> users.createUser(create).getContent().getInitialPassword()))
                .isNull();
        put(teacherId, "LIMITED", List.of("USERS_VIEW", "USER_CREDENTIALS_MANAGE"), 1);
        var permitted = identityFixture.select(teacherId, AuthRole.ADMIN);
        assertThat(
                        as(
                                permitted,
                                () ->
                                        assembler
                                                .toAdminModel(student)
                                                .getContent()
                                                .getInitialPassword()))
                .isEqualTo(initial);
        assertThat(
                        as(
                                permitted,
                                () ->
                                        assembler
                                                .toAdminModel(users.findUserById(secondId))
                                                .getContent()
                                                .getInitialPassword()))
                .isNull();
        student.setPasswordChanged(true);
        assertThat(
                        as(
                                permitted,
                                () ->
                                        assembler
                                                .toAdminModel(student)
                                                .getContent()
                                                .getInitialPassword()))
                .isNull();
    }

    @Test
    void groupPickerIncludesDraftAndExcludesArchivedWithoutRegistryMetadata() {
        identityFixture
                .fixtureOwnerAcademicJdbc()
                .update(
                        "INSERT INTO groups(name,archived_at) VALUES"
                            + " ('Тест-2',NULL),('Тест-1',NULL),('Тест-старый',NOW()),('Другой',NULL)");
        var picker = new ru.rutcampustrack.academic.group.GroupOptionsController(academic);
        var page = picker.options("тест", 0, 1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items().getFirst().name()).isEqualTo("Тест-1");
        assertThat(picker.options("тест", 1, 1).items().getFirst().name()).isEqualTo("Тест-2");
        assertThatThrownBy(() -> picker.options("", 0, 101))
                .isInstanceOf(AdminAccessException.class);
    }

    @Test
    void canonicalHeadmanAssignmentProtectsBothCandidateAndPreviousActiveAdmin() {
        as(
                first,
                () ->
                        users.updateRoleGrant(
                                teacherId,
                                "STUDENT",
                                new RoleGrantUpdateRequest(
                                        RoleGrantStatus.ACTIVE, group, null, 900000000111L)));
        put(teacherId, "LIMITED", List.of("USERS_VIEW"), 0);
        put(otherTeacherId, "LIMITED", List.of("GROUPS_VIEW", "GROUPS_MANAGE"), 0);
        var limited = identityFixture.select(otherTeacherId, AuthRole.ADMIN);
        long previous = identityFixture.canonical().studentId();
        denied(
                () ->
                        as(
                                limited,
                                () ->
                                        headmen.assign(
                                                group,
                                                new AssignHeadmanRequest(teacherId, previous))));
        assertThat(userRows.findActiveHeadmanIdsByGrantGroupId(group)).containsExactly(previous);
        as(first, () -> headmen.assign(group, new AssignHeadmanRequest(teacherId, previous)));
        denied(
                () ->
                        as(
                                limited,
                                () ->
                                        headmen.assign(
                                                group,
                                                new AssignHeadmanRequest(studentId, teacherId))));
        assertThat(userRows.findActiveHeadmanIdsByGrantGroupId(group)).containsExactly(teacherId);
        assertThat(users.findUserById(studentId).isHeadman()).isFalse();
    }

    @Test
    void userHeadmanPatchRequiresGroupsManageBeforeAnyMutation() {
        put(teacherId, "LIMITED", List.of("USERS_VIEW", "USERS_MANAGE"), 0);
        var limited = identityFixture.select(teacherId, AuthRole.ADMIN);
        long version = rolesVersion(studentId);
        String before = domainRows();
        for (boolean assign : List.of(true, false))
            denied(
                    () ->
                            as(
                                    limited,
                                    () ->
                                            users.patchUser(
                                                    studentId,
                                                    new PatchUserRequest(
                                                            null, null, null, assign, null, null,
                                                            null, null))));
        assertThat(domainRows()).isEqualTo(before);
        assertThat(rolesVersion(studentId)).isEqualTo(version);
        assertThat(roleCount(studentId, "headman", "active")).isZero();
    }

    @Test
    void ownTeacherRoleChangeCommitsAndProjectsSuccessWithoutCredentialsFromStaleClaims()
            throws Exception {
        as(
                first,
                () ->
                        users.updateRoleGrant(
                                firstId,
                                "TEACHER",
                                new RoleGrantUpdateRequest(
                                        RoleGrantStatus.ACTIVE, null, "E-SELF", null)));
        first = identityFixture.select(firstId, AuthRole.ADMIN);
        var original = first;
        long before = original.rolesVersion();
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/academic/users/{id}/roles/TEACHER", firstId)
                                .with(identityFixture.signed(original, UUID.randomUUID()))
                                .contentType("application/json")
                                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialPassword").doesNotExist());
        assertThat(roleCount(firstId, "teacher", "suspended")).isOne();
        assertThat(rolesVersion(firstId)).isGreaterThan(before);
        assertThat(
                        auth.queryForObject(
                                "SELECT g.role FROM auth_sessions s JOIN user_role_grants g ON"
                                        + " g.id=s.active_role_grant_id WHERE s.sid=?",
                                String.class,
                                original.sessionId()))
                .isEqualTo("admin");
        stale(original);
    }

    @Test
    void genericSemesterStatusAndOperationCannotContinueDeleteWithoutDataDelete() {
        put(teacherId, "LIMITED", List.of("SEMESTERS_VIEW", "SEMESTERS_MANAGE"), 0);
        var limited = identityFixture.select(teacherId, AuthRole.ADMIN);
        var operation =
                new ru.rutcampustrack.academic.entity.SemesterArchiveOperation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        firstId,
                        identityFixture.canonical().semesterId(),
                        SemesterArchiveAction.DELETE);
        identityFixture
                .academicTransactions()
                .executeWithoutResult(status -> semesterOperations.saveAndFlush(operation));
        denied(() -> as(limited, () -> semesters.status(identityFixture.canonical().semesterId())));
        denied(() -> as(limited, () -> semesters.getOperation(operation.getOperationId())));
    }

    @Test
    void concurrentFullRevocationsSerializeAndCannotRemoveLastFull() throws Exception {
        var second = identityFixture.select(secondId, AuthRole.ADMIN);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<Boolean> a = pool.submit(() -> raceRevoke(first, secondId, start));
            Future<Boolean> b = pool.submit(() -> raceRevoke(second, firstId, start));
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(
                            auth.queryForObject(
                                    "SELECT count(*) FROM admin_access_profiles WHERE assigned AND"
                                            + " mode='FULL'",
                                    Long.class))
                    .isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean raceRevoke(SelectedActor actor, long target, CyclicBarrier start)
            throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            revoke(actor, target, 1);
            return true;
        } catch (StatusRuntimeException failure) {
            assertThat(failure.getStatus().getCode())
                    .isIn(
                            Status.Code.PERMISSION_DENIED,
                            Status.Code.FAILED_PRECONDITION,
                            Status.Code.UNAUTHENTICATED);
            return false;
        } catch (ErrorResponseException failure) {
            assertThat(failure.getStatusCode().value()).isIn(401, 403, 409);
            return false;
        } catch (AcademicIdentityPendingException pending) {
            var terminal = settle(pending.operation().operationId());
            assertThat(terminal.phase())
                    .isIn(
                            AcademicIdentityCoordinator.Phase.SUCCEEDED,
                            AcademicIdentityCoordinator.Phase.REJECTED,
                            AcademicIdentityCoordinator.Phase.FAILED_SAFE);
            return terminal.phase() == AcademicIdentityCoordinator.Phase.SUCCEEDED;
        }
    }

    @Test
    void actorDowngradeWhileWaitingForGlobalLockCannotUseOldFullAdmission() throws Exception {
        var second = identityFixture.select(secondId, AuthRole.ADMIN);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        var snapshotsReady = new CountDownLatch(2);
        var releaseDownscope = new CountDownLatch(1);
        var releaseOldActor = new CountDownLatch(1);
        var observed = ConcurrentHashMap.<Long>newKeySet();
        // This observer returns the REAL Auth data unchanged. Admission and initial FULL
        // checks have already finished when these per-target reads arrive here.
        org.mockito.Mockito.doAnswer(
                        call -> {
                            Object result = call.callRealMethod();
                            Collection<Long> selected = call.getArgument(0);
                            if (selected.size() == 1) {
                                long target = selected.iterator().next();
                                if ((target == secondId || target == otherTeacherId)
                                        && observed.add(target)) {
                                    snapshotsReady.countDown();
                                    var gate =
                                            target == secondId ? releaseDownscope : releaseOldActor;
                                    if (!gate.await(5, TimeUnit.SECONDS))
                                        throw new AssertionError(
                                                "Actual Auth target snapshot gate was not"
                                                    + " released");
                                }
                            }
                            return result;
                        })
                .when(identityData)
                .readServiceIdentity(
                        org.mockito.ArgumentMatchers.anyCollection(),
                        org.mockito.ArgumentMatchers.anyLong());
        Future<?> downscope = pool.submit(() -> put(secondId, "LIMITED", List.of("USERS_VIEW"), 1));
        Future<?> oldActor =
                pool.submit(
                        () ->
                                as(
                                        second,
                                        () ->
                                                service.put(
                                                        otherTeacherId,
                                                        new AdminAccessInput("FULL", List.of()),
                                                        0L)));
        try {
            assertThat(snapshotsReady.await(5, TimeUnit.SECONDS)).isTrue();
            identityFixture
                    .authTransactions()
                    .executeWithoutResult(
                            status -> {
                                auth.queryForList(
                                        "SELECT pg_advisory_xact_lock(?)",
                                        AdminAccessJdbcAuthority.GLOBAL_LOCK);
                                releaseDownscope.countDown();
                                awaitAuthWaiters(
                                        1); // Only actual PREPARE remains after the observed
                                            // preflight.
                                releaseOldActor.countDown();
                                awaitAuthWaiters(2);
                            });
            downscope.get(15, TimeUnit.SECONDS);
            assertThatThrownBy(() -> oldActor.get(15, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(
                            ExecutionException.class,
                            failure ->
                                    denied(
                                            () -> {
                                                throw (RuntimeException) failure.getCause();
                                            }));
            assertThat(storedProfile(secondId).full()).isFalse();
            assertThat(storedProfile(otherTeacherId).assigned()).isFalse();
        } finally {
            releaseDownscope.countDown();
            releaseOldActor.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitAuthWaiters(int required) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            // A separate assertion connection observes only this fixture's owned Auth database.
            long count =
                    identityFixture
                            .fixtureOwnerAuthJdbc()
                            .queryForObject(
                                    "SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND"
                                        + " NOT granted AND database=(SELECT oid FROM pg_database"
                                        + " WHERE datname=current_database())",
                                    Long.class);
            if (count >= required) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        }
        fail("Expected %s actual Auth advisory-lock waiters before releasing the holder", required);
    }

    @Test
    void archiveTombstoneVersionAndLastFullGuardCoverLegacyGrantLifecycle() {
        // Keep the archived ADMIN-grant tombstone criterion and also exercise actual step-up.
        as(
                first,
                () ->
                        users.updateRoleGrant(
                                secondId,
                                "TEACHER",
                                new RoleGrantUpdateRequest(
                                        RoleGrantStatus.ACTIVE, null, "E-ARCHIVE-ADMIN", null)));
        var key = UUID.randomUUID();
        try (var ignored = identityFixture.request(first, key)) {
            var preview = archives.preview(secondId);
            assertThat(preview.requiresPassword()).isTrue();
            archives.archive(
                    secondId,
                    new UserArchiveModels.ArchiveRequest(
                            key,
                            preview.previewDigest(),
                            ru.rutcampustrack.academic.integration
                                    .AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD));
        }
        assertThat(
                        auth.queryForObject(
                                "SELECT account_state FROM auth_identities WHERE id=?",
                                String.class,
                                secondId))
                .isEqualTo("ARCHIVED");
        assertThat(roleCount(secondId, "admin", "archived")).isOne();
        assertThat(roleCount(secondId, "teacher", "archived")).isOne();
        assertThat(storedProfile(secondId).assigned()).isFalse();
        assertThat(storedProfile(secondId).version()).isEqualTo(2);
        var authAuthority = new ru.rutcampustrack.auth.authority.AuthAdminJdbcAuthority(auth);
        assertThatThrownBy(
                        () ->
                                identityFixture
                                        .authTransactions()
                                        .executeWithoutResult(
                                                status -> {
                                                    authAuthority.lockGlobal();
                                                    authAuthority.protectLastFull(firstId);
                                                }))
                .isInstanceOf(AdminAccessException.class)
                .hasMessageContaining("LAST_FULL_ADMIN");
        assertThat(storedProfile(firstId).full()).isTrue();
        assertThatThrownBy(
                        () ->
                                as(
                                        first,
                                        () ->
                                                users.updateRoleGrant(
                                                        revokedId,
                                                        "ADMIN",
                                                        new RoleGrantUpdateRequest(
                                                                RoleGrantStatus.ACTIVE,
                                                                null,
                                                                null,
                                                                null))))
                .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        assertThat(storedProfile(revokedId).assigned()).isFalse();
        assertThat(roleCount(revokedId, "admin", "active")).isZero();
    }

    private AcademicIdentityCoordinator.Operation settle(UUID operation) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var current = coordinator.recover(operation.toString());
            if (Set.of(
                            AcademicIdentityCoordinator.Phase.SUCCEEDED,
                            AcademicIdentityCoordinator.Phase.REJECTED,
                            AcademicIdentityCoordinator.Phase.FAILED_SAFE)
                    .contains(current.phase())) return current;
        }
        throw new AssertionError(
                "Accepted identity operation remained unknown; no success inferred");
    }

    private String authorityRows() {
        return auth.queryForObject(
                        "SELECT jsonb_build_object('identities',(SELECT jsonb_agg(to_jsonb(t) ORDER"
                            + " BY id) FROM auth_identities t),'grants',(SELECT"
                            + " jsonb_agg(to_jsonb(t) ORDER BY id) FROM user_role_grants"
                            + " t),'sessions',(SELECT jsonb_agg(to_jsonb(t) ORDER BY sid) FROM"
                            + " auth_sessions t),'profiles',(SELECT jsonb_agg(to_jsonb(t) ORDER BY"
                            + " user_id) FROM admin_access_profiles t))::text",
                        String.class)
                + domainRows();
    }

    private String domainRows() {
        return academic.queryForObject(
                "SELECT jsonb_build_object('groups',(SELECT jsonb_agg(to_jsonb(t) ORDER BY id) FROM"
                    + " groups t),'profiles',(SELECT jsonb_agg(to_jsonb(t) ORDER BY id) FROM"
                    + " academic_profiles t),'eligibility',(SELECT jsonb_agg(to_jsonb(t) ORDER BY"
                    + " user_id,role) FROM academic_role_eligibility t))::text",
                String.class);
    }

    private static void denied(Runnable action) {
        assertThatThrownBy(action::run)
                .satisfies(
                        failure -> {
                            if (failure instanceof StatusRuntimeException status)
                                assertThat(status.getStatus().getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED);
                            else {
                                assertThat(failure).isInstanceOf(ErrorResponseException.class);
                                assertThat(
                                                ((ErrorResponseException) failure)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(403);
                            }
                        });
    }

    private void stale(SelectedActor actor) {
        assertThatThrownBy(
                        () -> {
                            try (var ignored = identityFixture.request(actor)) {
                                fail("Stale identity admitted");
                            }
                        })
                .isInstanceOfSatisfying(
                        ErrorResponseException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(401));
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    }
}
