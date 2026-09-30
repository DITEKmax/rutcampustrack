package ru.rutcampustrack.attendance.studentrequest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.Binary;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import ru.rutcampustrack.attendance.studentrequest.AttachmentState;

import java.time.Instant;

/**
 * Mongo attachment document.  A single BSON binary stays below Mongo's 16 MiB
 * document limit because the domain caps each file at 10 MiB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "request_attachments")
public class RequestAttachmentDocument {

    @Id
    private String id;
    @Field("request_id")
    private String requestId;
    @Field("owner_student_id")
    private Long ownerStudentId;
    @Field("group_id")
    private Long groupId;
    @Field("semester_id")
    private Long semesterId;
    @Field("position")
    private Integer position;
    @Field("name")
    private String name;
    @Field("type")
    private String contentType;
    @Field("size")
    private Long size;
    @Field("sha256")
    private String sha256;
    @Field("state")
    private AttachmentState state;
    @Field("data")
    private Binary data;
    @Field("uploaded_at")
    private Instant uploadedAt;
    @Field("expires_at")
    private Instant expiresAt;
    @Field("expired_at")
    private Instant expiredAt;
}
