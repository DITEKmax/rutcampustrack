package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@SQLRestriction("status <> 'archived'")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 32)
    private String login;

    @Setter
    @Column(name = "password_hash")
    private String passwordHash;

    @Setter
    @Column(name = "last_name", nullable = false, length = 128)
    private String lastName;

    @Setter
    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Setter
    @Column(name = "middle_name", length = 128)
    private String middleName;

    /** Composed full name for display/log purposes. Не хранится в БД. */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder(lastName).append(' ').append(firstName);
        if (middleName != null && !middleName.isBlank()) {
            sb.append(' ').append(middleName);
        }
        return sb.toString();
    }

    @Setter
    private String email;

    @Setter
    private String phone;

    @Setter
    @Column(name = "telegram_id")
    private Long telegramId;

    @Setter
    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Setter
    @Column(name = "employee_number", length = 32)
    private String employeeNumber;

    @Setter
    @Column(nullable = false)
    private UserRole role;

    @Setter
    @Column(nullable = false)
    private AccountStatus status;

    @Setter
    @Column(name = "is_headman", nullable = false)
    private boolean isHeadman;

    @Setter
    @Column(name = "group_id")
    private Long groupId;

    @Setter
    @Column(name = "initial_password", length = 128)
    private String initialPassword;

    @Setter
    @Column(name = "password_changed", nullable = false)
    private boolean passwordChanged;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
