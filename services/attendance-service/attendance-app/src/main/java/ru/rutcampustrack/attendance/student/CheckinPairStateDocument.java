package ru.rutcampustrack.attendance.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "student_checkin_pairs")
public class CheckinPairStateDocument {
    @Id
    private String id;
    @Field("student_id")
    private Long studentId;
    @Field("lesson_id")
    private Long lessonId;
    @Field("group_id")
    private Long groupId;
    @Field("fence")
    private Long fence;
    @Field("last_attempt_at")
    private Instant lastAttemptAt;
    @Field("retry_at")
    private Instant retryAt;
    @Field("updated_at")
    private Instant updatedAt;
}
