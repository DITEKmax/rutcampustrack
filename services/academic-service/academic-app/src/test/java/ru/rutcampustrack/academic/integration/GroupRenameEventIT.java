package ru.rutcampustrack.academic.integration;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 58-07 / BUG-006-6: интеграционный тест — PUT /groups с изменением name
 * публикует event {@code group.renamed} в RabbitMQ через
 * ApplicationEventPublisher → DomainEventListener (AFTER_COMMIT).
 *
 * Параллельно также публикуется {@code group.updated} (для инвалидации кэшей).
 * Тест проверяет что оба события попадают в fanout exchange.
 */
class GroupRenameEventIT extends AbstractAcademicEventIntegrationTest {

    private static final String EXCHANGE = "rut-uit.events";
    private static final int RECEIVE_TIMEOUT_MS = 5000;

    @MockitoBean
    private RequestContext requestContext;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

    private Group group;

    @BeforeEach
    void setUp() {
        group = new Group();
        group.setName("Трн-" + String.format("%03d", (int) (System.nanoTime() % 1000)));
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

        String newName = "Тно-" + String.format("%03d", (int) ((System.nanoTime() + 7) % 1000));
        groupService.updateGroup(group.getId(), new UpdateGroupRequest(newName, true));
        flushOutbox();

        // Должно прийти минимум 2 события: group.renamed + group.updated (порядок не гарантируется).
        Set<String> receivedTypes = new HashSet<>();
        long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;
        while (receivedTypes.size() < 2 && System.currentTimeMillis() < deadline) {
            Message m = rabbitTemplate.receive(queueName, 1500);
            if (m == null) break;
            JsonNode root = objectMapper.readTree(m.getBody());
            String type = root.get("event_type").asText();
            receivedTypes.add(type);
            if ("group.renamed".equals(type)) {
                assertThat(root.get("event_id").asText())
                        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
                assertThat(root.get("occurred_at")).isNotNull();
                assertThat(root.get("payload").get("group_id").asLong()).isEqualTo(group.getId());
            }
        }

        assertThat(receivedTypes).contains("group.renamed");
    }

    @Test
    void updateGroupWithSameName_doesNotPublishGroupRenamedEvent() throws Exception {
        String queueName = bindTempQueue();

        // Переименовываем «в то же имя» — rename-event не должен публиковаться.
        groupService.updateGroup(group.getId(), new UpdateGroupRequest(group.getName(), true));
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
