package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CurrentSessionResponse")
public record CurrentSessionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(nullable = true) String groupLabel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sessionVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rolesVersion,
        @Schema(nullable = true) String activeRole,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RoleGrantResponse> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean readOnly,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PasswordPolicyResponse passwordPolicy
) {
    public CurrentSessionResponse {
        requireUuid(sessionId, "sessionId");
        requirePositiveDecimal(userId, "userId");
        Objects.requireNonNull(displayName, "displayName");
        requirePositiveDecimal(sessionVersion, "sessionVersion");
        requirePositiveDecimal(rolesVersion, "rolesVersion");
        if (activeRole != null) {
            requireUppercase(activeRole, "activeRole");
        }
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        if (roles.stream().map(RoleGrantResponse::role).distinct().count() != roles.size()) {
            throw new IllegalArgumentException("roles must contain unique role values");
        }
        Objects.requireNonNull(passwordPolicy, "passwordPolicy");
    }

    private static void requireUuid(String value, String name) {
        Objects.requireNonNull(value, name);
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException(name + " must be a canonical lowercase UUID string");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a UUID string", exception);
        }
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }

    private static void requireUppercase(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be an uppercase wire value");
        }
    }
}
