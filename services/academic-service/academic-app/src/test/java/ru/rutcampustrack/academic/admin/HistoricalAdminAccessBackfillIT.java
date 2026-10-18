package ru.rutcampustrack.academic.admin;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Historical V50-to-V51 compatibility only; current own-DB behavior uses the shared split fixture.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoricalAdminAccessBackfillIT {
    private final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withLabel("rct.package", "admin-permissions-20261008");
    private JdbcTemplate jdbc;
    private JdbcTemplate runtimeJdbc;
    private TransactionTemplate tx;
    private AdminAccessJdbcAuthority authority;
    private UserRoleGrantWriter grants;
    private RequestContext context;
    private AdminAccessService service;
    private org.springframework.context.ApplicationEventPublisher publisher;

    @BeforeAll
    void start() {
        postgres.start();
        ru.rutcampustrack.academic.migration.OwnedAuthPrincipals.prepare(postgres);
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @BeforeEach
    void schema() throws Exception {
        DriverManagerDataSource source =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        ru.rutcampustrack.academic.migration.OwnedAuthPrincipals.cleanCommands(jdbc);
        ru.rutcampustrack.shared.testcontainers.MigrationTestUtils.clean(source);
        org.flywaydb.core.Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration")
                .target("50")
                .load()
                .migrate();
        // V51 backfill remains real migration evidence, not a profile manufactured by a new
        // command.
        jdbc.execute("TRUNCATE public.groups RESTART IDENTITY CASCADE");
        for (long id = 1; id <= 6; id++)
            jdbc.update(
                    "INSERT INTO"
                        + " users(id,login,last_name,first_name,role,initial_password,password_hash)"
                        + " VALUES (?,?,"
                        + " 'Synthetic','Fixture',?::user_role,'synthetic-initial','synthetic-hash')",
                    id,
                    "admin_fixture_" + id,
                    id <= 3 ? "admin" : "teacher");
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at) VALUES"
                    + " (1,'admin','active',NOW(),NOW()),(2,'admin','active',NOW(),NOW()),(3,'admin','archived',NOW(),NOW()),(4,'teacher','active',NOW(),NOW())");
        org.flywaydb.core.Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        var runtime =
                ru.rutcampustrack.academic.migration.OwnedAuthPrincipals.source(
                        postgres, "rct_academic_runtime");
        tx = new TransactionTemplate(new DataSourceTransactionManager(runtime));
        runtimeJdbc = new JdbcTemplate(runtime);
        authority = new AdminAccessJdbcAuthority(runtimeJdbc);
        context = new RequestContext();
        grants =
                new UserRoleGrantWriter(
                        new AcademicAuthDataCommands(runtimeJdbc, context), event -> {});
        publisher =
                org.mockito.Mockito.mock(
                        org.springframework.context.ApplicationEventPublisher.class);
        service = service(context);
        select(context, 1);
    }

    private AdminAccessService service(RequestContext ctx) {
        return new AdminAccessService(
                runtimeJdbc,
                authority,
                new AcademicAdminAccess(authority, ctx, runtimeJdbc),
                ctx,
                grants,
                publisher,
                new AcademicAuthDataCommands(runtimeJdbc, ctx));
    }

    private void select(RequestContext ctx, long id) {
        UUID session = UUID.randomUUID();
        Long grant =
                jdbc.queryForObject(
                        "SELECT id FROM user_role_grants WHERE user_id=? AND role='admin'",
                        Long.class,
                        id);
        jdbc.update(
                "INSERT INTO"
                    + " auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)"
                    + " VALUES (?,?,?,1,?,NOW()+INTERVAL '1 day',NOW(),NOW(),'PASSWORD')",
                session,
                id,
                grant,
                UUID.randomUUID());
        ctx.setRole(UserRole.ADMIN);
        ctx.setUserId(id);
        ctx.setClaims(
                new InternalJwtClaims(
                        id,
                        session,
                        1,
                        jdbc.queryForObject(
                                "SELECT roles_version FROM users WHERE id=?", Long.class, id),
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false));
    }

    private AdminAccessProfile put(
            long target, String mode, List<String> permissions, long version) {
        return tx.execute(
                status -> service.put(target, new AdminAccessInput(mode, permissions), version));
    }

    @Test
    void migrationMakesOnlyExistingActiveAdministratorsFullAndKeepsRevokedTombstone() {
        assertThat(authority.profile(1).full()).isTrue();
        assertThat(authority.profile(2).permissions())
                .containsExactlyElementsOf(AdminPermission.allCodes());
        assertThat(authority.profile(3))
                .isEqualTo(new AdminAccessProfile(3, false, null, List.of(), 1));
        assertThat(authority.profile(4).version()).isZero();
        jdbc.update("DELETE FROM admin_access_profiles WHERE user_id=2");
        RequestContext missing = new RequestContext();
        select(missing, 2);
        assertThatThrownBy(() -> authority.current(missing.getClaims()))
                .isInstanceOf(AdminAccessException.class);
    }
}
