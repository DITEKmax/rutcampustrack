package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

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
import org.springframework.test.annotation.DirtiesContext;

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

/**
 * Integration tests for RabbitMQ event consumers. Publishes real messages to the fanout exchange
 * and verifies MongoDB state. Proves MARK-03, MARK-04, MARK-05 requirements.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventConsumerIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ru.rutcampustrack.attendance.integration.AuditTestIsolation.register(
                registry, "eventconsumerit", 9);
    }

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private AmqpAdmin amqpAdmin;

    @Autowired private AttendanceAttachmentService attendanceAttachmentService;

    @BeforeEach
    void setUp() {
        assertThat(RABBITMQ.isRunning()).isTrue();
        assertThat(RABBITMQ.getContainerId()).isNotBlank();
        amqpAdmin.purgeQueue("attendance-service.events", false);
        amqpAdmin.purgeQueue("attendance-service.events.dlq", false);
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        mongoTemplate.remove(new Query(), LessonCancellationMarker.class);
        Mockito.reset(scheduleGrpcClient, academicGrpcClient, semesterCacheService);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Map<String, Object> buildEnvelope(String eventType, Map<String, Object> payload) {
        Map<String, Object> eventPayload = new java.util.LinkedHashMap<>(payload);
        eventPayload.putIfAbsent("semester_id", 1L);
        return Map.of(
                "event_type",
                eventType,
                "event_id",
                UUID.randomUUID().toString(),
                "occurred_at",
                Instant.now().toString(),
                "event_version",
                1,
                "source",
                "schedule-service",
                "trace_id",
                "event-consumer-correlation",
                "payload",
                eventPayload);
    }

    private void publishEvent(Map<String, Object> envelope) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
    }

    private void mockGrpcForLesson(
            Long lessonId,
            Long groupId,
            Long subjectId,
            String date,
            int lessonNumber,
            Long... studentIds) {
        LessonResponse lesson =
                LessonResponse.newBuilder()
                        .setId(lessonId)
                        .setGroupId(groupId)
                        .setSubjectId(subjectId)
                        .setDate(date)
                        .setLessonNumber(lessonNumber)
                        .setStatus("closed")
                        .setSemesterId(1L)
                        .build();
        when(scheduleGrpcClient.getLessonById(lessonId)).thenReturn(lesson);

        GroupMembersResponse.Builder members = GroupMembersResponse.newBuilder();
        for (Long sid : studentIds) {
            members.addStudents(
                    StudentInfo.newBuilder()
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

        publishEvent(
                buildEnvelope(
                        "lesson.closed",
                        Map.of(
                                "lesson_id", 1,
                                "group_id", 10)));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AttendanceDocument> docs =
                                    mongoTemplate.findAll(AttendanceDocument.class);
                            assertThat(docs).hasSize(3);
                            for (AttendanceDocument doc : docs) {
                                assertThat(doc.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
                                assertThat(doc.getSource())
                                        .isEqualTo(AttendanceSource.AUTO_SCHEDULER);
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
        mongoTemplate.save(
                AttendanceDocument.builder()
                        .lessonId(1L)
                        .userId(100L)
                        .groupId(10L)
                        .subjectId(5L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 4, 1))
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.STUDENT_GEO)
                        .markedBy(null)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());

        mockGrpcForLesson(1L, 10L, 5L, "2026-04-01", 1, 100L, 101L);

        publishEvent(
                buildEnvelope(
                        "lesson.closed",
                        Map.of(
                                "lesson_id", 1,
                                "group_id", 10)));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AttendanceDocument> docs =
                                    mongoTemplate.findAll(AttendanceDocument.class);
                            assertThat(docs).hasSize(2);
                        });

        // Student 100 must still be PRESENT (not overwritten)
        AttendanceDocument doc100 =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("user_id").is(100L)), AttendanceDocument.class);
        assertThat(doc100).isNotNull();
        assertThat(doc100.getStatus()).isEqualTo(AttendanceStatus.PRESENT);

        // Student 101 was not pre-seeded, must be ABSENT
        AttendanceDocument doc101 =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("user_id").is(101L)), AttendanceDocument.class);
        assertThat(doc101).isNotNull();
        assertThat(doc101.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    // -------------------------------------------------------------------------
    // Test 3 — MARK-03 + MARK-04: partial checkins, only unmarked get ABSENT
    // -------------------------------------------------------------------------

    @Test
    void lessonClosed_partialCheckins_createsAbsentOnlyForUnmarked() {
        // Pre-seed student 100 with PRESENT, student 101 with EXCUSED
        mongoTemplate.save(
                AttendanceDocument.builder()
                        .lessonId(1L)
                        .userId(100L)
                        .groupId(10L)
                        .subjectId(5L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 4, 1))
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.STUDENT_GEO)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
        mongoTemplate.save(
                AttendanceDocument.builder()
                        .lessonId(1L)
                        .userId(101L)
                        .groupId(10L)
                        .subjectId(5L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 4, 1))
                        .status(AttendanceStatus.EXCUSED)
                        .source(AttendanceSource.HEADMAN)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());

        mockGrpcForLesson(1L, 10L, 5L, "2026-04-01", 1, 100L, 101L, 102L);

        publishEvent(
                buildEnvelope(
                        "lesson.closed",
                        Map.of(
                                "lesson_id", 1,
                                "group_id", 10)));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AttendanceDocument> docs =
                                    mongoTemplate.findAll(AttendanceDocument.class);
                            assertThat(docs).hasSize(3);
                        });

        // Student 100: PRESENT preserved
        AttendanceDocument doc100 =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("user_id").is(100L)), AttendanceDocument.class);
        assertThat(doc100.getStatus()).isEqualTo(AttendanceStatus.PRESENT);

        // Student 101: EXCUSED preserved
        AttendanceDocument doc101 =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("user_id").is(101L)), AttendanceDocument.class);
        assertThat(doc101.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);

        // Student 102: new ABSENT
        AttendanceDocument doc102 =
                mongoTemplate.findOne(
                        Query.query(Criteria.where("user_id").is(102L)), AttendanceDocument.class);
        assertThat(doc102.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    // -------------------------------------------------------------------------
    // Test 4 — MARK-05: lesson.cancelled updates all existing docs to CANCELLED
    // -------------------------------------------------------------------------

    @Test
    void lessonCancelled_existingDocs_updatesStatusToCancelled() {
        Instant before = Instant.now();

        mongoTemplate.save(
                AttendanceDocument.builder()
                        .lessonId(1L)
                        .userId(100L)
                        .groupId(10L)
                        .subjectId(5L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 4, 1))
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.STUDENT_GEO)
                        .createdAt(before)
                        .updatedAt(before)
                        .build());
        mongoTemplate.save(
                AttendanceDocument.builder()
                        .lessonId(1L)
                        .userId(101L)
                        .groupId(10L)
                        .subjectId(5L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 4, 1))
                        .status(AttendanceStatus.ABSENT)
                        .source(AttendanceSource.AUTO_SCHEDULER)
                        .createdAt(before)
                        .updatedAt(before)
                        .build());
        mongoTemplate.save(
                AttendanceDocument.builder()
                        .lessonId(1L)
                        .userId(102L)
                        .groupId(10L)
                        .subjectId(5L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 4, 1))
                        .status(AttendanceStatus.EXCUSED)
                        .source(AttendanceSource.HEADMAN)
                        .createdAt(before)
                        .updatedAt(before)
                        .build());

        publishEvent(
                buildEnvelope(
                        "lesson.cancelled",
                        Map.of(
                                "lesson_id", 1,
                                "group_id", 10,
                                "subject_id", 5,
                                "date", "2026-04-01")));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AttendanceDocument> docs =
                                    mongoTemplate.findAll(AttendanceDocument.class);
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
        var envelope = buildEnvelope("lesson.cancelled", Map.of("lesson_id", 999, "group_id", 10));
        publishEvent(envelope);
        awaitEffect(envelope, 1);
        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).isEmpty();
        assertThat(rabbitTemplate.receive("attendance-service.events.dlq")).isNull();
    }

    private void awaitEffect(Map<String, Object> envelope, int acknowledgements) {
        String eventId = envelope.get("event_id").toString();
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertThat(
                                            mongoTemplate.findById(
                                                    eventId + ":ATTENDANCE",
                                                    SemesterArchiveEffectReceiptDocument.class))
                                    .isNotNull()
                                    .extracting(SemesterArchiveEffectReceiptDocument::getResult)
                                    .isEqualTo("APPLIED");
                            assertThat(
                                            mongoTemplate.count(
                                                    Query.query(
                                                            Criteria.where("event_type")
                                                                    .is(
                                                                            "semester.archive.effect.ack")
                                                                    .and("payload")
                                                                    .regex(eventId)),
                                                    "attendance_outbox"))
                                    .isEqualTo(acknowledgements);
                        });
    }

    @Test
    void duplicateCancelledEventRequeuesAckFromExactStoredReceipt() {
        String eventId = UUID.randomUUID().toString();
        long lessonId = 9091L;
        Map<String, Object> envelope =
                new java.util.LinkedHashMap<>(
                        buildEnvelope(
                                "lesson.cancelled",
                                Map.of("lesson_id", lessonId, "group_id", 10L)));
        envelope.put("event_id", eventId);

        Query receiptQuery = Query.query(Criteria.where("_id").is(eventId + ":ATTENDANCE"));
        Query acknowledgementQuery =
                Query.query(
                        Criteria.where("event_type")
                                .is("semester.archive.effect.ack")
                                .and("payload")
                                .regex(eventId));

        publishEvent(envelope);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertThat(
                                            mongoTemplate.count(
                                                    receiptQuery,
                                                    SemesterArchiveEffectReceiptDocument.class))
                                    .isEqualTo(1);
                            assertThat(
                                            mongoTemplate.count(
                                                    acknowledgementQuery, "attendance_outbox"))
                                    .isEqualTo(1);
                        });

        publishEvent(envelope);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertThat(
                                            mongoTemplate.count(
                                                    receiptQuery,
                                                    SemesterArchiveEffectReceiptDocument.class))
                                    .isEqualTo(1);
                            assertThat(
                                            mongoTemplate.count(
                                                    acknowledgementQuery, "attendance_outbox"))
                                    .isEqualTo(2);
                            assertThat(
                                            mongoTemplate.count(
                                                    Query.query(
                                                            Criteria.where("lesson_id")
                                                                    .is(lessonId)),
                                                    LessonCancellationMarker.class))
                                    .isEqualTo(1);
                        });
    }

    @Test
    void lessonCancelled_beforeClose_preventsRetrospectiveMaterialization() {
        var cancelled =
                buildEnvelope("lesson.cancelled", Map.of("lesson_id", 1001, "group_id", 10));
        publishEvent(cancelled);
        awaitEffect(cancelled, 1);
        mockGrpcForLesson(1001L, 10L, 5L, "2026-04-01", 1, 100L, 101L);
        var close = buildEnvelope("lesson.closed", Map.of("lesson_id", 1001, "group_id", 10));
        publishEvent(close);
        awaitEffect(close, 1);
        assertThat(
                        mongoTemplate.find(
                                Query.query(Criteria.where("lesson_id").is(1001L)),
                                AttendanceDocument.class))
                .isEmpty();
        assertThat(
                        mongoTemplate.exists(
                                Query.query(Criteria.where("lesson_id").is(1001L)),
                                LessonCancellationMarker.class))
                .isTrue();
        assertThat(rabbitTemplate.receive("attendance-service.events.dlq")).isNull();
    }

    @Test
    void lessonClosed_thenCancelled_convergesEveryMaterializedRecord() {
        mockGrpcForLesson(1002L, 10L, 5L, "2026-04-01", 1, 100L, 101L);
        publishEvent(
                buildEnvelope(
                        "lesson.closed",
                        Map.of(
                                "lesson_id", 1002,
                                "group_id", 10)));
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertThat(
                                                mongoTemplate.find(
                                                        Query.query(
                                                                Criteria.where("lesson_id")
                                                                        .is(1002L)),
                                                        AttendanceDocument.class))
                                        .hasSize(2));

        publishEvent(
                buildEnvelope(
                        "lesson.cancelled",
                        Map.of(
                                "lesson_id", 1002,
                                "group_id", 10)));
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AttendanceDocument> docs =
                                    mongoTemplate.find(
                                            Query.query(Criteria.where("lesson_id").is(1002L)),
                                            AttendanceDocument.class);
                            assertThat(docs).hasSize(2);
                            assertThat(docs)
                                    .allMatch(doc -> doc.getStatus() == AttendanceStatus.CANCELLED);
                            assertThat(
                                            mongoTemplate.exists(
                                                    Query.query(
                                                            Criteria.where("lesson_id").is(1002L)),
                                                    LessonCancellationMarker.class))
                                    .isTrue();
                        });
    }

    @Test
    void concurrentCloseAndCancel_persistedBoundaryConvergesAndRetriesRemainIdempotent()
            throws Exception {
        Long duplicateLessonId = 1003L;
        mockGrpcForLesson(duplicateLessonId, 10L, 5L, "2026-04-01", 1, 100L, 101L);
        Instant explicitCreated = Instant.now();
        mongoTemplate.save(
                AttendanceDocument.builder()
                        .lessonId(duplicateLessonId)
                        .userId(100L)
                        .groupId(10L)
                        .subjectId(5L)
                        .semesterId(1L)
                        .lessonNumber(1)
                        .lessonDate(LocalDate.of(2026, 4, 1))
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.STUDENT_GEO)
                        .markedBy(100L)
                        .createdAt(explicitCreated)
                        .updatedAt(explicitCreated)
                        .build());

        LessonEventService persistedService =
                new LessonEventService(
                        mongoTemplate,
                        scheduleGrpcClient,
                        academicGrpcClient,
                        semesterCacheService,
                        new SyncTaskExecutor(),
                        attendanceAttachmentService);
        persistedService.processLessonClosed(duplicateLessonId, 10L);
        persistedService.processLessonClosed(duplicateLessonId, 10L);

        List<AttendanceDocument> duplicateCloseDocs =
                mongoTemplate.find(
                        Query.query(Criteria.where("lesson_id").is(duplicateLessonId)),
                        AttendanceDocument.class);
        assertThat(duplicateCloseDocs).hasSize(2);
        assertThat(duplicateCloseDocs)
                .anyMatch(
                        doc ->
                                doc.getUserId().equals(100L)
                                        && doc.getStatus() == AttendanceStatus.PRESENT
                                        && doc.getSource() == AttendanceSource.STUDENT_GEO);
        assertThat(duplicateCloseDocs)
                .anyMatch(
                        doc ->
                                doc.getUserId().equals(101L)
                                        && doc.getStatus() == AttendanceStatus.ABSENT
                                        && doc.getSource() == AttendanceSource.AUTO_SCHEDULER);

        Long raceLessonId = 1004L;
        mockGrpcForLesson(raceLessonId, 10L, 5L, "2026-04-01", 1, 100L, 101L);
        CountDownLatch closePrecheckReached = new CountDownLatch(1);
        CountDownLatch allowCloseInsert = new CountDownLatch(1);
        AtomicBoolean pauseFirstMarkerCheck = new AtomicBoolean();
        MongoTemplate barrierTemplate = Mockito.spy(mongoTemplate);
        Mockito.doAnswer(
                        invocation -> {
                            Query markerQuery = invocation.getArgument(0);
                            boolean markerExists =
                                    mongoTemplate.exists(
                                            markerQuery, LessonCancellationMarker.class);
                            if (pauseFirstMarkerCheck.compareAndSet(false, true)) {
                                closePrecheckReached.countDown();
                                if (!allowCloseInsert.await(10, TimeUnit.SECONDS)) {
                                    throw new AssertionError(
                                            "close insert barrier was not released");
                                }
                            }
                            return markerExists;
                        })
                .when(barrierTemplate)
                .exists(Mockito.any(Query.class), Mockito.eq(LessonCancellationMarker.class));

        LessonEventService raceService =
                new LessonEventService(
                        barrierTemplate,
                        scheduleGrpcClient,
                        academicGrpcClient,
                        semesterCacheService,
                        new SyncTaskExecutor(),
                        attendanceAttachmentService);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> closeFuture =
                    executor.submit(() -> raceService.processLessonClosed(raceLessonId, 10L));
            assertThat(closePrecheckReached.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> cancelFuture =
                    executor.submit(() -> raceService.processLessonCancelled(raceLessonId));
            await().atMost(10, TimeUnit.SECONDS)
                    .until(
                            () ->
                                    mongoTemplate.exists(
                                            Query.query(
                                                    Criteria.where("lesson_id").is(raceLessonId)),
                                            LessonCancellationMarker.class));
            assertThat(
                            mongoTemplate.find(
                                    Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                                    AttendanceDocument.class))
                    .isEmpty();

            cancelFuture.get(10, TimeUnit.SECONDS);
            allowCloseInsert.countDown();
            closeFuture.get(10, TimeUnit.SECONDS);
        } finally {
            allowCloseInsert.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        List<AttendanceDocument> racedDocs =
                mongoTemplate.find(
                        Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                        AttendanceDocument.class);
        assertThat(racedDocs)
                .hasSize(2)
                .allMatch(doc -> doc.getStatus() == AttendanceStatus.CANCELLED);
        assertThat(
                        mongoTemplate.exists(
                                Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                                LessonCancellationMarker.class))
                .isTrue();

        // A retried close/cancel pair must converge on the terminal marker and
        // never resurrect an AUTO_SCHEDULER ABSENT row.
        raceService.processLessonClosed(raceLessonId, 10L);
        raceService.processLessonCancelled(raceLessonId);
        List<AttendanceDocument> retriedDocs =
                mongoTemplate.find(
                        Query.query(Criteria.where("lesson_id").is(raceLessonId)),
                        AttendanceDocument.class);
        assertThat(retriedDocs)
                .hasSize(2)
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
    @Autowired
    private ru.rutcampustrack.attendance.event.InitialMembershipAttendanceBackfillService
            initialMembershipBackfills;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate backfillTransactions;

    @Autowired private ru.rutcampustrack.attendance.student.PairWriteCoordinator backfillFences;
    @Autowired private LessonEventService managedLessonEvents;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper backfillMapper;

    @Test
    void lateInitialMembershipBackfillsOnlyDatedTargetAfterCommittedCloseAndReplaysAfterSeal()
            throws Exception {
        long semester = Math.floorMod(System.nanoTime(), 1_000_000L) + 8_000_000L;
        long lessonId = semester + 1,
                cancelledId = semester + 2,
                group = semester + 3,
                subject = semester + 4;
        long explicit = semester + 5, late = semester + 6, other = semester + 7;
        String date = "2026-09-03";
        LessonResponse lesson = backfillLesson(lessonId, group, subject, semester, date);
        LessonResponse cancelled = backfillLesson(cancelledId, group, subject, semester, date);
        when(scheduleGrpcClient.getLessonById(lessonId)).thenReturn(lesson);
        when(academicGrpcClient.getGroupMembers(group, LocalDate.parse(date), semester))
                .thenReturn(backfillRoster(date, semester, explicit));
        AttendanceDocument marked =
                AttendanceDocument.builder()
                        .lessonId(lessonId)
                        .userId(explicit)
                        .groupId(group)
                        .subjectId(subject)
                        .semesterId(semester)
                        .lessonDate(LocalDate.parse(date))
                        .lessonNumber(1)
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.HEADMAN)
                        .markRevision(UUID.randomUUID().toString())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        mongoTemplate.save(marked);
        var original =
                mongoTemplate.findById(marked.getId(), org.bson.Document.class, "attendances");
        var close =
                buildEnvelope(
                        "lesson.closed",
                        Map.of("lesson_id", lessonId, "group_id", group, "semester_id", semester));
        String closeReceiptId = close.get("event_id") + ":ATTENDANCE";
        publishEvent(close);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertThat(
                                                mongoTemplate.findById(
                                                        closeReceiptId,
                                                        SemesterArchiveEffectReceiptDocument.class))
                                        .isNotNull());
        var closeReceipt =
                mongoTemplate.findById(
                        closeReceiptId,
                        org.bson.Document.class,
                        "semester_archive_effect_receipts");
        assertThat(closeReceipt.getString("result")).isEqualTo("APPLIED");
        // The new authoritative initial history becomes available only after that exact close
        // commits.
        when(academicGrpcClient.getGroupMembers(group, LocalDate.parse(date), semester))
                .thenReturn(backfillRoster(date, semester, explicit, late, other));
        when(academicGrpcClient.getSemesterState(semester))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(semester)
                                .build());
        when(scheduleGrpcClient.getLessonsByGroup(group, semester, "2026-09-01", "2027-01-31"))
                .thenReturn(
                        ru.rutcampustrack.schedule.grpc.LessonsResponse.newBuilder()
                                .addLessons(cancelled)
                                .addLessons(lesson)
                                .build());
        LessonCancellationMarker cancellation = new LessonCancellationMarker();
        cancellation.setLessonId(cancelledId);
        cancellation.setSemesterId(semester);
        cancellation.setMarkedAt(Instant.now());
        mongoTemplate.save(cancellation);
        var request = initialMembershipEnvelope(semester, group, late);
        String eventId = (String) request.get("event_id");
        publishEvent(
                request); // real listener routes the new private event; RPCs remain explicit mocks
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertThat(
                                                mongoTemplate.findById(
                                                        eventId,
                                                        ru.rutcampustrack.attendance.event
                                                                .InitialMembershipAttendanceBackfillReceiptDocument
                                                                .class))
                                        .isNotNull());
        AttendanceDocument inserted =
                mongoTemplate.findOne(
                        Query.query(
                                Criteria.where("lesson_id").is(lessonId).and("user_id").is(late)),
                        AttendanceDocument.class);
        assertThat(inserted != null)
                .as("late initial member must have a stored attendance record")
                .isTrue();
        assertThat(inserted.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(inserted.getSource()).isEqualTo(AttendanceSource.AUTO_SCHEDULER);
        assertThat(inserted.getMarkRevision()).isNotBlank();
        assertThat(
                        original.equals(
                                mongoTemplate.findById(
                                        marked.getId(), org.bson.Document.class, "attendances")))
                .as("explicit mark retains exact identity, timestamps, source and revision")
                .isTrue();
        assertThat(
                        closeReceipt.equals(
                                mongoTemplate.findById(
                                        closeReceiptId,
                                        org.bson.Document.class,
                                        "semester_archive_effect_receipts")))
                .as("original close receipt remains byte-for-byte unchanged")
                .isTrue();
        assertThat(
                        mongoTemplate.count(
                                Query.query(
                                        Criteria.where("lesson_id")
                                                .is(lessonId)
                                                .and("user_id")
                                                .is(other)),
                                AttendanceDocument.class))
                .isZero();
        assertThat(
                        mongoTemplate.count(
                                Query.query(Criteria.where("lesson_id").is(cancelledId)),
                                AttendanceDocument.class))
                .isZero();
        var receipt =
                mongoTemplate.findById(
                        eventId,
                        ru.rutcampustrack.attendance.event
                                .InitialMembershipAttendanceBackfillReceiptDocument.class);
        var payload =
                backfillMapper.convertValue(
                        request.get("payload"),
                        new com.fasterxml.jackson.core.type.TypeReference<
                                java.util.TreeMap<String, Object>>() {});
        String hash =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                new com.fasterxml.jackson.databind.ObjectMapper()
                                                        .writeValueAsBytes(payload)));
        assertThat(receipt.getPayloadHash()).isEqualTo(hash);
        assertThat(receipt.getHistoryId())
                .isEqualTo(((Number) payload.get("history_id")).longValue());
        assertThat(receipt.getResult()).isEqualTo("APPLIED");
        Query acknowledgements = initialMembershipAcknowledgements(eventId);
        assertThat(mongoTemplate.count(acknowledgements, "attendance_outbox")).isEqualTo(1);
        var ack =
                mongoTemplate.findOne(
                        acknowledgements, org.bson.Document.class, "attendance_outbox");
        assertThat(
                        ru.rutcampustrack.attendance.events.EventSchemaValidator.validate(
                                "student.initial-membership.attendance-backfilled.json",
                                ack.getString("payload")))
                .isEmpty();
        var ackBody = backfillMapper.readTree(ack.getString("payload"));
        assertThat(ackBody.path("source").asText()).isEqualTo("attendance-service");
        assertThat(ackBody.path("payload").path("payload_hash").asText()).isEqualTo(hash);
        assertThat(ackBody.path("payload").path("source_event_id").asText()).isEqualTo(eventId);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(Long.toString(semester))),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("barrier_state", "ARCHIVE_SEALED"),
                "semester_archive_fences");
        Mockito.clearInvocations(academicGrpcClient, scheduleGrpcClient);
        initialMembershipBackfills.handle(request);
        Mockito.verifyNoInteractions(academicGrpcClient, scheduleGrpcClient);
        assertThat(mongoTemplate.count(acknowledgements, "attendance_outbox")).isEqualTo(2);
        assertThat(
                        mongoTemplate.count(
                                Query.query(Criteria.where("lesson_id").is(lessonId)),
                                AttendanceDocument.class))
                .isEqualTo(2);
        var alteredPayload = new java.util.LinkedHashMap<>(payload);
        alteredPayload.put("student_id", late + 1);
        var altered = new java.util.LinkedHashMap<>(request);
        altered.put("payload", alteredPayload);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> initialMembershipBackfills.handle(altered))
                .isInstanceOf(IllegalArgumentException.class);
        var spoof = new java.util.LinkedHashMap<>(request);
        spoof.put("source", "schedule-service");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> initialMembershipBackfills.handle(spoof))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mongoTemplate.count(acknowledgements, "attendance_outbox")).isEqualTo(2);
        Mockito.verifyNoInteractions(academicGrpcClient, scheduleGrpcClient);
    }

    @Test
    void initialMembershipForeignUnknownAndOutboxFailureNeverPartiallyApply() {
        long semester = Math.floorMod(System.nanoTime(), 1_000_000L) + 10_000_000L;
        long group = semester + 1,
                lessonId = semester + 2,
                subject = semester + 3,
                student = semester + 4;
        String date = "2026-09-03";
        when(academicGrpcClient.getSemesterState(semester))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(semester)
                                .build());
        var lesson = backfillLesson(lessonId, group, subject, semester, date);
        when(scheduleGrpcClient.getLessonsByGroup(group, semester, "2026-09-01", "2027-01-31"))
                .thenReturn(
                        ru.rutcampustrack.schedule.grpc.LessonsResponse.newBuilder()
                                .addLessons(lesson.toBuilder().setSemesterId(semester + 1))
                                .build());
        var request = initialMembershipEnvelope(semester, group, student);
        String eventId = (String) request.get("event_id");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> initialMembershipBackfills.handle(request))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException
                                .class);
        assertThat(
                        mongoTemplate.findById(
                                eventId,
                                ru.rutcampustrack.attendance.event
                                        .InitialMembershipAttendanceBackfillReceiptDocument.class))
                .isNull();
        assertThat(
                        mongoTemplate.count(
                                initialMembershipAcknowledgements(eventId), "attendance_outbox"))
                .isZero();
        when(scheduleGrpcClient.getLessonsByGroup(group, semester, "2026-09-01", "2027-01-31"))
                .thenReturn(
                        ru.rutcampustrack.schedule.grpc.LessonsResponse.newBuilder()
                                .addLessons(lesson)
                                .build());
        // A roster with no authoritative echo is unknown, not an empty roster.
        when(academicGrpcClient.getGroupMembers(group, LocalDate.parse(date), semester))
                .thenReturn(GroupMembersResponse.getDefaultInstance());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> initialMembershipBackfills.handle(request))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException
                                .class);
        when(academicGrpcClient.getGroupMembers(group, LocalDate.parse(date), semester))
                .thenReturn(backfillRoster(date, semester, student));
        AttendanceDocument foreign =
                AttendanceDocument.builder()
                        .lessonId(lessonId)
                        .userId(student + 100)
                        .groupId(group)
                        .subjectId(subject)
                        .semesterId(semester + 1)
                        .lessonDate(LocalDate.parse(date))
                        .lessonNumber(1)
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.HEADMAN)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        mongoTemplate.save(foreign);
        var foreignBefore =
                mongoTemplate.findById(foreign.getId(), org.bson.Document.class, "attendances");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> initialMembershipBackfills.handle(request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        foreignBefore.equals(
                                mongoTemplate.findById(
                                        foreign.getId(), org.bson.Document.class, "attendances")))
                .as("foreign stored semester never changes")
                .isTrue();
        assertThat(
                        mongoTemplate.findById(
                                eventId,
                                ru.rutcampustrack.attendance.event
                                        .InitialMembershipAttendanceBackfillReceiptDocument.class))
                .isNull();
        assertThat(
                        mongoTemplate.count(
                                initialMembershipAcknowledgements(eventId), "attendance_outbox"))
                .isZero();
        mongoTemplate.remove(foreign);
        var refusedOutbox = Mockito.mock(ru.rutcampustrack.shared.outbox.OutboxStorage.class);
        Mockito.doThrow(new IllegalStateException("owned backfill acknowledgement refusal"))
                .when(refusedOutbox)
                .save(
                        Mockito.eq("student.initial-membership.attendance-backfilled"),
                        Mockito.anyString());
        var refused =
                new ru.rutcampustrack.attendance.event.InitialMembershipAttendanceBackfillService(
                        mongoTemplate,
                        backfillTransactions,
                        academicGrpcClient,
                        scheduleGrpcClient,
                        managedLessonEvents,
                        backfillFences,
                        refusedOutbox,
                        backfillMapper);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> refused.handle(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("owned backfill acknowledgement refusal");
        assertThat(
                        mongoTemplate.count(
                                Query.query(Criteria.where("lesson_id").is(lessonId)),
                                AttendanceDocument.class))
                .isZero();
        assertThat(
                        mongoTemplate.findById(
                                eventId,
                                ru.rutcampustrack.attendance.event
                                        .InitialMembershipAttendanceBackfillReceiptDocument.class))
                .isNull();
        assertThat(
                        mongoTemplate.count(
                                initialMembershipAcknowledgements(eventId), "attendance_outbox"))
                .isZero();
        initialMembershipBackfills.handle(request);
        assertThat(
                        mongoTemplate.count(
                                Query.query(
                                        Criteria.where("lesson_id")
                                                .is(lessonId)
                                                .and("user_id")
                                                .is(student)),
                                AttendanceDocument.class))
                .isEqualTo(1);
        assertThat(
                        mongoTemplate.count(
                                initialMembershipAcknowledgements(eventId), "attendance_outbox"))
                .isEqualTo(1);
    }

    @Test
    void initialMembershipCannotResurrectSubjectOrSemesterTombstones() {
        long semester = Math.floorMod(System.nanoTime(), 1_000_000L) + 12_000_000L;
        long group = semester + 1,
                lessonId = semester + 2,
                subject = semester + 3,
                student = semester + 4;
        when(academicGrpcClient.getSemesterState(semester))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(semester)
                                .build());
        var lesson = backfillLesson(lessonId, group, subject, semester, "2026-09-03");
        when(scheduleGrpcClient.getLessonsByGroup(group, semester, "2026-09-01", "2027-01-31"))
                .thenReturn(
                        ru.rutcampustrack.schedule.grpc.LessonsResponse.newBuilder()
                                .addLessons(lesson)
                                .build());
        mongoTemplate.insert(
                new org.bson.Document("_id", "lesson:" + lessonId)
                        .append("lesson_id", lessonId)
                        .append("group_id", group)
                        .append("subject_delete_committed", true)
                        .append("subject_delete_semester_id", semester),
                "student_checkin_pairs");
        var request = initialMembershipEnvelope(semester, group, student);
        initialMembershipBackfills.handle(request);
        assertThat(
                        mongoTemplate.count(
                                Query.query(Criteria.where("lesson_id").is(lessonId)),
                                AttendanceDocument.class))
                .isZero();
        assertThat(
                        mongoTemplate.findById(
                                (String) request.get("event_id"),
                                ru.rutcampustrack.attendance.event
                                        .InitialMembershipAttendanceBackfillReceiptDocument.class))
                .isNotNull();
        Mockito.verify(academicGrpcClient, Mockito.never())
                .getGroupMembers(
                        Mockito.eq(group), Mockito.any(LocalDate.class), Mockito.eq(semester));
        // A permanent semester tombstone must reject even a missing/damaged local fence.
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(Long.toString(semester))),
                "semester_archive_fences");
        mongoTemplate.insert(
                new org.bson.Document("_id", Long.toString(semester))
                        .append("semester_id", semester)
                        .append("state_version", 1L)
                        .append("operation_id", UUID.randomUUID().toString())
                        .append("deleted_at", java.util.Date.from(Instant.now())),
                "semester_deletion_tombstones");
        var later = initialMembershipEnvelope(semester, group, student + 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> initialMembershipBackfills.handle(later))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.event.SemesterArchiveEffectRejectedException
                                .class);
        assertThat(
                        mongoTemplate.findById(
                                (String) later.get("event_id"),
                                ru.rutcampustrack.attendance.event
                                        .InitialMembershipAttendanceBackfillReceiptDocument.class))
                .isNull();
        assertThat(
                        mongoTemplate.count(
                                initialMembershipAcknowledgements((String) later.get("event_id")),
                                "attendance_outbox"))
                .isZero();
        assertThat(
                        mongoTemplate.count(
                                Query.query(Criteria.where("lesson_id").is(lessonId)),
                                AttendanceDocument.class))
                .isZero();
    }

    private static LessonResponse backfillLesson(
            long lessonId, long group, long subject, long semester, String date) {
        return LessonResponse.newBuilder()
                .setId(lessonId)
                .setGroupId(group)
                .setSubjectId(subject)
                .setSemesterId(semester)
                .setDate(date)
                .setLessonNumber(1)
                .setStatus("closed")
                .build();
    }

    private static GroupMembersResponse backfillRoster(
            String date, long semester, long... students) {
        var builder = GroupMembersResponse.newBuilder().setAsOfDate(date).setSemesterId(semester);
        for (long student : students)
            builder.addStudents(StudentInfo.newBuilder().setUserId(student).build());
        return builder.build();
    }

    private static Map<String, Object> initialMembershipEnvelope(
            long semester, long group, long student) {
        return Map.of(
                "event_id",
                UUID.randomUUID().toString(),
                "event_type",
                "student.initial-membership.recorded",
                "source",
                "academic-service",
                "event_version",
                1,
                "occurred_at",
                Instant.now().toString(),
                "trace_id",
                "owned-initial-membership",
                "payload",
                Map.of(
                        "history_id",
                        student + 100,
                        "student_id",
                        student,
                        "group_id",
                        group,
                        "semester_id",
                        semester,
                        "joined_on",
                        "2026-09-01",
                        "semester_ends_on",
                        "2027-01-31"));
    }

    private static Query initialMembershipAcknowledgements(String eventId) {
        return Query.query(
                Criteria.where("event_type")
                        .is("student.initial-membership.attendance-backfilled")
                        .and("payload")
                        .regex(eventId));
    }
}
