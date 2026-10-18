package ru.rutcampustrack.auth.authority;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import io.grpc.StatusRuntimeException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.BcryptConcurrencyGuard;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.support.AuthDatabaseFixture;
import ru.rutcampustrack.authauthority.grpc.ReadInitialCredentialsRequest;
import ru.rutcampustrack.authoperations.grpc.*;

import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Native PG proof of the actual first-volume bootstrap, owned schema and durable operation store.
 */
@Testcontainers
class AuthIdentityOperationStoreIT {
    private static final String MIGRATION_PASSWORD = "synthetic-auth-migration";
    private static final String RUNTIME_PASSWORD = "synthetic-auth-runtime";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("academic_db")
                    .withUsername("rct_bootstrap")
                    .withPassword("synthetic-bootstrap")
                    .withEnv("POSTGRES_RUNTIME_PASSWORD", "synthetic-legacy-runtime")
                    .withEnv("POSTGRES_MIGRATION_PASSWORD", "synthetic-academic-migration")
                    .withEnv("POSTGRES_MONITOR_PASSWORD", "synthetic-monitor")
                    .withEnv("POSTGRES_ACADEMIC_RUNTIME_PASSWORD", "synthetic-academic-runtime")
                    .withEnv("POSTGRES_AUTH_PASSWORD", RUNTIME_PASSWORD)
                    .withEnv("POSTGRES_AUTH_MIGRATION_PASSWORD", MIGRATION_PASSWORD)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(bootstrap().toString(), 0755),
                            "/docker-entrypoint-initdb.d/10-rct-roles.sh");

    private static JdbcTemplate owner;
    private static JdbcTemplate runtime;
    private static DataSourceTransactionManager transactions;
    private SessionPrincipal actor;
    private AuthInternalActorReader actors;
    private AuthIdentityOperationStore operations;

    @BeforeAll
    static void migrate() {
        var migration = dataSource("auth_db", "rct_auth_migrator", MIGRATION_PASSWORD);
        Flyway.configure()
                .dataSource(migration)
                .locations("filesystem:" + AuthDatabaseFixture.migrations())
                .load()
                .migrate();
        owner = new JdbcTemplate(migration);
        var app = dataSource("auth_db", "rct_auth_runtime", RUNTIME_PASSWORD);
        runtime = new JdbcTemplate(app);
        transactions = new DataSourceTransactionManager(app);
    }

    @BeforeEach
    void seedActor() {
        Instant created = Instant.now().minusSeconds(3600);
        long id =
                AuthDatabaseFixture.identity(
                        owner, "admin-" + UUID.randomUUID().toString().substring(0, 12), created);
        long grant =
                owner.queryForObject(
                        "INSERT INTO user_role_grants(user_id,role,status) VALUES"
                                + " (?,'admin','active') RETURNING id",
                        Long.class,
                        id);
        owner.update(
                "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version)"
                        + " VALUES (?,TRUE,'FULL','{}',1)",
                id);
        long rv =
                owner.queryForObject(
                        "SELECT roles_version FROM auth_identities WHERE id=?", Long.class, id);
        UUID sid = UUID.randomUUID();
        owner.update(
                """
INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)
VALUES(?,?,?,1,?,?,?,?,'PASSWORD')
""",
                sid,
                id,
                grant,
                UUID.randomUUID(),
                Timestamp.from(Instant.now().plusSeconds(3600)),
                Timestamp.from(created),
                Timestamp.from(created));
        actor =
                new SessionPrincipal(
                        id, sid, 1, rv, AuthRole.ADMIN, RoleStatus.ACTIVE, null, false, false);
        actors = mock(AuthInternalActorReader.class);
        when(actors.read()).thenReturn(actor);
        operations = store(actors);
    }

    @Test
    void unchangedAdminPutAndUnassignedRevokeHaveNoAuthorityOrEventSideEffects() {
        for (String mode : List.of("FULL", "LIMITED", "UNASSIGNED", "NONE")) {
            long target = adminNoopTarget(mode);
            var command = adminNoopCommand(target);
            UUID sid = UUID.randomUUID();
            long studentGrant =
                    owner.queryForObject(
                            "SELECT id FROM user_role_grants WHERE user_id=? AND role='student'",
                            Long.class,
                            target);
            owner.update(
                    """
INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)
 VALUES(?,?,?,1,?,now()+interval '1 hour',now()-interval '1 minute',now(),'PASSWORD')
""",
                    sid,
                    target,
                    studentGrant,
                    UUID.randomUUID());
            String before = adminNoopEffects(target);
            long frontier = clock();
            var result = operations.prepare(command);
            assertThat(result.getPhase())
                    .as(mode)
                    .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP);
            assertThat(result.getTargets(0).getUserId()).isEqualTo(target);
            assertThat(result.getTargets(0).getFinalRolesVersion())
                    .isEqualTo(command.getTargets(0).getExpectedRolesVersion());
            assertThat(result.getTargets(0).getFinalAdminProfileVersion())
                    .isEqualTo(command.getTargets(0).getDesiredAdminProfile().getExpectedVersion());
            assertThat(result.getTargets(0).getExpectedAcademicRevision())
                    .isEqualTo(command.getTargets(0).getExpectedAcademicRevision());
            assertThat(result.getCommittedCatalogVersion()).isZero();
            assertThat(adminNoopEffects(target).equals(before))
                    .as("no identity/grant/profile/session/outbox writes: %s", mode)
                    .isTrue();
            assertThat(clock()).isEqualTo(frontier);
            assertThat(count("auth_admin_access_noop_receipts", command)).isEqualTo(1);
            assertThat(count("auth_identity_operations", command)).isZero();
            assertThat(count("auth_identity_operation_targets", command)).isZero();
        }
    }

    @Test
    void noopLostReplyReturnsImmutablePastReceiptAndNeverUndoesLaterRealChange() {
        long target = adminNoopTarget("LIMITED");
        var command = adminNoopCommand(target);
        var original = operations.prepare(command);
        var expired = mock(AuthInternalActorReader.class);
        when(expired.read()).thenThrow(new IllegalStateException("original token expired"));
        var restarted = store(expired);
        assertThat(restarted.prepare(command)).isEqualTo(original);
        assertThat(restarted.get(get(command))).isEqualTo(original);
        assertThat(
                        restarted.commit(
                                CommitIdentityOperationRequest.newBuilder()
                                        .setOperationId(command.getOperationId())
                                        .setCommandHash(command.getCommandHash())
                                        .build()))
                .isEqualTo(original);
        assertThat(
                        restarted.fail(
                                FailIdentityOperationRequest.newBuilder()
                                        .setOperationId(command.getOperationId())
                                        .setCommandHash(command.getCommandHash())
                                        .setErrorCode("ACADEMIC_CONFLICT")
                                        .build()))
                .isEqualTo(original);
        var changed =
                hash(
                        command.toBuilder()
                                .setTargets(
                                        0,
                                        command.getTargets(0).toBuilder()
                                                .setDesiredAdminProfile(
                                                        command
                                                                .getTargets(0)
                                                                .getDesiredAdminProfile()
                                                                .toBuilder()
                                                                .setMode("FULL")
                                                                .clearPermissions())));
        assertThatThrownBy(() -> restarted.prepare(changed))
                .isInstanceOf(StatusRuntimeException.class);
        var mutation = hash(changed.toBuilder().setOperationId(UUID.randomUUID().toString()));
        var prepared = operations.prepare(mutation);
        assertThat(prepared.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_PREPARED_FENCED);
        var committed = operations.commit(commit(mutation, prepared));
        assertThat(committed.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(operations.prepare(mutation))
                .isEqualTo(committed); // A real positive winner never becomes NOOP.
        assertThat(restarted.prepare(command))
                .isEqualTo(original); // Past receipt, not a current authority snapshot.
        assertThat(
                        owner.queryForObject(
                                "SELECT mode FROM admin_access_profiles WHERE user_id=?",
                                String.class,
                                target))
                .isEqualTo("FULL");
        assertThat(
                        owner.queryForObject(
                                "SELECT version FROM admin_access_profiles WHERE user_id=?",
                                Long.class,
                                target))
                .isEqualTo(2);
        verifyNoInteractions(expired);
    }

    @Test
    void newNoopStillRequiresCurrentFullNonSelfActiveTargetAndExactVersions() {
        long target = adminNoopTarget("NONE");
        var valid = adminNoopCommand(target);
        assertThatThrownBy(() -> operations.prepare(adminNoopCommand(actor.userId())))
                .isInstanceOf(StatusRuntimeException.class);
        var staleProfile =
                hash(
                        valid.toBuilder()
                                .setTargets(
                                        0,
                                        valid.getTargets(0).toBuilder()
                                                .setDesiredAdminProfile(
                                                        valid
                                                                .getTargets(0)
                                                                .getDesiredAdminProfile()
                                                                .toBuilder()
                                                                .setExpectedVersion(1))));
        assertThatThrownBy(() -> operations.prepare(staleProfile))
                .isInstanceOf(StatusRuntimeException.class);
        for (String generation : List.of("RV", "CV", "BV")) {
            var staleTarget = valid.getTargets(0).toBuilder();
            switch (generation) {
                case "RV" -> staleTarget.setExpectedRolesVersion(2);
                case "CV" -> staleTarget.setExpectedCredentialsVersion(2);
                case "BV" -> staleTarget.setExpectedTelegramBindingVersion(2);
            }
            var staleCommand = hash(valid.toBuilder().setTargets(0, staleTarget));
            assertThatThrownBy(() -> operations.prepare(staleCommand))
                    .as(generation)
                    .isInstanceOf(StatusRuntimeException.class);
        }
        owner.update(
                "UPDATE admin_access_profiles SET"
                        + " mode='LIMITED',permissions=ARRAY['USERS_VIEW'],version=version+1 WHERE"
                        + " user_id=?",
                actor.userId());
        assertThatThrownBy(() -> operations.prepare(valid))
                .isInstanceOf(StatusRuntimeException.class);
        seedActor();
        operations = store(actors);
        var archived = adminNoopCommand(target);
        owner.update("UPDATE auth_identities SET account_state='ARCHIVED' WHERE id=?", target);
        assertThatThrownBy(() -> operations.prepare(archived))
                .isInstanceOf(StatusRuntimeException.class);
        assertThat(
                        owner.queryForObject(
                                "SELECT count(*) FROM auth_admin_access_noop_receipts WHERE"
                                        + " target_id=?",
                                Long.class,
                                target))
                .isZero();
    }

    @Test
    void sameUuidNegativeAndNoopAreAtomicAndExistingNoopCannotBeRejectedOrRebound()
            throws Exception {
        long target = adminNoopTarget("NONE");
        var command = adminNoopCommand(target);
        var rejection = reject(command);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var positive =
                    executor.submit(
                            () -> {
                                start.await();
                                return operations.prepare(command);
                            });
            var negative =
                    executor.submit(
                            () -> {
                                start.await();
                                return operations.rejectUnprepared(rejection);
                            });
            start.countDown();
            var winner = positive.get(5, TimeUnit.SECONDS);
            assertThat(negative.get(5, TimeUnit.SECONDS)).isEqualTo(winner);
            assertThat(operations.prepare(command)).isEqualTo(winner);
            assertThat(operations.get(get(command))).isEqualTo(winner);
            assertThat(winner.getPhase())
                    .isIn(
                            IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP,
                            IdentityOperationPhase.IDENTITY_OPERATION_PHASE_REJECTED);
            assertThat(
                            owner.queryForObject(
                                    "SELECT roles_version FROM auth_identities WHERE id=?",
                                    Long.class,
                                    target))
                    .isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        var rejected = adminNoopCommand(target);
        var negativeWinner = operations.rejectUnprepared(reject(rejected));
        assertThat(negativeWinner.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_REJECTED);
        assertThat(operations.prepare(rejected)).isEqualTo(negativeWinner);
        assertThat(count("auth_admin_access_noop_receipts", rejected)).isZero();
        var second = adminNoopCommand(target);
        var receipt = operations.prepare(second);
        assertThat(operations.rejectUnprepared(reject(second))).isEqualTo(receipt);
        var foreign =
                reject(second).toBuilder()
                        .setActor(
                                reject(second).getActor().toBuilder()
                                        .setRolesVersion(actor.rolesVersion() + 1))
                        .build();
        assertThatThrownBy(() -> operations.rejectUnprepared(foreign))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void changedAdminFactCommitsOnceWithProfileAndRollsBackIfOutboxCannotCommit() throws Exception {
        long target = adminNoopTarget("LIMITED");
        var command = adminModeChange(target, "FULL");
        var prepared = operations.prepare(command);
        long before = clock();
        String constraint = "admin_access_outbox_" + UUID.randomUUID().toString().replace("-", "");
        owner.execute(
                "ALTER TABLE auth_outbox ADD CONSTRAINT "
                        + constraint
                        + " CHECK(event_type<>'admin.access.changed' OR"
                        + " (payload->'payload'->>'target_user_id')::bigint<>"
                        + target
                        + ")");
        try {
            assertThatThrownBy(() -> operations.commit(commit(command, prepared)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(operations.get(get(command)).getPhase())
                    .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_PREPARED_FENCED);
            assertThat(
                            owner.queryForObject(
                                    "SELECT mode FROM admin_access_profiles WHERE user_id=?",
                                    String.class,
                                    target))
                    .isEqualTo("LIMITED");
            assertThat(
                            owner.queryForObject(
                                    "SELECT fence_operation_id FROM auth_identities WHERE id=?",
                                    UUID.class,
                                    target))
                    .isEqualTo(UUID.fromString(command.getOperationId()));
            assertThat(adminChangedEvents(target)).isZero();
            assertThat(clock()).isEqualTo(before);
        } finally {
            owner.execute("ALTER TABLE auth_outbox DROP CONSTRAINT " + constraint);
        }
        var committed = operations.commit(commit(command, prepared));
        assertThat(committed.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(operations.commit(commit(command, prepared))).isEqualTo(committed);
        assertThat(adminChangedEvents(target)).isEqualTo(1);
        String encoded =
                owner.queryForObject(
                        "SELECT payload::text FROM auth_outbox WHERE"
                                + " event_type='admin.access.changed' AND"
                                + " payload->'payload'->>'target_user_id'=?",
                        String.class,
                        Long.toString(target));
        assertThat(
                        ru.rutcampustrack.auth.events.EventSchemaValidator.validate(
                                "admin.access.changed.json", encoded))
                .isEmpty();
        var event = new ObjectMapper().readTree(encoded);
        var payload = event.path("payload");
        assertThat(event.path("source").asText()).isEqualTo("auth-service");
        assertThat(event.path("event_version").asInt()).isEqualTo(1);
        assertThat(payload.path("actor_user_id").asLong()).isEqualTo(actor.userId());
        assertThat(payload.path("version").asLong())
                .isEqualTo(committed.getTargets(0).getFinalAdminProfileVersion());
        assertThat(payload.path("permissions_changed").asBoolean()).isTrue();
        assertThat(payload.path("mode").asText()).isEqualTo("FULL");
        var permissions = new ArrayList<String>();
        payload.path("permissions").forEach(p -> permissions.add(p.asText()));
        assertThat(permissions)
                .isEqualTo(ru.rutcampustrack.shared.security.admin.AdminPermission.allCodes());
        long newlyAssigned = adminNoopTarget("NONE");
        var assignment = adminNoopCommand(newlyAssigned);
        var assignedTarget =
                assignment.getTargets(0).toBuilder()
                        .setDesiredAdminProfile(
                                AdmissionAdminProfile.newBuilder()
                                        .setAssigned(true)
                                        .setMode("LIMITED")
                                        .addPermissions("USERS_VIEW"))
                        .clearDesiredGrants()
                        .addDesiredGrants(
                                AdmissionGrant.newBuilder().setRole("ADMIN").setStatus("ACTIVE"));
        assignedTarget.addAllDesiredGrants(assignment.getTargets(0).getDesiredGrantsList());
        var assign = hash(assignment.toBuilder().setTargets(0, assignedTarget));
        var accepted = operations.prepare(assign);
        assertThat(operations.commit(commit(assign, accepted)).getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(adminChangedEvents(newlyAssigned)).isEqualTo(1);
        assertThat(
                        owner.queryForObject(
                                "SELECT (payload->'payload'->>'version')::bigint FROM auth_outbox"
                                    + " WHERE event_type='admin.access.changed' AND"
                                    + " payload->'payload'->>'target_user_id'=?",
                                Long.class,
                                Long.toString(newlyAssigned)))
                .isEqualTo(1);
    }

    @Test
    void revokeAndFailedActorCommitNeverProduceAdminAccessChangedFact() {
        long revoked = adminNoopTarget("LIMITED");
        var original = adminNoopCommand(revoked);
        var target = original.getTargets(0).toBuilder();
        target.setDesiredAdminProfile(
                target.getDesiredAdminProfile().toBuilder()
                        .setAssigned(false)
                        .clearMode()
                        .clearPermissions());
        target.clearDesiredGrants();
        original.getTargets(0)
                .getDesiredGrantsList()
                .forEach(
                        g ->
                                target.addDesiredGrants(
                                        "ADMIN".equals(g.getRole())
                                                ? g.toBuilder().setStatus("ARCHIVED").build()
                                                : g));
        var revoke = hash(original.toBuilder().setTargets(0, target));
        var prepared = operations.prepare(revoke);
        assertThat(operations.commit(commit(revoke, prepared)).getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(adminChangedEvents(revoked)).isZero();
        long failed = adminNoopTarget("LIMITED");
        var change = adminModeChange(failed, "FULL");
        var pending = operations.prepare(change);
        mutateActor(
                () ->
                        owner.update(
                                "UPDATE auth_sessions SET"
                                        + " revoked_at=now(),revoke_reason='CURRENT_LOGOUT' WHERE"
                                        + " sid=?",
                                actor.sessionId()));
        assertThat(operations.commit(commit(change, pending)).getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE);
        assertThat(adminChangedEvents(failed)).isZero();
        assertThat(
                        owner.queryForObject(
                                "SELECT mode FROM admin_access_profiles WHERE user_id=?",
                                String.class,
                                failed))
                .isEqualTo("LIMITED");
    }

    private PrepareIdentityOperationRequest adminModeChange(long target, String mode) {
        var command = adminNoopCommand(target);
        return hash(
                command.toBuilder()
                        .setTargets(
                                0,
                                command.getTargets(0).toBuilder()
                                        .setDesiredAdminProfile(
                                                command
                                                        .getTargets(0)
                                                        .getDesiredAdminProfile()
                                                        .toBuilder()
                                                        .setMode(mode)
                                                        .clearPermissions())));
    }

    private long adminChangedEvents(long target) {
        return owner.queryForObject(
                "SELECT count(*) FROM auth_outbox WHERE event_type='admin.access.changed' AND"
                        + " payload->'payload'->>'target_user_id'=?",
                Long.class,
                Long.toString(target));
    }

    private long adminNoopTarget(String mode) {
        long id =
                AuthDatabaseFixture.identity(
                        owner,
                        "admin-noop-" + UUID.randomUUID().toString().substring(0, 8),
                        Instant.now().minusSeconds(60));
        owner.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                        + " (?,'student','active',?)",
                id,
                10000000 + id);
        if (!"NONE".equals(mode)) {
            boolean assigned = !"UNASSIGNED".equals(mode);
            owner.update(
                    "INSERT INTO user_role_grants(user_id,role,status) VALUES (?,'admin',?)",
                    id,
                    assigned ? "active" : "archived");
            owner.update(
                    "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version)"
                            + " VALUES (?,?,?,?::text[],?)",
                    id,
                    assigned,
                    assigned ? mode : null,
                    "LIMITED".equals(mode) ? "{USERS_VIEW}" : "{}",
                    assigned ? 1 : 3);
        }
        return id;
    }

    private PrepareIdentityOperationRequest adminNoopCommand(long target) {
        var current =
                new AuthIdentityAuthorityQuery(runtime, new ObjectMapper())
                        .read(
                                ru.rutcampustrack.authauthority.grpc.ReadIdentityAuthorityRequest
                                        .newBuilder()
                                        .addUserIds(target)
                                        .build())
                        .getIdentities(0);
        var profile = current.getAdminProfile();
        var delta =
                IdentityTargetCommand.newBuilder()
                        .setUserId(target)
                        .setExpectedRolesVersion(current.getRolesVersion())
                        .setExpectedCredentialsVersion(current.getCredentialsVersion())
                        .setExpectedTelegramBindingVersion(current.getTelegramBindingVersion())
                        .setExpectedAcademicRevision(1)
                        .setDesiredAccountState(IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                        .setDesiredAdminProfile(
                                AdmissionAdminProfile.newBuilder()
                                        .setAssigned(profile.getAssigned())
                                        .setMode(profile.getMode())
                                        .addAllPermissions(profile.getPermissionsList())
                                        .setExpectedVersion(profile.getVersion()));
        current.getGrantsList().stream()
                .sorted(
                        Comparator.comparing(
                                ru.rutcampustrack.authauthority.grpc.AuthorityRoleGrant::getRole))
                .forEach(
                        g -> {
                            var grant =
                                    AdmissionGrant.newBuilder()
                                            .setGrantId(g.getGrantId())
                                            .setRole(g.getRole())
                                            .setStatus(g.getStatus());
                            if (g.hasGroupId()) grant.setGroupId(g.getGroupId());
                            delta.addDesiredGrants(grant);
                        });
        return hash(
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS)
                        .addTargets(delta));
    }

    private String adminNoopEffects(long target) {
        return owner.queryForObject(
                """
SELECT jsonb_build_object(
 'identity',(SELECT to_jsonb(u)-ARRAY['password_hash','initial_password'] FROM auth_identities u WHERE id=?),
 'grants',(SELECT coalesce(jsonb_agg(to_jsonb(g) ORDER BY id),'[]') FROM user_role_grants g WHERE user_id=?),
 'profile',(SELECT to_jsonb(p) FROM admin_access_profiles p WHERE user_id=?),
 'sessions',(SELECT coalesce(jsonb_agg(to_jsonb(s)-ARRAY['current_refresh_jti','previous_refresh_jti'] ORDER BY sid),'[]') FROM auth_sessions s WHERE user_id=?),
 'outbox_count',(SELECT count(*) FROM auth_outbox))::text
""",
                String.class,
                target,
                target,
                target,
                target);
    }

    @Test
    void credentialReaderInvalidationBumpsOnlyHeadmanAndCommitsExactCatalogRevisionOnce() {
        long group = 1000000 + actor.userId();
        long headman = credentialHeadman(group);
        long unrelated =
                AuthDatabaseFixture.identity(
                        owner,
                        "untouched-" + UUID.randomUUID().toString().substring(0, 8),
                        Instant.now().minusSeconds(60));
        var store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
        var command = readerCommand(group, headman, 1, 1);
        var prepared = store.prepare(command);
        assertThat(prepared.getPhase())
                .isEqualTo(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_INVALIDATED);
        assertThat(store.prepare(command)).isEqualTo(prepared);
        assertThat(
                        runtime.queryForObject(
                                "SELECT roles_version FROM auth_identities WHERE id=?",
                                Long.class,
                                headman))
                .isEqualTo(2);
        assertThat(
                        runtime.queryForObject(
                                "SELECT roles_version FROM auth_identities WHERE id=?",
                                Long.class,
                                unrelated))
                .isEqualTo(1);
        assertThat(
                        runtime.queryForObject(
                                "SELECT fence_operation_id IS NULL FROM auth_identities WHERE id=?",
                                Boolean.class,
                                headman))
                .isTrue();
        var commit = readerCommit(command, prepared);
        var completed = store.commit(commit);
        assertThat(completed.getPhase())
                .isEqualTo(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_COMMITTED);
        assertThat(store.commit(commit)).isEqualTo(completed);
        assertThat(
                        runtime.queryForObject(
                                "SELECT projection->>'academic_revision' FROM"
                                        + " auth_identity_catalog_changes WHERE catalog_version=?",
                                String.class,
                                completed.getCommittedCatalogVersion()))
                .isEqualTo("2");
        assertThat(
                        runtime.queryForObject(
                                "SELECT version FROM auth_identity_catalog_clock WHERE singleton=1",
                                Long.class))
                .isEqualTo(completed.getCommittedCatalogVersion());
    }

    @Test
    void credentialReaderEmptySetAndSelfActorGenerationRemainSafeButLogoutDeniesCommit() {
        long group = 2000000 + actor.userId();
        owner.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                        + " (?,'student','active',?),(?,'headman','active',?)",
                actor.userId(),
                group,
                actor.userId(),
                group);
        var store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
        var command = readerCommand(group, actor.userId(), actor.rolesVersion(), 1);
        var prepared = store.prepare(command);
        // Only the operation's own RV+1 is admitted; natural expiry is not revocation.
        owner.update(
                "UPDATE auth_sessions SET refresh_expires_at=? WHERE sid=?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                actor.sessionId());
        assertThat(store.commit(readerCommit(command, prepared)).getPhase())
                .isEqualTo(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_COMMITTED);
        seedActor();
        long nextGroup = 3000000 + actor.userId();
        long headman = credentialHeadman(nextGroup);
        store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
        command = readerCommand(nextGroup, headman, 1, 1);
        prepared = store.prepare(command);
        owner.update(
                "UPDATE auth_sessions SET"
                    + " revoked_at=now(),revoke_reason='CURRENT_LOGOUT',session_version=session_version+1"
                    + " WHERE sid=?",
                actor.sessionId());
        assertThat(store.commit(readerCommit(command, prepared)).getPhase())
                .isEqualTo(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE);
        assertThat(
                        runtime.queryForObject(
                                "SELECT reserved FROM auth_credential_reader_groups WHERE"
                                        + " operation_id=?",
                                Boolean.class,
                                UUID.fromString(command.getOperationId())))
                .isTrue();
        seedActor();
        store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
        var empty = readerCommand(4000000 + actor.userId(), 0, 0, 0);
        var emptyPrepared = store.prepare(empty);
        assertThat(emptyPrepared.getHeadmenCount()).isZero();
        assertThat(store.commit(readerCommit(empty, emptyPrepared)).getPhase())
                .isEqualTo(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_COMMITTED);
    }

    @Test
    void credentialReaderPermanentActorSecurityChangesRetainGuardReservation() {
        for (String change : List.of("CREDENTIAL", "BINDING", "PROFILE", "ROLE")) {
            seedActor();
            long group = 6000000 + actor.userId(), headman = credentialHeadman(group);
            var store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
            var command = readerCommand(group, headman, 1, 1);
            var prepared = store.prepare(command);
            switch (change) {
                case "CREDENTIAL" ->
                        owner.update(
                                "UPDATE auth_identities SET"
                                        + " password_changed=true,initial_password=NULL WHERE id=?",
                                actor.userId());
                case "BINDING" ->
                        owner.update(
                                "UPDATE auth_identities SET telegram_id=? WHERE id=?",
                                7000000000L + actor.userId(),
                                actor.userId());
                case "PROFILE" ->
                        owner.update(
                                "UPDATE admin_access_profiles SET version=version+1 WHERE"
                                        + " user_id=?",
                                actor.userId());
                case "ROLE" ->
                        owner.update(
                                "UPDATE user_role_grants SET status='archived' WHERE user_id=? AND"
                                        + " role='admin'",
                                actor.userId());
            }
            assertThat(store.commit(readerCommit(command, prepared)).getPhase())
                    .as(change)
                    .isEqualTo(
                            CredentialReaderInvalidationPhase
                                    .CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE);
            assertThat(
                            runtime.queryForObject(
                                    "SELECT reserved FROM auth_credential_reader_groups WHERE"
                                            + " operation_id=?",
                                    Boolean.class,
                                    UUID.fromString(command.getOperationId())))
                    .isTrue();
        }
    }

    @Test
    void credentialReaderFreshAuthorizedReconcileRetainsExactIntentAndReleasesOldReservation() {
        long group = 8000000 + actor.userId(), headman = credentialHeadman(group);
        var store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
        var original = readerCommand(group, headman, 1, 1);
        var prepared = store.prepare(original);
        store.fail(
                FailCredentialReaderInvalidationRequest.newBuilder()
                        .setOperationId(original.getOperationId())
                        .setCommandHash(original.getCommandHash())
                        .setExpectedOperationRevision(prepared.getOperationRevision())
                        .build());
        seedActor();
        store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
        var retry =
                original.toBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .clearCommandHash()
                        .setSupersedesFailedOperationId(original.getOperationId())
                        .setGroups(
                                0,
                                original.getGroups(0).toBuilder()
                                        .setExpectedHeadmanRolesVersion(2));
        var changed =
                retry.clone()
                        .setAcademicCommandHash(
                                ByteString.copyFrom(new byte[31])
                                        .concat(ByteString.copyFrom(new byte[] {1})));
        changed.setCommandHash(
                ByteString.copyFrom(
                        CredentialReaderInvalidationCommand.sha(changed.build().toByteArray())));
        var current = store;
        assertThatThrownBy(() -> current.prepare(changed.build()))
                .isInstanceOf(StatusRuntimeException.class);
        var request =
                retry.setCommandHash(
                                ByteString.copyFrom(
                                        CredentialReaderInvalidationCommand.sha(
                                                retry.build().toByteArray())))
                        .build();
        var next = store.prepare(request);
        assertThat(next.getHeadmen(0).getFinalRolesVersion()).isEqualTo(3);
        assertThat(
                        runtime.queryForObject(
                                "SELECT reserved FROM auth_credential_reader_groups WHERE"
                                        + " operation_id=?",
                                Boolean.class,
                                UUID.fromString(original.getOperationId())))
                .isFalse();
        assertThat(store.commit(readerCommit(request, next)).getPhase())
                .isEqualTo(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_COMMITTED);
    }

    @Test
    void credentialReaderAtomicNegativeReservationWinsOrReturnsExactConcurrentPositive()
            throws Exception {
        long group = 9000000 + actor.userId(), headman = credentialHeadman(group);
        var store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
        var command = readerCommand(group, headman, 1, 1);
        var reject =
                RejectUnpreparedCredentialReaderInvalidationRequest.newBuilder()
                        .setCanonicalPrepare(command)
                        .setReason(
                                IdentityOperationRejectionReason
                                        .IDENTITY_OPERATION_REJECTION_REASON_ACTOR_DENIED)
                        .setActor(
                                IdentityOperationActor.newBuilder()
                                        .setUserId(actor.userId())
                                        .setSessionId(actor.sessionId().toString())
                                        .setSessionVersion(actor.sessionVersion())
                                        .setRolesVersion(actor.rolesVersion()))
                        .build();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var prepare =
                    executor.submit(
                            () -> {
                                start.await();
                                return store.prepare(command);
                            });
            var rejection =
                    executor.submit(
                            () -> {
                                start.await();
                                return store.rejectUnprepared(reject);
                            });
            start.countDown();
            var positiveOrNegative = prepare.get(5, TimeUnit.SECONDS);
            var negativeOrPositive = rejection.get(5, TimeUnit.SECONDS);
            assertThat(positiveOrNegative).isEqualTo(negativeOrPositive);
            assertThat(store.prepare(command)).isEqualTo(positiveOrNegative);
            boolean rejected =
                    positiveOrNegative.getPhase()
                            == CredentialReaderInvalidationPhase
                                    .CREDENTIAL_READER_INVALIDATION_PHASE_REJECTED;
            assertThat(
                            runtime.queryForObject(
                                    "SELECT roles_version FROM auth_identities WHERE id=?",
                                    Long.class,
                                    headman))
                    .isEqualTo(rejected ? 1 : 2);
            assertThat(
                            runtime.queryForObject(
                                    "SELECT count(*) FROM auth_credential_reader_groups WHERE"
                                            + " operation_id=?",
                                    Long.class,
                                    UUID.fromString(command.getOperationId())))
                    .isEqualTo(rejected ? 0 : 1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        var automatic =
                readerCommand(group + 1, 0, 0, 0).toBuilder()
                        .setAction(
                                CredentialReaderInvalidationAction
                                        .CREDENTIAL_READER_INVALIDATION_ACTION_AUTOMATIC_CYCLE_ARCHIVE)
                        .setCycleSemesterId(7)
                        .setCycleYear(2026)
                        .clearCommandHash();
        var autoCommand =
                automatic
                        .setCommandHash(
                                ByteString.copyFrom(
                                        CredentialReaderInvalidationCommand.sha(
                                                automatic.build().toByteArray())))
                        .build();
        var autoReject =
                RejectUnpreparedCredentialReaderInvalidationRequest.newBuilder()
                        .setCanonicalPrepare(autoCommand)
                        .setReason(
                                IdentityOperationRejectionReason
                                        .IDENTITY_OPERATION_REJECTION_REASON_ACADEMIC_REJECTED)
                        .build();
        assertThat(store.rejectUnprepared(autoReject).getPhase())
                .isEqualTo(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_REJECTED);
        assertThat(store.prepare(autoCommand).getPhase())
                .isEqualTo(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_REJECTED);
    }

    @Test
    void credentialReaderLockOrdersOldSecretReadBeforeInvalidationAndRejectsItsStaleSnapshot()
            throws Exception {
        long group = 5000000 + actor.userId(), headman = credentialHeadman(group);
        var store = new AuthCredentialReaderInvalidationStore(runtime, transactions, actors);
        var command = readerCommand(group, headman, 1, 1);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> oldRead =
                    executor.submit(
                            () ->
                                    new TransactionTemplate(transactions)
                                            .execute(
                                                    status -> {
                                                        assertThat(
                                                                        runtime.queryForObject(
                                                                                "SELECT"
                                                                                    + " roles_version"
                                                                                    + " FROM"
                                                                                    + " auth_identities"
                                                                                    + " WHERE id=?"
                                                                                    + " FOR UPDATE",
                                                                                Long.class,
                                                                                headman))
                                                                .isEqualTo(1);
                                                        locked.countDown();
                                                        try {
                                                            assertThat(
                                                                            release.await(
                                                                                    5,
                                                                                    TimeUnit
                                                                                            .SECONDS))
                                                                    .isTrue();
                                                        } catch (InterruptedException interrupted) {
                                                            throw new IllegalStateException(
                                                                    interrupted);
                                                        }
                                                        return null;
                                                    }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<CredentialReaderInvalidationResult> invalidation =
                    executor.submit(() -> store.prepare(command));
            assertThatThrownBy(() -> invalidation.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            oldRead.get(5, TimeUnit.SECONDS);
            assertThat(invalidation.get(5, TimeUnit.SECONDS).getHeadmen(0).getFinalRolesVersion())
                    .isEqualTo(2);
            // This is the same current-RV predicate checked under lock by
            // HeadmanCredentialsService.
            assertThat(
                            runtime.queryForObject(
                                    "SELECT roles_version=1 FROM auth_identities WHERE id=?",
                                    Boolean.class,
                                    headman))
                    .isFalse();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private long credentialHeadman(long group) {
        long id =
                AuthDatabaseFixture.identity(
                        owner,
                        "reader-" + UUID.randomUUID().toString().substring(0, 8),
                        Instant.now().minusSeconds(60));
        owner.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                        + " (?,'student','active',?),(?,'headman','active',?)",
                id,
                group,
                id,
                group);
        return id;
    }

    private PrepareCredentialReaderInvalidationRequest readerCommand(
            long group, long headman, long rv, long revision) {
        var request =
                PrepareCredentialReaderInvalidationRequest.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setAction(
                                CredentialReaderInvalidationAction
                                        .CREDENTIAL_READER_INVALIDATION_ACTION_GROUP_DEACTIVATE)
                        .setAcademicCommandHash(ByteString.copyFrom(new byte[32]))
                        .addGroups(
                                CredentialReaderGroup.newBuilder()
                                        .setGroupId(group)
                                        .setExpectedGroupRevision(1)
                                        .setHeadmanUserId(headman)
                                        .setExpectedHeadmanRolesVersion(rv)
                                        .setExpectedHeadmanAcademicRevision(revision));
        return request.setCommandHash(
                        ByteString.copyFrom(
                                CredentialReaderInvalidationCommand.sha(
                                        request.build().toByteArray())))
                .build();
    }

    private CommitCredentialReaderInvalidationRequest readerCommit(
            PrepareCredentialReaderInvalidationRequest command,
            CredentialReaderInvalidationResult result) {
        var request =
                CommitCredentialReaderInvalidationRequest.newBuilder()
                        .setOperationId(command.getOperationId())
                        .setCommandHash(command.getCommandHash())
                        .setExpectedOperationRevision(result.getOperationRevision());
        command.getGroupsList()
                .forEach(
                        g ->
                                request.addGroups(
                                        CredentialReaderGroupCommitReceipt.newBuilder()
                                                .setGroupId(g.getGroupId())
                                                .setPreviousGroupRevision(
                                                        g.getExpectedGroupRevision())
                                                .setCommittedGroupRevision(
                                                        g.getExpectedGroupRevision() + 1)));
        result.getHeadmenList()
                .forEach(
                        h ->
                                request.addHeadmen(
                                        AcademicIdentityCommitReceipt.newBuilder()
                                                .setUserId(h.getUserId())
                                                .setPreviousAcademicRevision(
                                                        h.getExpectedAcademicRevision())
                                                .setCommittedAcademicRevision(
                                                        h.getExpectedAcademicRevision() + 1)
                                                .setAppliedAuthRolesVersion(
                                                        h.getFinalRolesVersion())));
        return request.build();
    }

    @Test
    void runtimeCanConnectOnlyToItsOwnDatabaseAndCannotEditMigrationMetadata() {
        assertThat(runtime.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("auth_db");
        var academic =
                new JdbcTemplate(
                        dataSource(
                                "academic_db",
                                "rct_academic_runtime",
                                "synthetic-academic-runtime"));
        assertThat(academic.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("academic_db");
        for (String database : List.of("academic_db", "postgres", "template1"))
            assertThatThrownBy(
                            () ->
                                    new JdbcTemplate(
                                                    dataSource(
                                                            database,
                                                            "rct_auth_runtime",
                                                            RUNTIME_PASSWORD))
                                            .queryForObject("SELECT 1", Integer.class))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                new JdbcTemplate(
                                                dataSource(
                                                        "auth_db",
                                                        "rct_academic_runtime",
                                                        "synthetic-academic-runtime"))
                                        .queryForObject("SELECT 1", Integer.class))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> runtime.update("DELETE FROM flyway_schema_history"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(
                        runtime.queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE"
                                    + " table_schema='public' AND table_name IN"
                                    + " ('users','groups','subjects','semesters','export_jobs')",
                                Integer.class))
                .isZero();
        assertThat(runtime.queryForObject("SELECT count(*) FROM pg_foreign_server", Integer.class))
                .isZero();
    }

    @Test
    void ownRuntimeCanAcquireExistingOutboxLeaseWithoutSchemaPrivileges() {
        var config =
                net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration
                        .builder()
                        .withJdbcTemplate(runtime)
                        .usingDbTime()
                        .build();
        var first =
                new net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider(config);
        var second =
                new net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider(config);
        var lease =
                new net.javacrumbs.shedlock.core.LockConfiguration(
                        Instant.now(),
                        "auth-outbox-" + UUID.randomUUID(),
                        java.time.Duration.ofSeconds(30),
                        java.time.Duration.ZERO);
        var acquired = first.lock(lease);
        assertThat(acquired).isPresent();
        try {
            assertThat(second.lock(lease)).isEmpty();
        } finally {
            acquired.orElseThrow().unlock();
        }
        var recovered = second.lock(lease);
        assertThat(recovered).isPresent();
        recovered.orElseThrow().unlock();
    }

    @Test
    void delayedPrepareCannotCrossAnAcknowledgedNegativeReservation() throws Exception {
        var command = create(UUID.randomUUID());
        long identities = owner.queryForObject("SELECT count(*) FROM auth_identities", Long.class);
        var reached = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        call -> {
                            reached.countDown();
                            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            return actor;
                        })
                .when(actors)
                .read();
        var pool = Executors.newSingleThreadExecutor();
        try {
            var late = pool.submit(() -> operations.prepare(command));
            assertThat(reached.await(5, TimeUnit.SECONDS)).isTrue();
            var negative = operations.rejectUnprepared(reject(command));
            assertThat(negative.getPhase())
                    .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_REJECTED);
            release.countDown();
            assertThat(late.get(5, TimeUnit.SECONDS)).isEqualTo(negative);
            assertThat(operations.get(get(command))).isEqualTo(negative);
            assertThat(owner.queryForObject("SELECT count(*) FROM auth_identities", Long.class))
                    .isEqualTo(identities);
            assertThat(count("auth_identity_operations", command)).isZero();
            assertThat(count("auth_identity_operation_targets", command)).isZero();
            var changed =
                    hash(
                            command.toBuilder()
                                    .setCreateIdentity(
                                            command.getCreateIdentity().toBuilder()
                                                    .setLoginPrefix("other-name")));
            assertThatThrownBy(() -> operations.prepare(changed))
                    .isInstanceOf(StatusRuntimeException.class);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void positivePrepareCannotBeCancelledByLateRejectionAndSecretsStayUnavailable() {
        var command = create(UUID.randomUUID());
        var prepared = operations.prepare(command);
        assertThat(operations.rejectUnprepared(reject(command))).isEqualTo(prepared);
        assertThat(count("auth_identity_operation_rejections", command)).isZero();
        long user = prepared.getTargets(0).getUserId();
        assertThat(
                        owner.queryForObject(
                                "SELECT account_state FROM auth_identities WHERE id=?",
                                String.class,
                                user))
                .isEqualTo("PENDING");
        assertThat(
                        owner.queryForObject(
                                "SELECT fence_operation_id IS NOT NULL FROM auth_identities WHERE"
                                        + " id=?",
                                Boolean.class,
                                user))
                .isTrue();
        var credentials = new AuthAdministrativeAuthorityQuery(runtime, actors);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactions)
                                        .execute(
                                                status ->
                                                        credentials.credentials(
                                                                ReadInitialCredentialsRequest
                                                                        .newBuilder()
                                                                        .addUserIds(user)
                                                                        .build())))
                .isInstanceOf(StatusRuntimeException.class);
        var wrongActor =
                reject(command).toBuilder()
                        .setActor(
                                reject(command).getActor().toBuilder()
                                        .setRolesVersion(actor.rolesVersion() + 1))
                        .build();
        assertThatThrownBy(() -> operations.rejectUnprepared(wrongActor))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void restartAndUnknownCommitOutcomeReplayOneStableIdentityGrantAndCatalogReceipt() {
        var command = create(UUID.randomUUID());
        var prepared = operations.prepare(command);
        var restartedActor = mock(AuthInternalActorReader.class);
        when(restartedActor.read()).thenThrow(new IllegalStateException("original bearer expired"));
        var restarted = store(restartedActor);
        assertThat(restarted.prepare(command)).isEqualTo(prepared);
        long user = prepared.getTargets(0).getUserId();
        long before = clock();
        var completed = restarted.commit(commit(command, prepared));
        assertThat(completed.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(completed.getCommittedCatalogVersion()).isEqualTo(before + 1);
        assertThat(completed.getTargets(0).getFinalRolesVersion()).isEqualTo(1);
        assertThat(
                        owner.queryForObject(
                                "SELECT roles_version FROM auth_identities WHERE id=?",
                                Long.class,
                                user))
                .isEqualTo(1);
        assertThat(
                        owner.queryForObject(
                                "SELECT account_state='ACTIVE' AND fence_operation_id IS NULL FROM"
                                        + " auth_identities WHERE id=?",
                                Boolean.class,
                                user))
                .isTrue();
        assertThat(completed.getTargets(0).getGrantsList())
                .isEqualTo(prepared.getTargets(0).getGrantsList());
        assertThat(restarted.commit(commit(command, prepared))).isEqualTo(completed);
        assertThat(restarted.get(get(command))).isEqualTo(completed);
        assertThat(restarted.prepare(command)).isEqualTo(completed);
        assertThat(clock()).isEqualTo(before + 1);
        assertThat(
                        owner.queryForObject(
                                "SELECT (projection->>'academic_revision')::bigint FROM"
                                        + " auth_identity_catalog_changes WHERE catalog_version=?",
                                Long.class,
                                completed.getCommittedCatalogVersion()))
                .isEqualTo(1);
        verifyNoInteractions(restartedActor);
    }

    @Test
    void addingTeacherPreservesFullStudentGrantWhileStatusAndGroupChangesStillUpdateIt() {
        var create = create(UUID.randomUUID());
        var ready = operations.commit(commit(create, operations.prepare(create)));
        var student = ready.getTargets(0).getGrants(0);
        Timestamp past = Timestamp.from(Instant.parse("2020-01-02T00:00:00Z"));
        owner.update(
                "UPDATE user_role_grants SET created_at=?,updated_at=? WHERE id=?",
                Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")),
                past,
                student.getGrantId());
        var studentBefore =
                owner.queryForMap(
                        "SELECT * FROM user_role_grants WHERE id=?", student.getGrantId());
        var addTeacher =
                roleChange(
                        ready,
                        List.of(
                                student,
                                AdmissionGrant.newBuilder()
                                        .setRole("TEACHER")
                                        .setStatus("ACTIVE")
                                        .build()));
        ready = operations.commit(commit(addTeacher, operations.prepare(addTeacher)));
        assertThat(ready.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(
                        owner.queryForMap(
                                "SELECT * FROM user_role_grants WHERE id=?", student.getGrantId()))
                .isEqualTo(studentBefore);
        var teacher =
                ready.getTargets(0).getGrantsList().stream()
                        .filter(g -> g.getRole().equals("TEACHER"))
                        .findFirst()
                        .orElseThrow();
        var teacherBefore =
                owner.queryForMap(
                        "SELECT * FROM user_role_grants WHERE id=?", teacher.getGrantId());
        for (var changed :
                List.of(
                        student.toBuilder().setStatus("SUSPENDED").build(),
                        student.toBuilder().setStatus("SUSPENDED").setGroupId(12).build())) {
            owner.update(
                    "UPDATE user_role_grants SET updated_at=? WHERE id=?",
                    past,
                    student.getGrantId());
            var change = roleChange(ready, List.of(changed, teacher));
            ready = operations.commit(commit(change, operations.prepare(change)));
            assertThat(ready.getPhase())
                    .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
            var after =
                    owner.queryForMap(
                            "SELECT * FROM user_role_grants WHERE id=?", student.getGrantId());
            assertThat(after)
                    .containsEntry("id", student.getGrantId())
                    .containsEntry("status", changed.getStatus().toLowerCase(Locale.ROOT))
                    .containsEntry("group_id", changed.getGroupId())
                    .containsEntry("created_at", studentBefore.get("created_at"));
            assertThat((Timestamp) after.get("updated_at")).isAfter(past);
            assertThat(
                            owner.queryForMap(
                                    "SELECT * FROM user_role_grants WHERE id=?",
                                    teacher.getGrantId()))
                    .isEqualTo(teacherBefore);
        }
    }

    private static PrepareIdentityOperationRequest roleChange(
            IdentityOperationResult ready, List<AdmissionGrant> grants) {
        var target = ready.getTargets(0);
        return hash(
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE)
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setUserId(target.getUserId())
                                        .setExpectedRolesVersion(target.getFinalRolesVersion())
                                        .setExpectedCredentialsVersion(
                                                target.getCredentialsVersion())
                                        .setExpectedTelegramBindingVersion(
                                                target.getTelegramBindingVersion())
                                        .setExpectedAcademicRevision(
                                                target.getExpectedAcademicRevision() + 1)
                                        .setDesiredAccountState(
                                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                        .setDesiredAdminProfile(
                                                AdmissionAdminProfile.getDefaultInstance())
                                        .addAllDesiredGrants(grants)));
    }

    @Test
    void naturalSessionTimeExpiryDoesNotLoseTheAcceptedFixedCommand() {
        var command = create(UUID.randomUUID());
        var prepared = operations.prepare(command);
        mutateActor(
                () ->
                        owner.update(
                                "UPDATE auth_sessions SET refresh_expires_at=? WHERE sid=?",
                                Timestamp.from(Instant.now().minusSeconds(10)),
                                actor.sessionId()));
        assertThat(operations.commit(commit(command, prepared)).getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
    }

    @Test
    void explicitLogoutAndAdministrativeGenerationRevocationKeepFutureAuthorityFenced() {
        for (boolean logout : List.of(true, false)) {
            if (!logout) seedActor();
            var command = create(UUID.randomUUID());
            var prepared = operations.prepare(command);
            mutateActor(
                    () -> {
                        if (logout)
                            owner.update(
                                    "UPDATE auth_sessions SET"
                                        + " revoked_at=NOW(),revoke_reason='CURRENT_LOGOUT' WHERE"
                                        + " sid=?",
                                    actor.sessionId());
                        else
                            AuthDatabaseFixture.unfence(
                                    owner,
                                    AuthDatabaseFixture.fence(owner, actor.userId()),
                                    actor.userId());
                    });
            var denied = operations.commit(commit(command, prepared));
            assertThat(denied.getPhase())
                    .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE);
            assertThat(denied.getErrorCode()).isEqualTo("ACTOR_AUTHORITY_CHANGED");
            assertThat(
                            owner.queryForObject(
                                    "SELECT account_state='PENDING' AND fence_operation_id IS NOT"
                                            + " NULL FROM auth_identities WHERE id=?",
                                    Boolean.class,
                                    prepared.getTargets(0).getUserId()))
                    .isTrue();
        }
    }

    @Test
    void twoUsersCannotPrepareTheSameFutureTelegramBindingConcurrently() throws Exception {
        var first = create(UUID.randomUUID());
        var firstReady = operations.commit(commit(first, operations.prepare(first)));
        var second = create(UUID.randomUUID());
        var secondReady = operations.commit(commit(second, operations.prepare(second)));
        long telegram = 8_000_000_000L + firstReady.getTargets(0).getUserId();
        var commands = List.of(binding(firstReady, telegram), binding(secondReady, telegram));
        var barrier = new CyclicBarrier(2);
        doAnswer(
                        call -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return actor;
                        })
                .when(actors)
                .read();
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> attempts = new ArrayList<>();
            for (var command : commands)
                attempts.add(
                        pool.submit(
                                () -> {
                                    try {
                                        return operations.prepare(command);
                                    } catch (StatusRuntimeException denied) {
                                        return denied;
                                    }
                                }));
            int winner = -1;
            for (int i = 0; i < attempts.size(); i++) {
                Object result = attempts.get(i).get(10, TimeUnit.SECONDS);
                if (result instanceof IdentityOperationResult accepted) {
                    assertThat(winner).isEqualTo(-1);
                    winner = i;
                    assertThat(accepted.getPhase())
                            .isEqualTo(
                                    IdentityOperationPhase
                                            .IDENTITY_OPERATION_PHASE_PREPARED_FENCED);
                } else
                    assertThat(((StatusRuntimeException) result).getStatus().getCode())
                            .isEqualTo(io.grpc.Status.Code.ALREADY_EXISTS);
            }
            assertThat(winner).isGreaterThanOrEqualTo(0);
            var winning = commands.get(winner);
            var losing = commands.get(1 - winner);
            doReturn(actor).when(actors).read();
            assertThat(count("auth_identity_operations", losing)).isZero();
            assertThat(
                            owner.queryForObject(
                                    "SELECT fence_operation_id IS NULL FROM auth_identities WHERE"
                                            + " id=?",
                                    Boolean.class,
                                    losing.getTargets(0).getUserId()))
                    .isTrue();
            assertThat(
                            owner.queryForObject(
                                    "SELECT user_id FROM auth_telegram_binding_reservations WHERE"
                                            + " telegram_id=?",
                                    Long.class,
                                    telegram))
                    .isEqualTo(winning.getTargets(0).getUserId());
            assertThat(
                            owner.queryForObject(
                                    "SELECT count(*) FROM auth_identities WHERE telegram_id=?",
                                    Integer.class,
                                    telegram))
                    .isZero();
            var conflictingCreate =
                    hash(
                            create(UUID.randomUUID()).toBuilder()
                                    .setCreateIdentity(
                                            create(UUID.randomUUID())
                                                    .getCreateIdentity()
                                                    .toBuilder()
                                                    .setTelegramId(telegram)));
            assertThatThrownBy(() -> operations.prepare(conflictingCreate))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            e ->
                                    assertThat(((StatusRuntimeException) e).getStatus().getCode())
                                            .isEqualTo(io.grpc.Status.Code.ALREADY_EXISTS));
            var prepared = operations.get(get(winning));
            operations.commit(commit(winning, prepared));
            assertThat(
                            owner.queryForObject(
                                    "SELECT telegram_id FROM auth_identities WHERE id=?",
                                    Long.class,
                                    winning.getTargets(0).getUserId()))
                    .isEqualTo(telegram);
            assertThat(count("auth_telegram_binding_reservations", winning)).isZero();
            assertThatThrownBy(() -> operations.prepare(losing))
                    .isInstanceOf(StatusRuntimeException.class);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void failedBindingKeepsReservationUntilFreshAuthorizedExactIntentRecoveryCompletes() {
        var create = create(UUID.randomUUID());
        var ready = operations.commit(commit(create, operations.prepare(create)));
        long telegram = 9_000_000_000L + ready.getTargets(0).getUserId();
        var change = binding(ready, telegram);
        var prepared = operations.prepare(change);
        mutateActor(
                () ->
                        owner.update(
                                "UPDATE auth_sessions SET"
                                        + " revoked_at=NOW(),revoke_reason='CURRENT_LOGOUT' WHERE"
                                        + " sid=?",
                                actor.sessionId()));
        assertThat(operations.commit(commit(change, prepared)).getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE);
        assertThat(count("auth_telegram_binding_reservations", change)).isEqualTo(1);
        seedActor();
        var recovery =
                hash(
                        change.toBuilder()
                                .setOperationId(UUID.randomUUID().toString())
                                .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE)
                                .setTargets(
                                        0,
                                        change.getTargets(0).toBuilder()
                                                .setExpectedRolesVersion(
                                                        prepared.getTargets(0)
                                                                .getFinalRolesVersion())
                                                .setSupersedesFailedOperationId(
                                                        change.getOperationId())));
        var recovered = operations.prepare(recovery);
        assertThat(count("auth_telegram_binding_reservations", change)).isZero();
        assertThat(count("auth_telegram_binding_reservations", recovery)).isEqualTo(1);
        assertThat(
                        owner.queryForObject(
                                "SELECT telegram_id IS NULL AND fence_operation_id IS NOT NULL FROM"
                                        + " auth_identities WHERE id=?",
                                Boolean.class,
                                ready.getTargets(0).getUserId()))
                .isTrue();
        var done = operations.commit(commit(recovery, recovered));
        assertThat(done.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(count("auth_telegram_binding_reservations", recovery)).isZero();
        assertThat(
                        owner.queryForObject(
                                "SELECT telegram_id FROM auth_identities WHERE id=?",
                                Long.class,
                                ready.getTargets(0).getUserId()))
                .isEqualTo(telegram);
        assertThat(
                        owner.queryForObject(
                                "SELECT telegram_binding_version FROM auth_identities WHERE id=?",
                                Long.class,
                                ready.getTargets(0).getUserId()))
                .isEqualTo(2);
    }

    @Test
    void pendingCreateAlreadyOwnsItsTelegramIdBeforeActivation() {
        long telegram = 7_000_000_000L + actor.userId();
        var command = create(UUID.randomUUID());
        command =
                hash(
                        command.toBuilder()
                                .setCreateIdentity(
                                        command.getCreateIdentity().toBuilder()
                                                .setTelegramId(telegram)));
        var pending = operations.prepare(command);
        var other = create(UUID.randomUUID());
        var ready = operations.commit(commit(other, operations.prepare(other)));
        var binding = binding(ready, telegram);
        assertThatThrownBy(() -> operations.prepare(binding))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        e ->
                                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                                        .isEqualTo(io.grpc.Status.Code.ALREADY_EXISTS));
        assertThat(
                        owner.queryForObject(
                                "SELECT account_state FROM auth_identities WHERE id=?",
                                String.class,
                                pending.getTargets(0).getUserId()))
                .isEqualTo("PENDING");
        assertThat(count("auth_telegram_binding_reservations", command)).isZero();
    }

    @Test
    void plainStudentArchiveKeepsOptionalPasswordAndRecordsOneFixedAdmission() {
        var create = create(UUID.randomUUID());
        var ready = operations.commit(commit(create, operations.prepare(create)));
        var command = archive(ready);
        var prepared = operations.prepare(command);
        assertThat(
                        owner.queryForObject(
                                "SELECT NOT step_up_required AND receipt_id IS NULL FROM"
                                        + " auth_identity_step_up_admissions WHERE operation_id=?",
                                Boolean.class,
                                UUID.fromString(command.getOperationId())))
                .isTrue();
        var completed = operations.commit(commit(command, prepared));
        assertThat(completed.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(
                        owner.queryForObject(
                                "SELECT account_state FROM auth_identities WHERE id=?",
                                String.class,
                                ready.getTargets(0).getUserId()))
                .isEqualTo("ARCHIVED");
        assertThat(operations.prepare(command)).isEqualTo(completed);
        assertThat(count("auth_identity_step_up_admissions", command)).isEqualTo(1);
    }

    @Test
    void dangerousArchiveRejectsMissingExpiredForeignPurposeAndGenerationProofsWithoutFences() {
        var ready = teacher();
        var missing = archive(ready);
        assertArchiveDenied(missing);
        for (String mismatch :
                List.of(
                        "EXPIRED",
                        "PURPOSE",
                        "TARGET",
                        "SESSION",
                        "SESSION_VERSION",
                        "DIGEST",
                        "PROFILE",
                        "CREDENTIALS",
                        "BINDING",
                        "ROLES")) {
            var command = archive(ready);
            UUID receipt = receipt(command, mismatch);
            var withProof =
                    hash(command.toBuilder().setPasswordStepUpReceiptId(receipt.toString()));
            assertArchiveDenied(withProof);
        }
        assertThat(
                        owner.queryForObject(
                                "SELECT account_state='ACTIVE' AND fence_operation_id IS NULL FROM"
                                        + " auth_identities WHERE id=?",
                                Boolean.class,
                                ready.getTargets(0).getUserId()))
                .isTrue();
    }

    @Test
    void archiveProofIsConsumedOnceAndAcceptedReplaySurvivesProofTimeAndBearerExpiry() {
        var command = archive(teacher());
        command =
                hash(
                        command.toBuilder()
                                .setPasswordStepUpReceiptId(receipt(command, "VALID").toString()));
        var prepared = operations.prepare(command);
        var unavailable = mock(AuthInternalActorReader.class);
        when(unavailable.read()).thenThrow(new IllegalStateException("original bearer expired"));
        var later =
                new AuthIdentityOperationStore(
                        runtime,
                        new ObjectMapper(),
                        transactions,
                        unavailable,
                        new BCryptPasswordEncoder(4),
                        new BcryptConcurrencyGuard(1, 0),
                        java.time.Clock.offset(
                                java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(5)));
        assertThat(later.prepare(command)).isEqualTo(prepared);
        var completed = later.commit(commit(command, prepared));
        assertThat(completed.getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(later.commit(commit(command, prepared))).isEqualTo(completed);
        assertThat(count("auth_identity_step_up_admissions", command)).isEqualTo(1);
        var changed =
                hash(command.toBuilder().setPasswordStepUpReceiptId(UUID.randomUUID().toString()));
        assertThatThrownBy(() -> later.prepare(changed)).isInstanceOf(StatusRuntimeException.class);
        verifyNoInteractions(unavailable);
    }

    @Test
    void archiveRecoveryNeedsFreshProofAndCurrentActorWhileKeepingOriginalBusinessIntent() {
        var original = archive(teacher());
        original =
                hash(
                        original.toBuilder()
                                .setPasswordStepUpReceiptId(receipt(original, "VALID").toString()));
        var prepared = operations.prepare(original);
        mutateActor(
                () ->
                        owner.update(
                                "UPDATE auth_sessions SET"
                                        + " revoked_at=NOW(),revoke_reason='CURRENT_LOGOUT' WHERE"
                                        + " sid=?",
                                actor.sessionId()));
        assertThat(operations.commit(commit(original, prepared)).getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE);
        seedActor();
        var recovery =
                hash(
                        original.toBuilder()
                                .setOperationId(UUID.randomUUID().toString())
                                .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE)
                                .setTargets(
                                        0,
                                        original.getTargets(0).toBuilder()
                                                .setExpectedRolesVersion(
                                                        prepared.getTargets(0)
                                                                .getFinalRolesVersion())
                                                .setExpectedAcademicRevision(2)
                                                .setSupersedesFailedOperationId(
                                                        original.getOperationId())));
        assertArchiveDenied(recovery); // The old receipt belongs to another operation and actor.
        assertThat(
                        owner.queryForObject(
                                "SELECT fence_operation_id FROM auth_identities WHERE id=?",
                                UUID.class,
                                prepared.getTargets(0).getUserId()))
                .isEqualTo(UUID.fromString(original.getOperationId()));
        var freshIntent =
                hash(
                        recovery.toBuilder()
                                .setOperationId(UUID.randomUUID().toString())
                                .clearPasswordStepUpReceiptId());
        var fresh =
                hash(
                        freshIntent.toBuilder()
                                .setPasswordStepUpReceiptId(
                                        receipt(freshIntent, "VALID").toString()));
        var recovered = operations.prepare(fresh);
        assertThat(operations.commit(commit(fresh, recovered)).getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);
        assertThat(count("auth_identity_step_up_admissions", original)).isEqualTo(1);
        assertThat(count("auth_identity_step_up_admissions", fresh)).isEqualTo(1);
    }

    private void assertArchiveDenied(PrepareIdentityOperationRequest command) {
        assertThatThrownBy(() -> operations.prepare(command))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        e ->
                                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                                        .isEqualTo(io.grpc.Status.Code.FAILED_PRECONDITION));
        assertThat(count("auth_identity_operations", command)).isZero();
        assertThat(count("auth_identity_step_up_admissions", command)).isZero();
    }

    private IdentityOperationResult teacher() {
        var command = create(UUID.randomUUID());
        command =
                hash(
                        command.toBuilder()
                                .setTargets(
                                        0,
                                        command.getTargets(0).toBuilder()
                                                .clearDesiredGrants()
                                                .addDesiredGrants(
                                                        AdmissionGrant.newBuilder()
                                                                .setRole("TEACHER")
                                                                .setStatus("ACTIVE"))));
        return operations.commit(commit(command, operations.prepare(command)));
    }

    private static PrepareIdentityOperationRequest archive(IdentityOperationResult ready) {
        var current = ready.getTargets(0);
        var target =
                IdentityTargetCommand.newBuilder()
                        .setUserId(current.getUserId())
                        .setExpectedRolesVersion(current.getFinalRolesVersion())
                        .setExpectedCredentialsVersion(current.getCredentialsVersion())
                        .setExpectedTelegramBindingVersion(current.getTelegramBindingVersion())
                        .setExpectedAcademicRevision(1)
                        .setDesiredAccountState(
                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED)
                        .setDesiredAdminProfile(AdmissionAdminProfile.getDefaultInstance());
        current.getGrantsList()
                .forEach(grant -> target.addDesiredGrants(grant.toBuilder().setStatus("ARCHIVED")));
        return hash(
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE)
                        .addTargets(target)
                        .setArchiveStepUpProtocolVersion(1)
                        .setArchivePreviewDigest("a".repeat(64)));
    }

    /** Synthetic already-confirmed security receipt; password issuer has separate HTTP tests. */
    private UUID receipt(PrepareIdentityOperationRequest command, String mismatch) {
        UUID receipt = UUID.randomUUID();
        Instant now = Instant.now();
        var versions =
                owner.queryForMap(
                        "SELECT credentials_version,telegram_binding_version FROM auth_identities"
                                + " WHERE id=?",
                        actor.userId());
        owner.update(
                """
INSERT INTO auth_password_step_up_receipts(receipt_id,purpose,target_id,operation_id,preview_digest,
 actor_id,session_id,session_version,roles_version,credentials_version,binding_version,admin_profile_version,created_at,expires_at)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
                receipt,
                mismatch.equals("PURPOSE") ? "SEMESTER_DELETE" : "USER_ARCHIVE",
                command.getTargets(0).getUserId() + (mismatch.equals("TARGET") ? 1 : 0),
                UUID.fromString(command.getOperationId()),
                mismatch.equals("DIGEST") ? "b".repeat(64) : command.getArchivePreviewDigest(),
                actor.userId(),
                mismatch.equals("SESSION") ? UUID.randomUUID() : actor.sessionId(),
                actor.sessionVersion() + (mismatch.equals("SESSION_VERSION") ? 1 : 0),
                actor.rolesVersion() + (mismatch.equals("ROLES") ? 1 : 0),
                ((Number) versions.get("credentials_version")).longValue()
                        + (mismatch.equals("CREDENTIALS") ? 1 : 0),
                ((Number) versions.get("telegram_binding_version")).longValue()
                        + (mismatch.equals("BINDING") ? 1 : 0),
                mismatch.equals("PROFILE") ? 2 : 1,
                Timestamp.from(now.minusSeconds(120)),
                Timestamp.from(now.plusSeconds(mismatch.equals("EXPIRED") ? -60 : 60)));
        return receipt;
    }

    private static PrepareIdentityOperationRequest binding(
            IdentityOperationResult ready, long telegram) {
        var target = ready.getTargets(0);
        return hash(
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_TELEGRAM_BINDING)
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setUserId(target.getUserId())
                                        .setExpectedRolesVersion(target.getFinalRolesVersion())
                                        .setExpectedCredentialsVersion(
                                                target.getCredentialsVersion())
                                        .setExpectedTelegramBindingVersion(
                                                target.getTelegramBindingVersion())
                                        .setExpectedAcademicRevision(1)
                                        .setDesiredAccountState(
                                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                        .setDesiredAdminProfile(
                                                AdmissionAdminProfile.getDefaultInstance())
                                        .addAllDesiredGrants(target.getGrantsList())
                                        .setTelegramChange(
                                                TelegramBindingChange.newBuilder()
                                                        .setTelegramId(telegram))));
    }

    private void mutateActor(Runnable change) {
        new TransactionTemplate(new DataSourceTransactionManager(owner.getDataSource()))
                .executeWithoutResult(
                        status -> {
                            owner.queryForList(
                                    "SELECT id FROM auth_identities WHERE id=? FOR UPDATE",
                                    Long.class,
                                    actor.userId());
                            change.run();
                        });
    }

    private AuthIdentityOperationStore store(AuthInternalActorReader reader) {
        return new AuthIdentityOperationStore(
                runtime,
                new ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .disable(
                                com.fasterxml.jackson.databind.SerializationFeature
                                        .WRITE_DATES_AS_TIMESTAMPS),
                transactions,
                reader,
                new BCryptPasswordEncoder(4),
                new BcryptConcurrencyGuard(1, 0));
    }

    private PrepareIdentityOperationRequest create(UUID id) {
        return hash(
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(id.toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE)
                        .setCreateIdentity(
                                CreateIdentityMaterial.newBuilder()
                                        .setLoginPrefix("same-name")
                                        .setLoginDigits("0042"))
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setDesiredAccountState(
                                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                        .setDesiredAdminProfile(
                                                AdmissionAdminProfile.getDefaultInstance())
                                        .addDesiredGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setRole("STUDENT")
                                                        .setStatus("ACTIVE")
                                                        .setGroupId(11))));
    }

    private static PrepareIdentityOperationRequest hash(
            PrepareIdentityOperationRequest.Builder value) {
        try {
            var canonical = value.clearCommandHash().build();
            return canonical.toBuilder()
                    .setCommandHash(
                            ByteString.copyFrom(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(canonical.toByteArray())))
                    .build();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private RejectUnpreparedIdentityOperationRequest reject(
            PrepareIdentityOperationRequest command) {
        return RejectUnpreparedIdentityOperationRequest.newBuilder()
                .setCanonicalPrepare(command)
                .setReason(
                        IdentityOperationRejectionReason
                                .IDENTITY_OPERATION_REJECTION_REASON_ACADEMIC_REJECTED)
                .setActor(
                        IdentityOperationActor.newBuilder()
                                .setUserId(actor.userId())
                                .setSessionId(actor.sessionId().toString())
                                .setSessionVersion(actor.sessionVersion())
                                .setRolesVersion(actor.rolesVersion()))
                .build();
    }

    private static CommitIdentityOperationRequest commit(
            PrepareIdentityOperationRequest command, IdentityOperationResult prepared) {
        var target = prepared.getTargets(0);
        return CommitIdentityOperationRequest.newBuilder()
                .setOperationId(command.getOperationId())
                .setCommandHash(command.getCommandHash())
                .setExpectedOperationRevision(prepared.getOperationRevision())
                .addReceipts(
                        AcademicIdentityCommitReceipt.newBuilder()
                                .setUserId(target.getUserId())
                                .setPreviousAcademicRevision(target.getExpectedAcademicRevision())
                                .setCommittedAcademicRevision(
                                        target.getExpectedAcademicRevision() + 1)
                                .setAppliedAuthRolesVersion(target.getFinalRolesVersion()))
                .build();
    }

    private static GetIdentityOperationRequest get(PrepareIdentityOperationRequest command) {
        return GetIdentityOperationRequest.newBuilder()
                .setOperationId(command.getOperationId())
                .setCommandHash(command.getCommandHash())
                .build();
    }

    private static long count(String table, PrepareIdentityOperationRequest command) {
        return owner.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE operation_id=?",
                Long.class,
                UUID.fromString(command.getOperationId()));
    }

    private static long clock() {
        return owner.queryForObject(
                "SELECT version FROM auth_identity_catalog_clock WHERE singleton=1", Long.class);
    }

    private static DriverManagerDataSource dataSource(
            String database, String user, String password) {
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl().replace("/academic_db", "/" + database), user, password);
    }

    private static Path bootstrap() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path script = current.resolve("infra/postgres/init-roles.sh");
            if (Files.isRegularFile(script)) return script;
            current = current.getParent();
        }
        throw new IllegalStateException("PostgreSQL first-volume bootstrap source missing");
    }
}
