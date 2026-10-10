package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.rutcampustrack.shared.events.EventVersion;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

import java.util.List;

/** Personal administrative access fact, containing no sessions or credentials. */
@EventVersion(1)
public class AdministrativeNotificationAdminAccessChangedEvent extends DomainEvent {
    public record Payload(
            @JsonProperty("target_user_id") long targetUserId,
            @JsonProperty("mode") String mode,
            @JsonProperty("permissions") List<String> permissions,
            @JsonProperty("permissions_changed") boolean permissionsChanged,
            @JsonProperty("version") long version,
            @JsonProperty("actor_user_id") Long actorUserId) {}

    public AdministrativeNotificationAdminAccessChangedEvent(Object source, AdminAccessProfile result,
                                                           boolean permissionsChanged, Long actorUserId) {
        super(source, "admin.access.changed", new Payload(result.userId(), result.mode(),
                result.permissions(), permissionsChanged, result.version(), actorUserId));
    }
}
