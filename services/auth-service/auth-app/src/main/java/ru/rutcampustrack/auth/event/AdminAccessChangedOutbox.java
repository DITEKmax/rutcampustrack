package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.authoperations.grpc.AdmissionAdminProfile;
import ru.rutcampustrack.shared.events.AbstractEventPublisher;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;
import ru.rutcampustrack.shared.security.admin.AdminPermission;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Appends the final Auth-owned fact in the same local transaction as successful COMMIT. */
public final class AdminAccessChangedOutbox extends AbstractEventPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AdminAccessChangedOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
        super("auth-service");
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void append(
            UUID operation,
            long target,
            long actor,
            AdmissionAdminProfile desired,
            long finalVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("admin access event requires authority transaction");
        if (!desired.getAssigned() || finalVersion <= desired.getExpectedVersion())
            throw new IllegalArgumentException(
                    "admin access event requires assigned material change");
        var permissions =
                "FULL".equals(desired.getMode())
                        ? AdminPermission.allCodes()
                        : AdminPermission.validate(desired.getMode(), desired.getPermissionsList());
        var result =
                new AdminAccessProfile(target, true, desired.getMode(), permissions, finalVersion);
        var event = new AdminAccessChangedEvent(this, result, actor);
        event.setEventId(
                UUID.nameUUIDFromBytes(
                        ("admin.access.changed:" + operation + ":" + target)
                                .getBytes(StandardCharsets.UTF_8)));
        fillDefaults(event);
        try {
            jdbc.update(
                    "INSERT INTO auth_outbox(event_type,payload) VALUES(?,CAST(? AS jsonb))",
                    event.getEventType(),
                    mapper.writeValueAsString(event));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("cannot serialize administrative access fact", failure);
        }
    }
}
