package ru.rutcampustrack.auth.authority;

import static ru.rutcampustrack.auth.authority.IdentityOperationFailure.error;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/** Caller holds the operation global lock and target identity locks; no RPC or expiry release. */
final class AuthTelegramBindingReservations {
    private final JdbcTemplate jdbc;

    AuthTelegramBindingReservations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void requireAvailable(long telegram, long user) {
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM auth_identities WHERE telegram_id=? AND id<>?)"
                            + " OR EXISTS(SELECT 1 FROM auth_telegram_binding_reservations WHERE"
                            + " telegram_id=? AND user_id<>?)",
                        Boolean.class,
                        telegram,
                        user,
                        telegram,
                        user))) throw error(Status.ALREADY_EXISTS, "TELEGRAM_ID_CONFLICT");
    }

    void claim(long telegram, long user, UUID operation, UUID supersedes) {
        requireAvailable(telegram, user);
        var old =
                jdbc.queryForList(
                        "SELECT operation_id FROM auth_telegram_binding_reservations WHERE"
                            + " telegram_id=? AND user_id=?",
                        UUID.class,
                        telegram,
                        user);
        if (old.isEmpty())
            jdbc.update(
                    "INSERT INTO"
                        + " auth_telegram_binding_reservations(telegram_id,user_id,operation_id)"
                        + " VALUES(?,?,?)",
                    telegram,
                    user,
                    operation);
        else if (supersedes != null && old.getFirst().equals(supersedes))
            jdbc.update(
                    "UPDATE auth_telegram_binding_reservations SET operation_id=? WHERE"
                        + " telegram_id=? AND user_id=? AND operation_id=?",
                    operation,
                    telegram,
                    user,
                    supersedes);
        else throw error(Status.ABORTED, "IDENTITY_STATE_STALE");
    }

    boolean owns(long telegram, long user, UUID operation) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM auth_telegram_binding_reservations WHERE"
                            + " telegram_id=? AND user_id=? AND operation_id=?)",
                        Boolean.class,
                        telegram,
                        user,
                        operation));
    }

    void releaseCommitted(UUID operation) {
        jdbc.update(
                "DELETE FROM auth_telegram_binding_reservations WHERE operation_id=?", operation);
    }
}
