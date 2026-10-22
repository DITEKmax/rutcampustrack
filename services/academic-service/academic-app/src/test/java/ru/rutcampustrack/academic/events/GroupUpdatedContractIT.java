package ru.rutcampustrack.academic.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.group.GroupCodeRules;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.integration.AbstractAcademicEventIntegrationTest;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * M02 Группа 8 — contract test: updateGroup → group.updated envelope валидируется против
 * event-schemas/group.updated.json.
 */
class GroupUpdatedContractIT extends AbstractAcademicEventIntegrationTest {

    @Autowired GroupService groupService;

    @Autowired GroupRepository groupRepository;

    @Autowired OutboxStorage outboxStorage;

    @Autowired ObjectMapper objectMapper;

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
        GroupCodeRules.apply(group, GroupCodeRules.fromParts("ТГА", "111", 4));
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        group = groupRepository.save(group);

        String newName = "ТНБ-111";
        var actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        try (var ignored = identityFixture.request(actor)) {
            groupService.updateGroup(group.getId(), new UpdateGroupRequest(newName, true));
        }
        assertThat(groupRepository.findById(group.getId()).orElseThrow().getName())
                .isEqualTo(newName);

        List<OutboxRecord> groupUpdated =
                outboxStorage.findPending(100).stream()
                        .filter(r -> "group.updated".equals(r.eventType()))
                        .toList();
        assertThat(groupUpdated).as("group.updated должен быть в outbox ровно один раз").hasSize(1);

        Set<ValidationMessage> errors =
                EventSchemaValidator.validate(
                        "group.updated.json", groupUpdated.getFirst().payload());
        assertThat(errors).as("group.updated payload должен соответствовать schema").isEmpty();
        assertThat(
                        objectMapper
                                .readTree(groupUpdated.getFirst().payload())
                                .path("payload")
                                .path("group_id")
                                .asLong())
                .isEqualTo(group.getId());
    }
}
