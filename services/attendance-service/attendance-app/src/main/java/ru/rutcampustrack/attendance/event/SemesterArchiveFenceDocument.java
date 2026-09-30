package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Durable per-semester serialization point shared by writers and archive commands. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "semester_archive_fences")
public class SemesterArchiveFenceDocument {

    @Id
    private String id;
    @Field("semester_id")
    private Long semesterId;
    @Field("state_version")
    private Long stateVersion;
    @Field("operation_id")
    private String operationId;
    @Field("barrier_state")
    private String barrierState;
    @Field("write_fence")
    private Long writeFence;
    @Field("updated_at")
    private Instant updatedAt;
}
