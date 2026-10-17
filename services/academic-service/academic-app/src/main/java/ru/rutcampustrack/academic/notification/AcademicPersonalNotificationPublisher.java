package ru.rutcampustrack.academic.notification;

import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.event.AcademicPersonalNotificationEvent;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.security.RequestContext;

/** Called inside the canonical mutation transaction while its group/user locks are held. */
@Service
public class AcademicPersonalNotificationPublisher {
    private final ApplicationEventPublisher events;
    private final HeadmanAssistantRepository assistants;
    private final RequestContext context;

    public AcademicPersonalNotificationPublisher(
            ApplicationEventPublisher events,
            HeadmanAssistantRepository assistants,
            RequestContext context) {
        this.events = events;
        this.assistants = assistants;
        this.context = context;
    }

    public Long actor() {
        var claims = context.getClaims();
        return claims != null && claims.userId() > 0 ? claims.userId() : null;
    }

    public void membership(long user, long oldGroup, long newGroup, LocalDate day) {
        events.publishEvent(
                AcademicPersonalNotificationEvent.membership(
                        user, oldGroup, newGroup, day, actor()));
    }

    public void headman(long user, long group, boolean before, boolean after) {
        if (before != after)
            events.publishEvent(
                    AcademicPersonalNotificationEvent.headman(user, group, before, after, actor()));
    }

    public void assistant(
            long user,
            long group,
            boolean before,
            boolean after,
            String[] oldPermissions,
            String[] newPermissions) {
        if (before != after
                || !AcademicPersonalNotificationEvent.permissions(oldPermissions)
                        .equals(AcademicPersonalNotificationEvent.permissions(newPermissions)))
            events.publishEvent(
                    AcademicPersonalNotificationEvent.assistant(
                            user, group, before, after, oldPermissions, newPermissions, actor()));
    }

    public int revokeGroupAssistants(long group) {
        var active = assistants.findByGroupIdAndIsActiveTrue(group);
        int count = assistants.revokeAllByGroupId(group);
        if (count != active.size())
            throw new IllegalStateException("locked assistant revocation snapshot differs");
        for (var assistant : active)
            assistant(
                    assistant.getStudentId(),
                    group,
                    true,
                    false,
                    assistant.getPermissions(),
                    new String[0]);
        return count;
    }
}
