package ru.rutcampustrack.academic.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

/** Propagates only explicit machine codes from Auth, never localized detail guesses. */
public final class AuthConfirmationErrors {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> DENIALS =
            Set.of(
                    "CURRENT_PASSWORD_INVALID",
                    "ADMIN_PERMISSION_DENIED",
                    "ADMIN_FULL_REQUIRED",
                    "ADMIN_TARGET_PROTECTED");

    private AuthConfirmationErrors() {}

    public static void propagate(HttpStatusCodeException failure) {
        String code = "";
        try {
            var body = JSON.readTree(failure.getResponseBodyAsString());
            code = body.path("code").asText("");
            if (code.isEmpty()) code = body.path("extras").path("code").asText("");
        } catch (Exception malformed) {
            /* Opaque denials remain opaque. */
        }
        if (failure.getStatusCode().value() == 403 && DENIALS.contains(code))
            throw new AdminAccessException(HttpStatus.FORBIDDEN, code);
        if (failure.getStatusCode().value() == 503 && "ADMIN_AUTHORITY_UNAVAILABLE".equals(code))
            throw AdminAccessException.unavailable();
    }
}
