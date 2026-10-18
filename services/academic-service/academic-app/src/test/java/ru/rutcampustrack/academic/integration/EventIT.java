package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.homework.HomeworkService;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.semester.SemesterService;
import ru.rutcampustrack.academic.subject.SubjectService;
import ru.rutcampustrack.academic.user.UserService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests verifying end-to-end event publishing pipeline. M02 Группа 5: service -> Spring
 * ApplicationEvent -> DomainEventListener (BEFORE_COMMIT) -> academic_outbox ->
 * OutboxPublisherJob.publishBatch() -> RabbitTemplate -> real RabbitMQ broker.
 *
 * <p>CRITICAL: No @Transactional on test methods. Service methods manage their own transactions.
 * Листенер теперь срабатывает BEFORE_COMMIT и пишет в outbox в той же tx — если тест-tx откатится,
 * outbox-запись тоже откатится.
 *
 * <p>Тесты вызывают {@link #flushOutbox()} после service-метода чтобы эмулировать
 * OutboxPublisherJob tick (в test-профиле он не шедулится).
 */
@Import(EventIT.EventClockConfiguration.class)
class EventIT extends AbstractAcademicEventIntegrationTest {
    @TestConfiguration
    static class EventClockConfiguration {
        @Bean
        @Primary
        MutableEventClock eventClock() {
            return new MutableEventClock();
        }
    }

    static final class MutableEventClock extends Clock {
        private volatile Instant current = Instant.parse("2027-04-15T09:00:00Z");

        void setDate(LocalDate date) {
            current = date.atTime(12, 0).atZone(getZone()).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Europe/Moscow");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @Autowired private MutableEventClock eventClock;

    private static final String EXCHANGE = "rut-uit.events";
    private static final int RECEIVE_TIMEOUT_MS = 5000;

    @Autowired
    private ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations
            identityOperations;

    private AcademicIdentityIntegrationFixture.RequestScope actorRequest;

    /**
     * Phase 61-03 / D-04: createHomework теперь вызывает ScheduleGrpcClient. В интеграционном тесте
     * реального schedule-service нет — мочим клиент, возвращая LessonResponse с правильным
     * subjectId.
     */
    @MockitoBean private ScheduleGrpcClient scheduleGrpcClient;

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private GroupService groupService;

    @Autowired private UserService userService;

    @Autowired private SemesterService semesterService;

    @Autowired private HomeworkService homeworkService;

    @Autowired private SubjectService subjectService;

    @Autowired private GroupRepository groupRepository;

    @Autowired private GroupHistoryCoverageRepository coverageRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private SemesterRepository semesterRepository;

    @Autowired private SubjectRepository subjectRepository;

    @Autowired private HomeworkRepository homeworkRepository;

    @Autowired private StudentGroupHistoryRepository studentGroupHistoryRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    // Shared test entities created in @BeforeEach
    private Group groupA;
    private Group groupB;
    private Subject testSubject;
    private Semester testSemester;
    private User testUser;

    @BeforeEach
    void setUpTestEntities() {
        eventClock.setDate(LocalDate.of(2027, 4, 15));
        useCurrentAdministrator();
        // Create managed groups through the production writer so coverage
        // provenance exists for the transfer test as well.
        groupA =
                groupService.createGroup(
                        new ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest(
                                firstAvailableManagedGroupName("УИТ")));
        groupB =
                groupService.createGroup(
                        new ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest(
                                firstAvailableManagedGroupName("УВП")));

        // Create and activate the intended homework semester before the subject writer
        // binds its immutable semester identity (D029).
        testSemester = new Semester();
        testSemester.setName("Test Semester " + System.nanoTime());
        testSemester.setDateFrom(LocalDate.of(2027, 1, 1));
        testSemester.setDateTo(LocalDate.of(2027, 6, 30));
        testSemester.setSemesterType(ru.rutcampustrack.academic.contract.enums.SemesterType.SPRING);
        testSemester.setAcademicYear(2026);
        testSemester.setActive(false);
        testSemester.setCreatedAt(OffsetDateTime.now());
        testSemester = semesterRepository.save(testSemester);

        useCurrentAdministrator();

        // Create the student through UserService so enrollment history is
        // written atomically with the user row.
        EntityModel<UserCreatedResponse> created =
                userService.createUser(
                        new CreateUserRequest(
                                "Студентов",
                                "Тест",
                                null,
                                UserRole.STUDENT,
                                groupA.getId(),
                                null,
                                Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L));
        testUser =
                userRepository
                        .findByIdIncludingArchived(created.getContent().getId())
                        .orElseThrow();
        useCurrentAdministrator();
        identityOperations.headman(groupA.getId(), testUser.getId(), null, false, null);

        testSemester = activateSemesterAsAdministrator(testSemester.getId());
        useSelectedActor(testUser.getId(), ru.rutcampustrack.auth.session.model.AuthRole.HEADMAN);

        // Create the subject and its canonical lesson type atomically through
        // the managed writer; the database requires every subject to retain a
        // corresponding subject_lesson_types row.
        testSubject =
                subjectService.createSubject(
                        new CreateSubjectRequest(
                                "Test Subject " + System.nanoTime(),
                                SubjectType.LECTURE,
                                List.of(SubjectType.LECTURE),
                                List.of()));

        // Phase 61-03 / D-04: мок резолва пары — возвращаем LessonResponse с subjectId=testSubject.
        when(scheduleGrpcClient.resolveLesson(anyLong(), any(), anyInt()))
                .thenReturn(
                        Optional.of(
                                ru.rutcampustrack.schedule.grpc.LessonResponse.newBuilder()
                                        .setGroupId(groupA.getId())
                                        .setSubjectId(testSubject.getId())
                                        .setSemesterId(testSemester.getId())
                                        .setLessonNumber(1)
                                        .setDate(LocalDate.now(eventClock).plusDays(1).toString())
                                        .setOccurrenceId(9001L)
                                        .setRevision(1L)
                                        .setStatus("planned")
                                        .build()));
        when(scheduleGrpcClient.reserveHomeworkBinding(
                        anyLong(), any(UUID.class), anyLong(), any(byte[].class)))
                .thenAnswer(
                        invocation ->
                                homeworkBindingResponse(
                                        9101L,
                                        9001L,
                                        null,
                                        ru.rutcampustrack.schedule.grpc.HomeworkBindingState
                                                .HOMEWORK_BINDING_STATE_PENDING));
        when(scheduleGrpcClient.confirmHomeworkBinding(anyLong(), anyLong(), any(UUID.class)))
                .thenAnswer(
                        invocation ->
                                homeworkBindingResponse(
                                        9101L,
                                        9001L,
                                        (Long) invocation.getArgument(1),
                                        ru.rutcampustrack.schedule.grpc.HomeworkBindingState
                                                .HOMEWORK_BINDING_STATE_ACTIVE));
        when(scheduleGrpcClient.countSubjectReferences(anyLong()))
                .thenReturn(
                        ru.rutcampustrack.schedule.grpc.CountSubjectReferencesResponse
                                .getDefaultInstance());
        // Commit/publish setup operations before a scenario binds its queue.
        flushOutbox();
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

    // AcademicFixtureResetListener owns cleanup; no application deletion is used as teardown.

    private Semester createSemester(String name, LocalDate from, LocalDate to) {
        Semester semester = new Semester();
        semester.setName(name + " " + System.nanoTime());
        semester.setDateFrom(from);
        semester.setDateTo(to);
        semester.setSemesterType(
                from.getMonthValue() <= 6
                        ? ru.rutcampustrack.academic.contract.enums.SemesterType.SPRING
                        : ru.rutcampustrack.academic.contract.enums.SemesterType.AUTUMN);
        semester.setAcademicYear(from.getYear() - (from.getMonthValue() <= 6 ? 1 : 0));
        semester.setActive(false);
        semester.setCreatedAt(OffsetDateTime.now());
        return semesterRepository.save(semester);
    }

    private Semester activateAfterBarrier(
            Long semesterId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("activation race barrier timed out");
        }
        return activateSemesterAsAdministrator(semesterId);
    }

    private long latestOutboxId() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM academic_outbox", Long.class);
    }

    private List<Long> archivedSemesterIdsAfter(long outboxId) {
        return jdbcTemplate.queryForList(
                """
                SELECT (payload -> 'payload' ->> 'semester_id')::bigint
                FROM academic_outbox
                WHERE id > ? AND event_type = 'semester.archived'
                ORDER BY id
                """,
                Long.class,
                outboxId);
    }

    private void assertOnlyActiveSemester(Long expectedId) {
        List<Long> activeIds =
                semesterRepository.findAllByIsActiveTrueOrderByIdAsc().stream()
                        .map(Semester::getId)
                        .toList();
        assertThat(activeIds).containsExactly(expectedId);
    }

    // --- Helper: declare a named non-exclusive non-auto-delete queue bound to rut-uit.events
    // exchange ---
    // Using named queue to avoid issues with exclusive auto-delete queues across multiple receive()
    // calls.

    private String bindTempQueue() {
        String name = "test.events." + System.nanoTime();
        Queue queue =
                new Queue(
                        name, false, false,
                        false); // durable=false, exclusive=false, autoDelete=false
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(new FanoutExchange(EXCHANGE)));
        return name;
    }

    private List<JsonNode> receiveEvents(String queueName) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        Message message = rabbitTemplate.receive(queueName, RECEIVE_TIMEOUT_MS);
        while (message != null) {
            events.add(objectMapper.readTree(message.getBody()));
            message = rabbitTemplate.receive(queueName, 250);
        }
        return events;
    }

    // --- EVENT-01: group.updated events ---

    @Test
    void updateGroup_publishesGroupUpdatedEvent() throws Exception {
        String queueName = bindTempQueue();
        useCurrentAdministrator();

        // 58-04: UpdateGroupRequest(name, active). Новое имя должно матчить активный паттерн.
        // 58-07: при изменении name публикуются ДВА события: group.renamed + group.updated.
        // Читаем очередь до тех пор, пока не встретим group.updated.
        groupService.updateGroup(groupA.getId(), new UpdateGroupRequest("ТНА-121", true, 4));
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
        assertThat(root.get("event_id").asText())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("group_id").asLong()).isEqualTo(groupA.getId());
    }

    @Test
    void deleteGroup_withManagedCoverageIsRejected() {
        useCurrentAdministrator();
        assertThatThrownBy(() -> groupService.deleteGroup(groupA.getId()))
                .isInstanceOfSatisfying(
                        HistoricalMembershipException.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(
                                                HistoricalMembershipException.Code
                                                        .UNSUPPORTED_MUTATION));
        assertThat(groupRepository.findById(groupA.getId())).isPresent();
    }

    @Test
    void transferStudent_publishesGroupUpdatedEventForBothGroups() throws Exception {
        String queueName = bindTempQueue();

        Long oldGroupId = groupA.getId();
        Long newGroupId = groupB.getId();

        useCurrentAdministrator();
        userService.transferStudent(
                testUser.getId(), new TransferStudentRequest(newGroupId, "Test transfer reason"));
        flushOutbox();

        // Dashboard invalidation is also accepted output; identify the two
        // group.updated events without relying on unrelated message order.
        assertThat(
                        receiveEvents(queueName).stream()
                                .filter(
                                        event ->
                                                "group.updated"
                                                        .equals(event.path("event_type").asText()))
                                .map(event -> event.path("payload").path("group_id").asLong())
                                .toList())
                .containsExactlyInAnyOrder(oldGroupId, newGroupId);
    }

    private void useCurrentAdministrator() {
        useSelectedActor(
                identityFixture.canonical().adminId(),
                ru.rutcampustrack.auth.session.model.AuthRole.ADMIN);
    }

    private void useSelectedActor(long userId, ru.rutcampustrack.auth.session.model.AuthRole role) {
        closeActorRequest();
        actorRequest = identityFixture.request(identityFixture.select(userId, role), null);
    }

    @AfterEach
    void closeActorRequest() {
        if (actorRequest != null) {
            actorRequest.close();
            actorRequest = null;
        }
    }

    private Semester activateSemesterAsAdministrator(long semesterId) {
        var admin =
                identityFixture.select(
                        identityFixture.canonical().adminId(),
                        ru.rutcampustrack.auth.session.model.AuthRole.ADMIN);
        try (var ignored = identityFixture.request(admin, null)) {
            return semesterService.activateSemester(semesterId);
        }
    }

    // --- EVENT-02: semester.archived event ---

    @Test
    void activateSemester_publishesSemesterArchivedEvent() throws Exception {
        // Create two new inactive semesters
        Semester semesterA =
                createSemester(
                        "Archived Semester", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 6, 30));
        Semester semesterB =
                createSemester(
                        "New Semester", LocalDate.of(2028, 7, 1), LocalDate.of(2028, 12, 31));

        String queueName = bindTempQueue();

        // Activate semesterA -- this deactivates whatever is currently active (V2 seed semester)
        eventClock.setDate(LocalDate.of(2028, 4, 15));
        activateSemesterAsAdministrator(semesterA.getId());
        flushOutbox();

        // Drain events from the first activation (seed semester archiving + semesterA activation).
        while (rabbitTemplate.receive(queueName, 500) != null) {
            /* drain */
        }

        // Activate semesterB -- this archives semesterA and should publish semester.archived for
        // semesterA
        Long archivedSemesterId = semesterA.getId();
        long outboxBeforeSwitch = latestOutboxId();
        eventClock.setDate(LocalDate.of(2028, 10, 15));
        activateSemesterAsAdministrator(semesterB.getId());
        assertThat(archivedSemesterIdsAfter(outboxBeforeSwitch))
                .containsExactly(archivedSemesterId);
        flushOutbox();

        List<JsonNode> transitionEvents = receiveEvents(queueName);
        List<JsonNode> archivedEvents =
                transitionEvents.stream()
                        .filter(
                                event ->
                                        "semester.archived"
                                                .equals(event.path("event_type").asText()))
                        .toList();
        assertThat(archivedEvents).hasSize(1);
        assertThat(
                        transitionEvents.stream()
                                .filter(
                                        event ->
                                                "semester.activated"
                                                        .equals(event.path("event_type").asText())))
                .hasSize(1);
        JsonNode root = archivedEvents.getFirst();
        assertThat(root.get("event_type").asText()).isEqualTo("semester.archived");
        assertThat(root.get("event_id").asText())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(root.get("occurred_at")).isNotNull();

        JsonNode payload = root.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.get("semester_id").asLong()).isEqualTo(archivedSemesterId);

        assertOnlyActiveSemester(semesterB.getId());

        long outboxBeforeReplay = latestOutboxId();
        activateSemesterAsAdministrator(semesterB.getId());
        flushOutbox();
        assertThat(latestOutboxId()).isEqualTo(outboxBeforeReplay);
        assertThat(rabbitTemplate.receive(queueName, 250))
                .as("A → A replay must not publish an archive event")
                .isNull();
        assertOnlyActiveSemester(semesterB.getId());

        long outboxBeforeMissingTarget = latestOutboxId();
        assertThatThrownBy(() -> activateSemesterAsAdministrator(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        flushOutbox();
        assertThat(latestOutboxId()).isEqualTo(outboxBeforeMissingTarget);
        assertThat(rabbitTemplate.receive(queueName, 250))
                .as("missing target must leave the outbox unchanged")
                .isNull();
        assertOnlyActiveSemester(semesterB.getId());
    }

    @Test
    void concurrentActivationOfSameSemester_archivesPreviousSemesterOnce() throws Exception {
        Semester semesterA =
                createSemester("Concurrent A", LocalDate.of(2038, 1, 1), LocalDate.of(2038, 6, 30));
        Semester semesterB =
                createSemester(
                        "Concurrent B", LocalDate.of(2038, 7, 1), LocalDate.of(2038, 12, 31));
        String queueName = bindTempQueue();

        eventClock.setDate(LocalDate.of(2038, 4, 15));
        activateSemesterAsAdministrator(semesterA.getId());
        flushOutbox();
        while (rabbitTemplate.receive(queueName, 500) != null) {
            /* discard setup transition */
        }
        long outboxBeforeRace = latestOutboxId();
        eventClock.setDate(LocalDate.of(2038, 10, 15));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Semester> activateB =
                executor.submit(() -> activateAfterBarrier(semesterB.getId(), ready, start));
        Future<Semester> replayB =
                executor.submit(() -> activateAfterBarrier(semesterB.getId(), ready, start));
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(activateB.get(30, TimeUnit.SECONDS).getId()).isEqualTo(semesterB.getId());
            assertThat(replayB.get(30, TimeUnit.SECONDS).getId()).isEqualTo(semesterB.getId());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertOnlyActiveSemester(semesterB.getId());
        List<Long> archivedIds = archivedSemesterIdsAfter(outboxBeforeRace);
        assertThat(archivedIds).containsExactly(semesterA.getId());

        flushOutbox();
        List<JsonNode> transitionEvents = receiveEvents(queueName);
        assertThat(
                        transitionEvents.stream()
                                .filter(
                                        event ->
                                                "semester.archived"
                                                        .equals(event.path("event_type").asText())))
                .hasSize(1);
        assertThat(
                        transitionEvents.stream()
                                .filter(
                                        event ->
                                                "semester.activated"
                                                        .equals(event.path("event_type").asText())))
                .hasSize(1);
    }

    // --- EVENT-03: homework.published and homework.updated events ---

    @Test
    void createHomework_publishesHomeworkPublishedEvent() throws Exception {
        String queueName = bindTempQueue();

        homeworkService.createHomework(
                new CreateHomeworkRequest(
                        "HW Title",
                        "description",
                        null,
                        testSubject.getId(),
                        groupA.getId(),
                        testSemester.getId(),
                        LocalDate.now(eventClock).plusDays(1),
                        1));
        flushOutbox();

        List<JsonNode> published =
                receiveEvents(queueName).stream()
                        .filter(
                                event ->
                                        "homework.published"
                                                .equals(event.path("event_type").asText()))
                        .toList();
        assertThat(published).hasSize(1);
        JsonNode root = published.getFirst();
        assertThat(root.get("event_type").asText()).isEqualTo("homework.published");
        assertThat(root.get("event_id").asText())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
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
        assertThat(payload.get("lesson_date").asText())
                .isEqualTo(LocalDate.now(eventClock).plusDays(1).toString());
        assertThat(payload.get("lesson_number").asInt()).isEqualTo(1);
    }

    private ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse homeworkBindingResponse(
            long bindingId,
            long occurrenceId,
            Long homeworkId,
            ru.rutcampustrack.schedule.grpc.HomeworkBindingState state) {
        ru.rutcampustrack.schedule.grpc.LessonInfo lesson =
                ru.rutcampustrack.schedule.grpc.LessonInfo.newBuilder()
                        .setLessonId(9002L)
                        .setGroupId(groupA.getId())
                        .setSubjectId(testSubject.getId())
                        .setStartsAt(LocalDate.now(eventClock).plusDays(1) + "T09:00")
                        .setLessonNumber(1)
                        .setDate(LocalDate.now(eventClock).plusDays(1).toString())
                        .setOccurrenceId(occurrenceId)
                        .setAssignmentId(1L)
                        .setSemesterId(testSemester.getId())
                        .setTeacherId(1L)
                        .setLessonType("lecture")
                        .setGeneration(1L)
                        .setRevision(1L)
                        .setStatus("planned")
                        .build();
        var builder =
                ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse.newBuilder()
                        .setBindingId(bindingId)
                        .setOccurrenceId(occurrenceId)
                        .setCurrentLesson(lesson)
                        .setState(state)
                        .setBindingMode("LESSON")
                        .setRevision(
                                state
                                                == ru.rutcampustrack.schedule.grpc
                                                        .HomeworkBindingState
                                                        .HOMEWORK_BINDING_STATE_ACTIVE
                                        ? 2L
                                        : 1L)
                        .setGroupId(groupA.getId())
                        .setSubjectId(testSubject.getId())
                        .setSemesterId(testSemester.getId())
                        .setDate(LocalDate.now(eventClock).plusDays(1).toString())
                        .setLessonNumber(1);
        if (homeworkId != null) {
            builder.setHomeworkId(homeworkId);
        }
        return builder.build();
    }
}
