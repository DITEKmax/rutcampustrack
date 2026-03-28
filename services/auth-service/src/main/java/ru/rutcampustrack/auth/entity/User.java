package ru.rutcampustrack.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String email;
    private String phone;

    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Column(name = "employee_number", length = 32)
    private String employeeNumber;

    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "is_headman", nullable = false)
    private boolean isHeadman;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "initial_password", length = 128)
    private String initialPassword;

    @Column(name = "password_changed", nullable = false)
    private boolean passwordChanged;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
