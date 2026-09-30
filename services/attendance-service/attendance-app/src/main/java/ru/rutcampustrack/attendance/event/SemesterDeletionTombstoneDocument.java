package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Permanent local deletion proof keyed by semester, independent of later replay IDs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "semester_deletion_tombstones")
public class SemesterDeletionTombstoneDocument {

    @Id
    private String id;
    @Field("semester_id")
    private Long semesterId;
    @Field("operation_id")
    private String operationId;
    @Field("state_version")
    private Long stateVersion;
    @Field("participant_digest")
    private String participantDigest;
    @Field("attendance_marks_count")
    private Long attendanceMarksCount;
    @Field("student_requests_count")
    private Long studentRequestsCount;
    @Field("deleted_at")
    private Instant deletedAt;
}
