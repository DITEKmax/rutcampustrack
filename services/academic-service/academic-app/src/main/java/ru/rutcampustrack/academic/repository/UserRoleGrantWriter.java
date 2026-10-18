package ru.rutcampustrack.academic.repository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.admin.AcademicAuthDataCommands;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.DashboardInvalidatedEvent;

import java.util.Objects;

/** Academic mutation adapter. Auth owns durable identity and grant writes in the same PG commit. */
@Repository
public class UserRoleGrantWriter {
    private final AcademicAuthDataCommands commands;
    private final ApplicationEventPublisher events;

    public UserRoleGrantWriter(
            AcademicAuthDataCommands commands, ApplicationEventPublisher events) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(User user) {
        commands.initialize(user);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void project(User user) {
        commands.project(user);
    }

    /** Other role rows retain their IDs and statuses; active selections are fenced atomically. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronize(User user) {
        commands.synchronize(user, false);
        invalidate(user.getRole().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void upsertRole(Long userId, String role, String status, Long groupId) {
        commands.grant(userId, role, status, groupId);
        invalidate(role);
    }

    /** The current durable STUDENT grant, rather than the requested group, supplies the scope. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronizeDerivedHeadman(Long userId, boolean active, Long requestedGroupId) {
        commands.headman(userId, active);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceHeadman(
            Long previous,
            long candidate,
            long group,
            boolean legacy,
            Long telegram,
            String status) {
        commands.replaceHeadman(previous, candidate, group, legacy, telegram, status);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transfer(User user, String studentStatus, long group) {
        commands.transfer(user, studentStatus, group);
        invalidate("student");
        invalidate(user.getRole().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void archive(User user) {
        commands.synchronize(user, true);
        events.publishEvent(new DashboardInvalidatedEvent(this));
    }

    private void invalidate(String role) {
        if ("student".equalsIgnoreCase(role) || "teacher".equalsIgnoreCase(role))
            events.publishEvent(new DashboardInvalidatedEvent(this));
    }
}
