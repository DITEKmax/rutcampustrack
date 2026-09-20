package ru.rutcampustrack.academic.contract.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Предмет (HATEOAS Level 3 с _links; включает groupId и teacherIds)")
public class SubjectResponse extends RepresentationModel<SubjectResponse> {

    private Long id;
    private String name;
    private SubjectType type;
    private Long groupId;
    private List<SubjectType> lessonTypes;
    private List<Long> teacherIds;
    private List<AssignmentSummaryResponse> assignments;
    private List<Long> createdAssignmentIds;
    private OffsetDateTime createdAt;

    public SubjectResponse() {}

    public SubjectResponse(Long id, String name, SubjectType type,
                           Long groupId, List<Long> teacherIds,
                           OffsetDateTime createdAt) {
        this(id, name, type, groupId, List.of(), teacherIds, List.of(), List.of(), createdAt);
    }

    public SubjectResponse(Long id,
                           String name,
                           SubjectType type,
                           Long groupId,
                           List<SubjectType> lessonTypes,
                           List<Long> teacherIds,
                           List<AssignmentSummaryResponse> assignments,
                           List<Long> createdAssignmentIds,
                           OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.groupId = groupId;
        this.lessonTypes = lessonTypes;
        this.teacherIds = teacherIds;
        this.assignments = assignments;
        this.createdAssignmentIds = createdAssignmentIds;
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

    public List<SubjectType> getLessonTypes() { return lessonTypes; }
    public void setLessonTypes(List<SubjectType> lessonTypes) { this.lessonTypes = lessonTypes; }

    public List<Long> getTeacherIds() { return teacherIds; }
    public void setTeacherIds(List<Long> teacherIds) { this.teacherIds = teacherIds; }

    public List<AssignmentSummaryResponse> getAssignments() { return assignments; }
    public void setAssignments(List<AssignmentSummaryResponse> assignments) { this.assignments = assignments; }

    public List<Long> getCreatedAssignmentIds() { return createdAssignmentIds; }
    public void setCreatedAssignmentIds(List<Long> createdAssignmentIds) { this.createdAssignmentIds = createdAssignmentIds; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
