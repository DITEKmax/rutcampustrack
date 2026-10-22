package ru.rutcampustrack.academic.groupavailability;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.group.GroupPromotionService;
import ru.rutcampustrack.academic.group.GroupService;

import java.util.UUID;

/** Reuses the existing canonical domain writers under the accepted whole-command transaction. */
@Component
public class GroupAvailabilityDomainWriter {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<GroupService> groups;
    private final ObjectProvider<GroupPromotionService> cycles;

    public GroupAvailabilityDomainWriter(
            JdbcTemplate jdbc,
            ObjectProvider<GroupService> groups,
            ObjectProvider<GroupPromotionService> cycles) {
        this.jdbc = jdbc;
        this.groups = groups;
        this.cycles = cycles;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(GroupAvailabilityCommand command, Long acceptedActor, UUID operation) {
        for (var group : command.groups()) {
            var revision =
                    jdbc.queryForList(
                            "SELECT academic_revision FROM groups WHERE id=? FOR UPDATE",
                            Long.class,
                            group.id());
            if (revision.size() != 1 || revision.getFirst() != group.revision())
                throw GroupAvailabilityStore.conflict();
            Boolean owned =
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM academic_group_availability_guards WHERE"
                                    + " group_id=? AND operation_id=?)",
                            Boolean.class,
                            group.id(),
                            operation);
            if (!Boolean.TRUE.equals(owned)) throw GroupAvailabilityStore.conflict();
        }
        try {
            switch (command.action()) {
                case DEACTIVATE ->
                        groups.getObject()
                                .applyAvailabilityUpdate(
                                        command.scopeGroupId(),
                                        command.update(),
                                        command.graduationYear(),
                                        acceptedActor,
                                        operation);
                case RESTORE ->
                        groups.getObject()
                                .applyAvailabilityRestore(command.scopeGroupId(), operation);
                case MANUAL_CYCLE, AUTOMATIC_CYCLE ->
                        cycles.getObject()
                                .applyAvailabilityPlan(
                                        command.cycle(),
                                        command.action()
                                                == GroupAvailabilityCommand.Action.AUTOMATIC_CYCLE,
                                        operation);
            }
        } catch (ru.rutcampustrack.academic.exception.BadRequestException
                | ru.rutcampustrack.academic.exception.ConflictException localRejected) {
            throw GroupAvailabilityStore.conflict();
        } catch (org.springframework.web.server.ResponseStatusException localRejected) {
            int status = localRejected.getStatusCode().value();
            if (status == 400 || status == 404 || status == 409 || status == 422)
                throw GroupAvailabilityStore.conflict();
            throw localRejected;
        }
    }
}
