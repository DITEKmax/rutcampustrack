package ru.rutcampustrack.academic.contract.dto.user;

import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;

import java.time.OffsetDateTime;

public class UserCreatedResponse extends RepresentationModel<UserCreatedResponse> {

    private Long id;
    private String login;
    private String displayName;
    private UserRole role;
    private AccountStatus status;
    private Long groupId;
    private boolean headman;
    private String employeeNumber;
    private Long telegramId;
    private OffsetDateTime createdAt;
    private String initialPassword;

    public UserCreatedResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

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

    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
}
