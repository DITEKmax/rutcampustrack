package ru.rutcampustrack.academic.contract.dto.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO for a headman assistant record with HATEOAS links.
 */
@Schema(description = "Помощник старосты с набором делегированных прав (HATEOAS Level 3 с _links)")
public class AssistantResponse extends RepresentationModel<AssistantResponse> {

    private Long id;
    private Long studentId;
    private String studentName;
    private String fullName;
    private String login;
    private Long groupId;
    private List<AssistantPermission> permissions;
    private boolean active;
    private OffsetDateTime assignedAt;
    private OffsetDateTime revokedAt;

    public AssistantResponse() {}

    public AssistantResponse(Long id, Long studentId, String studentName, Long groupId,
                             List<AssistantPermission> permissions, boolean active,
                             OffsetDateTime assignedAt, OffsetDateTime revokedAt) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.fullName = studentName;
        this.groupId = groupId;
        this.permissions = permissions;
        this.active = active;
        this.assignedAt = assignedAt;
        this.revokedAt = revokedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public List<AssistantPermission> getPermissions() { return permissions; }
    public void setPermissions(List<AssistantPermission> permissions) { this.permissions = permissions; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }

    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
}
