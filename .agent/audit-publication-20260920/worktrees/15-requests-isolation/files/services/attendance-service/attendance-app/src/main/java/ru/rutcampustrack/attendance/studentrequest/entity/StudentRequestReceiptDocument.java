package ru.rutcampustrack.attendance.studentrequest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Receipt shared by EXCUSE and manual LATE_CHECKIN commands. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "student_request_receipts")
public class StudentRequestReceiptDocument {

    @Id
    private String id;
    @Field("student_id")
    private Long studentId;
    @Field("command_kind")
    private String commandKind;
    @Field("idempotency_key")
    private String idempotencyKey;
    @Field("payload_hash")
    private String payloadHash;
    @Field("request_id")
    private String requestId;
    @Field("created_at")
    private Instant createdAt;
}
