package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;

import java.time.OffsetDateTime;

/**
 * Response DTO returned when a new user is created.
 * Includes all UserResponse fields plus the one-time {@code initialPassword}
 * that the admin must securely deliver to the user (per D-10).
 */
@Schema(description = "Ответ при создании нового пользователя — все поля UserResponse + " +
        "одноразовый начальный пароль (показывается только один раз, per D-10)")
public class UserCreatedResponse extends RepresentationModel<UserCreatedResponse> {

    private Long id;
    private String login;
    private String lastName;
    private String firstName;
    private String middleName;
    private UserRole role;
    private AccountStatus status;
    private Long groupId;
    private boolean headman;
    private String employeeNumber;
    private Long telegramId;
    private OffsetDateTime createdAt;

    /** One-time initial password shown only on creation. Never returned again. */
    private String initialPassword;

    public UserCreatedResponse() {}

    public UserCreatedResponse(Long id, String login, String lastName, String firstName, String middleName,
                               UserRole role, AccountStatus status, Long groupId, boolean headman,
                               String employeeNumber, Long telegramId, OffsetDateTime createdAt,
                               String initialPassword) {
        this.id = id;
        this.login = login;
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.role = role;
        this.status = status;
        this.groupId = groupId;
        this.headman = headman;
        this.employeeNumber = employeeNumber;
        this.telegramId = telegramId;
        this.createdAt = createdAt;
        this.initialPassword = initialPassword;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public boolean isHeadman() { return headman; }
    public void setHeadman(boolean headman) { this.headman = headman; }

    public String getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }

    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Schema(description = "Одноразовый начальный пароль (per D-10). Показывается только " +
            "при создании, впоследствии недоступен. Admin обязан передать безопасным каналом.",
            example = "Kx9mQ7wP2n")
    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
}
