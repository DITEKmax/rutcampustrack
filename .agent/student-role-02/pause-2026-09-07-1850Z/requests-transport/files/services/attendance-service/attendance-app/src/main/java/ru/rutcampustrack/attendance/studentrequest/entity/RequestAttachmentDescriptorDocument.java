package ru.rutcampustrack.attendance.studentrequest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import ru.rutcampustrack.attendance.studentrequest.AttachmentState;

import java.time.Instant;

/** Descriptor embedded in a ticket and copied into event payloads. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestAttachmentDescriptorDocument {

    @Field("id")
    private String id;
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
    @Field("uploaded_at")
    private Instant uploadedAt;
    @Field("expires_at")
    private Instant expiresAt;
    @Field("expired_at")
    private Instant expiredAt;
}
