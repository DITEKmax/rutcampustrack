package ru.rutcampustrack.academic.contract.dto.assistant;

import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

import java.time.OffsetDateTime;
import java.util.List;

public class AssistantResponse extends RepresentationModel<AssistantResponse> {

    private Long id;
    private Long studentId;
    private String studentName;
    private Long groupId;
    private List<AssistantPermission> permissions;
    private boolean active;
    private OffsetDateTime assignedAt;
    private OffsetDateTime revokedAt;

    public AssistantResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

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
