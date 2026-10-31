package ru.rutcampustrack.auth.authority;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.rutcampustrack.authauthority.grpc.InitialCredentialSnapshot;

/**
 * Data reader only: callers hold identity locks and perform their own live disclosure
 * authorization.
 */
@Repository
public class AuthInitialCredentialReader {
    private final JdbcTemplate jdbc;

    public AuthInitialCredentialReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InitialCredentialSnapshot active(long userId) {
        var rows =
                jdbc.query(
                        """
                SELECT id,login,initial_password,password_changed,credentials_version,telegram_binding_version,generated_password_origin
                FROM auth_identities WHERE id=? AND account_state='ACTIVE' AND fence_operation_id IS NULL
                """,
                        (rs, n) -> {
                            var row =
                                    InitialCredentialSnapshot.newBuilder()
                                            .setUserId(rs.getLong(1))
                                            .setLogin(rs.getString(2))
                                            .setPasswordChanged(rs.getBoolean(4))
                                            .setCredentialsVersion(rs.getLong(5))
                                            .setTelegramBindingVersion(rs.getLong(6));
                            String initial = rs.getString(3);
                            if (!rs.getBoolean(4) && initial != null)
                                row.setInitialPassword(initial);
                            String origin = rs.getString(7);
                            if (origin != null) row.setGeneratedPasswordOrigin(origin);
                            return row.build();
                        },
                        userId);
        return rows.size() == 1 ? rows.getFirst() : null;
    }
}
