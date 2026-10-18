package ru.rutcampustrack.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import ru.rutcampustrack.auth.entity.enums.AccountStatus;
import ru.rutcampustrack.auth.entity.enums.UserRole;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String login;

    @Setter
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(
            name = "last_name",
            nullable = false,
            length = 128,
            insertable = false,
            updatable = false)
    private String lastName;

    @Column(
            name = "first_name",
            nullable = false,
            length = 128,
            insertable = false,
            updatable = false)
    private String firstName;

    @Column(name = "middle_name", length = 128, insertable = false, updatable = false)
    private String middleName;

    @JsonIgnore
    @Transient
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder(lastName).append(' ').append(firstName);
        if (middleName != null && !middleName.isBlank()) {
            sb.append(' ').append(middleName);
        }
        return sb.toString();
    }

    @Column(insertable = false, updatable = false)
    private String email;

    @Column(insertable = false, updatable = false)
    private String phone;

    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Column(name = "employee_number", length = 32, insertable = false, updatable = false)
    private String employeeNumber;

    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "is_headman", nullable = false)
    private boolean isHeadman;

    @Column(name = "group_id", insertable = false, updatable = false)
    private Long groupId;

    @Setter
    @Column(name = "initial_password", length = 128)
    private String initialPassword;

    @Setter
    @Column(name = "password_changed", nullable = false)
    private boolean passwordChanged;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
