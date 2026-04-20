package ru.rutcampustrack.attendance.latecheckin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;

import java.time.Instant;

/**
 * MongoDB document for late-checkin requests.
 * Collection: late_checkin_requests.
 * Lombok allowed in app module.
 * <p>
 * Indexes declared programmatically in {@code MongoConfig.initIndexes()}
 * (proj convention — avoids dependency on
 * {@code spring.data.mongodb.auto-index-creation=true}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "late_checkin_requests")
public class LateCheckinRequest {

    @Id
    private String id;

    @Field("student_id")
    private Long studentId;

    @Field("group_id")
    private Long groupId;

    @Field("lesson_id")
    private Long lessonId;

    @Field("student_name")
    private String studentName;

    @Field("status")
    @Builder.Default
    private LateCheckinRequestStatus status = LateCheckinRequestStatus.PENDING;

    @Field("decision_by")
    private Long decisionBy;

    @Field("decision_at")
    private Instant decisionAt;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;
}
