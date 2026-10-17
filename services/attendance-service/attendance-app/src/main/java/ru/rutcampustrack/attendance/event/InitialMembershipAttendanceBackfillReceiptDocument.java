package ru.rutcampustrack.attendance.event;

import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Permanent control-plane identity; excluded from business deletion and never TTL-purged. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "initial_membership_attendance_backfill_receipts")
public class InitialMembershipAttendanceBackfillReceiptDocument {
    @Id private String id;

    @Field("source_event_id")
    private String sourceEventId;

    @Field("history_id")
    private Long historyId;

    @Field("student_id")
    private Long studentId;

    @Field("group_id")
    private Long groupId;

    @Field("semester_id")
    private Long semesterId;

    @Field("joined_on")
    private LocalDate joinedOn;

    @Field("semester_ends_on")
    private LocalDate semesterEndsOn;

    @Field("payload_hash")
    private String payloadHash;

    @Field("result")
    private String result;

    @Field("created_at")
    private Instant createdAt;
}
