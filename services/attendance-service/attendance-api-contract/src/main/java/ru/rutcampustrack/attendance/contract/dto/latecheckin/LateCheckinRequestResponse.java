package ru.rutcampustrack.attendance.contract.dto.latecheckin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.time.Instant;

/**
 * HATEOAS response for a late-checkin request.
 * Student asked the headman to confirm their presence after a failed geo-checkin.
 * Plain class (no Lombok) — contract module rule.
 * status is lowercase to match Mongo storage and event payloads.
 */
@Schema(description = "Запрос поздней отметки от студента к старосте: ID, студент, пара, статус, метаданные решения")
public class LateCheckinRequestResponse extends RepresentationModel<LateCheckinRequestResponse> {

    private final String id;
    private final Long studentId;
    private final Long groupId;
    private final Long lessonId;
    private final String studentName;
    private final String status;
    private final Long decisionBy;
    private final Instant decisionAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public LateCheckinRequestResponse(
            String id,
            Long studentId,
            Long groupId,
            Long lessonId,
            String studentName,
            String status,
            Long decisionBy,
            Instant decisionAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.studentId = studentId;
        this.groupId = groupId;
        this.lessonId = lessonId;
        this.studentName = studentName;
        this.status = status;
        this.decisionBy = decisionBy;
        this.decisionAt = decisionAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public Long getStudentId() { return studentId; }
    public Long getGroupId() { return groupId; }
    public Long getLessonId() { return lessonId; }
    public String getStudentName() { return studentName; }
    public String getStatus() { return status; }
    public Long getDecisionBy() { return decisionBy; }
    public Instant getDecisionAt() { return decisionAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
