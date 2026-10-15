package ru.rutcampustrack.academic.integration;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

/** Resets only the fresh database owned by these integration-test bases. */
public final class AcademicFixtureResetListener extends AbstractTestExecutionListener {
    private static final List<String> TABLES =
            List.of(
                    "account_security_events",
                    "academic_outbox",
                    "academic_semester_archive_barriers",
                    "academic_semester_archive_effect_receipts",
                    "academic_semester_archive_publication_admissions",
                    "admin_access_profiles",
                    "assignment_replacement_operations",
                    "assignments",
                    "attendance_thresholds",
                    "audit_log",
                    "auth_export_jobs",
                    "auth_outbox",
                    "auth_sessions",
                    "bot_credential_messages",
                    "campus_map_activity_coverage",
                    "campus_map_asset",
                    "campus_map_building",
                    "campus_map_catalog_revision",
                    "campus_map_deletion_receipt",
                    "campus_map_floor",
                    "campus_map_floor_daily_demand",
                    "campus_map_floor_demand_dedupe",
                    "campus_map_floor_hourly_activity",
                    "campus_map_open_intent",
                    "campus_map_plan_format",
                    "campus_map_plan_version",
                    "campus_settings",
                    "dashboard_scale_observations",
                    "event_consumer_processed",
                    "group_history_coverage",
                    "group_promotion_cycle_record",
                    "group_promotion_notification_cycle",
                    "groups",
                    "headman_assistants",
                    "homework_binding_archives",
                    "homework_binding_transfer_history",
                    "homework_binding_transfer_markers",
                    "homework_completions",
                    "homework_edit_archive_admissions",
                    "homework_edit_history",
                    "homework_edit_operations",
                    "homework_edit_receipts",
                    "homework_weekly_digest_runs",
                    "homeworks",
                    "lesson_transfer_receipts",
                    "password_reset_tokens",
                    "qr_login_challenges",
                    "qr_login_issuers",
                    "semester_archive_operations",
                    "semesters",
                    "shedlock",
                    "student_group_history",
                    "subject_lesson_types",
                    "subjects",
                    "teacher_subject_groups",
                    "user_archive_preview",
                    "user_archive_receipt",
                    "user_role_grants",
                    "users");

    // Committed fixture reset precedes Spring's test transaction (order 4000).
    @Override
    public int getOrder() {
        return 3500;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        PostgreSQLContainer<?> owner =
                AbstractAcademicEventIntegrationTest.class.isAssignableFrom(
                                testContext.getTestClass())
                        ? AbstractAcademicEventIntegrationTest.POSTGRES
                        : AbstractAcademicCacheIntegrationTest.class.isAssignableFrom(
                                        testContext.getTestClass())
                                ? AbstractAcademicCacheIntegrationTest.POSTGRES
                                : AbstractAcademicIntegrationTest.POSTGRES;
        DataSource source = testContext.getApplicationContext().getBean(DataSource.class);
        try (Connection connection = source.getConnection()) {
            if (!owner.isRunning()
                    || !owner.getDatabaseName().startsWith("academic_ci_")
                    || !owner.getJdbcUrl().equals(connection.getMetaData().getURL())
                    || !owner.getDatabaseName().equals(connection.getCatalog())) {
                throw new IllegalStateException(
                        "Fixture reset requires this test's fresh owned PostgreSQL");
            }
            List<String> actual =
                    new JdbcTemplate(source)
                            .queryForList(
                                    "SELECT tablename FROM pg_tables WHERE schemaname='public' "
                                            + "AND tablename<>'flyway_schema_history' ORDER BY tablename",
                                    String.class);
            if (!TABLES.containsAll(actual) || actual.size() != TABLES.size()) {
                throw new IllegalStateException(
                        "Application table inventory changed; review fixture reset scope");
            }
            connection.setAutoCommit(false);
            try {
                connection
                        .createStatement()
                        .execute(
                                "TRUNCATE TABLE "
                                        + TABLES.stream()
                                                .map(table -> "public." + table)
                                                .collect(java.util.stream.Collectors.joining(", "))
                                        + " RESTART IDENTITY");
                ScriptUtils.executeSqlScript(
                        connection, new ClassPathResource("academic-fixture-seed.sql"));
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
        if (AbstractAcademicCacheIntegrationTest.class.isAssignableFrom(
                testContext.getTestClass())) {
            var redis = AbstractAcademicCacheIntegrationTest.REDIS;
            var factory =
                    testContext
                            .getApplicationContext()
                            .getBean(
                                    org.springframework.data.redis.connection.RedisConnectionFactory
                                            .class);
            if (!redis.isRunning()
                    || !(factory
                            instanceof
                            org.springframework.data.redis.connection.lettuce
                                            .LettuceConnectionFactory
                                    lettuce)
                    || !redis.getHost().equals(lettuce.getHostName())
                    || redis.getMappedPort(6379) != lettuce.getPort()) {
                throw new IllegalStateException(
                        "Cache reset requires this test's fresh owned Redis");
            }
        } else if (testContext
                        .getApplicationContext()
                        .getBeansOfType(
                                org.springframework.data.redis.cache.RedisCacheManager.class)
                        .size()
                > 0) {
            throw new IllegalStateException("Fixture reset cannot clear an unowned Redis cache");
        }
        testContext
                .getApplicationContext()
                .getBeansOfType(CacheManager.class)
                .values()
                .forEach(
                        manager ->
                                manager.getCacheNames()
                                        .forEach(
                                                name -> {
                                                    var cache = manager.getCache(name);
                                                    if (cache != null) cache.clear();
                                                }));

        // Direct administrative service calls have a real request scope too.
        // Newly created race threads inherit this same admitted ADMIN identity.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()), true);
        RequestContext context = testContext.getApplicationContext().getBean(RequestContext.class);
        if (!org.mockito.Mockito.mockingDetails(context).isMock()) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            long adminId =
                    jdbc.queryForObject("SELECT id FROM users WHERE login='admin'", Long.class);
            long rolesVersion =
                    jdbc.queryForObject(
                            "SELECT roles_version FROM users WHERE id=?", Long.class, adminId);
            long sessionVersion = 1L; // Version of the fresh selected auth_sessions row.
            UUID session = AcademicTestAdminRequest.selectedSession(jdbc, adminId, sessionVersion);
            context.setClaims(
                    new InternalJwtClaims(
                            adminId,
                            session,
                            sessionVersion,
                            rolesVersion,
                            "ADMIN",
                            "ACTIVE",
                            null,
                            false,
                            false));
            context.setUserId(adminId);
            context.setRole(UserRole.ADMIN);
            context.setGroupId(null);
            context.setHeadman(false);
            InternalJwtTestFactory signer =
                    testContext.getApplicationContext().getBean(InternalJwtTestFactory.class);
            context.setInternalToken(
                    signer.validToken(
                            adminId,
                            session,
                            sessionVersion,
                            rolesVersion,
                            "ADMIN",
                            "ACTIVE",
                            null,
                            false,
                            false));
        }
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        RequestContextHolder.resetRequestAttributes();
    }
}
