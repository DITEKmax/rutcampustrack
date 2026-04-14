package ru.rutcampustrack.academic.contract.dto.subject;

import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO for a subject with HATEOAS links.
 *
 * <p>Phase 60-01: добавлены {@code groupId} и {@code teacherIds} — для UI
 * отображения привязки к группе и списка назначенных преподавателей (D-15, D-19).
 */
public class SubjectResponse extends RepresentationModel<SubjectResponse> {

    private Long id;
    private String name;
    private SubjectType type;
    private Long groupId;
    private List<Long> teacherIds;
    private OffsetDateTime createdAt;

    public SubjectResponse() {}

    public SubjectResponse(Long id, String name, SubjectType type,
                           Long groupId, List<Long> teacherIds,
                           OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.groupId = groupId;
        this.teacherIds = teacherIds;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SubjectType getType() { return type; }
    public void setType(SubjectType type) { this.type = type; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public List<Long> getTeacherIds() { return teacherIds; }
    public void setTeacherIds(List<Long> teacherIds) { this.teacherIds = teacherIds; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
