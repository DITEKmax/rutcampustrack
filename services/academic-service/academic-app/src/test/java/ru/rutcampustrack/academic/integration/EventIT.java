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
import org.springframework.hateoas.EntityModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.homework.HomeworkService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.semester.SemesterService;
import ru.rutcampustrack.academic.subject.SubjectService;
import ru.rutcampustrack.academic.user.UserService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Integration tests verifying end-to-end event publishing pipeline.
 * M02 Группа 5: service -> Spring ApplicationEvent -> DomainEventListener
 * (BEFORE_COMMIT) -> academic_outbox -> OutboxPublisherJob.publishBatch()
 * -> RabbitTemplate -> real RabbitMQ broker.
 *
 * CRITICAL: No @Transactional on test methods. Service methods manage their own
 * transactions. Листенер теперь срабатывает BEFORE_COMMIT и пишет в outbox
 * в той же tx — если тест-tx откатится, outbox-запись тоже откатится.
 *
 * Тесты вызывают {@link #flushOutbox()} после service-метода чтобы
 * эмулировать OutboxPublisherJob tick (в test-профиле он не шедулится).
 */
class EventIT extends AbstractAcademicEventIntegrationTest {

    private static final String EXCHANGE = "rut-uit.events";
    private static final int RECEIVE_TIMEOUT_MS = 5000;

    @MockitoBean
    private RequestContext requestContext;

    /**
     * Phase 61-03 / D-04: createHomework теперь вызывает ScheduleGrpcClient.
     * В интеграционном тесте реального schedule-service нет — мочим клиент,
     * возвращая LessonResponse с правильным subjectId.
     */
    @MockitoBean
    private ScheduleGrpcClient scheduleGrpcClient;

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
    private SubjectService subjectService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupHistoryCoverageRepository coverageRepository;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Shared test entities created in @BeforeEach
    private Group groupA;
    private Group groupB;
    private Subject testSubject;
    private Semester testSemester;
    private User testUser;
    private Long semesterActivationBaselineId;
    private String semesterActivationQueue;
    private final List<Long> semesterActivationFixtureIds = new ArrayList<>();

    @BeforeEach
    void setUpTestEntities() {
        // Create managed groups through the production writer so coverage
        // provenance exists for the transfer test as well.
        groupA = groupService.createGroup(new ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest(
                firstAvailableManagedGroupName("УИТ")));
        groupB = groupService.createGroup(new ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest(
                firstAvailableManagedGroupName("УВП")));

        // Create the subject and its canonical lesson type atomically through
        // the managed writer; the database requires every subject to retain a
        // corresponding subject_lesson_types row.
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(groupA.getId());
        testSubject = subjectService.createSubject(new CreateSubjectRequest(
                "Test Subject " + System.nanoTime(),
                SubjectType.LECTURE,
                List.of(SubjectType.LECTURE),
                List.of()));

        // Create a semester for homework tests (inactive, so it doesn't conflict with exclusion constraint)
        testSemester = new Semester();
        testSemester.setName("Test Semester " + System.nanoTime());
        testSemester.setDateFrom(LocalDate.of(2027, 1, 1));
        testSemester.setDateTo(LocalDate.of(2027, 6, 30));
        testSemester.setActive(false);
        testSemester.setCreatedAt(OffsetDateTime.now());
        testSemester = semesterRepository.save(testSemester);

        // Create the student through UserService so enrollment history is
        // written atomically with the user row.
        EntityModel<UserCreatedResponse> created = userService.createUser(new CreateUserRequest(
                "Студентов", "Тест", null, UserRole.STUDENT, groupA.getId(), null,
                Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L));
        testUser = userRepository.findByIdIncludingArchived(created.getContent().getId()).orElseThrow();

        // Stub RequestContext mock for homework permission checks
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(testUser.getId());
        when(requestContext.getGroupId()).thenReturn(groupA.getId());

        // Phase 61-03 / D-04: мок резолва пары — возвращаем LessonResponse с subjectId=testSubject.
        when(scheduleGrpcClient.resolveLesson(anyLong(), any(), anyInt()))
                .thenReturn(Optional.of(
                        ru.rutcampustrack.schedule.grpc.LessonResponse.newBuilder()
                                .setGroupId(groupA.getId())
                                .setSubjectId(testSubject.getId())
                                .setSemesterId(testSemester.getId())
                                .setLessonNumber(1)
                                .setDate(LocalDate.now().plusDays(1).toString())
                                .setOccurrenceId(9001L)
                                .setRevision(1L)
                                .setStatus("planned")
                                .build()));
        when(scheduleGrpcClient.reserveHomeworkBinding(anyLong(), any(UUID.class), anyLong(), any(byte[].class)))
                .thenAnswer(invocation -> homeworkBindingResponse(
                        9101L, 9001L, null,
                        ru.rutcampustrack.schedule.grpc.HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING));
        when(scheduleGrpcClient.confirmHomeworkBinding(anyLong(), anyLong(), any(UUID.class)))
                .thenAnswer(invocation -> homeworkBindingResponse(
                        9101L, 9001L, (Long) invocation.getArgument(1),
                        ru.rutcampustrack.schedule.grpc.HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE));
        when(scheduleGrpcClient.countSubjectReferences(anyLong()))
                .thenReturn(ru.rutcampustrack.schedule.grpc.CountSubjectReferencesResponse
                        .getDefaultInstance());
    }

    private String firstAvailableManagedGroupName(String prefix) {
        for (int number = 1; number <= 9; number++) {
            String candidate = prefix + "-11" + number;
            if (!groupRepository.existsByName(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No free managed test group name for " + prefix);
    }

    @AfterEach
    void cleanUpTestEntities() {
        restoreSemesterActivationFixture();

        // Clean up homework data first (FK dependencies)
        if (testSemester != null && testSemester.getId() != null
                && groupA != null && groupA.getId() != null) {
            homeworkRepository.deleteAll(homeworkRepository.findByGroupIdAndSemesterId(groupA.getId(), testSemester.getId()));
        }
        if (testSemester != null && testSemester.getId() != null
                && groupB != null && groupB.getId() != null) {
            homeworkRepository.deleteAll(homeworkRepository.findByGroupIdAndSemesterId(groupB.getId(), testSemester.getId()));
        }

        // Remove student group history entries before deleting user (FK: student_group_history.user_id -> users.id)
        if (testUser != null && testUser.getId() != null) {
            studentGroupHistoryRepository.deleteAll(
                    studentGroupHistoryRepository.findByUserIdOrderByJoinedAtDesc(testUser.getId()));
        }

        // Remove test user (soft-deleted or hard delete for test cleanup)
        if (testUser != null && testUser.getId() != null) {
            jdbcTemplate.update("DELETE FROM user_role_grants WHERE user_id = ?", testUser.getId());
            userRepository.deleteById(testUser.getId());
        }

        // Remove test subject FIRST (Phase 60-01 V12: subjects.group_id FK to groups)
        if (testSubject != null && testSubject.getId() != null) {
            subjectService.deleteSubject(testSubject.getId(), false);
        }

        // Remove test groups after subjects (FK dependency via subjects.group_id)
        if (groupA != null && groupA.getId() != null) {
            coverageRepository.deleteById(groupA.getId());
            groupRepository.deleteById(groupA.getId());
        }
        if (groupB != null && groupB.getId() != null) {
            coverageRepository.deleteById(groupB.getId());
            groupRepository.deleteById(groupB.getId());
        }

        // Remove test semester (only if it was not deleted during test)
        if (testSemester != null && testSemester.getId() != null) {
            semesterRepository.findById(testSemester.getId()).ifPresent(s -> semesterRepository.delete(s));
        }
    }

    private Semester createSemester(String name, LocalDate from, LocalDate to) {
        Semester semester = new Semester();
        semester.setName(name + " " + System.nanoTime());
        semester.setDateFrom(from);
        semester.setDateTo(to);
        semester.setActive(false);
        semester.setCreatedAt(OffsetDateTime.now());
        Semester saved = semesterRepository.save(semester);
        trackSemesterActivationFixture(saved);
        return saved;
    }

    private void beginSemesterActivationFixture() {
        semesterActivationBaselineId = semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new AssertionError("expected one active semester before activation test"))
                .getId();
    }

    private void trackSemesterActivationFixture(Semester semester) {
        semesterActivationFixtureIds.add(semester.getId());
    }

    private void restoreSemesterActivationFixture() {
        if (semesterActivationBaselineId == null && semesterActivationFixtureIds.isEmpty()) {
            return;
        }
        try {
            if (semesterActivationBaselineId != null
                    && semesterRepository.findById(semesterActivationBaselineId).isPresent()) {
                semesterService.activateSemester(semesterActivationBaselineId);
                flushOutbox();
                if (semesterActivationQueue != null) {
                    while (rabbitTemplate.receive(semesterActivationQueue, 250) != null) { /* drain restore event */ }
                }
            }
        } finally {
            semesterActivationFixtureIds.forEach(id -> semesterRepository.findById(id)
                    .filter(semester -> !semester.isActive())
                    .ifPresent(semesterRepository::delete));
            semesterActivationFixtureIds.clear();
            semesterActivationBaselineId = null;
            semesterActivationQueue = null;
        }
    }

    private Semester activateAfterBarrier(Long semesterId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("activation race barrier timed out");
        }
        return semesterService.activateSemester(semesterId);
    }

    private long latestOutboxId() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM academic_outbox", Long.class);
    }

    private List<Long> archivedSemesterIdsAfter(long outboxId) {
        return jdbcTemplate.queryForList("""
                SELECT (payload -> 'payload' ->> 'semester_id')::bigint
                FROM academic_outbox
                WHERE id > ? AND event_type = 'semester.archived'
                ORDER BY id
                """, Long.class, outboxId);
    }

    private void assertOnlyActiveSemester(Long expectedId) {
        List<Long> activeIds = semesterRepository.findAllByIsActiveTrueOrderByIdAsc()
                .stream()
                .map(Semester::getId)
                .toList();
        assertThat(activeIds).containsExactly(expectedId);
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

        // 58-04: UpdateGroupRequest(name, active). Новое имя должно матчить активный паттерн.
        // 58-07: при изменении name публикуются ДВА события: group.renamed + group.updated.
        // Читаем очередь до тех пор, пока не встретим group.updated.
        groupService.updateGroup(groupA.getId(),
                new UpdateGroupRequest("Тна-" + String.format("%03d", (int) (System.nanoTime() % 1000)), true));
        flushOutbox();

        JsonNode root = null;
        for (int i = 0; i < 3 && root == null; i++) {
            Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
            assertThat(message).isNotNull();
            JsonNode candidate = objectMapper.readTree(message.getBody());
            if ("group.updated".equals(candidate.get("event_type").asText())) {
                root = candidate;
            }
        }
        assertThat(root).as("expected group.updated event to be published").isNotNull();

        assertThat(root.get("event_type").asText()).isEqualTo("group.updated");
        assertThat(root.get("event_id").asText()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("group_id").asLong()).isEqualTo(groupA.getId());
    }

    @Test
    void deleteGroup_withManagedCoverageIsRejected() {
        assertThatThrownBy(() -> groupService.deleteGroup(groupA.getId()))
                .isInstanceOfSatisfying(HistoricalMembershipException.class, error ->
                        assertThat(error.code())
                                .isEqualTo(HistoricalMembershipException.Code.UNSUPPORTED_MUTATION));
        assertThat(groupRepository.findById(groupA.getId())).isPresent();
    }

    @Test
    void transferStudent_publishesGroupUpdatedEventForBothGroups() throws Exception {
        String queueName = bindTempQueue();

        Long oldGroupId = groupA.getId();
        Long newGroupId = groupB.getId();

        userService.transferStudent(testUser.getId(),
                new TransferStudentRequest(newGroupId, "Test transfer reason"));
        flushOutbox();

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
        beginSemesterActivationFixture();

        // Create two new inactive semesters
        Semester semesterA = new Semester();
        semesterA.setName("Archived Semester " + System.nanoTime());
        semesterA.setDateFrom(LocalDate.of(2028, 1, 1));
        semesterA.setDateTo(LocalDate.of(2028, 6, 30));
        semesterA.setActive(false);
        semesterA.setCreatedAt(OffsetDateTime.now());
        semesterA = semesterRepository.save(semesterA);
        trackSemesterActivationFixture(semesterA);

        Semester semesterB = new Semester();
        semesterB.setName("New Semester " + System.nanoTime());
        semesterB.setDateFrom(LocalDate.of(2028, 7, 1));
        semesterB.setDateTo(LocalDate.of(2028, 12, 31));
        semesterB.setActive(false);
        semesterB.setCreatedAt(OffsetDateTime.now());
        semesterB = semesterRepository.save(semesterB);
        trackSemesterActivationFixture(semesterB);

        String queueName = bindTempQueue();
        semesterActivationQueue = queueName;

        // Activate semesterA -- this deactivates whatever is currently active (V2 seed semester)
        semesterService.activateSemester(semesterA.getId());
        flushOutbox();

        // Drain events from the first activation (seed semester archiving + semesterA activation).
        while (rabbitTemplate.receive(queueName, 500) != null) { /* drain */ }

        // Activate semesterB -- this archives semesterA and should publish semester.archived for semesterA
        Long archivedSemesterId = semesterA.getId();
        long outboxBeforeSwitch = latestOutboxId();
        semesterService.activateSemester(semesterB.getId());
        assertThat(archivedSemesterIdsAfter(outboxBeforeSwitch)).containsExactly(archivedSemesterId);
        flushOutbox();

        Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        assertThat(message).isNotNull();

        JsonNode root = objectMapper.readTree(message.getBody());
        assertThat(root.get("event_type").asText()).isEqualTo("semester.archived");
        assertThat(root.get("event_id").asText()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("semester_id").asLong()).isEqualTo(archivedSemesterId);
        assertThat(rabbitTemplate.receive(queueName, 250))
                .as("A → B must publish exactly one archive event")
                .isNull();

        assertOnlyActiveSemester(semesterB.getId());

        long outboxBeforeReplay = latestOutboxId();
        semesterService.activateSemester(semesterB.getId());
        flushOutbox();
        assertThat(latestOutboxId()).isEqualTo(outboxBeforeReplay);
        assertThat(rabbitTemplate.receive(queueName, 250))
                .as("A → A replay must not publish an archive event")
                .isNull();
        assertOnlyActiveSemester(semesterB.getId());

        long outboxBeforeMissingTarget = latestOutboxId();
        assertThatThrownBy(() -> semesterService.activateSemester(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        flushOutbox();
        assertThat(latestOutboxId()).isEqualTo(outboxBeforeMissingTarget);
        assertThat(rabbitTemplate.receive(queueName, 250))
                .as("missing target must leave the outbox unchanged")
                .isNull();
        assertOnlyActiveSemester(semesterB.getId());

    }

    @Test
    void concurrentSemesterActivations_areSerializedAndPublishOnlyRealTransitions() throws Exception {
        beginSemesterActivationFixture();
        Semester semesterA = createSemester("Concurrent A", LocalDate.of(2038, 1, 1), LocalDate.of(2038, 6, 30));
        Semester semesterB = createSemester("Concurrent B", LocalDate.of(2038, 7, 1), LocalDate.of(2038, 12, 31));
        Semester semesterC = createSemester("Concurrent C", LocalDate.of(2039, 1, 1), LocalDate.of(2039, 6, 30));
        String queueName = bindTempQueue();
        semesterActivationQueue = queueName;

        semesterService.activateSemester(semesterA.getId());
        flushOutbox();
        while (rabbitTemplate.receive(queueName, 500) != null) { /* discard setup transition */ }
        long outboxBeforeRace = latestOutboxId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Semester> activateB = executor.submit(() -> activateAfterBarrier(semesterB.getId(), ready, start));
        Future<Semester> activateC = executor.submit(() -> activateAfterBarrier(semesterC.getId(), ready, start));
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(activateB.get(30, TimeUnit.SECONDS).getId()).isEqualTo(semesterB.getId());
            assertThat(activateC.get(30, TimeUnit.SECONDS).getId()).isEqualTo(semesterC.getId());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        List<Semester> activeSemesters = semesterRepository.findAllByIsActiveTrueOrderByIdAsc();
        assertThat(activeSemesters).hasSize(1);
        Long finalActiveId = activeSemesters.get(0).getId();
        Long firstActivatedId = finalActiveId.equals(semesterB.getId())
                ? semesterC.getId()
                : semesterB.getId();
        List<Long> archivedIds = archivedSemesterIdsAfter(outboxBeforeRace);
        assertThat(archivedIds).containsExactlyInAnyOrder(semesterA.getId(), firstActivatedId);

        flushOutbox();
        List<Long> publishedIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
            assertThat(message).isNotNull();
            JsonNode root = objectMapper.readTree(message.getBody());
            assertThat(root.get("event_type").asText()).isEqualTo("semester.archived");
            publishedIds.add(root.get("payload").get("semester_id").asLong());
        }
        assertThat(publishedIds).containsExactlyInAnyOrderElementsOf(archivedIds);
        assertThat(rabbitTemplate.receive(queueName, 250))
                .as("serialized B/C transitions must publish two archive events total")
                .isNull();

    }

    // --- EVENT-03: homework.published and homework.updated events ---

    @Test
    void createHomework_publishesHomeworkPublishedEvent() throws Exception {
        String queueName = bindTempQueue();

        homeworkService.createHomework(new CreateHomeworkRequest(
                "HW Title", "description", null,
                testSubject.getId(), groupA.getId(), testSemester.getId(),
                java.time.LocalDate.now().plusDays(1), 1
        ));
        flushOutbox();

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
        assertThat(payload.get("description").asText()).isEqualTo("description");
        assertThat(payload.path("link").isMissingNode() || payload.path("link").isNull()).isTrue();
        assertThat(payload.get("has_link").asBoolean()).isFalse();
        // Phase 61 / D-07: payload обязан содержать lesson_date + lesson_number
        assertThat(payload.get("lesson_date").asText()).isEqualTo(LocalDate.now().plusDays(1).toString());
        assertThat(payload.get("lesson_number").asInt()).isEqualTo(1);
    }

    private ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse homeworkBindingResponse(
            long bindingId, long occurrenceId, Long homeworkId,
            ru.rutcampustrack.schedule.grpc.HomeworkBindingState state) {
        ru.rutcampustrack.schedule.grpc.LessonInfo lesson =
                ru.rutcampustrack.schedule.grpc.LessonInfo.newBuilder()
                        .setLessonId(9002L)
                        .setGroupId(groupA.getId())
                        .setSubjectId(testSubject.getId())
                        .setStartsAt(LocalDate.now().plusDays(1) + "T09:00")
                        .setLessonNumber(1)
                        .setDate(LocalDate.now().plusDays(1).toString())
                        .setOccurrenceId(occurrenceId)
                        .setAssignmentId(1L)
                        .setSemesterId(testSemester.getId())
                        .setTeacherId(1L)
                        .setLessonType("lecture")
                        .setGeneration(1L)
                        .setRevision(1L)
                        .setStatus("planned")
                        .build();
        var builder = ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse.newBuilder()
                .setBindingId(bindingId)
                .setOccurrenceId(occurrenceId)
                .setCurrentLesson(lesson)
                .setState(state)
                .setRevision(state == ru.rutcampustrack.schedule.grpc.HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE ? 2L : 1L)
                .setGroupId(groupA.getId())
                .setSubjectId(testSubject.getId())
                .setSemesterId(testSemester.getId())
                .setDate(LocalDate.now().plusDays(1).toString())
                .setLessonNumber(1);
        if (homeworkId != null) {
            builder.setHomeworkId(homeworkId);
        }
        return builder.build();
    }
}
