package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.event.LessonCancellationMarker;
import ru.rutcampustrack.attendance.event.LessonEventService;
import ru.rutcampustrack.attendance.event.SemesterArchiveEffectReceiptDocument;
import ru.rutcampustrack.attendance.marking.AttendanceAttachmentService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * Integration tests for RabbitMQ event consumers.
 * Publishes real messages to the fanout exchange and verifies MongoDB state.
 * Proves MARK-03, MARK-04, MARK-05 requirements.
 */
class EventConsumerIT extends AbstractAttendanceIntegrationTest {

    private static final String RABBIT_VHOST = "rct_requests_isolation_event_consumer";

    static {
        ensureRabbitVhost(RABBIT_VHOST);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private AttendanceAttachmentService attendanceAttachmentService;

    @org.springframework.test.context.DynamicPropertySource
    static void overrideRabbitVhost(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.virtual-host", () -> RABBIT_VHOST);
    }

    @BeforeEach
    void setUp() {
        assertThat(RABBITMQ.isRunning()).isTrue();
        assertThat(RABBITMQ.getContainerId()).isNotBlank();
        System.out.printf("requests-isolation runtime rabbitContainer=%s rabbitPort=%s rabbitVhost=%s labels=%s%n",
                RABBITMQ.getContainerId(), RABBITMQ.getMappedPort(5672), RABBIT_VHOST,
                RABBITMQ.getLabels());
        amqpAdmin.purgeQueue("attendance-service.events", false);
        amqpAdmin.purgeQueue("attendance-service.events.dlq", false);
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        mongoTemplate.remove(new Query(), LessonCancellationMarker.class);
        Mockito.reset(scheduleGrpcClient, academicGrpcClient, semesterCacheService);
    }

    private static void ensureRabbitVhost(String vhost) {
        try {
            org.testcontainers.containers.Container.ExecResult result =
                    RABBITMQ.execInContainer("rabbitmqctl", "add_vhost", vhost);
            if (result.getExitCode() != 0 && !result.getStderr().contains("already exists")) {
                throw new IllegalStateException("Cannot create Rabbit vhost " + vhost
                        + ": " + result.getStderr());
            }
            org.testcontainers.containers.Container.ExecResult permissions =
                    RABBITMQ.execInContainer("rabbitmqctl", "set_permissions", "-p", vhost,
                            RABBITMQ.getAdminUsername(), ".*", ".*", ".*");
            if (permissions.getExitCode() != 0) {
                throw new IllegalStateException("Cannot grant Rabbit vhost permissions for " + vhost
                        + ": " + permissions.getStderr());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cannot initialize Rabbit vhost " + vhost, ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot initialize Rabbit vhost " + vhost, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Map<String, Object> buildEnvelope(String eventType, Map<String, Object> payload) {
        Map<String, Object> eventPayload = new java.util.LinkedHashMap<>(payload);
        eventPayload.putIfAbsent("semester_id", 1L);
        return Map.of(
                "event_type", eventType,
                "event_id", UUID.randomUUID().toString(),
                "occurred_at", Instant.now().toString(),
                "event_version", 1,
                "source", "schedule-service",
                "trace_id", "event-consumer-correlation",
                "payload", eventPayload
        );
    }

    private void publishEvent(Map<String, Object> envelope) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
    }

    private void mockGrpcForLesson(Long lessonId, Long groupId, Long subjectId,
                                   String date, int lessonNumber, Long... studentIds) {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(lessonId).setGroupId(groupId).setSubjectId(subjectId)
                .setDate(date).setLessonNumber(lessonNumber).setStatus("closed")
                .setSemesterId(1L)
                .build();
        when(scheduleGrpcClient.getLessonById(lessonId)).thenReturn(lesson);

        GroupMembersResponse.Builder members = GroupMembersResponse.newBuilder();
        for (Long sid : studentIds) {
            members.addStudents(StudentInfo.newBuilder()
                    .setUserId(sid)
                    .setDisplayName("Student " + sid)
                    .build());
        }
        when(academicGrpcClient.getGroupMembers(groupId, LocalDate.parse(date), 1L))
                .thenReturn(members.setAsOfDate(date).setSemesterId(1L).build());
    }

    // -------------------------------------------------------------------------
    // Test 1 — MARK-03: lesson.closed with no existing records creates ABSENT for all
    // -------------------------------------------------------------------------

    @Test
    void lessonClosed_noExistingRecords_createsAbsentForAllStudents() {
        mockGrpcForLesson(1L, 10L, 5L, "2026-04-01", 1, 100L, 101L, 102L);

        publishEvent(buildEnvelope("lesson.closed", Map.of(
                "lesson_id", 1,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).hasSize(3);
            for (AttendanceDocument doc : docs) {
                assertThat(doc.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
                assertThat(doc.getSource()).isEqualTo(AttendanceSource.AUTO_SCHEDULER);
                assertThat(doc.getMarkedBy()).isNull();
                assertThat(doc.getGroupId()).isEqualTo(10L);
                assertThat(doc.getSubjectId()).isEqualTo(5L);
                assertThat(doc.getSemesterId()).isEqualTo(1L);
                assertThat(doc.getLessonNumber()).isEqualTo(1);
                assertThat(doc.getLessonDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Test 2 — MARK-04: lesson.closed preserves existing PRESENT checkin
    // -------------------------------------------------------------------------

    @Test
    void lessonClosed_existingCheckin_preservesCheckinStatus() {
        // Pre-seed student 100 with PRESENT status
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .markedBy(null).createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        mockGrpcForLesson(1L, 10L, 5L, "2026-04-01", 1, 100L, 101L);

        publishEvent(buildEnvelope("lesson.closed", Map.of(
                "lesson_id", 1,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).hasSize(2);
        });

        // Student 100 must still be PRESENT (not overwritten)
        AttendanceDocument doc100 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(100L)),
                AttendanceDocument.class);
        assertThat(doc100).isNotNull();
        assertThat(doc100.getStatus()).isEqualTo(AttendanceStatus.PRESENT);

        // Student 101 was not pre-seeded, must be ABSENT
        AttendanceDocument doc101 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(101L)),
                AttendanceDocument.class);
        assertThat(doc101).isNotNull();
        assertThat(doc101.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    // -------------------------------------------------------------------------
    // Test 3 — MARK-03 + MARK-04: partial checkins, only unmarked get ABSENT
    // -------------------------------------------------------------------------

    @Test
    void lessonClosed_partialCheckins_createsAbsentOnlyForUnmarked() {
        // Pre-seed student 100 with PRESENT, student 101 with EXCUSED
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(101L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.EXCUSED).source(AttendanceSource.HEADMAN)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        mockGrpcForLesson(1L, 10L, 5L, "2026-04-01", 1, 100L, 101L, 102L);

        publishEvent(buildEnvelope("lesson.closed", Map.of(
                "lesson_id", 1,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).hasSize(3);
        });

        // Student 100: PRESENT preserved
        AttendanceDocument doc100 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(100L)),
                AttendanceDocument.class);
        assertThat(doc100.getStatus()).isEqualTo(AttendanceStatus.PRESENT);

        // Student 101: EXCUSED preserved
        AttendanceDocument doc101 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(101L)),
                AttendanceDocument.class);
        assertThat(doc101.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);

        // Student 102: new ABSENT
        AttendanceDocument doc102 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(102L)),
                AttendanceDocument.class);
        assertThat(doc102.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    // -------------------------------------------------------------------------
    // Test 4 — MARK-05: lesson.cancelled updates all existing docs to CANCELLED
    // -------------------------------------------------------------------------

    @Test
    void lessonCancelled_existingDocs_updatesStatusToCancelled() {
        Instant before = Instant.now();

        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(before).updatedAt(before)
                .build());
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(101L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.ABSENT).source(AttendanceSource.AUTO_SCHEDULER)
                .createdAt(before).updatedAt(before)
                .build());
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(102L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.EXCUSED).source(AttendanceSource.HEADMAN)
                .createdAt(before).updatedAt(before)
                .build());

        publishEvent(buildEnvelope("lesson.cancelled", Map.of(
                "lesson_id", 1,
                "group_id", 10,
                "subject_id", 5,
                "date", "2026-04-01"
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).hasSize(3);
            for (AttendanceDocument doc : docs) {
                assertThat(doc.getStatus()).isEqualTo(AttendanceStatus.CANCELLED);
                assertThat(doc.getUpdatedAt()).isAfter(before);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Test 5 — MARK-05 edge case: lesson.cancelled with no docs does not error
    // -------------------------------------------------------------------------

    @Test
    void lessonCancelled_noDocs_noError() {
        publishEvent(buildEnvelope("lesson.cancelled", Map.of(
                "lesson_id", 999,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).isEmpty();
        });
    }

    @Test
    void duplicateCancelledEventRequeuesAckFromExactStoredReceipt() {
        String eventId = UUID.randomUUID().toString();
        long lessonId = 9091L;
        Map<String, Object> envelope = new java.util.LinkedHashMap<>(buildEnvelope(
                "lesson.cancelled", Map.of("lesson_id", lessonId, "group_id", 10L)));
        envelope.put("event_id", eventId);

        Query receiptQuery = Query.query(Criteria.where("_id").is(eventId + ":ATTENDANCE"));
        Query acknowledgementQuery = Query.query(Criteria.where("event_type")
                .is("semester.archive.effect.ack").and("payload").regex(eventId));

        publishEvent(envelope);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(mongoTemplate.count(receiptQuery, SemesterArchiveEffectReceiptDocument.class)).isEqualTo(1);
            assertThat(mongoTemplate.count(acknowledgementQuery, "attendance_outbox")).isEqualTo(1);
        });

        publishEvent(envelope);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(mongoTemplate.count(receiptQuery, SemesterArchiveEffectReceiptDocument.class)).isEqualTo(1);
            assertThat(mongoTemplate.count(acknowledgementQuery, "attendance_outbox")).isEqualTo(2);
            assertThat(mongoTemplate.count(Query.query(Criteria.where("lesson_id").is(lessonId)),
                    LessonCancellationMarker.class)).isEqualTo(1);
        });
    }

    @Test
    void lessonCancelled_beforeClose_preventsRetrospectiveMaterialization() {
        publishEvent(buildEnvelope("lesson.cancelled", Map.of(
                "lesson_id", 1001,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(mongoTemplate.exists(
                        Query.query(Criteria.where("lesson_id").is(1001L)),
                        LessonCancellationMarker.class)).isTrue());

        mockGrpcForLesson(1001L, 10L, 5L, "2026-04-01", 1, 100L, 101L);
        publishEvent(buildEnvelope("lesson.closed", Map.of(
                "lesson_id", 1001,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(mongoTemplate.find(
                    Query.query(Criteria.where("lesson_id").is(1001L)),
                    AttendanceDocument.class)).isEmpty();
            assertThat(mongoTemplate.exists(
                    Query.query(Criteria.where("lesson_id").is(1001L)),
                    LessonCancellationMarker.class)).isTrue();
        });
    }

    @Test
    void lessonClosed_thenCancelled_convergesEveryMaterializedRecord() {
        mockGrpcForLesson(1002L, 10L, 5L, "2026-04-01", 1, 100L, 101L);
        publishEvent(buildEnvelope("lesson.closed", Map.of(
                "lesson_id", 1002,
                "group_id", 10
        )));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(mongoTemplate.find(
                        Query.query(Criteria.where("lesson_id").is(1002L)),
                        AttendanceDocument.class)).hasSize(2));

        publishEvent(buildEnvelope("lesson.cancelled", Map.of(
                "lesson_id", 1002,
                "group_id", 10
        )));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.find(
                    Query.query(Criteria.where("lesson_id").is(1002L)),
                    AttendanceDocument.class);
            assertThat(docs).hasSize(2);
            assertThat(docs).allMatch(doc -> doc.getStatus() == AttendanceStatus.CANCELLED);
            assertThat(mongoTemplate.exists(
                    Query.query(Criteria.where("lesson_id").is(1002L)),
                    LessonCancellationMarker.class)).isTrue();
        });
    }

    @Test
    void concurrentCloseAndCancel_persistedBoundaryConvergesAndRetriesRemainIdempotent()
            throws Exception {
        Long duplicateLessonId = 1003L;
        mockGrpcForLesson(duplicateLessonId, 10L, 5L, "2026-04-01", 1, 100L, 101L);
        Instant explicitCreated = Instant.now();
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(duplicateLessonId).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .markedBy(100L).createdAt(explicitCreated).updatedAt(explicitCreated)
                .build());

        LessonEventService persistedService = new LessonEventService(
                mongoTemplate, scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, new SyncTaskExecutor(), attendanceAttachmentService);
        persistedService.processLessonClosed(duplicateLessonId, 10L);
        persistedService.processLessonClosed(duplicateLessonId, 10L);

        List<AttendanceDocument> duplicateCloseDocs = mongoTemplate.find(
                Query.query(Criteria.where("lesson_id").is(duplicateLessonId)),
                AttendanceDocument.class);
        assertThat(duplicateCloseDocs).hasSize(2);
        assertThat(duplicateCloseDocs).anyMatch(doc -> doc.getUserId().equals(100L)
                && doc.getStatus() == AttendanceStatus.PRESENT
                && doc.getSource() == AttendanceSource.STUDENT_GEO);
        assertThat(duplicateCloseDocs).anyMatch(doc -> doc.getUserId().equals(101L)
                && doc.getStatus() == AttendanceStatus.ABSENT
                && doc.getSource() == AttendanceSource.AUTO_SCHEDULER);

        Long raceLessonId = 1004L;
        mockGrpcForLesson(raceLessonId, 10L, 5L, "2026-04-01", 1, 100L, 101L);
        CountDownLatch closePrecheckReached = new CountDownLatch(1);
        CountDownLatch allowCloseInsert = new CountDownLatch(1);
        AtomicBoolean pauseFirstMarkerCheck = new AtomicBoolean();
        MongoTemplate barrierTemplate = Mockito.spy(mongoTemplate);
        Mockito.doAnswer(invocation -> {
            Query markerQuery = invocation.getArgument(0);
            boolean markerExists = mongoTemplate.exists(markerQuery, LessonCancellationMarker.class);
            if (pauseFirstMarkerCheck.compareAndSet(false, true)) {
                closePrecheckReached.countDown();
                if (!allowCloseInsert.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("close insert barrier was not released");
                }
            }
            return markerExists;
        }).when(barrierTemplate).exists(Mockito.any(Query.class),
                Mockito.eq(LessonCancellationMarker.class));

        LessonEventService raceService = new LessonEventService(
                barrierTemplate, scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, new SyncTaskExecutor(), attendanceAttachmentService);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> closeFuture = executor.submit(() ->
                    raceService.processLessonClosed(raceLessonId, 10L));
            assertThat(closePrecheckReached.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> cancelFuture = executor.submit(() ->
                    raceService.processLessonCancelled(raceLessonId));
            await().atMost(10, TimeUnit.SECONDS).until(() -> mongoTemplate.exists(
                    Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                    LessonCancellationMarker.class));
            assertThat(mongoTemplate.find(
                    Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                    AttendanceDocument.class)).isEmpty();

            cancelFuture.get(10, TimeUnit.SECONDS);
            allowCloseInsert.countDown();
            closeFuture.get(10, TimeUnit.SECONDS);
        } finally {
            allowCloseInsert.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        List<AttendanceDocument> racedDocs = mongoTemplate.find(
                Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                AttendanceDocument.class);
        assertThat(racedDocs).hasSize(2)
                .allMatch(doc -> doc.getStatus() == AttendanceStatus.CANCELLED);
        assertThat(mongoTemplate.exists(
                Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                LessonCancellationMarker.class)).isTrue();

        // A retried close/cancel pair must converge on the terminal marker and
        // never resurrect an AUTO_SCHEDULER ABSENT row.
        raceService.processLessonClosed(raceLessonId, 10L);
        raceService.processLessonCancelled(raceLessonId);
        List<AttendanceDocument> retriedDocs = mongoTemplate.find(
                Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                AttendanceDocument.class);
        assertThat(retriedDocs).hasSize(2)
                .allMatch(doc -> doc.getStatus() == AttendanceStatus.CANCELLED)
                .noneMatch(doc -> doc.getStatus() == AttendanceStatus.ABSENT);
    }

    // D-09 (semester.archived → SemesterCacheService.refresh()) is covered by
    // EventConsumerTest unit test. The equivalent IT was removed because cached
    // Spring context reuse across test classes left EventConsumer holding a stale
    // @MockitoBean reference, so verify() could not see interactions even when the
    // handler had run (log showed "refreshed semester cache" yet Mockito reported
    // zero interactions). Unit coverage is sufficient for routing logic; the
    // RabbitMQ-to-handler plumbing itself is exercised by the other 5 tests here.
}
