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
@Document(collection = "student_checkin_receipts")
public class StudentCheckinReceiptDocument {
    @Id
    private String id;
    @Field("student_id")
    private Long studentId;
    @Field("lesson_id")
    private Long lessonId;
    @Field("idempotency_key")
    private String idempotencyKey;
    @Field("payload_hash")
    private String payloadHash;
    @Field("outcome")
    private String outcome;
    @Field("attendance_status")
    private String attendanceStatus;
    @Field("attendance_source")
    private String attendanceSource;
    @Field("marked_at")
    private Instant markedAt;
    @Field("request_id")
    private String requestId;
    @Field("request_status")
    private String requestStatus;
    @Field("request_resolution")
    private String requestResolution;
    @Field("retry_at")
    private Instant retryAt;
    @Field("server_now")
    private Instant serverNow;
    @Field("created_at")
    private Instant createdAt;
}
