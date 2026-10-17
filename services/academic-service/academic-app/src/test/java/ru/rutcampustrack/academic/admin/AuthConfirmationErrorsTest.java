package ru.rutcampustrack.academic.admin;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

class AuthConfirmationErrorsTest {
    @Test
    void explicitPasswordCodePropagatesButOpaqueOrPermissionDenialNeverBecomesPasswordError() {
        assertThatThrownBy(
                        () ->
                                AuthConfirmationErrors.propagate(
                                        error(
                                                "{\"extras\":{\"code\":\"CURRENT_PASSWORD_INVALID\"}}")))
                .isInstanceOf(AdminAccessException.class)
                .hasMessageContaining("CURRENT_PASSWORD_INVALID");
        assertThatThrownBy(
                        () ->
                                AuthConfirmationErrors.propagate(
                                        error("{\"code\":\"ADMIN_PERMISSION_DENIED\"}")))
                .isInstanceOf(AdminAccessException.class)
                .hasMessageContaining("ADMIN_PERMISSION_DENIED");
        assertThatCode(
                        () ->
                                AuthConfirmationErrors.propagate(
                                        error("{\"detail\":\"CURRENT_PASSWORD_INVALID\"}")))
                .doesNotThrowAnyException();
        assertThatCode(() -> AuthConfirmationErrors.propagate(error("opaque forbidden")))
                .doesNotThrowAnyException();
    }

    private HttpClientErrorException error(String body) {
        return new HttpClientErrorException(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }
}
