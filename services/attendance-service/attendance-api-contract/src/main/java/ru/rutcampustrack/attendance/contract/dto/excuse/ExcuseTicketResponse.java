package ru.rutcampustrack.attendance.contract.dto.excuse;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;

import java.time.Instant;
import java.util.List;

/**
 * HATEOAS response for an excuse ticket (D-04..D-08).
 * Extends RepresentationModel for _links support.
 * Plain Java class — no Lombok (contract module rule).
 *
 * status is exposed as a lowercase string matching Mongo storage and event payloads.
 */
@Schema(description = "Excuse-тикет студента: ID, связанные пары, тип, комментарий, статус, метаданные решения")
public class ExcuseTicketResponse extends RepresentationModel<ExcuseTicketResponse> {

    private final String id;
    private final Long studentId;
    private final Long groupId;
    private final String studentName;
    private final List<Long> lessonIds;
    private final ExcuseType excuseType;
    private final String comment;
    private final String status;
    private final Long decisionBy;
    private final String decisionComment;
    private final Instant decisionAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ExcuseTicketResponse(
            String id,
            Long studentId,
            Long groupId,
            String studentName,
            List<Long> lessonIds,
            ExcuseType excuseType,
            String comment,
            String status,
            Long decisionBy,
            String decisionComment,
            Instant decisionAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.studentId = studentId;
        this.groupId = groupId;
        this.studentName = studentName;
        this.lessonIds = lessonIds;
        this.excuseType = excuseType;
        this.comment = comment;
        this.status = status;
        this.decisionBy = decisionBy;
        this.decisionComment = decisionComment;
        this.decisionAt = decisionAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public Long getStudentId() { return studentId; }
    public Long getGroupId() { return groupId; }
    public String getStudentName() { return studentName; }
    public List<Long> getLessonIds() { return lessonIds; }
    public ExcuseType getExcuseType() { return excuseType; }
    public String getComment() { return comment; }
    public String getStatus() { return status; }
    public Long getDecisionBy() { return decisionBy; }
    public String getDecisionComment() { return decisionComment; }
    public Instant getDecisionAt() { return decisionAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
