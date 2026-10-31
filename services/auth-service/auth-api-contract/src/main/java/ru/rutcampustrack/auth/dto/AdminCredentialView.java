package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/** Current permitted direct disclosure only; never a log, cache or event payload. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminCredentialView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[1-9][0-9]*$")
                String userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[1-9][0-9]*$")
                String credentialsVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State state,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        types = {"string", "null"},
                        pattern = "^(initial|headman_reset|admin_reset)$")
                String origin,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        types = {"string", "null"},
                        maxLength = 128)
                String password) {
    public enum State {
        GENERATED,
        USER_CHANGED,
        UNAVAILABLE
    }

    public AdminCredentialView {
        if (!positive(userId)
                || !positive(credentialsVersion)
                || state == null
                || (state == State.GENERATED
                        ? origin == null
                                || !Set.of("initial", "headman_reset", "admin_reset")
                                        .contains(origin)
                                || password == null
                                || password.isBlank()
                                || password.length() > 128
                        : origin != null || password != null))
            throw new IllegalArgumentException("invalid administrative credential view");
    }

    private static boolean positive(String value) {
        try {
            return value != null && value.matches("[1-9][0-9]{0,18}") && Long.parseLong(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "AdminCredentialView[state="
                + state
                + ", origin="
                + origin
                + ", password=<redacted>]";
    }
}
