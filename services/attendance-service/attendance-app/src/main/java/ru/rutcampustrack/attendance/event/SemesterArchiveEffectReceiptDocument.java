package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Durable proof that Attendance applied one exact Schedule lifecycle event. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "semester_archive_effect_receipts")
public class SemesterArchiveEffectReceiptDocument {

    @Id
    private String id;
    @Field("source_event_id")
    private String sourceEventId;
    @Field("target")
    private String target;
    @Field("source_event_type")
    private String sourceEventType;
    @Field("semester_id")
    private Long semesterId;
    @Field("payload_hash")
    private String payloadHash;
    @Field("result")
    private String result;
    @Field("blocking_reason")
    private String blockingReason;
    @Field("created_at")
    private Instant createdAt;
}
