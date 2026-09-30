package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Exact command receipt; it contains protocol metadata, never request payloads. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "semester_deletion_participant_receipts")
public class SemesterDeletionParticipantReceiptDocument {

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
    @Field("expected_participant_digest")
    private String expectedParticipantDigest;
    @Field("participant_digest")
    private String participantDigest;
    @Field("attendance_marks_count")
    private Long attendanceMarksCount;
    @Field("student_requests_count")
    private Long studentRequestsCount;
    @Field("status")
    private String status;
    @Field("blocking_reason")
    private String blockingReason;
    @Field("created_at")
    private Instant createdAt;
}
