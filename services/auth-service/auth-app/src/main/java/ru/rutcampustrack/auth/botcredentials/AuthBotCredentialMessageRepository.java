package ru.rutcampustrack.auth.botcredentials;

import static ru.rutcampustrack.auth.botcredentials.AuthBotOwnInitialCredentialsQuery.*;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.grpc.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Metadata-only card lifecycle. Caller supplies a short local transaction, never a provider call.
 */
@Repository
public class AuthBotCredentialMessageRepository {

    private static final String COLUMNS =
            """
            record_id, user_id, private_chat_id, message_id, telegram_binding_version,
            credentials_version, record_revision, render_revision, render_state, cleanup_state,
            invalidated_by_credentials_version
            """;
    private final JdbcTemplate jdbc;
    private final AuthBotOwnInitialCredentialsQuery authority;

    public AuthBotCredentialMessageRepository(
            JdbcTemplate jdbc, AuthBotOwnInitialCredentialsQuery authority) {
        this.jdbc = jdbc;
        this.authority = authority;
    }

    @Transactional
    public BotCredentialMessagesResponse manage(BotCredentialMessagesRequest request) {
        AuthBotCredentialServiceIdentityInterceptor.requireBot();
        return switch (request.getCommandCase()) {
            case REGISTER -> register(request.getRegister());
            case LIST_INVALIDATED -> list(request.getListInvalidated());
            case COMPLETE_RENDER -> completeRender(request.getCompleteRender());
            case COMPLETE_CLEANUP -> completeCleanup(request.getCompleteCleanup());
            default -> throw invalid();
        };
    }

    BotCredentialMessagesResponse register(RegisterBotCredentialMessage command) {
        if (!command.hasMessage()) throw invalid();
        var requested = command.getMessage();
        requireOriginal(requested);
        if (requested.getRecordId() != 0
                || requested.getRecordRevision() != 0
                || requested.getRenderRevision() != 0
                || requested.getRenderStateValue() != 0
                || requested.getCleanupStateValue() != 0
                || requested.getInvalidatedCredentialsVersion() != 0) {
            throw invalid();
        }
        var user = authority.lockUser(requested.getUserId());
        if (user == null || user.telegramId != requested.getPrivateChatId()) throw denied();
        if (user.bindingVersion != requested.getTelegramBindingVersion()
                || user.credentialsVersion != requested.getCredentialsVersion()
                || !authority.hasOwnAuthority(user)
                || !user.eligible()) throw stale();
        int inserted =
                jdbc.update(
                        """
                        INSERT INTO bot_credential_messages
                            (user_id, private_chat_id, message_id,
                             telegram_binding_version, credentials_version)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (private_chat_id, message_id) DO NOTHING
                        """,
                        requested.getUserId(),
                        requested.getPrivateChatId(),
                        requested.getMessageId(),
                        requested.getTelegramBindingVersion(),
                        requested.getCredentialsVersion());
        var card =
                jdbc.query(
                                "SELECT "
                                        + COLUMNS
                                        + " FROM bot_credential_messages "
                                        + "WHERE private_chat_id = ? AND message_id = ? FOR UPDATE",
                                this::map,
                                requested.getPrivateChatId(),
                                requested.getMessageId())
                        .getFirst();
        requireSameOriginal(requested, card);
        return response(
                inserted == 1
                        ? BotCredentialMessageDisposition
                                .BOT_CREDENTIAL_MESSAGE_DISPOSITION_REGISTERED
                        : BotCredentialMessageDisposition
                                .BOT_CREDENTIAL_MESSAGE_DISPOSITION_ALREADY_REGISTERED,
                card);
    }

    BotCredentialMessagesResponse list(ListInvalidatedBotCredentialMessages command) {
        if (command.getUserId() <= 0
                || command.getInvalidatedCredentialsVersion() <= 0
                || command.getAfterRecordId() < 0
                || command.getPageSize() < 1
                || command.getPageSize() > 200) {
            throw invalid();
        }
        eventId(command.getEventId());
        var rows =
                jdbc.query(
                        "SELECT "
                                + COLUMNS
                                + " FROM bot_credential_messages "
                                + """
WHERE user_id = ? AND invalidated_by_credentials_version IS NOT NULL
  AND invalidated_by_credentials_version <= ? AND cleanup_state = 'PENDING'
  AND record_id > ? ORDER BY record_id LIMIT ?
""",
                        this::map,
                        command.getUserId(),
                        command.getInvalidatedCredentialsVersion(),
                        command.getAfterRecordId(),
                        command.getPageSize() + 1);
        var result =
                BotCredentialMessagesResponse.newBuilder()
                        .setDisposition(
                                BotCredentialMessageDisposition
                                        .BOT_CREDENTIAL_MESSAGE_DISPOSITION_INVALIDATED_LIST);
        int count = Math.min(rows.size(), command.getPageSize());
        for (int index = 0; index < count; index++) result.addMessages(rows.get(index));
        if (rows.size() > count) result.setNextAfterRecordId(rows.get(count - 1).getRecordId());
        return result.build();
    }

    BotCredentialMessagesResponse completeRender(CompleteBotCredentialMessageRender command) {
        if (!command.hasMessage()) throw invalid();
        var requested = command.getMessage();
        requireReceipt(requested);
        var outcome =
                switch (command.getOutcome()) {
                    case BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT ->
                            BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_CONFIRMED_EDIT;
                    case BOT_CREDENTIAL_RENDER_OUTCOME_NO_EDIT ->
                            BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_NO_EDIT;
                    case BOT_CREDENTIAL_RENDER_OUTCOME_UNCERTAIN ->
                            BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_UNCERTAIN;
                    default -> throw invalid();
                };
        var user = authority.lockUser(requested.getUserId());
        var card = lockCard(requested);
        // A confirmed delete/absence fences all late edits on this message ID.
        if (card.getCleanupState()
                == BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED) {
            return response(
                    BotCredentialMessageDisposition
                            .BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED,
                    card);
        }
        boolean inFlight =
                card.getRenderState()
                        == BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_IN_FLIGHT;
        if (!inFlight && card.getRenderState() != outcome) throw stale();
        boolean lostAuthority =
                user == null
                        || user.telegramId != card.getPrivateChatId()
                        || user.bindingVersion != card.getTelegramBindingVersion()
                        || user.credentialsVersion != card.getCredentialsVersion()
                        || !user.eligible()
                        || !authority.hasOwnAuthority(user);
        long invalidation = card.getInvalidatedCredentialsVersion();
        if (invalidation == 0 && lostAuthority) {
            invalidation =
                    Math.max(
                            card.getCredentialsVersion(),
                            user == null ? 0 : user.credentialsVersion);
        }
        if (inFlight || invalidation != card.getInvalidatedCredentialsVersion()) {
            requireRevisionCapacity(card);
            int changed =
                    jdbc.update(
                            """
UPDATE bot_credential_messages
SET render_state = ?, render_completed_at = COALESCE(render_completed_at, NOW()),
    invalidated_by_credentials_version = ?,
    invalidated_at = CASE WHEN ?::bigint IS NULL THEN invalidated_at
                          ELSE COALESCE(invalidated_at, NOW()) END,
    cleanup_state = CASE WHEN ?::bigint IS NULL THEN 'NOT_REQUIRED' ELSE 'PENDING' END,
    record_revision = record_revision + 1, updated_at = NOW()
WHERE record_id = ? AND record_revision = ? AND cleanup_state <> 'CLEANED'
""",
                            renderCode(outcome),
                            invalidation == 0 ? null : invalidation,
                            invalidation == 0 ? null : invalidation,
                            invalidation == 0 ? null : invalidation,
                            card.getRecordId(),
                            card.getRecordRevision());
            if (changed != 1) throw stale();
            card = lockCard(requested);
        }
        return response(
                invalidation > 0
                        ? BotCredentialMessageDisposition
                                .BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_REQUIRED
                        : BotCredentialMessageDisposition
                                .BOT_CREDENTIAL_MESSAGE_DISPOSITION_RENDER_COMPLETED,
                card);
    }

    BotCredentialMessagesResponse completeCleanup(CompleteBotCredentialMessageCleanup command) {
        if (!command.hasMessage()) throw invalid();
        var requested = command.getMessage();
        requireReceipt(requested);
        UUID event = eventId(command.getEventId());
        String outcome =
                switch (command.getOutcome()) {
                    case BOT_CREDENTIAL_CLEANUP_OUTCOME_DELETED -> "DELETED";
                    case BOT_CREDENTIAL_CLEANUP_OUTCOME_ABSENT -> "ABSENT";
                    case BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED -> "SANITIZED";
                    default -> throw invalid();
                };
        // Missing/archived/unlinked user never denies sanitation of their exact recorded card.
        authority.lockUser(requested.getUserId());
        var card = lockCard(requested);
        if (command.getInvalidatedCredentialsVersion() <= 0
                || command.getInvalidatedCredentialsVersion()
                        != card.getInvalidatedCredentialsVersion()) throw stale();
        if (card.getCleanupState()
                == BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED) {
            return response(
                    BotCredentialMessageDisposition
                            .BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED,
                    card);
        }
        if (card.getCleanupState() != BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_PENDING
                || card.getRecordRevision() != requested.getRecordRevision()) throw stale();
        if (command.getOutcome()
                        == BotCredentialCleanupOutcome.BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED
                && card.getRenderState()
                        != BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_CONFIRMED_EDIT
                && card.getRenderState()
                        != BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_NO_EDIT)
            throw stale();
        requireRevisionCapacity(card);
        int changed =
                jdbc.update(
                        """
UPDATE bot_credential_messages
SET cleanup_state = 'CLEANED', cleanup_event_id = ?, cleanup_outcome = ?,
    cleaned_at = NOW(), updated_at = NOW(), record_revision = record_revision + 1
WHERE record_id = ? AND record_revision = ? AND cleanup_state = 'PENDING'
  AND (? <> 'SANITIZED' OR render_state IN ('CONFIRMED_EDIT', 'NO_EDIT'))
""",
                        event,
                        outcome,
                        card.getRecordId(),
                        card.getRecordRevision(),
                        outcome);
        if (changed != 1) throw stale();
        return response(
                BotCredentialMessageDisposition
                        .BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED,
                lockCard(requested));
    }

    private BotCredentialMessageReference lockCard(BotCredentialMessageReference requested) {
        List<BotCredentialMessageReference> rows =
                jdbc.query(
                        "SELECT "
                                + COLUMNS
                                + " FROM bot_credential_messages WHERE record_id = ? AND user_id ="
                                + " ? FOR UPDATE",
                        this::map,
                        requested.getRecordId(),
                        requested.getUserId());
        if (rows.isEmpty()) throw denied();
        var card = rows.getFirst();
        requireSameOriginal(requested, card);
        if (requested.getRenderRevision() != card.getRenderRevision()) throw stale();
        return card;
    }

    private BotCredentialMessageReference map(ResultSet row, int index) throws SQLException {
        var render =
                switch (row.getString("render_state")) {
                    case "IN_FLIGHT" ->
                            BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_IN_FLIGHT;
                    case "CONFIRMED_EDIT" ->
                            BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_CONFIRMED_EDIT;
                    case "NO_EDIT" -> BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_NO_EDIT;
                    case "UNCERTAIN" ->
                            BotCredentialRenderState.BOT_CREDENTIAL_RENDER_STATE_UNCERTAIN;
                    default ->
                            throw Status.UNAVAILABLE
                                    .withDescription("card render state unavailable")
                                    .asRuntimeException();
                };
        var cleanup =
                switch (row.getString("cleanup_state")) {
                    case "NOT_REQUIRED" ->
                            BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_NOT_REQUIRED;
                    case "PENDING" ->
                            BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_PENDING;
                    case "CLEANED" ->
                            BotCredentialCleanupState.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED;
                    default ->
                            throw Status.UNAVAILABLE
                                    .withDescription("card cleanup state unavailable")
                                    .asRuntimeException();
                };
        return BotCredentialMessageReference.newBuilder()
                .setRecordId(row.getLong("record_id"))
                .setUserId(row.getLong("user_id"))
                .setPrivateChatId(row.getLong("private_chat_id"))
                .setMessageId(row.getLong("message_id"))
                .setTelegramBindingVersion(row.getLong("telegram_binding_version"))
                .setCredentialsVersion(row.getLong("credentials_version"))
                .setRecordRevision(row.getLong("record_revision"))
                .setRenderRevision(row.getLong("render_revision"))
                .setRenderState(render)
                .setCleanupState(cleanup)
                .setInvalidatedCredentialsVersion(row.getLong("invalidated_by_credentials_version"))
                .build();
    }

    private static String renderCode(BotCredentialRenderState outcome) {
        return switch (outcome) {
            case BOT_CREDENTIAL_RENDER_STATE_CONFIRMED_EDIT -> "CONFIRMED_EDIT";
            case BOT_CREDENTIAL_RENDER_STATE_NO_EDIT -> "NO_EDIT";
            case BOT_CREDENTIAL_RENDER_STATE_UNCERTAIN -> "UNCERTAIN";
            default -> throw invalid();
        };
    }

    private static void requireOriginal(BotCredentialMessageReference message) {
        requireOwnIds(message.getPrivateChatId(), message.getPrivateChatId(), message.getUserId());
        if (message.getMessageId() <= 0
                || message.getCredentialsVersion() <= 0
                || message.getTelegramBindingVersion() <= 0) throw invalid();
    }

    private static void requireReceipt(BotCredentialMessageReference message) {
        requireOriginal(message);
        if (message.getRecordId() <= 0
                || message.getRecordRevision() <= 0
                || message.getRenderRevision() <= 0) {
            throw invalid();
        }
    }

    private static void requireSameOriginal(
            BotCredentialMessageReference requested, BotCredentialMessageReference card) {
        if (requested.getUserId() != card.getUserId()
                || requested.getPrivateChatId() != card.getPrivateChatId()
                || requested.getMessageId() != card.getMessageId()
                || requested.getCredentialsVersion() != card.getCredentialsVersion()
                || requested.getTelegramBindingVersion() != card.getTelegramBindingVersion())
            throw denied();
    }

    private static void requireRevisionCapacity(BotCredentialMessageReference card) {
        if (card.getRecordRevision() == Long.MAX_VALUE) throw stale();
    }

    private static UUID eventId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) throw invalid();
            return parsed;
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
    }

    private static BotCredentialMessagesResponse response(
            BotCredentialMessageDisposition disposition, BotCredentialMessageReference card) {
        return BotCredentialMessagesResponse.newBuilder()
                .setDisposition(disposition)
                .addMessages(card)
                .build();
    }
}
