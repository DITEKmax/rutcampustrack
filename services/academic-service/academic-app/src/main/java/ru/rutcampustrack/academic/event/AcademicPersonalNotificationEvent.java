package ru.rutcampustrack.academic.event;

import ru.rutcampustrack.shared.events.EventVersion;
import java.time.LocalDate;
import java.util.*;

/** Closed personal facts. No names, reasons, credentials or restored privileged role. */
@EventVersion(1)
public final class AcademicPersonalNotificationEvent extends DomainEvent {
    private AcademicPersonalNotificationEvent(String type, Map<String, Object> payload) { super(type, type, payload); }

    public static AcademicPersonalNotificationEvent membership(long user, long oldGroup, long newGroup,
                                                               LocalDate effectiveDate, Long actor) {
        var payload = base(user, actor);
        payload.put("old_group_id", oldGroup); payload.put("new_group_id", newGroup);
        payload.put("effective_date", Objects.requireNonNull(effectiveDate).toString());
        payload.put("change", "TRANSFERRED");
        return new AcademicPersonalNotificationEvent("group.membership.changed", payload);
    }
    public static AcademicPersonalNotificationEvent headman(long user, long group, boolean before, boolean after, Long actor) {
        if (before == after) throw new IllegalArgumentException("headman fact requires a transition");
        var payload = base(user, actor);
        payload.put("group_id", group); payload.put("before_active", before); payload.put("after_active", after);
        payload.put("change", after ? "GRANTED" : "REVOKED");
        return new AcademicPersonalNotificationEvent("headman.access.changed", payload);
    }
    public static AcademicPersonalNotificationEvent assistant(long user, long group, boolean before, boolean after,
                                                              String[] beforePermissions, String[] afterPermissions, Long actor) {
        var oldPermissions = permissions(beforePermissions);
        var newPermissions = permissions(afterPermissions);
        if (before == after && oldPermissions.equals(newPermissions)) throw new IllegalArgumentException("assistant fact requires a transition");
        var payload = base(user, actor);
        payload.put("group_id", group); payload.put("before_active", before); payload.put("after_active", after);
        payload.put("before_permissions", oldPermissions); payload.put("after_permissions", newPermissions);
        payload.put("change", !after ? "REVOKED" : !before ? "GRANTED" : "PERMISSIONS_CHANGED");
        return new AcademicPersonalNotificationEvent("assistant.access.changed", payload);
    }
    private static LinkedHashMap<String, Object> base(long user, Long actor) {
        if (user <= 0) throw new IllegalArgumentException("positive personal target required");
        var payload = new LinkedHashMap<String, Object>(); payload.put("user_id", user);
        if (actor != null && actor > 0) payload.put("actor_user_id", actor);
        return payload;
    }
    public static List<String> permissions(String[] values) {
        return values == null ? List.of() : Arrays.stream(values).filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT)).distinct().sorted().toList();
    }
}
