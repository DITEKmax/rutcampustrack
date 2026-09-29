package ru.rutcampustrack.attendance.event;

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
@Document(collection = "lesson_transfer_receipts")
public class LessonTransferReceiptDocument {

    @Id
    private String id;
    @Field("request_key")
    private String requestKey;
    @Field("actor_id")
    private Long actorId;
    @Field("group_id")
    private Long groupId;
    @Field("semester_id")
    private Long semesterId;
    @Field("occurrence_id")
    private Long occurrenceId;
    @Field("transfer_payload_hash")
    private String transferPayloadHash;
    @Field("transfer_revision")
    private Long transferRevision;
    @Field("source_snapshot")
    private LessonTransferSnapshotDocument sourceSnapshot;
    @Field("target_snapshot")
    private LessonTransferSnapshotDocument targetSnapshot;
    @Field("result")
    private String result;
    @Field("retryable")
    private Boolean retryable;
    @Field("error_code")
    private String errorCode;
    @Field("created_at")
    private Instant createdAt;
}
