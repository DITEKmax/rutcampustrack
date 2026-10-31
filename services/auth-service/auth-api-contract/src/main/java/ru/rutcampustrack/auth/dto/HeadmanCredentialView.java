package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/** Only a current generated password may cross this scoped, non-cacheable boundary. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HeadmanCredentialView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String studentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String credentialsVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Origin origin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String password) {
    public HeadmanCredentialView {
        if (!positive(studentId)
                || !positive(credentialsVersion)
                || state == null
                || (state == State.GENERATED
                        ? origin == null
                                || password == null
                                || password.isBlank()
                                || password.length() > 128
                        : origin != null || password != null)) {
            throw new IllegalArgumentException("invalid scoped credential view");
        }
    }

    private static boolean positive(String value) {
        try {
            return value != null && value.matches("[1-9][0-9]{0,18}") && Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public enum State {
        GENERATED,
        STUDENT_CHANGED,
        UNAVAILABLE
    }

    public enum Origin {
        INITIAL("initial"),
        HEADMAN_RESET("headman_reset"),
        ADMIN_RESET("admin_reset");
        private final String code;

        Origin(String code) {
            this.code = code;
        }

        @JsonValue
        public String code() {
            return code;
        }
    }

    @Override
    public String toString() {
        return "HeadmanCredentialView[state="
                + state
                + ", origin="
                + origin
                + ", password=<redacted>]";
    }
}
