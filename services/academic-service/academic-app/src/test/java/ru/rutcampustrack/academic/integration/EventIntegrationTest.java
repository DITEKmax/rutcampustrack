package ru.rutcampustrack.academic.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.homework.HomeworkService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.semester.SemesterService;
import ru.rutcampustrack.academic.user.UserService;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests verifying end-to-end event publishing pipeline.
 * Each test proves: service method -> Spring ApplicationEvent ->
 * @TransactionalEventListener(AFTER_COMMIT) -> RabbitTemplate -> real RabbitMQ broker.
 *
 * CRITICAL: No @Transactional on test methods -- service methods manage their own
 * transactions. Test-level @Transactional wraps the test in a transaction that rolls back,
 * so AFTER_COMMIT never fires (Pitfall 5 from RESEARCH.md).
 *
 * RequestContext is mocked via @MockBean -- replaces the request-scoped bean with a
 * Mockito mock that can be stubbed per-test (no active request scope needed).
 */
class EventIntegrationTest extends AbstractAcademicEventIntegrationTest {

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
    private UserService userService;

    @Autowired
    private SemesterService semesterService;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private StudentGroupHistoryRepository studentGroupHistoryRepository;

    // Shared test entities created in @BeforeEach
    private Group groupA;
    private Group groupB;
    private Subject testSubject;
    private Semester testSemester;
    private User testUser;

    @BeforeEach
    void setUpTestEntities() {
        // Create two groups for use across tests
        groupA = new Group();
        groupA.setName("Test Group A " + System.nanoTime());
        groupA.setCode("TGA-" + System.nanoTime() % 100000);
        groupA.setActive(true);
        groupA.setCreatedAt(OffsetDateTime.now());
        groupA = groupRepository.save(groupA);

        groupB = new Group();
        groupB.setName("Test Group B " + System.nanoTime());
        groupB.setCode("TGB-" + System.nanoTime() % 100000);
        groupB.setActive(true);
        groupB.setCreatedAt(OffsetDateTime.now());
        groupB = groupRepository.save(groupB);

        // Create a subject for homework tests
        testSubject = new Subject();
        testSubject.setName("Test Subject " + System.nanoTime());
        testSubject.setType(SubjectType.LECTURE);
        testSubject = subjectRepository.save(testSubject);

        // Create a semester for homework tests (inactive, so it doesn't conflict with exclusion constraint)
        testSemester = new Semester();
        testSemester.setName("Test Semester " + System.nanoTime());
        testSemester.setDateFrom(LocalDate.of(2027, 1, 1));
        testSemester.setDateTo(LocalDate.of(2027, 6, 30));
        testSemester.setActive(false);
        testSemester.setCreatedAt(OffsetDateTime.now());
        testSemester = semesterRepository.save(testSemester);

        // Create a student user in groupA for transfer tests
        long seq = userRepository.nextStudentLoginSeq();
        testUser = new User();
        testUser.setLogin("student" + String.format("%05d", seq));
        testUser.setLastName("Студентов");
        testUser.setFirstName("Тест" + seq);
        testUser.setPasswordHash("$2a$10$dummy");
        testUser.setRole(UserRole.STUDENT);
        testUser.setStatus(AccountStatus.ACTIVE);
        testUser.setGroupId(groupA.getId());
        testUser.setHeadman(false);
        testUser.setPasswordChanged(false);
        testUser.setCreatedAt(OffsetDateTime.now());
        testUser.setUpdatedAt(OffsetDateTime.now());
        testUser = userRepository.save(testUser);

        // Stub RequestContext mock for homework permission checks
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(testUser.getId());
        when(requestContext.getGroupId()).thenReturn(groupA.getId());
    }

    @AfterEach
    void cleanUpTestEntities() {
        // Clean up homework data first (FK dependencies)
        if (groupA != null && groupA.getId() != null) {
            homeworkRepository.deleteAll(homeworkRepository.findByGroupIdAndSemesterId(groupA.getId(), testSemester.getId()));
        }
        if (groupB != null && groupB.getId() != null) {
            homeworkRepository.deleteAll(homeworkRepository.findByGroupIdAndSemesterId(groupB.getId(), testSemester.getId()));
        }

        // Remove student group history entries before deleting user (FK: student_group_history.user_id -> users.id)
        if (testUser != null && testUser.getId() != null) {
            studentGroupHistoryRepository.deleteAll(
                    studentGroupHistoryRepository.findByUserIdOrderByJoinedAtDesc(testUser.getId()));
        }

        // Remove test user (soft-deleted or hard delete for test cleanup)
        if (testUser != null && testUser.getId() != null) {
            userRepository.deleteById(testUser.getId());
        }

        // Remove test groups
        if (groupA != null && groupA.getId() != null) {
            groupRepository.deleteById(groupA.getId());
        }
        if (groupB != null && groupB.getId() != null) {
            groupRepository.deleteById(groupB.getId());
        }

        // Remove test subject
        if (testSubject != null && testSubject.getId() != null) {
            subjectRepository.deleteById(testSubject.getId());
        }

        // Remove test semester (only if it was not deleted during test)
        if (testSemester != null && testSemester.getId() != null) {
            semesterRepository.findById(testSemester.getId()).ifPresent(s -> semesterRepository.delete(s));
        }
    }

    // --- Helper: declare a named non-exclusive non-auto-delete queue bound to rut-uit.events exchange ---
    // Using named queue to avoid issues with exclusive auto-delete queues across multiple receive() calls.

    private String bindTempQueue() {
        String name = "test.events." + System.nanoTime();
        Queue queue = new Queue(name, false, false, false); // durable=false, exclusive=false, autoDelete=false
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(new FanoutExchange(EXCHANGE)));
        return name;
    }

    // --- EVENT-01: group.updated events ---

    @Test
    void updateGroup_publishesGroupUpdatedEvent() throws Exception {
        String queueName = bindTempQueue();

        groupService.updateGroup(groupA.getId(), new UpdateGroupRequest("Updated Name A", groupA.getCode(), true));

        Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        assertThat(message).isNotNull();

        JsonNode root = objectMapper.readTree(message.getBody());
        assertThat(root.get("event_type").asText()).isEqualTo("group.updated");
        assertThat(root.get("event_id").asText()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("group_id").asLong()).isEqualTo(groupA.getId());
    }

    @Test
    void deleteGroup_publishesGroupUpdatedEvent() throws Exception {
        String queueName = bindTempQueue();
        Long deletedGroupId = groupA.getId();

        groupService.deleteGroup(groupA.getId());
        groupA = null; // prevent @AfterEach from trying to delete again

        Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        assertThat(message).isNotNull();

        JsonNode root = objectMapper.readTree(message.getBody());
        assertThat(root.get("event_type").asText()).isEqualTo("group.updated");
        assertThat(root.get("event_id").asText()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("group_id").asLong()).isEqualTo(deletedGroupId);
    }

    @Test
    void transferStudent_publishesGroupUpdatedEventForBothGroups() throws Exception {
        String queueName = bindTempQueue();

        Long oldGroupId = groupA.getId();
        Long newGroupId = groupB.getId();

        userService.transferStudent(testUser.getId(),
                new TransferStudentRequest(newGroupId, "Test transfer reason"));

        // First message -- old group
        Message message1 = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        assertThat(message1).isNotNull();
        JsonNode root1 = objectMapper.readTree(message1.getBody());
        assertThat(root1.get("event_type").asText()).isEqualTo("group.updated");

        // Second message -- new group
        Message message2 = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        assertThat(message2).isNotNull();
        JsonNode root2 = objectMapper.readTree(message2.getBody());
        assertThat(root2.get("event_type").asText()).isEqualTo("group.updated");

        // Both group IDs should be present across the two events
        long groupIdInMsg1 = root1.get("payload").get("group_id").asLong();
        long groupIdInMsg2 = root2.get("payload").get("group_id").asLong();
        assertThat(java.util.Set.of(groupIdInMsg1, groupIdInMsg2))
                .containsExactlyInAnyOrder(oldGroupId, newGroupId);
    }

    // --- EVENT-02: semester.archived event ---

    @Test
    void activateSemester_publishesSemesterArchivedEvent() throws Exception {
        // Create two new inactive semesters
        Semester semesterA = new Semester();
        semesterA.setName("Archived Semester " + System.nanoTime());
        semesterA.setDateFrom(LocalDate.of(2028, 1, 1));
        semesterA.setDateTo(LocalDate.of(2028, 6, 30));
        semesterA.setActive(false);
        semesterA.setCreatedAt(OffsetDateTime.now());
        semesterA = semesterRepository.save(semesterA);

        Semester semesterB = new Semester();
        semesterB.setName("New Semester " + System.nanoTime());
        semesterB.setDateFrom(LocalDate.of(2028, 7, 1));
        semesterB.setDateTo(LocalDate.of(2028, 12, 31));
        semesterB.setActive(false);
        semesterB.setCreatedAt(OffsetDateTime.now());
        semesterB = semesterRepository.save(semesterB);

        String queueName = bindTempQueue();

        // Activate semesterA -- this deactivates whatever is currently active (V2 seed semester)
        semesterService.activateSemester(semesterA.getId());

        // Drain the archived event for the seed semester (not what we're testing)
        rabbitTemplate.receive(queueName, 2000);

        // Activate semesterB -- this archives semesterA and should publish semester.archived for semesterA
        Long archivedSemesterId = semesterA.getId();
        semesterService.activateSemester(semesterB.getId());

        Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        assertThat(message).isNotNull();

        JsonNode root = objectMapper.readTree(message.getBody());
        assertThat(root.get("event_type").asText()).isEqualTo("semester.archived");
        assertThat(root.get("event_id").asText()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("semester_id").asLong()).isEqualTo(archivedSemesterId);

        // Cleanup semesters created in this test
        semesterRepository.deleteById(semesterB.getId());
        semesterRepository.deleteById(semesterA.getId());
    }

    // --- EVENT-03: homework.published and homework.updated events ---

    @Test
    void createHomework_publishesHomeworkPublishedEvent() throws Exception {
        String queueName = bindTempQueue();

        homeworkService.createHomework(new CreateHomeworkRequest(
                "HW Title", "description", null,
                testSubject.getId(), groupA.getId(), testSemester.getId()
        ));

        Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        assertThat(message).isNotNull();

        JsonNode root = objectMapper.readTree(message.getBody());
        assertThat(root.get("event_type").asText()).isEqualTo("homework.published");
        assertThat(root.get("event_id").asText()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("homework_id")).isNotNull();
        assertThat(payload.get("group_id").asLong()).isEqualTo(groupA.getId());
        assertThat(payload.get("subject_id").asLong()).isEqualTo(testSubject.getId());
        assertThat(payload.get("title").asText()).isEqualTo("HW Title");
        assertThat(payload.get("has_link").asBoolean()).isFalse();
    }

    @Test
    void updateHomework_publishesHomeworkUpdatedEvent() throws Exception {
        // Save a homework directly via repository to bypass permission checks for setup
        Homework homework = new Homework(
                groupA.getId(), testSubject.getId(), testSemester.getId(),
                "Original Title", "description", null, testUser.getId()
        );
        homework = homeworkRepository.save(homework);

        String queueName = bindTempQueue();

        homeworkService.updateHomework(homework.getId(),
                new UpdateHomeworkRequest("Updated Title", "new desc", "https://link.example.com"));

        Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        assertThat(message).isNotNull();

        JsonNode root = objectMapper.readTree(message.getBody());
        assertThat(root.get("event_type").asText()).isEqualTo("homework.updated");
        assertThat(root.get("event_id").asText()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("homework_id").asLong()).isEqualTo(homework.getId());
        assertThat(payload.get("group_id").asLong()).isEqualTo(groupA.getId());
        assertThat(payload.get("title").asText()).isEqualTo("Updated Title");
    }
}
