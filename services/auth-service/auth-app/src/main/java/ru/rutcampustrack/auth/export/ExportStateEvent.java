package ru.rutcampustrack.auth.export;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import ru.rutcampustrack.auth.event.DomainEvent;

/** Metadata only; a live authenticated status/file request remains mandatory. */
public final class ExportStateEvent extends DomainEvent {
    public record Payload(@JsonProperty("job_id") UUID jobId, @JsonProperty("user_id") long userId,
            @JsonProperty("session_id") UUID sessionId, String state) {}
    public ExportStateEvent(Object source, UUID jobId, long userId, UUID sessionId, String state) {
        super(source, "export.state.changed", new Payload(jobId, userId, sessionId, state));
    }
}
