package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Durable source fence: stale Attendance writers may not recreate a transferred lesson. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "lesson_transfer_fences")
public class LessonTransferFenceDocument {

    @Id
    private String id;
    @Field("operation_id")
    private String operationId;
    @Field("transfer_payload_hash")
    private String transferPayloadHash;
    @Field("source_lesson_id")
    private Long sourceLessonId;
    @Field("target_lesson_id")
    private Long targetLessonId;
    @Field("group_id")
    private Long groupId;
    @Field("created_at")
    private Instant createdAt;
}
