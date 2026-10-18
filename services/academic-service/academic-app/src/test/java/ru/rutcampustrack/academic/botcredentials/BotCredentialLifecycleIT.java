package ru.rutcampustrack.academic.botcredentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.auth.botcredentials.AuthBotCredentialServiceIdentityInterceptor;
import ru.rutcampustrack.auth.botcredentials.AuthBotOwnInitialCredentialsQuery;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Real PostgreSQL authority/generation/CAS evidence, separate from the provider transport bridge.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BotCredentialLifecycleIT {
    private final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("rct_np01_domain")
                    .withUsername("rct_np01_domain")
                    .withPassword("rct_np01_disposable")
                    .withReuse(false);
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private BotCredentialsService service;
    private AnnotationConfigApplicationContext authContext;
    private AuthBotOwnInitialCredentialsQuery authRead;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class AuthTransactions {}

    @BeforeAll
    void start() {
        postgres.start();
        ru.rutcampustrack.academic.migration.OwnedAuthPrincipals.prepare(postgres);
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .load()
                .migrate();
        var data =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(data);
        var runtimeSource =
                ru.rutcampustrack.academic.migration.OwnedAuthPrincipals.source(
                        postgres, "rct_academic_runtime");
        var runtime = new JdbcTemplate(runtimeSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(runtimeSource));
        var query =
                new BotOwnInitialCredentialsQuery(runtime, new AdminAccessJdbcAuthority(runtime));
        service =
                new BotCredentialsService(
                        query, new BotCredentialMessageRepository(runtime, query));
        var authSource =
                ru.rutcampustrack.academic.migration.OwnedAuthPrincipals.source(
                        postgres, "rct_auth_runtime");
        var authJdbc = new JdbcTemplate(authSource);
        assertThat(authJdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("rct_auth_runtime");
        authContext = new AnnotationConfigApplicationContext();
        authContext.registerBean(
                "transactionManager",
                DataSourceTransactionManager.class,
                () -> new DataSourceTransactionManager(authSource));
        authContext.registerBean(JdbcTemplate.class, () -> authJdbc);
        authContext.registerBean(
                AdminAccessJdbcAuthority.class, () -> new AdminAccessJdbcAuthority(authJdbc));
        authContext.register(AuthTransactions.class, AuthBotOwnInitialCredentialsQuery.class);
        authContext.refresh();
        authRead = authContext.getBean(AuthBotOwnInitialCredentialsQuery.class);
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(authRead)).isTrue();
    }

    @AfterAll
    void stop() {
        try {
            if (authContext != null) authContext.close();
        } finally {
            postgres.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"student", "teacher", "headman", "FULL", "LIMITED"})
    void validOwnActiveRolesDoNotRequireDefaultRoleOrTargetCredentialPermission(String role) {
        var user = user(role);
        var response = read(user, 0, 0);
        assertThat(response.getFound() && response.getEligible()).isTrue();
        assertThat(response.getInitialPassword().equals(user.initial)).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_sessions WHERE user_id=?",
                                Long.class,
                                user.id))
                .isZero();
        if (role.equals("LIMITED")) {
            assertThat(
                            new AdminAccessJdbcAuthority(jdbc)
                                    .profile(user.id)
                                    .allows("USER_CREDENTIALS_MANAGE"))
                    .isFalse();
        }
    }

    @Test
    void orphanHeadmanAndRevokedOnlyRestoreCannotGrantOwnInitial() {
        var user = user("headman");
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended' WHERE user_id=? AND role='student'",
                user.id);
        assertThat(read(user, 0, 0).getFound()).isFalse();
        jdbc.update("UPDATE users SET status='archived' WHERE id=?", user.id);
        jdbc.update("UPDATE user_role_grants SET status='archived' WHERE user_id=?", user.id);
        jdbc.update("UPDATE users SET status='active' WHERE id=?", user.id);
        var restored = read(user, 0, 0);
        assertThat(restored.getFound()).isFalse();
        assertThat(restored.getInitialPassword().isEmpty()).isTrue();
        grant(user.id, "teacher", null);
        assertThat(read(user, 0, 0).getEligible()).isTrue();
    }

    @Test
    void privateForeignAndUnknownReadModesFailClosed() {
        var own = user("student");
        var foreign = user("student");
        denied(
                () ->
                        authBot(
                                () ->
                                        authRead.read(
                                                request(own)
                                                        .setExpectedUserId(foreign.id)
                                                        .build())),
                Status.Code.PERMISSION_DENIED);
        denied(
                () ->
                        authBot(
                                () ->
                                        authRead.read(
                                                request(own)
                                                        .setPrivateChatId(own.chat + 1)
                                                        .build())),
                Status.Code.PERMISSION_DENIED);
        denied(
                () -> authBot(() -> authRead.read(request(own).setModeValue(0).build())),
                Status.Code.INVALID_ARGUMENT);
        denied(
                () -> authBot(() -> authRead.read(request(own).setModeValue(991).build())),
                Status.Code.INVALID_ARGUMENT);
        denied(
                () ->
                        authBot(
                                () ->
                                        authRead.read(
                                                request(own)
                                                        .setExpectedCredentialsVersion(1)
                                                        .build())),
                Status.Code.INVALID_ARGUMENT);
        denied(() -> authRead.read(request(own).build()), Status.Code.UNAUTHENTICATED);
        denied(() -> bot(() -> service.read(request(own).build())), Status.Code.UNIMPLEMENTED);
    }

    @Test
    void changedPasswordNeverExposesLeftoverInitialOrLoginFromDedicatedRead() {
        var user = user("student");
        jdbc.update("UPDATE users SET password_changed=true WHERE id=?", user.id);
        var response = read(user, 0, 0);
        assertThat(response.getFound()).isTrue();
        assertThat(response.getEligible()).isFalse();
        assertThat(response.getInitialPassword().isEmpty() && response.getLogin().isEmpty())
                .isTrue();
    }

    @Test
    void eventBeforeRegistrationAndRebindingFenceOldSnapshots() {
        var user = user("student");
        var original = reference(user, 101);
        change(user);
        denied(() -> manage(register(original)), Status.Code.FAILED_PRECONDITION);
        assertThat(count(user)).isZero();
        var rebound = user("student");
        var old = reference(rebound, 102);
        jdbc.update("UPDATE users SET telegram_id=NULL WHERE id=?", rebound.id);
        jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", rebound.chat, rebound.id);
        denied(() -> manage(register(old)), Status.Code.FAILED_PRECONDITION);
        assertThat(count(rebound)).isZero();
    }

    @Test
    void simultaneousRegistrationHasOneRenderPermitAndMetadataOnlyDurability() throws Exception {
        var user = user("student");
        var original = reference(user, 103);
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<BotCredentialMessagesResponse> register =
                    () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return manage(register(original));
                    };
            var first = workers.submit(register);
            var second = workers.submit(register);
            assertThat(
                            List.of(
                                    first.get(10, TimeUnit.SECONDS).getDisposition(),
                                    second.get(10, TimeUnit.SECONDS).getDisposition()))
                    .containsExactlyInAnyOrder(
                            BotCredentialMessageDisposition
                                    .BOT_CREDENTIAL_MESSAGE_DISPOSITION_REGISTERED,
                            BotCredentialMessageDisposition
                                    .BOT_CREDENTIAL_MESSAGE_DISPOSITION_ALREADY_REGISTERED);
        }
        assertThat(count(user)).isEqualTo(1);
        boolean noInitial =
                jdbc.queryForObject(
                        "SELECT NOT EXISTS(SELECT 1 FROM bot_credential_messages m "
                                + "WHERE m.user_id=? AND row_to_json(m)::text LIKE ?)",
                        Boolean.class,
                        user.id,
                        "%" + user.initial + "%");
        assertThat(noInitial).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_FLIGHT", "UNCERTAIN"})
    void unresolvedRenderCannotBeSanitizedButConfirmedDeleteFencesLateEdit(String state) {
        var user = user("student");
        var card = manage(register(reference(user, 104))).getMessages(0);
        if (state.equals("UNCERTAIN"))
            card =
                    render(card, BotCredentialRenderOutcome.BOT_CREDENTIAL_RENDER_OUTCOME_UNCERTAIN)
                            .getMessages(0);
        var stableAttempt = card;
        change(user);
        var invalidated = list(user, Long.MAX_VALUE, 0, 10).getMessages(0);
        assertThat(invalidated.getRenderRevision()).isEqualTo(stableAttempt.getRenderRevision());
        assertThat(invalidated.getRecordRevision())
                .isGreaterThan(stableAttempt.getRecordRevision());
        denied(
                () ->
                        clean(
                                invalidated,
                                BotCredentialCleanupOutcome
                                        .BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED),
                Status.Code.FAILED_PRECONDITION);
        var deleted =
                clean(
                                invalidated,
                                BotCredentialCleanupOutcome.BOT_CREDENTIAL_CLEANUP_OUTCOME_DELETED)
                        .getMessages(0);
        assertThat(deleted.getCleanupState())
                .isEqualTo(BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED);
        var late =
                render(
                                stableAttempt,
                                BotCredentialRenderOutcome
                                        .BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT)
                        .getMessages(0);
        assertThat(late.getRecordRevision()).isEqualTo(deleted.getRecordRevision());
        assertThat(late.getCleanupState())
                .isEqualTo(BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED);
        assertThat(list(user, Long.MAX_VALUE, 0, 10).getMessagesCount()).isZero();
    }

    @Test
    void invalidationCannotLoseStableRenderReceiptAndStaleCleanupCasCannotSettle() {
        var user = user("student");
        var card = manage(register(reference(user, 105))).getMessages(0);
        change(user);
        var pendingBeforeReceipt = list(user, Long.MAX_VALUE, 0, 10).getMessages(0);
        var settled =
                render(
                        card,
                        BotCredentialRenderOutcome.BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT);
        assertThat(settled.getDisposition())
                .isEqualTo(
                        BotCredentialMessageDisposition
                                .BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_REQUIRED);
        denied(
                () ->
                        clean(
                                pendingBeforeReceipt,
                                BotCredentialCleanupOutcome
                                        .BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED),
                Status.Code.FAILED_PRECONDITION);
        assertThat(list(user, Long.MAX_VALUE, 0, 10).getMessagesCount()).isEqualTo(1);
        clean(
                settled.getMessages(0),
                BotCredentialCleanupOutcome.BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED);
        assertThat(list(user, Long.MAX_VALUE, 0, 10).getMessagesCount()).isZero();
    }

    @Test
    void bindingAndGrantLossInvalidateKnownCardWithoutNewCredentials() {
        var user = user("student");
        var card = manage(register(reference(user, 106))).getMessages(0);
        jdbc.update("UPDATE user_role_grants SET status='suspended' WHERE user_id=?", user.id);
        var settled =
                render(card, BotCredentialRenderOutcome.BOT_CREDENTIAL_RENDER_OUTCOME_NO_EDIT);
        assertThat(settled.getDisposition())
                .isEqualTo(
                        BotCredentialMessageDisposition
                                .BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_REQUIRED);
        assertThat(settled.getMessages(0).getInvalidatedCredentialsVersion())
                .isEqualTo(card.getCredentialsVersion());
        clean(
                settled.getMessages(0),
                BotCredentialCleanupOutcome.BOT_CREDENTIAL_CLEANUP_OUTCOME_ABSENT);
    }

    @Test
    void archiveAndFinalDeleteKeepOriginalExactCleanupIdentity() {
        var user = user("student");
        var card = manage(register(reference(user, 107))).getMessages(0);
        jdbc.update("UPDATE users SET status='archived', telegram_id=NULL WHERE id=?", user.id);
        jdbc.update("DELETE FROM user_role_grants WHERE user_id=?", user.id);
        jdbc.update("DELETE FROM users WHERE id=?", user.id);
        var pending = list(user, card.getCredentialsVersion(), 0, 10).getMessages(0);
        assertThat(pending.getUserId()).isEqualTo(user.id);
        assertThat(pending.getPrivateChatId()).isEqualTo(user.chat);
        clean(pending, BotCredentialCleanupOutcome.BOT_CREDENTIAL_CLEANUP_OUTCOME_ABSENT);
    }

    @Test
    void olderEventLeavesLaterInitialGenerationAndForeignAccountCardUntouched() {
        var user = user("student");
        manage(register(reference(user, 108)));
        change(user);
        long eventGeneration = generation(user);
        jdbc.update(
                "UPDATE users SET password_changed=false, initial_password=? WHERE id=?",
                UUID.randomUUID().toString(),
                user.id);
        var later = manage(register(reference(user, 109))).getMessages(0);
        var foreign = user("student");
        var foreignCard = manage(register(reference(foreign, 110))).getMessages(0);
        var old = list(user, eventGeneration, 0, 10);
        assertThat(old.getMessagesCount()).isEqualTo(1);
        clean(
                old.getMessages(0),
                BotCredentialCleanupOutcome.BOT_CREDENTIAL_CLEANUP_OUTCOME_DELETED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cleanup_state FROM bot_credential_messages WHERE"
                                        + " record_id=?",
                                String.class,
                                later.getRecordId()))
                .isEqualTo("NOT_REQUIRED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cleanup_state FROM bot_credential_messages WHERE"
                                        + " record_id=?",
                                String.class,
                                foreignCard.getRecordId()))
                .isEqualTo("NOT_REQUIRED");
    }

    @Test
    void credentialRollbackRestoresGenerationAndTrackerInvalidation() {
        var user = user("student");
        var card = manage(register(reference(user, 111))).getMessages(0);
        long original = generation(user);
        assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        tx -> {
                                            change(user);
                                            assertThat(generation(user)).isEqualTo(original + 1);
                                            throw new IllegalStateException(
                                                    "synthetic local rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(generation(user)).isEqualTo(original);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT record_revision FROM bot_credential_messages WHERE"
                                        + " record_id=?",
                                Long.class,
                                card.getRecordId()))
                .isEqualTo(card.getRecordRevision());
        assertThat(list(user, Long.MAX_VALUE, 0, 10).getMessagesCount()).isZero();
    }

    @Test
    void forgedCardTupleAndUnknownOutcomesCannotCompleteOriginalCleanup() {
        var user = user("student");
        manage(register(reference(user, 115)));
        change(user);
        var pending = list(user, generation(user), 0, 10).getMessages(0);
        var forged = pending.toBuilder().setMessageId(116).build();
        denied(
                () ->
                        clean(
                                forged,
                                BotCredentialCleanupOutcome.BOT_CREDENTIAL_CLEANUP_OUTCOME_DELETED),
                Status.Code.PERMISSION_DENIED);
        denied(
                () ->
                        clean(
                                pending,
                                BotCredentialCleanupOutcome
                                        .BOT_CREDENTIAL_CLEANUP_OUTCOME_UNSPECIFIED),
                Status.Code.INVALID_ARGUMENT);
        denied(
                () ->
                        manage(
                                BotCredentialMessagesRequest.newBuilder()
                                        .setCompleteRender(
                                                CompleteBotCredentialMessageRender.newBuilder()
                                                        .setMessage(pending)
                                                        .setOutcomeValue(991))
                                        .build()),
                Status.Code.INVALID_ARGUMENT);
        assertThat(list(user, generation(user), 0, 10).getMessagesCount()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cleanup_state FROM bot_credential_messages WHERE"
                                        + " record_id=?",
                                String.class,
                                pending.getRecordId()))
                .isEqualTo("PENDING");
    }

    @Test
    void cursorPagesIncludeEveryUnresolvedInvalidatedCard() {
        var user = user("student");
        for (int message = 112; message < 115; message++)
            manage(register(reference(user, message)));
        change(user);
        var first = list(user, generation(user), 0, 2);
        assertThat(first.getMessagesCount()).isEqualTo(2);
        assertThat(first.getNextAfterRecordId()).isEqualTo(first.getMessages(1).getRecordId());
        var last = list(user, generation(user), first.getNextAfterRecordId(), 2);
        assertThat(last.getMessagesCount()).isEqualTo(1);
        assertThat(last.getNextAfterRecordId()).isZero();
    }

    private Fixture user(String role) {
        long group =
                jdbc.queryForObject(
                        "INSERT INTO groups(name) VALUES(?) RETURNING id",
                        Long.class,
                        "NP01 " + UUID.randomUUID().toString().substring(0, 20));
        String login = "np" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String initial = UUID.randomUUID().toString();
        long id =
                jdbc.queryForObject(
                        """
INSERT INTO users(login,password_hash,last_name,first_name,initial_password,role,group_id)
VALUES(?,?,'NP01','Synthetic',?,'student',?) RETURNING id
""",
                        Long.class,
                        login,
                        UUID.randomUUID().toString(),
                        initial,
                        group);
        long chat = 810000000L + id;
        jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", chat, id);
        if (role.equals("FULL") || role.equals("LIMITED")) {
            jdbc.update(
                    "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version)"
                            + " VALUES(?,true,?,?::text[],1)",
                    id,
                    role,
                    role.equals("FULL") ? "{}" : "{USERS_VIEW}");
            grant(id, "admin", null);
        } else {
            grant(
                    id,
                    role.equals("headman") ? "student" : role,
                    role.equals("teacher") ? null : group);
            if (role.equals("headman")) grant(id, "headman", group);
        }
        return new Fixture(id, chat, initial);
    }

    private void grant(long user, String role, Long group) {
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) "
                        + "VALUES(?,?,'active',?,NOW(),NOW())",
                user,
                role,
                group);
    }

    private void change(Fixture user) {
        jdbc.update(
                "UPDATE users SET password_hash=?, password_changed=true, initial_password=NULL"
                        + " WHERE id=?",
                UUID.randomUUID().toString(),
                user.id);
    }

    private long generation(Fixture user) {
        return jdbc.queryForObject(
                "SELECT credentials_version FROM users WHERE id=?", Long.class, user.id);
    }

    private long count(Fixture user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM bot_credential_messages WHERE user_id=?",
                Long.class,
                user.id);
    }

    private BotOwnInitialCredentialsRequest.Builder request(Fixture user) {
        return BotOwnInitialCredentialsRequest.newBuilder()
                .setTelegramSenderId(user.chat)
                .setPrivateChatId(user.chat)
                .setExpectedUserId(user.id)
                .setMode(BotCredentialReadMode.BOT_CREDENTIAL_READ_MODE_SNAPSHOT);
    }

    private BotOwnInitialCredentialsResponse read(Fixture user, long binding, long generation) {
        var request =
                request(user)
                        .setExpectedTelegramBindingVersion(binding)
                        .setExpectedCredentialsVersion(generation);
        if (binding > 0 || generation > 0)
            request.setMode(BotCredentialReadMode.BOT_CREDENTIAL_READ_MODE_RECHECK);
        return authBot(() -> authRead.read(request.build()));
    }

    private BotCredentialMessageReference reference(Fixture user, long message) {
        var own = read(user, 0, 0);
        return BotCredentialMessageReference.newBuilder()
                .setUserId(user.id)
                .setPrivateChatId(user.chat)
                .setMessageId(message)
                .setTelegramBindingVersion(own.getTelegramBindingVersion())
                .setCredentialsVersion(own.getCredentialsVersion())
                .build();
    }

    private BotCredentialMessagesRequest register(BotCredentialMessageReference message) {
        return BotCredentialMessagesRequest.newBuilder()
                .setRegister(RegisterBotCredentialMessage.newBuilder().setMessage(message))
                .build();
    }

    private BotCredentialMessagesResponse list(Fixture user, long fence, long after, int size) {
        return manage(
                BotCredentialMessagesRequest.newBuilder()
                        .setListInvalidated(
                                ListInvalidatedBotCredentialMessages.newBuilder()
                                        .setUserId(user.id)
                                        .setInvalidatedCredentialsVersion(fence)
                                        .setAfterRecordId(after)
                                        .setPageSize(size)
                                        .setEventId(UUID.randomUUID().toString()))
                        .build());
    }

    private BotCredentialMessagesResponse render(
            BotCredentialMessageReference message, BotCredentialRenderOutcome outcome) {
        return manage(
                BotCredentialMessagesRequest.newBuilder()
                        .setCompleteRender(
                                CompleteBotCredentialMessageRender.newBuilder()
                                        .setMessage(message)
                                        .setOutcome(outcome))
                        .build());
    }

    private BotCredentialMessagesResponse clean(
            BotCredentialMessageReference message, BotCredentialCleanupOutcome outcome) {
        return manage(
                BotCredentialMessagesRequest.newBuilder()
                        .setCompleteCleanup(
                                CompleteBotCredentialMessageCleanup.newBuilder()
                                        .setMessage(message)
                                        .setOutcome(outcome)
                                        .setInvalidatedCredentialsVersion(
                                                message.getInvalidatedCredentialsVersion())
                                        .setEventId(UUID.randomUUID().toString()))
                        .build());
    }

    private BotCredentialMessagesResponse manage(BotCredentialMessagesRequest request) {
        return bot(() -> service.manage(request));
    }

    private <T> T authBot(Supplier<T> operation) {
        var result = new java.util.concurrent.atomic.AtomicReference<T>();
        Context.current()
                .withValue(
                        ServicePrincipal.CONTEXT_KEY,
                        AuthBotCredentialServiceIdentityInterceptor.BOT)
                .run(() -> result.set(operation.get()));
        return result.get();
    }

    private <T> T bot(Supplier<T> operation) {
        var result = new java.util.concurrent.atomic.AtomicReference<T>();
        Context.current()
                .withValue(
                        ServicePrincipal.CONTEXT_KEY, BotCredentialServiceIdentityInterceptor.BOT)
                .run(() -> result.set(transactions.execute(tx -> operation.get())));
        return result.get();
    }

    private static void denied(Runnable operation, Status.Code code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error -> assertThat(error.getStatus().getCode()).isEqualTo(code));
    }

    private static Path migrationDirectory() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            var migrations =
                    path.resolve(
                            "services/academic-service/academic-app/src/main/resources/db/migration");
            if (Files.isRegularFile(
                    migrations.resolve("V57__bot_credential_message_lifecycle.sql")))
                return migrations;
            path = path.getParent();
        }
        throw new IllegalStateException("published NP01 migration unavailable");
    }

    private static final class Fixture {
        final long id;
        final long chat;
        final String initial;

        Fixture(long id, long chat, String initial) {
            this.id = id;
            this.chat = chat;
            this.initial = initial;
        }

        @Override
        public String toString() {
            return "NP01Fixture[redacted]";
        }
    }
}
