package ru.rutcampustrack.academic.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.authdb.AuthDataCommands;

import java.sql.SQLException;
import java.util.UUID;

class AcademicAuthDataCommandsTest {
    @Test
    void credentialCommandRejectsMissingAndReadOnlyAuthorityBeforeJdbc() {
        var jdbc = mock(JdbcTemplate.class);
        var commands = new AuthDataCommands(jdbc);
        assertThatThrownBy(() -> commands.initialPassword(null, 2))
                .isInstanceOf(AdminAccessException.class);
        var readOnly =
                new InternalJwtClaims(
                        1, UUID.randomUUID(), 1, 1, "ADMIN", "ACTIVE", null, false, true);
        assertThatThrownBy(() -> commands.initialPassword(readOnly, 2))
                .isInstanceOf(AdminAccessException.class);
        var student =
                new InternalJwtClaims(
                        1, UUID.randomUUID(), 1, 1, "STUDENT", "ACTIVE", 1L, false, false);
        assertThatThrownBy(() -> commands.initialPassword(student, 2))
                .isInstanceOf(AdminAccessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void failedDependencyDropsCredentialBearingSqlDiagnosticsAndCause() {
        var jdbc = mock(JdbcTemplate.class);
        var marker = "synthetic-private-diagnostic";
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenThrow(
                        new UncategorizedSQLException(
                                marker, marker, new SQLException(marker, "08006")));
        var commands = new AuthDataCommands(jdbc);
        var actor =
                new InternalJwtClaims(
                        1, UUID.randomUUID(), 1, 1, "ADMIN", "ACTIVE", null, false, false);
        assertThatThrownBy(() -> commands.initialPassword(actor, 2))
                .isInstanceOfSatisfying(
                        AdminAccessException.class,
                        failure -> {
                            assertThat(failure.getStatusCode().value()).isEqualTo(503);
                            assertThat(failure.getMessage().contains(marker)).isFalse();
                            assertThat(failure.getCause()).isNull();
                        });
    }
}
