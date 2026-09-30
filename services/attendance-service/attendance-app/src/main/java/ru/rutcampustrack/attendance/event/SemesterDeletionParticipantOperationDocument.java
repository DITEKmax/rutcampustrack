package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Durable protocol state retained separately from the Attendance domain rows. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "semester_deletion_participant_operations")
public class SemesterDeletionParticipantOperationDocument {

    @Id
    private String id;
    @Field("semester_id")
    private Long semesterId;
    @Field("state_version")
    private Long stateVersion;
    @Field("expected_participant_digest")
    private String expectedParticipantDigest;
    @Field("previous_barrier_state")
    private String previousBarrierState;
    @Field("previous_state_version")
    private Long previousStateVersion;
    @Field("participant_digest")
    private String participantDigest;
    @Field("attendance_marks_count")
    private Long attendanceMarksCount;
    @Field("student_requests_count")
    private Long studentRequestsCount;
    @Field("status")
    private String status;
    @Field("created_at")
    private Instant createdAt;
    @Field("updated_at")
    private Instant updatedAt;
}
