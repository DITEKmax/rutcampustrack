package ru.rutcampustrack.auth.botcredentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.auth.support.AuthDatabaseFixture;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** Own Auth DB proof for card authority, first-invalidation fences and terminal cleanup. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthBotCredentialLifecycleIT {
    private final PostgreSQLContainer<?> database =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("auth_cards")
                    .withUsername("auth_cards")
                    .withPassword("synthetic-auth-cards")
                    .withReuse(false);
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private AuthBotOwnInitialCredentialsQuery authority;
    private AuthBotCredentialMessageRepository cards;
    private long sequence;

    @BeforeAll
    void start() {
        database.start();
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("filesystem:" + AuthDatabaseFixture.migrations())
                .load()
                .migrate();
        var source =
                new DriverManagerDataSource(
                        database.getJdbcUrl(), database.getUsername(), database.getPassword());
        var owner = new JdbcTemplate(source);
        AuthDatabaseFixture.runtimePrincipal(owner);
        var runtime =
                new DriverManagerDataSource(
                        database.getJdbcUrl(),
                        "rct_auth_runtime",
                        AuthDatabaseFixture.RUNTIME_PASSWORD);
        jdbc = new JdbcTemplate(runtime);
        tx = new TransactionTemplate(new DataSourceTransactionManager(runtime));
        authority = new AuthBotOwnInitialCredentialsQuery(jdbc);
        cards = new AuthBotCredentialMessageRepository(jdbc, authority);
    }

    @AfterAll
    void stop() {
        database.stop();
    }

    @Test
    void initialAndAdminResetRemainVisibleUntilUserChangesPasswordAndOldVersionsDeny() {
        long id = identity();
        var initial = bot(() -> authority.read(snapshot(id, id)));
        assertThat(initial.getEligible()).isTrue();
        assertThat(initial.getInitialPassword()).isEqualTo("initial");
        jdbc.update(
                "UPDATE auth_identities SET"
                    + " password_hash=?,initial_password='reset',generated_password_origin='headman_reset'"
                    + " WHERE id=?",
                AuthDatabaseFixture.REPLACEMENT_HASH,
                id);
        var reset = bot(() -> authority.read(snapshot(id, id)));
        assertThat(reset.getEligible()).isTrue();
        assertThat(reset.getInitialPassword()).isEqualTo("reset");
        assertThat(reset.getCredentialsVersion()).isGreaterThan(initial.getCredentialsVersion());
        expect(
                Status.Code.FAILED_PRECONDITION,
                () ->
                        bot(
                                () ->
                                        authority.read(
                                                snapshot(id, id).toBuilder()
                                                        .setMode(
                                                                BotCredentialReadMode
                                                                        .BOT_CREDENTIAL_READ_MODE_RECHECK)
                                                        .setExpectedCredentialsVersion(
                                                                initial.getCredentialsVersion())
                                                        .setExpectedTelegramBindingVersion(
                                                                initial.getTelegramBindingVersion())
                                                        .build())));
        jdbc.update(
                "UPDATE auth_identities SET password_changed=TRUE,initial_password=NULL WHERE id=?",
                id);
        var changed = bot(() -> authority.read(snapshot(id, id)));
        assertThat(changed.getFound()).isTrue();
        assertThat(changed.getEligible()).isFalse();
        assertThat(changed.getLogin()).isEmpty();
        assertThat(changed.getInitialPassword()).isEmpty();
    }

    @Test
    void pendingFencedArchivedWrongSenderAndBindingNeverReadOrRegisterCredentials() {
        long id = identity();
        var original = original(id, 41);
        expect(
                Status.Code.PERMISSION_DENIED,
                () -> bot(() -> authority.read(snapshot(id, id + 1))));
        jdbc.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", id + 100000, id);
        expect(
                Status.Code.FAILED_PRECONDITION,
                () ->
                        bot(
                                () ->
                                        authority.read(
                                                snapshot(id, id + 100000).toBuilder()
                                                        .setMode(
                                                                BotCredentialReadMode
                                                                        .BOT_CREDENTIAL_READ_MODE_RECHECK)
                                                        .setExpectedTelegramBindingVersion(
                                                                original
                                                                        .getTelegramBindingVersion())
                                                        .setExpectedCredentialsVersion(
                                                                original.getCredentialsVersion())
                                                        .build())));
        jdbc.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", id, id);
        tx.executeWithoutResult(status -> AuthDatabaseFixture.fence(jdbc, id));
        assertThat(bot(() -> authority.read(snapshot(id, id))).getFound()).isFalse();
        expect(Status.Code.FAILED_PRECONDITION, () -> register(original(id, 42)));
        jdbc.update("UPDATE auth_identities SET account_state='PENDING' WHERE id=?", id);
        assertThat(bot(() -> authority.read(snapshot(id, id))).getFound()).isFalse();
        expect(Status.Code.FAILED_PRECONDITION, () -> register(original(id, 43)));
        long archived = identity();
        jdbc.update("UPDATE auth_identities SET account_state='ARCHIVED' WHERE id=?", archived);
        assertThat(bot(() -> authority.read(snapshot(archived, archived))).getFound()).isFalse();
        expect(Status.Code.FAILED_PRECONDITION, () -> register(original(archived, 44)));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM bot_credential_messages WHERE user_id=?",
                                Long.class,
                                id))
                .isZero();
    }

    @Test
    void cleanupSurvivesArchiveAndLateRenderCannotReviveCleanedCard() {
        long id = identity();
        var original = original(id, 51);
        var registered = register(original);
        var card = registered.getMessages(0);
        assertThat(register(original).getDisposition())
                .isEqualTo(
                        BotCredentialMessageDisposition
                                .BOT_CREDENTIAL_MESSAGE_DISPOSITION_ALREADY_REGISTERED);
        jdbc.update("UPDATE auth_identities SET account_state='ARCHIVED' WHERE id=?", id);
        String event = UUID.randomUUID().toString();
        var invalidated =
                bot(() ->
                                cards.manage(
                                        BotCredentialMessagesRequest.newBuilder()
                                                .setListInvalidated(
                                                        ListInvalidatedBotCredentialMessages
                                                                .newBuilder()
                                                                .setUserId(id)
                                                                .setInvalidatedCredentialsVersion(
                                                                        card
                                                                                .getCredentialsVersion())
                                                                .setEventId(event)
                                                                .setPageSize(1))
                                                .build()))
                        .getMessages(0);
        assertThat(invalidated.getCleanupState())
                .isEqualTo(BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_PENDING);
        assertThat(invalidated.getRenderRevision()).isEqualTo(card.getRenderRevision());
        expect(
                Status.Code.FAILED_PRECONDITION,
                () ->
                        cleanup(
                                invalidated,
                                event,
                                BotCredentialCleanupOutcome
                                        .BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED));
        var cleaned =
                cleanup(
                        invalidated,
                        event,
                        BotCredentialCleanupOutcome.BOT_CREDENTIAL_CLEANUP_OUTCOME_DELETED);
        assertThat(cleaned.getMessages(0).getCleanupState())
                .isEqualTo(BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED);
        var late =
                bot(
                        () ->
                                cards.manage(
                                        BotCredentialMessagesRequest.newBuilder()
                                                .setCompleteRender(
                                                        CompleteBotCredentialMessageRender
                                                                .newBuilder()
                                                                .setMessage(card)
                                                                .setOutcome(
                                                                        BotCredentialRenderOutcome
                                                                                .BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT))
                                                .build()));
        assertThat(late.getDisposition())
                .isEqualTo(
                        BotCredentialMessageDisposition
                                .BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED);
        assertThat(late.getMessages(0).getRenderState())
                .isEqualTo(BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_IN_FLIGHT);
        expect(
                Status.Code.PERMISSION_DENIED,
                () ->
                        bot(
                                () ->
                                        cards.manage(
                                                BotCredentialMessagesRequest.newBuilder()
                                                        .setCompleteRender(
                                                                CompleteBotCredentialMessageRender
                                                                        .newBuilder()
                                                                        .setMessage(
                                                                                card.toBuilder()
                                                                                        .setUserId(
                                                                                                id
                                                                                                        + 1))
                                                                        .setOutcome(
                                                                                BotCredentialRenderOutcome
                                                                                        .BOT_CREDENTIAL_RENDER_OUTCOME_NO_EDIT))
                                                        .build())));
    }

    @Test
    void firstInvalidationAndRenderRevisionSurviveRepeatedCredentialChangesAndCleanupCAS() {
        long id = identity();
        var card = register(original(id, 61)).getMessages(0);
        jdbc.update(
                "UPDATE auth_identities SET password_changed=TRUE,initial_password=NULL WHERE id=?",
                id);
        var invalidation =
                jdbc.queryForObject(
                        "SELECT invalidated_by_credentials_version FROM bot_credential_messages"
                                + " WHERE record_id=?",
                        Long.class,
                        card.getRecordId());
        jdbc.update(
                "UPDATE auth_identities SET password_hash=? WHERE id=?",
                AuthDatabaseFixture.REPLACEMENT_HASH,
                id);
        var rendered =
                bot(() ->
                                cards.manage(
                                        BotCredentialMessagesRequest.newBuilder()
                                                .setCompleteRender(
                                                        CompleteBotCredentialMessageRender
                                                                .newBuilder()
                                                                .setMessage(card)
                                                                .setOutcome(
                                                                        BotCredentialRenderOutcome
                                                                                .BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT))
                                                .build()))
                        .getMessages(0);
        assertThat(rendered.getInvalidatedCredentialsVersion()).isEqualTo(invalidation);
        assertThat(rendered.getRenderRevision()).isEqualTo(card.getRenderRevision());
        assertThat(rendered.getRecordRevision()).isGreaterThan(card.getRecordRevision());
        String event = UUID.randomUUID().toString();
        expect(
                Status.Code.FAILED_PRECONDITION,
                () ->
                        cleanup(
                                rendered.toBuilder()
                                        .setRecordRevision(card.getRecordRevision())
                                        .build(),
                                event,
                                BotCredentialCleanupOutcome
                                        .BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED));
        assertThat(
                        cleanup(
                                        rendered,
                                        event,
                                        BotCredentialCleanupOutcome
                                                .BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED)
                                .getMessages(0)
                                .getCleanupState())
                .isEqualTo(BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED);
    }

    @Test
    void retainedCardWithoutAuthorityStillAllowsExactRecordedCleanup() {
        long missingOwner = 900000000L + ++sequence;
        long record =
                jdbc.queryForObject(
                        """
                        INSERT INTO bot_credential_messages(user_id,private_chat_id,message_id,
                            telegram_binding_version,credentials_version)
                        VALUES (?,?,71,1,1) RETURNING record_id
                        """,
                        Long.class,
                        missingOwner,
                        missingOwner);
        jdbc.update(
                """
                UPDATE bot_credential_messages SET invalidated_by_credentials_version=1,
                    invalidated_at=NOW(),cleanup_state='PENDING',record_revision=record_revision+1
                WHERE record_id=?
                """,
                record);
        String event = UUID.randomUUID().toString();
        var pending =
                bot(() ->
                                cards.manage(
                                        BotCredentialMessagesRequest.newBuilder()
                                                .setListInvalidated(
                                                        ListInvalidatedBotCredentialMessages
                                                                .newBuilder()
                                                                .setUserId(missingOwner)
                                                                .setInvalidatedCredentialsVersion(1)
                                                                .setEventId(event)
                                                                .setPageSize(1))
                                                .build()))
                        .getMessages(0);
        assertThat(bot(() -> authority.read(snapshot(missingOwner, missingOwner))).getFound())
                .isFalse();
        assertThat(
                        cleanup(
                                        pending,
                                        event,
                                        BotCredentialCleanupOutcome
                                                .BOT_CREDENTIAL_CLEANUP_OUTCOME_ABSENT)
                                .getMessages(0)
                                .getCleanupState())
                .isEqualTo(BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED);
    }

    private long identity() {
        long id =
                AuthDatabaseFixture.identity(
                        jdbc, "card-" + ++sequence, Instant.parse("2026-10-18T00:00:00Z"));
        jdbc.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", id, id);
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                        + " (?,'student','active',9981)",
                id);
        return id;
    }

    private BotCredentialMessageReference original(long id, long message) {
        var user = bot(() -> tx.execute(status -> authority.lockUser(id)));
        return BotCredentialMessageReference.newBuilder()
                .setUserId(id)
                .setPrivateChatId(id)
                .setMessageId(message)
                .setTelegramBindingVersion(user.bindingVersion)
                .setCredentialsVersion(user.credentialsVersion)
                .build();
    }

    private BotOwnInitialCredentialsRequest snapshot(long id, long sender) {
        return BotOwnInitialCredentialsRequest.newBuilder()
                .setExpectedUserId(id)
                .setTelegramSenderId(sender)
                .setPrivateChatId(sender)
                .setMode(BotCredentialReadMode.BOT_CREDENTIAL_READ_MODE_SNAPSHOT)
                .build();
    }

    private BotCredentialMessagesResponse register(BotCredentialMessageReference message) {
        return bot(
                () ->
                        cards.manage(
                                BotCredentialMessagesRequest.newBuilder()
                                        .setRegister(
                                                RegisterBotCredentialMessage.newBuilder()
                                                        .setMessage(message))
                                        .build()));
    }

    private BotCredentialMessagesResponse cleanup(
            BotCredentialMessageReference message,
            String event,
            BotCredentialCleanupOutcome outcome) {
        return bot(
                () ->
                        cards.manage(
                                BotCredentialMessagesRequest.newBuilder()
                                        .setCompleteCleanup(
                                                CompleteBotCredentialMessageCleanup.newBuilder()
                                                        .setMessage(message)
                                                        .setInvalidatedCredentialsVersion(
                                                                message
                                                                        .getInvalidatedCredentialsVersion())
                                                        .setEventId(event)
                                                        .setOutcome(outcome))
                                        .build()));
    }

    private <T> T bot(Supplier<T> operation) {
        try {
            return Context.current()
                    .withValue(
                            ServicePrincipal.CONTEXT_KEY,
                            AuthBotCredentialServiceIdentityInterceptor.BOT)
                    .call(() -> tx.execute(status -> operation.get()));
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("synthetic bot call failed", failure);
        }
    }

    private void expect(Status.Code code, Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error -> assertThat(error.getStatus().getCode()).isEqualTo(code));
    }
}
