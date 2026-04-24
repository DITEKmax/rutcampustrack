package ru.rutcampustrack.academic.contract.dto.assignment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

/**
 * Response DTO for a teacher-subject-group assignment with HATEOAS links.
 */
@Schema(description = "Назначение преподавателя на предмет/группу/семестр (HATEOAS Level 3 с _links)")
public class AssignmentResponse extends RepresentationModel<AssignmentResponse> {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private Long subjectId;
    private String subjectName;
    private Long groupId;
    private String groupName;
    private Long semesterId;

    public AssignmentResponse() {}

    public AssignmentResponse(Long id, Long teacherId, String teacherName,
                              Long subjectId, String subjectName,
                              Long groupId, String groupName, Long semesterId) {
        this.id = id;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.groupId = groupId;
        this.groupName = groupName;
        this.semesterId = semesterId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public Long getSemesterId() { return semesterId; }
    public void setSemesterId(Long semesterId) { this.semesterId = semesterId; }
}
