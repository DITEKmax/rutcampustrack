package ru.rutcampustrack.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import ru.rutcampustrack.auth.entity.enums.AccountStatus;
import ru.rutcampustrack.auth.entity.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Auth-owned identity only. Academic presentation is composed outside authentication. */
@Entity
@Table(name = "auth_identities")
@Getter
@NoArgsConstructor
public class User {
    @Id private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String login;

    @Setter
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Setter
    @Column(name = "initial_password", length = 128)
    private String initialPassword;

    @Setter
    @Column(name = "password_changed", nullable = false)
    private boolean passwordChanged;

    @Column(name = "generated_password_origin", length = 16)
    private String generatedPasswordOrigin;

    @Column(name = "account_state", nullable = false)
    private String accountState;

    @Column(name = "roles_version", nullable = false)
    private long rolesVersion;

    @Column(name = "credentials_version", nullable = false)
    private long credentialsVersion;

    @Column(name = "telegram_binding_version", nullable = false)
    private long telegramBindingVersion;

    @Column(name = "provision_operation_id", nullable = false)
    private UUID provisionOperationId;

    @Column(name = "fence_operation_id")
    private UUID fenceOperationId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    // Compatibility for the retired entity-based JWT builder. Admission and role
    // selection always use the current Auth session/grant ports, never these values.
    @JsonIgnore
    @Transient
    public UserRole getRole() {
        return null;
    }

    @JsonIgnore
    @Transient
    public Long getGroupId() {
        return null;
    }

    @JsonIgnore
    @Transient
    public boolean isHeadman() {
        return false;
    }

    @JsonIgnore
    @Transient
    public AccountStatus getStatus() {
        return "ACTIVE".equals(accountState) && fenceOperationId == null
                ? AccountStatus.ACTIVE
                : AccountStatus.ARCHIVED;
    }

    @JsonIgnore
    @Transient
    public String getDisplayName() {
        return "";
    }
}
