package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Immutable receipt keyed by operation and participant command, independent of Rabbit delivery id. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "semester_archive_participant_receipts")
public class SemesterArchiveParticipantReceiptDocument {

    @Id
    private String id;
    @Field("operation_id")
    private String operationId;
    @Field("semester_id")
    private Long semesterId;
    @Field("state_version")
    private Long stateVersion;
    @Field("command")
    private String command;
    @Field("status")
    private String status;
    @Field("blocking_reason")
    private String blockingReason;
    @Field("created_at")
    private Instant createdAt;
}
