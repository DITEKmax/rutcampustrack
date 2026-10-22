package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import ru.rutcampustrack.shared.events.EventVersion;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

import java.util.List;

/** Existing v1 personal administrative-access fact, now committed by its Auth owner. */
@EventVersion(1)
public final class AdminAccessChangedEvent extends DomainEvent {
    public record Payload(
            @JsonProperty("target_user_id") long targetUserId,
            @JsonProperty("mode") String mode,
            @JsonProperty("permissions") List<String> permissions,
            @JsonProperty("permissions_changed") boolean permissionsChanged,
            @JsonProperty("version") long version,
            @JsonProperty("actor_user_id") Long actorUserId) {}

    public AdminAccessChangedEvent(Object source, AdminAccessProfile result, long actorUserId) {
        super(
                source,
                "admin.access.changed",
                new Payload(
                        result.userId(),
                        result.mode(),
                        result.permissions(),
                        true,
                        result.version(),
                        actorUserId));
    }
}
