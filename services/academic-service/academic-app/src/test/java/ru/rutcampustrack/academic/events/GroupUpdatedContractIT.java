package ru.rutcampustrack.academic.events;

import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.integration.AbstractAcademicEventIntegrationTest;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M02 Группа 8 — contract test: updateGroup → group.updated envelope
 * валидируется против event-schemas/group.updated.json.
 */
class GroupUpdatedContractIT extends AbstractAcademicEventIntegrationTest {

    @Autowired
    GroupService groupService;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    OutboxStorage outboxStorage;

    @MockitoBean
    RequestContext requestContext;

    private Group group;

    @AfterEach
    void cleanup() {
        if (group != null) {
            groupRepository.findById(group.getId()).ifPresent(groupRepository::delete);
        }
    }

    @Test
    void updateGroup_publishesValidGroupUpdatedEvent() throws Exception {
        group = new Group();
        group.setName("Тга-" + String.format("%03d", (int) (System.nanoTime() % 1000)));
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        group = groupRepository.save(group);

        String newName = "Тнб-" + String.format("%03d", (int) ((System.nanoTime() + 17) % 1000));
        groupService.updateGroup(group.getId(), new UpdateGroupRequest(newName, true));

        Optional<OutboxRecord> groupUpdated = outboxStorage.findPending(100).stream()
                .filter(r -> "group.updated".equals(r.eventType()))
                .findFirst();
        assertThat(groupUpdated).as("group.updated должен быть в outbox").isPresent();

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "group.updated.json", groupUpdated.get().payload());
        assertThat(errors)
                .as("group.updated payload должен соответствовать schema")
                .isEmpty();
    }
}
