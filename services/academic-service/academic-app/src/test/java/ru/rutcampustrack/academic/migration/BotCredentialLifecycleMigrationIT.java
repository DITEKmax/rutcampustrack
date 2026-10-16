package ru.rutcampustrack.academic.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real V57/Flyway/PostgreSQL proof. No Academic/Auth/bot/provider cross-layer claim. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class BotCredentialLifecycleMigrationIT {
    private final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("np01_common_disposable")
                    .withUsername("rct_user")
                    .withPassword("rct_dev_pass")
                    .withLabel("rct.owner", "np01-contract-20261011");
    private final AtomicInteger schemaSequence = new AtomicInteger();
    private DriverManagerDataSource ds;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeAll
    void start() {
        postgres.start();
        System.out.println("NP01_OWN_CONTAINER_ID=" + postgres.getContainerId());
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @Test
    void freshFullChainAndMetadataOnlyTableValidateAndRepeat() {
        fresh(null);
        Flyway f = flyway(null);
        f.validate();
        assertThat(f.info().current().getVersion().getVersion()).isEqualTo("60");
        assertThat(f.info().pending()).isEmpty();
        assertThat(f.migrate().migrationsExecuted).isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT column_name FROM information_schema.columns WHERE"
                                    + " table_schema=? AND table_name='bot_credential_messages'",
                                String.class,
                                schema))
                .doesNotContain(
                        "login",
                        "password",
                        "initial_password",
                        "password_hash",
                        "body",
                        "payload");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE"
                                    + " table_schema=? AND table_name='bot_credential_messages' AND"
                                    + " constraint_type='FOREIGN KEY'",
                                Integer.class,
                                schema))
                .isZero();
    }

    @Test
    void historicalV56UpgradePreservesRowsAndOldChecksums() {
        fresh("56");
        long u = user(71101);
        long changed = user(71116);
        jdbc.update(
                "UPDATE users SET initial_password=NULL,password_changed=TRUE,telegram_id=NULL"
                        + " WHERE id=?",
                changed);
        var before =
                jdbc.queryForList(
                        "SELECT installed_rank,version,checksum FROM flyway_schema_history ORDER BY"
                                + " installed_rank");
        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(4);
        flyway(null).validate();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT credentials_version FROM users WHERE id=?", Long.class, u))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT telegram_binding_version FROM users WHERE id=?",
                                Long.class,
                                u))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password IS NOT NULL AND NOT password_changed FROM"
                                        + " users WHERE id=?",
                                Boolean.class,
                                u))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password IS NULL AND password_changed AND"
                                        + " telegram_id IS NULL FROM users WHERE id=?",
                                Boolean.class,
                                changed))
                .isTrue();
        assertThat(version(changed, "credentials_version")).isEqualTo(1);
        assertThat(version(changed, "telegram_binding_version")).isEqualTo(1);
        assertThat(
                        jdbc.queryForList(
                                "SELECT installed_rank,version,checksum FROM flyway_schema_history"
                                        + " ORDER BY installed_rank LIMIT ?",
                                before.size()))
                .isEqualTo(before);
    }

    @Test
    void actualTupleChangesAdvanceOnlyTheirOwnGeneration() {
        fresh(null);
        long u = user(71102);
        jdbc.update(
                "UPDATE users SET first_name='renamed',roles_version=roles_version+1 WHERE id=?",
                u);
        assertThat(version(u, "credentials_version")).isEqualTo(1);
        assertThat(version(u, "telegram_binding_version")).isEqualTo(1);
        mutate("generations", "DROP TRIGGER users_credential_generations ON users");
        jdbc.update("UPDATE users SET password_hash='synthetic-hash-2' WHERE id=?", u);
        assertThat(version(u, "credentials_version")).isEqualTo(2);
        assertThat(version(u, "telegram_binding_version")).isEqualTo(1);
        jdbc.update(
                "UPDATE users SET password_hash=password_hash,initial_password=initial_password"
                        + " WHERE id=?",
                u);
        assertThat(version(u, "credentials_version")).isEqualTo(2);
        jdbc.update("UPDATE users SET initial_password=NULL,password_changed=TRUE WHERE id=?", u);
        assertThat(version(u, "credentials_version")).isEqualTo(3);
        jdbc.update("UPDATE users SET telegram_id=NULL WHERE id=?", u);
        assertThat(version(u, "telegram_binding_version")).isEqualTo(2);
        jdbc.update("UPDATE users SET telegram_id=NULL WHERE id=?", u);
        assertThat(version(u, "telegram_binding_version")).isEqualTo(2);
        jdbc.update("UPDATE users SET telegram_id=71103 WHERE id=?", u);
        assertThat(version(u, "telegram_binding_version")).isEqualTo(3);
        assertThat(version(u, "credentials_version")).isEqualTo(3);
        assertThatThrownBy(
                        () -> jdbc.update("UPDATE users SET credentials_version=100 WHERE id=?", u))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rollbackAndCommitCoverVersionInvalidationAndOutboxTogether() throws Exception {
        fresh(null);
        long u = user(71104);
        long r = card(u, 71104, 101);
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            execute(
                    c,
                    "UPDATE users SET initial_password=NULL,password_changed=TRUE WHERE id=" + u);
            execute(
                    c,
                    "INSERT INTO auth_outbox(event_type,payload) VALUES ('password.changed',"
                        + "'{\"event_id\":\"00000000-0000-4000-8000-000000000004\",\"event_version\":2}')");
            c.rollback();
        }
        assertThat(version(u, "credentials_version")).isEqualTo(1);
        assertThat(state(r, "cleanup_state")).isEqualTo("NOT_REQUIRED");
        assertThat(revision(r)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_outbox", Integer.class)).isZero();
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            execute(
                    c,
                    "UPDATE users SET initial_password=NULL,password_changed=TRUE WHERE id=" + u);
            execute(
                    c,
                    "INSERT INTO auth_outbox(event_type,payload) VALUES ('password.changed',"
                        + "'{\"event_id\":\"00000000-0000-4000-8000-000000000004\",\"event_version\":2}')");
            c.commit();
        }
        assertThat(version(u, "credentials_version")).isEqualTo(2);
        assertThat(state(r, "cleanup_state")).isEqualTo("PENDING");
        assertThat(invalidation(r)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_outbox", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void credentialChangeInvalidatesUnsettledCardsAndPreservesFirstFence() {
        fresh(null);
        long u = user(71105);
        long a = card(u, 71105, 101);
        long b = card(u, 71105, 102);
        render(b, "UNCERTAIN");
        mutate("invalidation", "DROP TRIGGER users_invalidate_bot_credential_messages ON users");
        jdbc.update("UPDATE users SET password_hash='synthetic-hash-2' WHERE id=?", u);
        assertThat(state(a, "cleanup_state")).isEqualTo("PENDING");
        assertThat(state(a, "render_state")).isEqualTo("IN_FLIGHT");
        assertThat(state(b, "cleanup_state")).isEqualTo("PENDING");
        assertThat(state(b, "render_state")).isEqualTo("UNCERTAIN");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT render_revision FROM bot_credential_messages WHERE"
                                        + " record_id=?",
                                Long.class,
                                a))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM bot_credential_messages WHERE user_id=? AND"
                                        + " cleanup_state='PENDING' AND"
                                        + " invalidated_by_credentials_version<=2",
                                Integer.class,
                                u))
                .isEqualTo(2);
        jdbc.update("UPDATE users SET password_hash='synthetic-hash-3' WHERE id=?", u);
        assertThat(invalidation(a)).isEqualTo(2);
        assertThat(invalidation(b)).isEqualTo(2);
    }

    @Test
    void sanitationIsRejectedWhileRenderIsInFlightOrUncertain() {
        fresh(null);
        long u = user(71106);
        long a = card(u, 71106, 101);
        long b = card(u, 71106, 102);
        render(b, "UNCERTAIN");
        invalidate(u);
        mutate(
                "sanitation",
                "ALTER TABLE bot_credential_messages DROP CONSTRAINT"
                        + " bot_credential_messages_sanitation_settled");
        assertThatThrownBy(() -> cleanup(a, "SANITIZED")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cleanup(b, "SANITIZED")).isInstanceOf(RuntimeException.class);
        assertThat(state(a, "cleanup_state")).isEqualTo("PENDING");
        assertThat(state(b, "cleanup_state")).isEqualTo("PENDING");
    }

    @Test
    void confirmedDeletionAndAbsenceSettleUnresolvedCardsAndLateReceiptKeepsCleanup() {
        fresh(null);
        long u = user(71107);
        long a = card(u, 71107, 101);
        long b = card(u, 71107, 102);
        render(b, "UNCERTAIN");
        invalidate(u);
        cleanup(a, "DELETED");
        cleanup(b, "ABSENT");
        assertThat(state(a, "cleanup_state")).isEqualTo("CLEANED");
        assertThat(state(b, "cleanup_state")).isEqualTo("CLEANED");
        render(a, "CONFIRMED_EDIT");
        assertThat(state(a, "cleanup_state")).isEqualTo("CLEANED");
        assertThat(state(a, "cleanup_outcome")).isEqualTo("DELETED");
        assertThatThrownBy(() -> card(u, 71107, 101)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "DELETE FROM bot_credential_messages WHERE record_id=?", a))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void sanitationIsAllowedOnlyForDefinitivelySettledRender() {
        fresh(null);
        long u = user(71108);
        long a = card(u, 71108, 101);
        long b = card(u, 71108, 102);
        render(a, "CONFIRMED_EDIT");
        render(b, "NO_EDIT");
        invalidate(u);
        cleanup(a, "SANITIZED");
        cleanup(b, "SANITIZED");
        assertThat(state(a, "cleanup_state")).isEqualTo("CLEANED");
        assertThat(state(b, "cleanup_state")).isEqualTo("CLEANED");
    }

    @Test
    void unlinkArchiveAndFinalDeleteRetainOriginalCleanupIdentity() {
        fresh(null);
        long u = user(71109);
        long a = card(u, 71109, 101);
        jdbc.update("UPDATE users SET telegram_id=NULL WHERE id=?", u);
        assertThat(invalidation(a)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT private_chat_id FROM bot_credential_messages WHERE"
                                        + " record_id=?",
                                Long.class,
                                a))
                .isEqualTo(71109);
        long v = user(71110);
        long b = card(v, 71110, 101);
        jdbc.update("UPDATE users SET status='archived' WHERE id=?", v);
        assertThat(invalidation(b)).isEqualTo(1);
        long w = user(71111);
        long c = card(w, 71111, 101);
        jdbc.update("DELETE FROM user_role_grants WHERE user_id=?", w);
        jdbc.update("DELETE FROM users WHERE id=?", w);
        assertThat(state(c, "cleanup_state")).isEqualTo("PENDING");
        assertThat(invalidation(c)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT user_id FROM bot_credential_messages WHERE record_id=?",
                                Long.class,
                                c))
                .isEqualTo(w);
        cleanup(c, "DELETED");
        assertThat(state(c, "cleanup_state")).isEqualTo("CLEANED");
    }

    @Test
    void identityAttemptCasAndTerminalReceiptsCannotBeRewritten() {
        fresh(null);
        long u = user(71112);
        long r = card(u, 71112, 101);
        mutate(
                "identity",
                "DROP TRIGGER bot_credential_messages_lifecycle ON bot_credential_messages");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET"
                                            + " message_id=102,record_revision=record_revision+1"
                                            + " WHERE record_id=?",
                                        r))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET"
                                            + " render_revision=2,record_revision=record_revision+1"
                                            + " WHERE record_id=?",
                                        r))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET updated_at=NOW() WHERE"
                                                + " record_id=?",
                                        r))
                .isInstanceOf(RuntimeException.class);
        render(r, "UNCERTAIN");
        assertThatThrownBy(() -> render(r, "CONFIRMED_EDIT")).isInstanceOf(RuntimeException.class);
        invalidate(u);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET"
                                            + " invalidated_by_credentials_version=3,record_revision=record_revision+1"
                                            + " WHERE record_id=?",
                                        r))
                .isInstanceOf(RuntimeException.class);
        cleanup(r, "DELETED");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET"
                                            + " cleanup_outcome='ABSENT',record_revision=record_revision+1"
                                            + " WHERE record_id=?",
                                        r))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownStatesMissingReceiptsAndNullInvalidationCannotCompleteCleanup() {
        fresh(null);
        long u = user(71117);
        long r = card(u, 71117, 101);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET"
                                            + " cleanup_state='PENDING',invalidated_at=NOW(),record_revision=record_revision+1"
                                            + " WHERE record_id=?",
                                        r))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> render(r, "UNKNOWN")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET"
                                            + " cleanup_state='UNKNOWN',record_revision=record_revision+1"
                                            + " WHERE record_id=?",
                                        r))
                .isInstanceOf(RuntimeException.class);
        invalidate(u);
        assertThatThrownBy(() -> cleanup(r, "UNKNOWN")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET"
                                            + " cleanup_state='CLEANED',cleaned_at=NOW(),cleanup_event_id=?,record_revision=record_revision+1"
                                            + " WHERE record_id=?",
                                        UUID.fromString("00000000-0000-4000-8000-000000000001"),
                                        r))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE bot_credential_messages SET"
                                            + " invalidated_by_credentials_version=NULL,invalidated_at=NULL,record_revision=record_revision+1"
                                            + " WHERE record_id=?",
                                        r))
                .isInstanceOf(RuntimeException.class);
        assertThat(state(r, "cleanup_state")).isEqualTo("PENDING");
    }

    @Test
    void staleCleanupCasCannotCompleteAfterRenderSettlement() {
        fresh(null);
        long u = user(71118);
        long r = card(u, 71118, 101);
        invalidate(u);
        long observedRevision = revision(r);
        render(r, "CONFIRMED_EDIT");
        int stale =
                jdbc.update(
                        "UPDATE bot_credential_messages SET"
                            + " cleanup_state='CLEANED',cleaned_at=NOW(),cleanup_event_id=?,cleanup_outcome='SANITIZED',record_revision=record_revision+1,updated_at=NOW()"
                            + " WHERE record_id=? AND record_revision=?",
                        UUID.fromString("00000000-0000-4000-8000-000000000001"),
                        r,
                        observedRevision);
        assertThat(stale).isZero();
        assertThat(state(r, "cleanup_state")).isEqualTo("PENDING");
        int current =
                jdbc.update(
                        "UPDATE bot_credential_messages SET"
                            + " cleanup_state='CLEANED',cleaned_at=NOW(),cleanup_event_id=?,cleanup_outcome='SANITIZED',record_revision=record_revision+1,updated_at=NOW()"
                            + " WHERE record_id=? AND record_revision=?",
                        UUID.fromString("00000000-0000-4000-8000-000000000001"),
                        r,
                        revision(r));
        assertThat(current).isEqualTo(1);
        assertThat(state(r, "cleanup_state")).isEqualTo("CLEANED");
    }

    @Test
    void overflowFailsWithoutReusingGenerationOrChangingCredentialTuple() {
        fresh(null);
        long a =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " users(login,last_name,first_name,credentials_version,telegram_binding_version)"
                            + " VALUES"
                            + " ('overflow_credentials','Synthetic','Common',9223372036854775807,1)"
                            + " RETURNING id",
                        Long.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE users SET password_hash='synthetic-overflow' WHERE"
                                                + " id=?",
                                        a))
                .isInstanceOf(RuntimeException.class);
        assertThat(version(a, "credentials_version")).isEqualTo(Long.MAX_VALUE);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash IS NULL FROM users WHERE id=?",
                                Boolean.class,
                                a))
                .isTrue();
        long b =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " users(login,last_name,first_name,credentials_version,telegram_binding_version)"
                            + " VALUES"
                            + " ('overflow_binding','Synthetic','Common',1,9223372036854775807)"
                            + " RETURNING id",
                        Long.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET telegram_id=71114 WHERE id=?", b))
                .isInstanceOf(RuntimeException.class);
        assertThat(version(b, "telegram_binding_version")).isEqualTo(Long.MAX_VALUE);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT telegram_id IS NULL FROM users WHERE id=?",
                                Boolean.class,
                                b))
                .isTrue();
    }

    @Test
    void concurrentCredentialWritesSerializeVersionsAndOriginalCardFence() throws Exception {
        fresh(null);
        long u = user(71115);
        long r = card(u, 71115, 101);
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> second;
        try (Connection first = ds.getConnection()) {
            first.setAutoCommit(false);
            execute(first, "UPDATE users SET password_hash='synthetic-concurrent-2' WHERE id=" + u);
            second =
                    CompletableFuture.runAsync(
                            () -> {
                                try (Connection c = ds.getConnection()) {
                                    execute(c, "SET application_name='np01_waiter'");
                                    started.countDown();
                                    execute(
                                            c,
                                            "UPDATE users SET"
                                                + " password_hash='synthetic-concurrent-3' WHERE"
                                                + " id="
                                                    + u);
                                } catch (SQLException e) {
                                    throw new IllegalStateException(
                                            "synthetic concurrent write failed", e);
                                }
                            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean waiting = false;
            while (System.nanoTime() < deadline) {
                waiting =
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE"
                                    + " application_name='np01_waiter' AND wait_event_type='Lock')",
                                Boolean.class);
                if (waiting) break;
                Thread.sleep(20);
            }
            assertThat(waiting).isTrue();
            assertThat(second.isDone()).isFalse();
            first.commit();
        }
        second.get(5, TimeUnit.SECONDS);
        assertThat(version(u, "credentials_version")).isEqualTo(3);
        assertThat(invalidation(r)).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT render_revision FROM bot_credential_messages WHERE"
                                        + " record_id=?",
                                Long.class,
                                r))
                .isEqualTo(1);
    }

    private long seedSubjectWithType(long group, Long semester) {
        return new TransactionTemplate(new DataSourceTransactionManager(ds))
                .execute(
                        status -> {
                            long id =
                                    semester == null
                                            ? jdbc.queryForObject(
                                                    "INSERT INTO subjects(name,type,group_id)"
                                                        + " VALUES('Unresolved legacy','lecture',?)"
                                                        + " RETURNING id",
                                                    Long.class,
                                                    group)
                                            : jdbc.queryForObject(
                                                    "INSERT INTO"
                                                        + " subjects(name,type,group_id,semester_id)"
                                                        + " VALUES('Bound new','lecture',?,?)"
                                                        + " RETURNING id",
                                                    Long.class,
                                                    group,
                                                    semester);
                            jdbc.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                                            + " VALUES(?,'lecture')",
                                    id);
                            return id;
                        });
    }

    @Test
    void subjectDeletionV60UpgradeKeepsLegacyUnresolvedAndNewIdentityImmutable() {
        fresh("59");
        long group =
                jdbc.queryForObject(
                        "INSERT INTO groups(name) VALUES('subject-upgrade') RETURNING id",
                        Long.class);
        long legacy = seedSubjectWithType(group, null);
        var before =
                jdbc.queryForList(
                        "SELECT installed_rank,version,checksum FROM flyway_schema_history ORDER BY"
                                + " installed_rank");
        var guards = new java.util.LinkedHashMap<String, String>();
        for (String name :
                java.util.List.of(
                        "protect_assignment_identity",
                        "retain_homework_edit_evidence",
                        "protect_homework_edit_operation",
                        "protect_homework_binding_archive_identity",
                        "protect_homework_transfer_edit_supersession"))
            guards.put(
                    name,
                    jdbc.queryForObject(
                            "SELECT pg_get_functiondef((?||'()')::REGPROCEDURE)",
                            String.class,
                            name));
        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(1);
        flyway(null).validate();
        for (var guard : guards.entrySet()) {
            String after =
                    jdbc.queryForObject(
                            "SELECT pg_get_functiondef((?||'()')::REGPROCEDURE)",
                            String.class,
                            guard.getKey());
            String withoutAdmission =
                    after.replaceAll(
                            "(?m)^    IF TG_OP[^\\n"
                                    + "]+academic_subject_delete_row_authorized[^\\n"
                                    + "]+\\n",
                            "");
            assertThat(withoutAdmission.equals(guard.getValue()))
                    .as("current legacy guard retained except exact subject admission")
                    .isTrue();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT semester_id IS NULL FROM subjects WHERE id=?",
                                Boolean.class,
                                legacy))
                .isTrue();
        long semester = jdbc.queryForObject("SELECT id FROM semesters WHERE is_active", Long.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE subjects SET semester_id=? WHERE id=?",
                                        semester,
                                        legacy))
                .isInstanceOf(RuntimeException.class);
        long bound = seedSubjectWithType(group, semester);
        assertThatThrownBy(
                        () -> jdbc.update("UPDATE subjects SET semester_id=NULL WHERE id=?", bound))
                .isInstanceOf(RuntimeException.class);
        assertThat(
                        jdbc.queryForList(
                                "SELECT installed_rank,version,checksum FROM flyway_schema_history"
                                        + " ORDER BY installed_rank LIMIT ?",
                                before.size()))
                .isEqualTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM subject_deletion_operations", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subject_deletion_proofs", Long.class))
                .isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT column_name FROM information_schema.columns WHERE"
                                    + " table_schema=? AND table_name='subject_deletion_proofs'",
                                String.class,
                                schema))
                .doesNotContain("password", "code", "token", "body");
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }

    @Test
    void subjectDeletionV60DdlRollbackLeavesV59IdentityAndChecksumsIntact() throws Exception {
        fresh("59");
        var before =
                jdbc.queryForList(
                        "SELECT installed_rank,version,checksum FROM flyway_schema_history ORDER BY"
                                + " installed_rank");
        try (Connection connection = ds.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var ddl =
                        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                                new org.springframework.core.io.ClassPathResource(
                                        "db/migration/V60__subject_semester_and_deletion.sql"));
                ddl.setSeparator(
                        org.springframework.jdbc.datasource.init.ScriptUtils
                                .EOF_STATEMENT_SEPARATOR);
                ddl.populate(connection);
                connection.rollback();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.columns WHERE"
                                        + " table_schema=? AND table_name='subjects' AND"
                                        + " column_name='semester_id'",
                                Long.class,
                                schema))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE"
                                    + " table_schema=? AND table_name IN"
                                    + " ('subject_deletion_proofs','subject_deletion_operations')",
                                Long.class,
                                schema))
                .isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT installed_rank,version,checksum FROM flyway_schema_history"
                                        + " ORDER BY installed_rank"))
                .isEqualTo(before);
        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(1);
        flyway(null).validate();
    }

    private void fresh(String target) {
        schema = "np01_common_" + schemaSequence.incrementAndGet();
        DriverManagerDataSource admin = source(null);
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        ds = source(schema);
        jdbc = new JdbcTemplate(ds);
        flyway(target).migrate();
    }

    private DriverManagerDataSource source(String name) {
        String url = postgres.getJdbcUrl();
        if (name != null) url += (url.contains("?") ? "&" : "?") + "currentSchema=" + name;
        return new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
    }

    private Flyway flyway(String target) {
        var c =
                Flyway.configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration")
                        .schemas(schema)
                        .defaultSchema(schema)
                        .cleanDisabled(true);
        if (target != null) c.target(target);
        return c.load();
    }

    private long user(long chat) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " users(login,last_name,first_name,role,telegram_id,password_hash,initial_password)"
                            + " VALUES (?, 'Synthetic', 'Common', 'teacher', ?, 'synthetic-hash-1',"
                            + " 'synthetic-initial') RETURNING id",
                        Long.class,
                        "np01_" + chat,
                        chat);
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at) VALUES"
                        + " (?,'teacher','active',NOW(),NOW())",
                id);
        return id;
    }

    private long card(long u, long chat, long message) {
        return jdbc.queryForObject(
                "INSERT INTO"
                    + " bot_credential_messages(user_id,private_chat_id,message_id,telegram_binding_version,credentials_version)"
                    + " VALUES (?,?,?,1,1) RETURNING record_id",
                Long.class,
                u,
                chat,
                message);
    }

    private void invalidate(long u) {
        jdbc.update("UPDATE users SET initial_password=NULL,password_changed=TRUE WHERE id=?", u);
    }

    private void render(long r, String outcome) {
        jdbc.update(
                "UPDATE bot_credential_messages SET"
                    + " render_state=?,render_completed_at=NOW(),record_revision=record_revision+1,updated_at=NOW()"
                    + " WHERE record_id=?",
                outcome,
                r);
    }

    private void cleanup(long r, String outcome) {
        jdbc.update(
                "UPDATE bot_credential_messages SET"
                    + " cleanup_state='CLEANED',cleaned_at=NOW(),cleanup_event_id=?,cleanup_outcome=?,record_revision=record_revision+1,updated_at=NOW()"
                    + " WHERE record_id=?",
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                outcome,
                r);
    }

    private String state(long r, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM bot_credential_messages WHERE record_id=?",
                String.class,
                r);
    }

    private long revision(long r) {
        return jdbc.queryForObject(
                "SELECT record_revision FROM bot_credential_messages WHERE record_id=?",
                Long.class,
                r);
    }

    private Long invalidation(long r) {
        return jdbc.queryForObject(
                "SELECT invalidated_by_credentials_version FROM bot_credential_messages WHERE"
                        + " record_id=?",
                Long.class,
                r);
    }

    private long version(long u, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM users WHERE id=?", Long.class, u);
    }

    private void mutate(String name, String sql) {
        if (name.equals(System.getenv().getOrDefault("RCT_NP01_MUTATION", ""))) jdbc.execute(sql);
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
