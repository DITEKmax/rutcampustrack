package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 58-07 / BUG-006-6: интеграционный тест — PUT /groups с изменением name публикует event {@code
 * group.renamed} в RabbitMQ через ApplicationEventPublisher → DomainEventListener (AFTER_COMMIT).
 *
 * <p>Параллельно также публикуется {@code group.updated} (для инвалидации кэшей). Тест проверяет
 * что оба события попадают в fanout exchange.
 */
class GroupRenameEventIT extends AbstractAcademicEventIntegrationTest {

    private static final String EXCHANGE = "rut-uit.events";
    private static final int RECEIVE_TIMEOUT_MS = 5000;

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired private GroupService groupService;

    @Autowired private GroupRepository groupRepository;

    private Group group;

    @BeforeEach
    void setUp() {
        group = new Group();
        group.setName("ТРН-121");
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        group = groupRepository.save(group);
    }

    @AfterEach
    void cleanUp() {
        if (group != null && group.getId() != null) {
            groupRepository.findById(group.getId()).ifPresent(groupRepository::delete);
        }
    }

    private String bindTempQueue() {
        String name = "test.rename." + System.nanoTime();
        Queue queue = new Queue(name, false, false, false);
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(new FanoutExchange(EXCHANGE)));
        return name;
    }

    @Test
    void updateGroupWithNewName_publishesGroupRenamedEvent() throws Exception {
        String queueName = bindTempQueue();
        var actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", actor.internalToken());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var response =
                http.exchange(
                        "/academic/groups/" + group.getId(),
                        org.springframework.http.HttpMethod.PUT,
                        new org.springframework.http.HttpEntity<>(
                                new UpdateGroupRequest("Тно-121", true, 4), headers),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        String persistedName =
                jdbc.queryForObject(
                        "SELECT name FROM groups WHERE id=?", String.class, group.getId());
        assertThat(persistedName).isEqualTo("ТНО-121");
        var durable =
                jdbc.queryForList(
                        "SELECT payload::text FROM academic_outbox WHERE event_type IN"
                            + " ('group.renamed','group.updated') AND payload #>>"
                            + " '{payload,group_id}'=? ORDER BY id",
                        String.class,
                        group.getId().toString());
        assertThat(durable).hasSize(2);
        var expectedIds = new java.util.HashSet<String>();
        for (String json : durable)
            expectedIds.add(objectMapper.readTree(json).path("event_id").asText());
        assertThat(expectedIds).hasSize(2);
        flushOutbox();
        var observed = new java.util.ArrayList<JsonNode>();
        long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(queueName, 500);
            if (message == null) {
                if (observed.size() >= 2) break;
                continue;
            }
            var event = objectMapper.readTree(message.getBody());
            if (event.path("payload").path("group_id").asLong() == group.getId()
                    && java.util.List.of("group.renamed", "group.updated")
                            .contains(event.path("event_type").asText())) observed.add(event);
        }
        assertThat(observed).hasSize(2);
        assertThat(observed)
                .extracting(event -> event.path("event_type").asText())
                .containsExactlyInAnyOrder("group.renamed", "group.updated");
        assertThat(observed)
                .extracting(event -> event.path("event_id").asText())
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(expectedIds);
        for (var event : observed) {
            assertThat(event.path("event_id").asText())
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(event.path("occurred_at").asText()).isNotBlank();
            assertThat(event.path("payload").path("group_id").asLong()).isEqualTo(group.getId());
            if (event.path("event_type").asText().equals("group.renamed")) {
                assertThat(event.path("payload").path("new_name").asText())
                        .isEqualTo(persistedName);
                assertThat(event.path("payload").path("actor_user_id").asLong())
                        .isEqualTo(actor.userId());
            }
        }
    }

    @Test
    void updateGroupWithSameName_doesNotPublishGroupRenamedEvent() throws Exception {
        String queueName = bindTempQueue();

        // Переименовываем «в то же имя» — rename-event не должен публиковаться.
        var actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        try (var ignored = identityFixture.request(actor)) {
            groupService.updateGroup(
                    group.getId(), new UpdateGroupRequest(group.getName(), true, 4));
        }
        flushOutbox();

        // Собрать все события в окне и убедиться что rename среди них нет.
        Set<String> receivedTypes = new HashSet<>();
        long deadline = System.currentTimeMillis() + 2500;
        while (System.currentTimeMillis() < deadline) {
            Message m = rabbitTemplate.receive(queueName, 800);
            if (m == null) continue;
            JsonNode root = objectMapper.readTree(m.getBody());
            receivedTypes.add(root.get("event_type").asText());
        }

        assertThat(receivedTypes).doesNotContain("group.renamed");
        assertThat(receivedTypes).contains("group.updated");
    }
}
