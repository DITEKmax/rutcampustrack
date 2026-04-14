package ru.rutcampustrack.attendance.excuse.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB document for excuse tickets (Phase 59, D-01, D-02).
 * Collection: excuse_tickets.
 * Lombok allowed in app module (entity).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "excuse_tickets")
public class ExcuseTicket {

    @Id
    private String id;

    @Field("student_id")
    private Long studentId;

    @Field("group_id")
    private Long groupId;

    @Field("student_name")
    private String studentName;

    @Field("lesson_ids")
    private List<Long> lessonIds;

    @Field("excuse_type")
    private ExcuseType excuseType;

    @Field("comment")
    private String comment;

    @Field("status")
    @Builder.Default
    private ExcuseTicketStatus status = ExcuseTicketStatus.SUBMITTED;

    @Field("decision_by")
    private Long decisionBy;

    @Field("decision_comment")
    private String decisionComment;

    @Field("decision_at")
    private Instant decisionAt;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;
}
