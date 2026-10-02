package ru.rutcampustrack.attendance.studentrequest;

import com.mongodb.client.model.Filters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.attendance.config.MongoConvertersConfig;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestKind;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestPageResponse;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.excuse.ExcuseEventPublisher;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.marking.AttendanceAttachmentService;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.events.EventSchemaValidator;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentInput;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.ExcuseSubmission;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.LateCheckinSubmission;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.LessonSnapshot;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.RequestDetail;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.RequestPage;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLateCheckinBudgetDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentRequestReceiptDocument;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Domain-level Mongo replica-set checks for the frozen student request contract.
 * The container and database are task-owned and receive a unique name/database
 * on every class run; this test never cleans an existing runtime.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import({StudentRequestService.class, HeadmanRequestService.class, MongoConvertersConfig.class,
        StudentRequestDomainIT.TestConfig.class})
class StudentRequestDomainIT {

    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final String DATABASE = "rct_student_role_02_requests_" + RUN_ID;
    private static final String CONTAINER_NAME = "rct-student-role-02-requests-" + RUN_ID;
    private static final long STUDENT_ID = 100L;
    private static final long GROUP_ID = 10L;
    private static final long SEMESTER_ID = 30L;
    private static final String OUTBOX_COLLECTION = "student_request_test_outbox";
    private static final Instant START = Instant.parse("2026-09-07T08:00:00Z");
    private static final Identity STUDENT = new Identity(STUDENT_ID, UserRole.STUDENT, GROUP_ID, false);
    private static final Identity HEADMAN = new Identity(777L, UserRole.STUDENT, GROUP_ID, true);

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0")
            .withCreateContainerCmdModifier(command -> command.withName(CONTAINER_NAME));

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", StudentRequestDomainIT::mongoUri);
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    private static String mongoUri() {
        String replicaSetUri = MONGO.getReplicaSetUrl();
        int slash = replicaSetUri.indexOf('/', "mongodb://".length());
        int query = replicaSetUri.indexOf('?', slash);
        String suffix = query < 0 ? "" : replicaSetUri.substring(query);
        return replicaSetUri.substring(0, slash) + "/" + DATABASE + suffix;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean({"transactionManager", "mongoTransactionManager"})
        MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
            return new MongoTransactionManager(factory);
        }

        @Bean
        FaultInjectingTransactionTemplate transactionTemplate(MongoTransactionManager manager) {
            return new FaultInjectingTransactionTemplate(manager);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MongoOutboxStorage outboxStorage(MongoTemplate mongoTemplate) {
            return new MongoOutboxStorage(mongoTemplate, OUTBOX_COLLECTION);
        }

        @Bean
        AttendanceAttachmentService attendanceAttachmentService(
                RequestAttachmentRepository attachmentRepository,
                MutableClock clock) {
            return new AttendanceAttachmentService(attachmentRepository, clock);
        }

        @Bean
        ExcuseEventPublisher excuseEventPublisher(MongoOutboxStorage outboxStorage, ObjectMapper objectMapper) {
            return new ExcuseEventPublisher(outboxStorage, objectMapper);
        }

        @Bean
        LateCheckinEventPublisher lateCheckinEventPublisher(MongoOutboxStorage outboxStorage,
                                                            ObjectMapper objectMapper) {
            return new LateCheckinEventPublisher(outboxStorage, objectMapper);
        }

        @Bean
        BarrierPairWriteCoordinator pairWriteCoordinator(MongoTemplate mongoTemplate) {
            return new BarrierPairWriteCoordinator(mongoTemplate);
        }

        @Bean
        MutableClock clock() {
            return new MutableClock(START, ZoneOffset.UTC);
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> value;
        private final ZoneId zone;

        MutableClock(Instant initial, ZoneId zone) {
            this.value = new AtomicReference<>(initial);
            this.zone = zone;
        }

        void set(Instant instant) {
            value.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(value.get(), requestedZone);
        }

        @Override
        public Instant instant() {
            return value.get();
        }
    }

    static final class BarrierPairWriteCoordinator extends PairWriteCoordinator {
        private volatile CyclicBarrier barrier;
        private volatile long studentId;
        private volatile long lessonId;

        BarrierPairWriteCoordinator(MongoTemplate mongoTemplate) {
            super(mongoTemplate);
        }

        void arm(long studentId, long lessonId) {
            this.studentId = studentId;
            this.lessonId = lessonId;
            this.barrier = new CyclicBarrier(2);
        }

        void clearBarrier() {
            barrier = null;
        }

        @Override
        public ru.rutcampustrack.attendance.student.CheckinPairStateDocument lock(
                long semesterId, long studentId, long lessonId, long groupId, Instant now) {
            CyclicBarrier active = barrier;
            if (active != null && this.studentId == studentId && this.lessonId == lessonId) {
                try {
                    active.await(30, TimeUnit.SECONDS);
                    if (barrier == active) {
                        barrier = null;
                    }
                } catch (Exception error) {
                    throw new IllegalStateException("Pair-race barrier failed", error);
                }
            }
            return super.lock(semesterId, studentId, lessonId, groupId, now);
        }
    }

    /** Test-only fault injector: the real transaction commits before the labelled ACK fault. */
    static final class FaultInjectingTransactionTemplate extends TransactionTemplate {
        private final AtomicReference<Fault> nextFault = new AtomicReference<>();
        private final AtomicInteger transactionExecutions = new AtomicInteger();

        FaultInjectingTransactionTemplate(MongoTransactionManager manager) {
            super(manager);
        }

        void failNextCommitAck() {
            nextFault.set(Fault.UNKNOWN_COMMIT_RESULT);
        }

        void failNextBeforeTransaction() {
            nextFault.set(Fault.TRANSIENT_TRANSACTION_ERROR);
        }

        void resetTransactionExecutionCount() {
            transactionExecutions.set(0);
        }

        int transactionExecutionCount() {
            return transactionExecutions.get();
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            Fault before = nextFault.get();
            if (before == Fault.TRANSIENT_TRANSACTION_ERROR
                    && nextFault.compareAndSet(before, null)) {
                throw labelled(before);
            }
            transactionExecutions.incrementAndGet();
            T result = super.execute(action);
            Fault after = nextFault.get();
            if (after == Fault.UNKNOWN_COMMIT_RESULT
                    && nextFault.compareAndSet(after, null)) {
                throw labelled(after);
            }
            return result;
        }

        private static MongoException labelled(Fault fault) {
            MongoException exception = new MongoException("test fault: " + fault.name());
            exception.addLabel(fault.label);
            return exception;
        }

        private enum Fault {
            UNKNOWN_COMMIT_RESULT(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL),
            TRANSIENT_TRANSACTION_ERROR(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);

            private final String label;

            Fault(String label) {
                this.label = label;
            }
        }
    }

    @jakarta.annotation.Resource
    StudentRequestService service;
    @jakarta.annotation.Resource
    HeadmanRequestService headmanRequestService;
    @jakarta.annotation.Resource
    MongoTemplate mongoTemplate;
    @jakarta.annotation.Resource
    AttendanceRepository attendanceRepository;
    @jakarta.annotation.Resource
    ExcuseRepository excuseRepository;
    @jakarta.annotation.Resource
    LateCheckinRepository lateCheckinRepository;
    @jakarta.annotation.Resource
    StudentRequestReceiptRepository receiptRepository;
    @jakarta.annotation.Resource
    StudentLateCheckinBudgetRepository budgetRepository;
    @jakarta.annotation.Resource
    RequestAttachmentRepository attachmentRepository;
    @jakarta.annotation.Resource
    MutableClock clock;
    @jakarta.annotation.Resource
    TransactionTemplate transactionTemplate;
    @jakarta.annotation.Resource
    BarrierPairWriteCoordinator pairWriteCoordinator;
    @jakarta.annotation.Resource
    MongoOutboxStorage outboxStorage;
    @jakarta.annotation.Resource
    FaultInjectingTransactionTemplate faultInjectingTransactionTemplate;

    @MockitoBean
    ScheduleGrpcClient scheduleGrpcClient;
    @MockitoBean
    AcademicGrpcClient academicGrpcClient;
    @MockitoBean
    SemesterCacheService semesterCacheService;
    private final Set<Long> currentLessonIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> lessonStatuses = new ConcurrentHashMap<>();
    private final Set<Long> blockedLessonIds = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        // The URI points at a unique task database, so this only clears this run's fixture.
        mongoTemplate.getDb().drop();
        ensureIndexes();

        currentLessonIds.clear();
        lessonStatuses.clear();
        blockedLessonIds.clear();
        IntStream.rangeClosed(1, 100).forEach(id -> {
            currentLessonIds.add((long) id);
            lessonStatuses.put((long) id, "closed");
        });
        clock.set(START);
        reset(scheduleGrpcClient, academicGrpcClient, semesterCacheService);
        pairWriteCoordinator.clearBarrier();
        when(semesterCacheService.getActiveSemesterId()).thenReturn(SEMESTER_ID);
        when(academicGrpcClient.getUserDisplayName(anyLong())).thenReturn("Student 100");
        when(academicGrpcClient.isHeadman(anyLong(), anyLong())).thenReturn(
                HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());
        when(academicGrpcClient.getSubjectDetailsByIds(anyList())).thenAnswer(invocation -> {
            List<Long> subjectIds = invocation.getArgument(0);
            Map<Long, AcademicGrpcClient.SubjectDetails> result = new HashMap<>();
            for (Long subjectId : subjectIds) {
                result.put(subjectId, new AcademicGrpcClient.SubjectDetails(
                        "Subject " + subjectId, "LECTURE"));
            }
            return result;
        });
        when(scheduleGrpcClient.getLessonsByIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().map(this::compactLesson).toList();
        });
        when(scheduleGrpcClient.getLessonsByGroup(anyLong(), anyLong(), anyString(), anyString()))
                .thenAnswer(invocation -> LessonsResponse.newBuilder()
                        .addAllLessons(currentLessonIds.stream().sorted().map(this::fullLesson).toList())
                        .build());
        when(scheduleGrpcClient.getLessonById(anyLong()))
                .thenAnswer(invocation -> fullLesson(invocation.getArgument(0)));
        when(scheduleGrpcClient.requireAttendanceMutationReady(anyLong(), eq(GROUP_ID)))
                .thenAnswer(invocation -> fullLesson(invocation.getArgument(0)));
    }

    @Test
    void sixConcurrentManualLateSubmissionsChargeExactlyFive() throws Exception {
        for (long lessonId = 1; lessonId <= 6; lessonId++) {
            seedAbsent(lessonId);
        }

        int count = 6;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RequestDetail>> futures;
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<RequestDetail>> pending = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                long lessonId = index + 1L;
                String key = "manual-budget-key-" + lessonId;
                pending.add(executor.submit(() -> {
                    start.await();
                    return service.submitLateCheckin(STUDENT, new LateCheckinSubmission(lessonId, key));
                }));
            }
            start.countDown();
            futures = pending;
            executor.shutdown();
            assertThat(executor.awaitTermination(45, TimeUnit.SECONDS)).isTrue();
        }

        int successes = 0;
        int conflicts = 0;
        for (Future<RequestDetail> future : futures) {
            try {
                assertThat(future.get()).isNotNull();
                successes++;
            } catch (ExecutionException error) {
                assertThat(error.getCause()).isInstanceOf(ConflictException.class);
                conflicts++;
            }
        }
        assertThat(successes).isEqualTo(5);
        assertThat(conflicts).isEqualTo(1);
        assertThat(lateCheckinRepository.findAll()).hasSize(5);
        assertThat(budgetRepository.findByStudentIdAndSemesterId(STUDENT_ID, SEMESTER_ID))
                .get().extracting(StudentLateCheckinBudgetDocument::getUsed).isEqualTo(5);
    }

    @Test
    void concurrentSameExcuseKeyCreatesOneTicketAndOneReceipt() throws Exception {
        seedAbsent(10L);
        ExcuseSubmission command = excuse(List.of(10L), "same-excuse-key-0001", List.of());

        int count = 6;
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<RequestDetail>> futures = IntStream.range(0, count)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        return service.submitExcuse(STUDENT, command);
                    }))
                    .toList();
            start.countDown();
            List<RequestDetail> results = new ArrayList<>();
            for (Future<RequestDetail> future : futures) {
                results.add(future.get(45, TimeUnit.SECONDS));
            }
            assertThat(results).allMatch(result -> result.summary().id()
                    .equals(results.getFirst().summary().id()));
        }

        assertThat(excuseRepository.findAll()).hasSize(1);
        assertThat(receiptRepository.findAll()).hasSize(1);
    }

    @Test
    void exactExcuseRetryKeepsSingleRequestReceiptAndOutboxButFilenameChangeConflicts() {
        seedAbsent(11L);
        byte[] proof = pdfBytes(64);
        ExcuseSubmission original = new ExcuseSubmission(
                List.of(11L), ExcuseType.ILLNESS, null,
                List.of(new AttachmentInput("proof.pdf", "application/pdf", proof)),
                "fingerprint-filename-key");

        RequestDetail first = service.submitExcuse(STUDENT, original);
        RequestDetail replay = service.submitExcuse(STUDENT, original);

        assertThat(replay.summary().id()).isEqualTo(first.summary().id());
        assertThat(excuseRepository.findAll()).hasSize(1);
        assertThat(receiptRepository.findAll()).hasSize(1);
        assertThat(actualOutboxEvents("excuse.requested")).hasSize(1);

        ExcuseSubmission renamed = new ExcuseSubmission(
                List.of(11L), ExcuseType.ILLNESS, null,
                List.of(new AttachmentInput("renamed.pdf", "application/pdf", proof)),
                original.idempotencyKey());

        assertThatThrownBy(() -> service.submitExcuse(STUDENT, renamed))
                .isInstanceOf(ConflictException.class);
        assertThat(excuseRepository.findAll()).hasSize(1);
        assertThat(receiptRepository.findAll()).hasSize(1);
        assertThat(actualOutboxEvents("excuse.requested")).hasSize(1);
    }

    @Test
    void excuseIdempotencyFingerprintDistinguishesNullCommentFromLiteralNull() {
        seedAbsent(12L);
        String key = "fingerprint-comment-key";
        ExcuseSubmission withoutComment = new ExcuseSubmission(
                List.of(12L), ExcuseType.ILLNESS, null, List.of(), key);

        service.submitExcuse(STUDENT, withoutComment);

        ExcuseSubmission literalNull = new ExcuseSubmission(
                List.of(12L), ExcuseType.ILLNESS, "null", List.of(), key);

        assertThatThrownBy(() -> service.submitExcuse(STUDENT, literalNull))
                .isInstanceOf(ConflictException.class);
        assertThat(excuseRepository.findAll()).hasSize(1);
        assertThat(receiptRepository.findAll()).hasSize(1);
        assertThat(actualOutboxEvents("excuse.requested")).hasSize(1);
    }

    @Test
    void invalidLessonMakesExcusePackageRollbackWithoutReceiptOrAttachment() {
        seedAbsent(20L);
        ExcuseSubmission command = excuse(List.of(20L, 21L), "rollback-package-key", List.of(
                new AttachmentInput("proof.pdf", "application/pdf", pdfBytes(64))));

        assertThatThrownBy(() -> service.submitExcuse(STUDENT, command))
                .isInstanceOf(ConflictException.class);

        assertThat(excuseRepository.findAll()).isEmpty();
        assertThat(receiptRepository.findAll()).isEmpty();
        assertThat(attachmentRepository.findAll()).isEmpty();
    }

    @Test
    void overlappingExcusePackagesCannotBothActivate() throws Exception {
        seedAbsent(30L);
        seedAbsent(31L);
        seedAbsent(32L);
        ExcuseSubmission first = excuse(List.of(30L, 31L), "overlap-first-key", List.of());
        ExcuseSubmission second = excuse(List.of(31L, 32L), "overlap-second-key", List.of());

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<RequestDetail> firstFuture = executor.submit(() -> {
                start.await();
                return service.submitExcuse(STUDENT, first);
            });
            Future<RequestDetail> secondFuture = executor.submit(() -> {
                start.await();
                return service.submitExcuse(STUDENT, second);
            });
            start.countDown();
            List<Future<RequestDetail>> futures = List.of(firstFuture, secondFuture);
            int succeeded = 0;
            int rejected = 0;
            for (Future<RequestDetail> future : futures) {
                try {
                    future.get(45, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException error) {
                    assertThat(error.getCause()).isInstanceOf(ConflictException.class);
                    rejected++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        }
        assertThat(excuseRepository.findAll()).hasSize(1);
    }

    @Test
    void exactAttachmentLimitsPersistBinarySeparatelyAndCancelRetainsIt() {
        seedAbsent(40L);
        byte[] first = pdfBytes((int) StudentRequestService.MAX_ATTACHMENT_BYTES);
        byte[] second = pdfBytes((int) StudentRequestService.MAX_ATTACHMENT_BYTES);

        RequestDetail detail = service.submitExcuse(STUDENT, excuse(List.of(40L), "attachment-bound-key",
                List.of(new AttachmentInput("../first.pdf", "application/pdf", first),
                        new AttachmentInput("second.pdf", "application/pdf", second))));

        List<RequestAttachmentDocument> stored = attachmentRepository
                .findByRequestIdAndOwnerStudentIdOrderByPositionAsc(detail.summary().id(), STUDENT_ID);
        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(RequestAttachmentDocument::getSize)
                .containsExactly((long) first.length, (long) second.length);
        assertThat(stored).allSatisfy(document ->
                assertThat(document.getData()).isNotNull());

        Document ticket = mongoTemplate.getCollection("excuse_tickets")
                .find(Filters.eq("_id", new ObjectId(detail.summary().id()))).first();
        assertThat(ticket).isNotNull();
        assertThat(ticket.toJson()).doesNotContain("data", "file_payload_b64", "payload_b64");

        RequestDetail cancelled = service.cancel(STUDENT, detail.summary().id());
        assertThat(cancelled.summary().status()).isEqualTo(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus.CANCELLED);
        assertThat(attachmentRepository.findById(stored.getFirst().getId()).orElseThrow().getData())
                .isNotNull();
    }

    @Test
    void attachmentSpoofThirdAndOversizeInputsRejectBeforeAnyWrite() {
        seedAbsent(41L);
        AttachmentInput spoof = new AttachmentInput("proof.jpg", "image/jpeg", pdfBytes(64));
        assertThatThrownBy(() -> service.submitExcuse(STUDENT, excuse(List.of(41L), "spoof-file-key-0001",
                List.of(spoof)))).isInstanceOf(BadRequestException.class);
        assertThat(excuseRepository.findAll()).isEmpty();

        List<AttachmentInput> third = List.of(
                new AttachmentInput("one.pdf", "application/pdf", pdfBytes(16)),
                new AttachmentInput("two.pdf", "application/pdf", pdfBytes(16)),
                new AttachmentInput("three.pdf", "application/pdf", pdfBytes(16)));
        assertThatThrownBy(() -> service.submitExcuse(STUDENT, excuse(List.of(41L), "third-file-key-0001", third)))
                .isInstanceOf(BadRequestException.class);

        byte[] oversize = pdfBytes((int) StudentRequestService.MAX_ATTACHMENT_BYTES + 1);
        assertThatThrownBy(() -> service.submitExcuse(STUDENT, excuse(List.of(41L), "oversize-file-key", List.of(
                new AttachmentInput("oversize.pdf", "application/pdf", oversize)))))
                .isInstanceOf(BadRequestException.class);
        assertThat(excuseRepository.findAll()).isEmpty();
        assertThat(receiptRepository.findAll()).isEmpty();
        assertThat(attachmentRepository.findAll()).isEmpty();
    }

    @Test
    void attachmentReadProjectsExpiryButRetentionClearsBytesUnderTheSemesterFence() {
        seedAbsent(42L);
        RequestDetail detail = service.submitExcuse(STUDENT, excuse(List.of(42L), "expiry-file-key-0001", List.of(
                new AttachmentInput("proof.pdf", "application/pdf", pdfBytes(64)))));
        String attachmentId = attachmentRepository.findAll().getFirst().getId();

        assertThat(service.download(STUDENT, detail.summary().id(), attachmentId).bytes()).hasSize(64);

        clock.set(detail.attachments().getFirst().expiresAt());
        RequestDetail logicallyExpired = service.get(STUDENT, detail.summary().id());
        assertThat(logicallyExpired.attachments()).singleElement().satisfies(attachment ->
                assertThat(attachment.state()).isEqualTo(AttachmentState.EXPIRED));
        assertThat(attachmentRepository.findById(attachmentId).orElseThrow())
                .satisfies(attachment -> {
                    assertThat(attachment.getState()).isEqualTo(AttachmentState.ACTIVE);
                    assertThat(attachment.getData()).isNotNull();
                });
        assertThatThrownBy(() -> service.download(STUDENT, detail.summary().id(), attachmentId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(error -> assertThat(((org.springframework.web.server.ResponseStatusException) error)
                        .getStatusCode().value()).isEqualTo(410));
        RequestAttachmentDocument stillStored = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(stillStored.getState()).isEqualTo(AttachmentState.ACTIVE);
        assertThat(stillStored.getData()).isNotNull();

        assertThat(service.expireAttachments()).isEqualTo(1);
        RequestAttachmentDocument expired = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(expired.getState()).isEqualTo(AttachmentState.EXPIRED);
        assertThat(expired.getData()).isNull();
    }

    @Test
    void mixedOwnerListAndOptionsExposeBothPendingKindsWithoutPeerData() {
        TimeZone previousTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
        try {
            assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Europe/Moscow"));
            seedAbsent(50L);
            seedAbsent(51L);
            RequestDetail excuse = service.submitExcuse(STUDENT,
                    excuse(List.of(50L), "mixed-excuse-key-01", List.of()));
            LateCheckinRequest autoLate = lateCheckinRepository.save(LateCheckinRequest.builder()
                    .id("62a000000000000000000051")
                    .studentId(STUDENT_ID)
                    .groupId(GROUP_ID)
                    .lessonId(51L)
                    .subjectId(1051L)
                    .subjectName("Алгебра")
                    .subjectType("SEMINAR")
                    .semesterId(SEMESTER_ID)
                    .lessonNumber(51)
                    .lessonDate(LocalDate.of(2026, 9, 7))
                    .startsAt(LocalTime.of(10, 0))
                    .endsAt(LocalTime.of(11, 0))
                    .studentName("Иванов Иван")
                    .status(LateCheckinRequestStatus.PENDING)
                    .origin(LateCheckinRequestOrigin.AUTO_GEO_FAILURE)
                    .createdAt(START)
                    .updatedAt(START)
                    .build());
            RequestDetail mappedLate = service.get(STUDENT, autoLate.getId());

            Document storedExcuse = mongoTemplate.getCollection("excuse_tickets")
                    .find(Filters.eq("_id", new ObjectId(excuse.summary().id()))).first();
            assertThat(storedExcuse).isNotNull();
            assertThat(storedExcuse.getString("status")).isEqualTo("submitted");

            RequestPage ownerPage = service.list(STUDENT, RequestBucket.OPEN, 0, 20);
            assertThat(ownerPage.totalElements()).isEqualTo(2);
            assertThat(ownerPage.content()).extracting(summary -> summary.kind())
                    .containsExactlyInAnyOrder(StudentRequestKind.EXCUSE, StudentRequestKind.LATE_CHECKIN);
            RequestDetail mappedExcuse = service.get(STUDENT, excuse.summary().id());
            var listedExcuse = ownerPage.content().stream()
                    .filter(summary -> summary.id().equals(excuse.summary().id()))
                    .findFirst().orElseThrow();
            assertThat(listedExcuse.status()).isEqualTo(StudentRequestStatus.PENDING);
            assertThat(listedExcuse.lessons()).containsExactlyElementsOf(mappedExcuse.summary().lessons());
            assertThat(listedExcuse.lessons()).singleElement().satisfies(lesson -> {
                assertThat(lesson.date()).isEqualTo(LocalDate.of(2026, 9, 7));
                assertThat(lesson.startsAt()).isEqualTo(LocalTime.of(10, 0));
                assertThat(lesson.endsAt()).isEqualTo(LocalTime.of(11, 0));
            });
            var listedLate = ownerPage.content().stream()
                    .filter(summary -> summary.id().equals(autoLate.getId()))
                    .findFirst().orElseThrow();
            assertThat(listedLate.origin()).isEqualTo(StudentRequestOrigin.AUTO_GEO_FAILURE);
            assertThat(listedLate.lessons()).containsExactlyElementsOf(mappedLate.summary().lessons());
            assertThat(listedLate.lessons()).singleElement().satisfies(lesson -> {
                assertThat(lesson.subjectName()).isEqualTo("Алгебра");
                assertThat(lesson.subjectType()).isEqualTo("SEMINAR");
                assertThat(lesson.startsAt()).isEqualTo(LocalTime.of(10, 0));
                assertThat(lesson.endsAt()).isEqualTo(LocalTime.of(11, 0));
            });

            RequestPage firstPage = service.list(STUDENT, RequestBucket.OPEN, 0, 1);
            RequestPage secondPage = service.list(STUDENT, RequestBucket.OPEN, 1, 1);
            assertThat(firstPage.totalElements()).isEqualTo(2);
            assertThat(firstPage.totalPages()).isEqualTo(2);
            assertThat(List.of(firstPage.content().getFirst().id(), secondPage.content().getFirst().id()))
                    .containsExactlyInAnyOrder(excuse.summary().id(), autoLate.getId());

            assertThatThrownBy(() -> service.get(student(200L), excuse.summary().id()))
                    .isInstanceOf(ru.rutcampustrack.attendance.exception.AccessDeniedException.class);
            assertThat(service.list(student(200L), RequestBucket.OPEN, 0, 20).content()).isEmpty();

            var options = service.options(STUDENT, List.of(snapshot(50L)));
            assertThat(options.lessons()).singleElement().satisfies(option -> {
                assertThat(option.pendingRequests()).singleElement().satisfies(ref -> {
                    assertThat(ref.id()).isEqualTo(excuse.summary().id());
                    assertThat(ref.kind()).isEqualTo(StudentRequestKind.EXCUSE);
                    assertThat(ref.origin()).isEqualTo(StudentRequestOrigin.MANUAL);
                });
                assertThat(option.excuseEligible()).isFalse();
            });

            seedAbsent(52L);
            RequestDetail approved = service.submitExcuse(STUDENT,
                    excuse(List.of(52L), "mixed-approved-key-01", List.of()));
            service.decideExcuse(headman(777L), approved.summary().id(), true, "approved");
            RequestPage archivePage = service.list(STUDENT, RequestBucket.ARCHIVE, 0, 20);
            assertThat(archivePage.content()).singleElement().satisfies(summary -> {
                assertThat(summary.id()).isEqualTo(approved.summary().id());
                assertThat(summary.status()).isEqualTo(StudentRequestStatus.APPROVED);
            });
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void commentRequiredIsDomainOwnedAndOtherValidationRemainsStrict() {
        var options = service.options(STUDENT, List.of(snapshot(90L)));

        var other = options.reasons().stream()
                .filter(reason -> reason.code() == ExcuseType.OTHER)
                .findFirst().orElseThrow();
        assertThat(other.commentRequired()).isTrue();
        assertThat(options.reasons().stream()
                .filter(reason -> reason.code() != ExcuseType.OTHER)
                .map(StudentRequestModels.ReasonOption::commentRequired))
                .containsOnly(false);

        for (String comment : Arrays.asList(null, "", " \t\r\n")) {
            assertThatThrownBy(() -> service.submitExcuse(STUDENT,
                    new ExcuseSubmission(List.of(90L), ExcuseType.OTHER, comment, List.of(),
                            "other-invalid-" + UUID.randomUUID())))
                    .isInstanceOf(BadRequestException.class);
        }

        seedAbsent(91L);
        RequestDetail accepted = service.submitExcuse(STUDENT,
                new ExcuseSubmission(List.of(91L), ExcuseType.OTHER, "  custom reason  ", List.of(),
                        "other-nonblank-accepted-key"));
        assertThat(accepted.reason()).isEqualTo(ExcuseType.OTHER);
        assertThat(accepted.comment()).isEqualTo("custom reason");
    }

    @Test
    void persistedLimitControlsOptionsAndSubmissionsUntilExhausted() {
        seedAbsent(74L);
        seedAbsent(75L);
        seedAbsent(76L);
        budgetRepository.save(StudentLateCheckinBudgetDocument.builder()
                .studentId(STUDENT_ID).semesterId(SEMESTER_ID).limit(7).used(5).updatedAt(START).build());

        var available = service.options(STUDENT, List.of(snapshot(74L)));
        assertThat(available.budget()).extracting(budget -> budget.limit(), budget -> budget.used(),
                budget -> budget.remaining()).containsExactly(7, 5, 2);
        assertThat(available.lessons()).singleElement()
                .satisfies(option -> assertThat(option.lateCheckinEligible()).isTrue());

        service.submitLateCheckin(STUDENT, new LateCheckinSubmission(74L, "limit-seven-key-0001"));
        service.submitLateCheckin(STUDENT, new LateCheckinSubmission(75L, "limit-seven-key-0002"));

        var exhausted = service.options(STUDENT, List.of(snapshot(76L)));
        assertThat(exhausted.budget().remaining()).isZero();
        assertThat(exhausted.lessons()).singleElement()
                .satisfies(option -> assertThat(option.lateCheckinEligible()).isFalse());
        assertThatThrownBy(() -> service.submitLateCheckin(STUDENT,
                new LateCheckinSubmission(76L, "limit-seven-key-0003")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void presentPriorityKeepsExistingAttendanceDuringExcuseApproval() {
        seedAbsent(60L);
        RequestDetail detail = service.submitExcuse(STUDENT, excuse(List.of(60L), "present-race-key-0001", List.of()));
        AttendanceDocument current = attendanceRepository.findByLessonIdAndUserId(60L, STUDENT_ID).orElseThrow();
        current.setStatus(AttendanceStatus.PRESENT);
        current.setSource(AttendanceSource.STUDENT_GEO);
        attendanceRepository.save(current);

        RequestDetail decided = service.decideExcuse(headman(777L), detail.summary().id(), true, "already present");

        assertThat(decided.summary().status()).isEqualTo(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus.APPROVED);
        assertThat(attendanceRepository.findByLessonIdAndUserId(60L, STUDENT_ID).orElseThrow())
                .satisfies(attendance -> {
                    assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(attendance.getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
        });
    }

    @Test
    void headmanOpenUnionNormalizesSubmittedAndExcludesDraft() {
        seedHeadmanTicket("headman-submitted", ExcuseTicketStatus.SUBMITTED,
                List.of(LocalDate.of(2026, 9, 7)), START, START, null);
        seedHeadmanTicket("headman-draft", ExcuseTicketStatus.DRAFT,
                List.of(LocalDate.of(2026, 9, 8)), START.plusSeconds(1), START.plusSeconds(1), null);

        HeadmanRequestPageResponse page = headmanRequestService.list(
                HEADMAN, "OPEN", 0, 20, "EXCUSE", null, null, null);

        assertThat(page.content()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo("headman-submitted");
            assertThat(summary.status()).isEqualTo("PENDING");
        });
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void headmanMongoDatesMatchDetailAndInclusiveCoverageInMoscow() {
        TimeZone previousTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
        try {
            LocalDate lessonDate = LocalDate.of(2026, 10, 1);
            ExcuseTicket ticket = seedHeadmanTicket("62a000000000000000000101", ExcuseTicketStatus.SUBMITTED,
                    List.of(lessonDate), START, START, null);
            ticket.setExcuseType(ExcuseType.OTHER);
            excuseRepository.save(ticket);
            Document stored = mongoTemplate.getCollection("excuse_tickets")
                    .find(Filters.eq("_id", new ObjectId(ticket.getId()))).first();
            assertThat(stored).isNotNull();
            assertThat(stored.getList("lesson_snapshots", Document.class).getFirst().getDate("date").toInstant())
                    .isEqualTo(Instant.parse("2026-09-30T21:00:00Z"));
            assertThat(stored.getString("excuse_type")).isEqualTo("other");

            var detail = headmanRequestService.get(HEADMAN, ticket.getId());
            assertThat(detail.lessons()).singleElement().satisfies(lesson ->
                    assertThat(lesson.date()).isEqualTo(lessonDate));
            HeadmanRequestPageResponse page = headmanRequestService.list(
                    HEADMAN, "OPEN", 0, 20, "EXCUSE", null, "2026-10-01", "2026-10-01");
            assertThat(page.content()).singleElement().satisfies(summary -> {
                assertThat(summary.coverageStart()).isEqualTo(lessonDate);
                assertThat(summary.coverageEnd()).isEqualTo(lessonDate);
                assertThat(summary.reason()).isEqualTo("OTHER");
                assertThat(summary).isEqualTo(detail.summary());
            });
            assertThat(headmanRequestService.list(HEADMAN, "OPEN", 0, 20, "EXCUSE", null,
                    "2026-09-30", "2026-09-30").content()).isEmpty();
            assertThat(headmanRequestService.list(HEADMAN, "OPEN", 0, 20, "EXCUSE", null,
                    "2026-10-02", "2026-10-02").content()).isEmpty();
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void headmanCoverageFilterMatchesCoverageIntervalGap() {
        seedHeadmanTicket("headman-gap", ExcuseTicketStatus.APPROVED,
                List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)),
                START, START.plusSeconds(2), START.plusSeconds(1));
        seedHeadmanTicket("headman-after", ExcuseTicketStatus.APPROVED,
                List.of(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 21)),
                START.plusSeconds(3), START.plusSeconds(5), START.plusSeconds(4));
        seedHeadmanTicket("headman-empty", ExcuseTicketStatus.APPROVED,
                List.of(), START.plusSeconds(6), START.plusSeconds(8), START.plusSeconds(7));

        HeadmanRequestPageResponse page = headmanRequestService.list(
                HEADMAN, "ARCHIVE", 0, 20, "EXCUSE", null,
                "2026-09-05", "2026-09-05");

        assertThat(page.content()).extracting(summary -> summary.id())
                .containsExactly("headman-gap");
        assertThat(page.totalElements()).isEqualTo(1);

        HeadmanRequestPageResponse fromOnly = headmanRequestService.list(
                HEADMAN, "ARCHIVE", 0, 20, "EXCUSE", null,
                "2026-09-15", null);
        assertThat(fromOnly.content()).extracting(summary -> summary.id())
                .containsExactly("headman-after");
        assertThat(fromOnly.totalElements()).isEqualTo(1);

        HeadmanRequestPageResponse toOnly = headmanRequestService.list(
                HEADMAN, "ARCHIVE", 0, 20, "EXCUSE", null,
                null, "2026-09-05");
        assertThat(toOnly.content()).extracting(summary -> summary.id())
                .containsExactly("headman-gap");
        assertThat(toOnly.totalElements()).isEqualTo(1);

        HeadmanRequestPageResponse emptyRange = headmanRequestService.list(
                HEADMAN, "ARCHIVE", 0, 20, "EXCUSE", null,
                null, "2026-08-31");
        assertThat(emptyRange.content()).isEmpty();
        assertThat(emptyRange.totalElements()).isZero();
    }

    @Test
    void headmanArchiveSortUsesDecisionTimeBeforeStableKindAndIdTieBreakers() {
        seedHeadmanTicket("headman-decision-old", ExcuseTicketStatus.REJECTED,
                List.of(LocalDate.of(2026, 9, 7)), START.plusSeconds(1), START.plusSeconds(30),
                START.plusSeconds(2));
        seedHeadmanTicket("headman-decision-new", ExcuseTicketStatus.APPROVED,
                List.of(LocalDate.of(2026, 9, 8)), START.plusSeconds(2), START.plusSeconds(3),
                START.plusSeconds(4));

        HeadmanRequestPageResponse page = headmanRequestService.list(
                HEADMAN, "ARCHIVE", 0, 20, "EXCUSE", null, null, null);

        assertThat(page.content()).extracting(summary -> summary.id())
                .containsExactly("headman-decision-new", "headman-decision-old");
    }

    @Test
    void approvalChangesFreeAttendanceToExcused() {
        seedAbsent(61L);
        RequestDetail detail = service.submitExcuse(STUDENT, excuse(List.of(61L), "free-attendance-key", List.of()));
        AttendanceDocument current = attendanceRepository.findByLessonIdAndUserId(61L, STUDENT_ID).orElseThrow();
        current.setStatus(AttendanceStatus.FREE_ATTENDANCE);
        attendanceRepository.save(current);

        service.decideExcuse(headman(777L), detail.summary().id(), true, null);

        assertThat(attendanceRepository.findByLessonIdAndUserId(61L, STUDENT_ID).orElseThrow().getStatus())
                .isEqualTo(AttendanceStatus.EXCUSED);
    }

    @Test
    void ambiguousExcuseCommitRecoversPersistedDecisionWithoutDuplicateEvent() throws Exception {
        seedAbsent(82L);
        RequestDetail ticket = service.submitExcuse(STUDENT,
                excuse(List.of(82L), "ambiguous-excuse-key-0001", List.of()));
        clearOutbox();
        faultInjectingTransactionTemplate.resetTransactionExecutionCount();
        faultInjectingTransactionTemplate.failNextCommitAck();

        RequestDetail recovered = service.decideExcuse(headman(777L), ticket.summary().id(), true,
                "  accepted  ");

        assertThat(recovered.summary().status())
                .isEqualTo(ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus.APPROVED);
        assertThat(recovered.decision()).extracting(
                StudentRequestModels.Decision::decidedBy, StudentRequestModels.Decision::comment)
                .containsExactly(777L, "accepted");
        assertThat(faultInjectingTransactionTemplate.transactionExecutionCount()).isEqualTo(1);
        assertThat(attendanceRepository.findByLessonIdAndUserId(82L, STUDENT_ID).orElseThrow().getStatus())
                .isEqualTo(AttendanceStatus.EXCUSED);
        List<OutboxRecord> events = actualOutboxEvents("excuse.decided");
        assertThat(events).hasSize(1);
        assertThat(EventSchemaValidator.validate("excuse.decided.json", events.getFirst().payload())).isEmpty();
    }

    @Test
    void ambiguousLateCheckinCommitRecoversPersistedDecisionWithoutDuplicateEvent() throws Exception {
        seedAbsent(83L);
        RequestDetail request = service.submitLateCheckin(STUDENT,
                new LateCheckinSubmission(83L, "ambiguous-late-key-0001"));
        clearOutbox();
        faultInjectingTransactionTemplate.resetTransactionExecutionCount();
        faultInjectingTransactionTemplate.failNextCommitAck();

        RequestDetail recovered = service.decideLateCheckin(headman(777L), request.summary().id(), true);

        assertThat(recovered.summary().status())
                .isEqualTo(ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus.APPROVED);
        assertThat(recovered.decision().decidedBy()).isEqualTo(777L);
        assertThat(faultInjectingTransactionTemplate.transactionExecutionCount()).isEqualTo(1);
        assertThat(attendanceRepository.findByLessonIdAndUserId(83L, STUDENT_ID).orElseThrow().getStatus())
                .isEqualTo(AttendanceStatus.PRESENT);
        List<OutboxRecord> events = actualOutboxEvents("late_checkin.decided");
        assertThat(events).hasSize(1);
        assertThat(EventSchemaValidator.validate("late_checkin.decided.json", events.getFirst().payload())).isEmpty();
    }

    @Test
    void uncommittedTransientDecisionFailureStillRetriesOnce() {
        seedAbsent(84L);
        RequestDetail request = service.submitLateCheckin(STUDENT,
                new LateCheckinSubmission(84L, "transient-late-key-0001"));
        clearOutbox();
        faultInjectingTransactionTemplate.failNextBeforeTransaction();

        RequestDetail decided = service.decideLateCheckin(headman(777L), request.summary().id(), false,
                "Не подтверждено старостой");

        assertThat(decided.summary().status())
                .isEqualTo(ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus.REJECTED);
        assertThat(decided.decision().comment()).isEqualTo("Не подтверждено старостой");
        assertThat(lateCheckinRepository.findById(request.summary().id()).orElseThrow().getDecisionComment())
                .isEqualTo("Не подтверждено старостой");
        assertThat(actualOutboxEvents("late_checkin.decided")).hasSize(1);
    }

    @Test
    void excuseApprovalAfterTransferUsesCurrentLessonAndPreservesOriginalRequestEvidence() {
        for (boolean targetMarkExists : List.of(false, true)) {
            long sourceId = targetMarkExists ? 88L : 87L;
            long targetId = sourceId + 100L;
            seedAbsent(sourceId);
            RequestDetail detail = service.submitExcuse(STUDENT,
                    excuse(List.of(sourceId), "transferred-excuse-" + sourceId, List.of()));
            ExcuseTicket ticket = excuseRepository.findById(detail.summary().id()).orElseThrow();
            List<StudentLessonSnapshotDocument> originalSnapshots = ticket.getLessonSnapshots();
            ticket.setLessonIds(List.of(targetId));
            excuseRepository.save(ticket);
            if (targetMarkExists) {
                seedAbsent(targetId);
            }
            when(scheduleGrpcClient.requireAttendanceMutationReady(targetId, GROUP_ID))
                    .thenReturn(fullLesson(targetId).toBuilder().setDate("2026-09-09")
                            .setLessonNumber(3).setSubjectId(4321L).build());

            service.decideExcuse(HEADMAN, ticket.getId(), true, null);

            assertThat(attendanceRepository.findByLessonIdAndUserId(targetId, STUDENT_ID))
                    .hasValueSatisfying(mark -> {
                        assertThat(mark.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
                        assertThat(mark.getLessonDate()).isEqualTo(LocalDate.of(2026, 9, 9));
                        assertThat(mark.getLessonNumber()).isEqualTo(3);
                        assertThat(mark.getSubjectId()).isEqualTo(4321L);
                        assertThat(mark.getSemesterId()).isEqualTo(SEMESTER_ID);
                    });
            assertThat(attendanceRepository.findByLessonIdAndUserId(sourceId, STUDENT_ID).orElseThrow()
                    .getStatus()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(excuseRepository.findById(ticket.getId()).orElseThrow().getLessonSnapshots())
                    .usingRecursiveComparison().isEqualTo(originalSnapshots);
            assertThat(mongoTemplate.count(Query.query(Criteria.where("lesson_id").is(null)),
                    AttendanceDocument.class)).isZero();
        }
    }

    @Test
    void exactDecisionRepeatsKeepAttendanceTimestampBudgetAndSingleEvent() {
        for (boolean approved : List.of(true, false)) {
            long excuseLesson = approved ? 89L : 90L;
            long lateLesson = approved ? 91L : 92L;
            seedAbsent(excuseLesson);
            seedAbsent(lateLesson);
            RequestDetail ticket = service.submitExcuse(STUDENT,
                    excuse(List.of(excuseLesson), "repeat-excuse-" + excuseLesson, List.of()));
            RequestDetail late = service.submitLateCheckin(STUDENT,
                    new LateCheckinSubmission(lateLesson, "repeat-late-key-" + lateLesson));
            clearOutbox();
            RequestDetail firstExcuse = service.decideExcuse(HEADMAN, ticket.summary().id(), approved, " decided ");
            RequestDetail firstLate = service.decideLateCheckin(HEADMAN, late.summary().id(), approved, " decided ");
            AttendanceDocument excuseMark = attendanceRepository
                    .findByLessonIdAndUserId(excuseLesson, STUDENT_ID).orElseThrow();
            AttendanceDocument lateMark = attendanceRepository
                    .findByLessonIdAndUserId(lateLesson, STUDENT_ID).orElseThrow();
            StudentLateCheckinBudgetDocument budget = budgetRepository
                    .findByStudentIdAndSemesterId(STUDENT_ID, SEMESTER_ID).orElseThrow();
            clock.set(clock.instant().plusSeconds(30));

            assertThat(service.decideExcuse(HEADMAN, ticket.summary().id(), approved, "decided"))
                    .isEqualTo(firstExcuse);
            assertThat(service.decideLateCheckin(HEADMAN, late.summary().id(), approved, "decided"))
                    .isEqualTo(firstLate);
            assertThat(attendanceRepository.findByLessonIdAndUserId(excuseLesson, STUDENT_ID).orElseThrow())
                    .usingRecursiveComparison().isEqualTo(excuseMark);
            assertThat(attendanceRepository.findByLessonIdAndUserId(lateLesson, STUDENT_ID).orElseThrow())
                    .usingRecursiveComparison().isEqualTo(lateMark);
            assertThat(budgetRepository.findByStudentIdAndSemesterId(STUDENT_ID, SEMESTER_ID).orElseThrow())
                    .usingRecursiveComparison().isEqualTo(budget);
            assertThat(actualOutboxEvents("excuse.decided")).hasSize(1);
            assertThat(actualOutboxEvents("late_checkin.decided")).hasSize(1);
        }
    }

    @Test
    void decisionRepeatStillRequiresCurrentAuthorityAndWritableSemester() {
        seedAbsent(93L);
        seedAbsent(94L);
        RequestDetail ticket = service.submitExcuse(STUDENT,
                excuse(List.of(93L), "repeat-fenced-excuse", List.of()));
        RequestDetail late = service.submitLateCheckin(STUDENT,
                new LateCheckinSubmission(94L, "repeat-fenced-late"));
        service.decideExcuse(HEADMAN, ticket.summary().id(), true, null);
        service.decideLateCheckin(HEADMAN, late.summary().id(), true);
        clearOutbox();
        when(academicGrpcClient.isHeadman(777L, GROUP_ID)).thenReturn(
                HeadmanCheckResponse.newBuilder().setIsHeadman(false).build());
        assertThatThrownBy(() -> service.decideExcuse(HEADMAN, ticket.summary().id(), true, null))
                .isInstanceOf(ru.rutcampustrack.attendance.exception.AccessDeniedException.class);
        assertThatThrownBy(() -> service.decideLateCheckin(HEADMAN, late.summary().id(), true))
                .isInstanceOf(ru.rutcampustrack.attendance.exception.AccessDeniedException.class);
        when(academicGrpcClient.isHeadman(777L, GROUP_ID)).thenReturn(
                HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());
        mongoTemplate.save(ru.rutcampustrack.attendance.event.SemesterArchiveFenceDocument.builder()
                .id(Long.toString(SEMESTER_ID)).semesterId(SEMESTER_ID).barrierState("ARCHIVED").build());
        assertThatThrownBy(() -> service.decideExcuse(HEADMAN, ticket.summary().id(), true, null))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.decideLateCheckin(HEADMAN, late.summary().id(), true))
                .isInstanceOf(ConflictException.class);
        assertThat(actualOutboxEvents("excuse.decided")).isEmpty();
        assertThat(actualOutboxEvents("late_checkin.decided")).isEmpty();
    }

    @Test
    void terminalDecisionDoesNotReplayForAnotherActorOutcomeOrComment() {
        seedAbsent(85L);
        RequestDetail ticket = service.submitExcuse(STUDENT,
                excuse(List.of(85L), "terminal-mismatch-excuse-key", List.of()));
        clearOutbox();
        service.decideExcuse(headman(777L), ticket.summary().id(), true, "matching comment");

        assertThatThrownBy(() -> service.decideExcuse(
                headman(778L), ticket.summary().id(), true, "matching comment"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.decideExcuse(
                headman(777L), ticket.summary().id(), false, "matching comment"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.decideExcuse(
                headman(777L), ticket.summary().id(), true, "different comment"))
                .isInstanceOf(ConflictException.class);
        assertThat(actualOutboxEvents("excuse.decided")).hasSize(1);

        seedAbsent(86L);
        RequestDetail request = service.submitLateCheckin(STUDENT,
                new LateCheckinSubmission(86L, "terminal-mismatch-late-key"));
        clearOutbox();
        service.decideLateCheckin(headman(777L), request.summary().id(), true);

        assertThatThrownBy(() -> service.decideLateCheckin(
                headman(778L), request.summary().id(), true))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.decideLateCheckin(
                headman(777L), request.summary().id(), false))
                .isInstanceOf(ConflictException.class);
        assertThat(actualOutboxEvents("late_checkin.decided")).hasSize(1);
    }

    @Test
    void cancelAndDecisionRaceLeaveOneTerminalLateOutcomeAndOneOutboxEvent() throws Exception {
        seedAbsent(80L);
        RequestDetail request = service.submitLateCheckin(STUDENT,
                new LateCheckinSubmission(80L, "cancel-decision-race"));
        clearOutbox();
        pairWriteCoordinator.arm(STUDENT_ID, 80L);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> cancel = executor.submit(() -> {
                start.await();
                return service.cancel(STUDENT, request.summary().id());
            });
            Future<?> decide = executor.submit(() -> {
                start.await();
                return service.decideLateCheckin(headman(777L), request.summary().id(), true);
            });
            start.countDown();
            assertOneSuccessAndOneConflict(List.of(cancel, decide));
        }

        LateCheckinRequest terminal = lateCheckinRepository.findById(request.summary().id()).orElseThrow();
        assertThat(terminal.getStatus()).isIn(LateCheckinRequestStatus.CANCELLED,
                LateCheckinRequestStatus.APPROVED);
        List<OutboxRecord> events = actualOutboxEvents("late_checkin.decided");
        assertThat(events).hasSize(1);
        assertThat(EventSchemaValidator.validate("late_checkin.decided.json", events.getFirst().payload())).isEmpty();
    }

    @Test
    void approvalAndRealPairWriterRacePreservesPresent() throws Exception {
        seedAbsent(81L);
        RequestDetail ticket = service.submitExcuse(STUDENT,
                excuse(List.of(81L), "approval-present-race", List.of()));
        clearOutbox();
        pairWriteCoordinator.arm(STUDENT_ID, 81L);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> approval = executor.submit(() -> {
                start.await();
                return service.decideExcuse(headman(777L), ticket.summary().id(), true, null);
            });
            Future<?> presentWriter = executor.submit(() -> {
                start.await();
                writePresentThroughPairCoordinator(81L);
                return null;
            });
            start.countDown();
            approval.get(45, TimeUnit.SECONDS);
            presentWriter.get(45, TimeUnit.SECONDS);
        }

        assertThat(attendanceRepository.findByLessonIdAndUserId(81L, STUDENT_ID).orElseThrow().getStatus())
                .isEqualTo(AttendanceStatus.PRESENT);
        List<OutboxRecord> events = actualOutboxEvents("excuse.decided");
        assertThat(events).hasSize(1);
        assertThat(EventSchemaValidator.validate("excuse.decided.json", events.getFirst().payload())).isEmpty();
    }

    @Test
    void productionMongoOutboxInsertRollsBackWithTransactionFailure() {
        clearOutbox();

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            outboxStorage.save("student_request.rollback_probe", "{\"probe\":true}");
            throw new IllegalStateException("force rollback after outbox insert");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxStorage.findPending(10)).isEmpty();
    }

    @Test
    void rejectedAndCancelledManualLateRequestsDoNotRefundBudget() {
        seedAbsent(70L);
        seedAbsent(71L);
        seedAbsent(72L);
        seedAbsent(73L);
        RequestDetail rejected = service.submitLateCheckin(STUDENT, new LateCheckinSubmission(70L, "reject-late-key-001"));

        service.decideLateCheckin(headman(777L), rejected.summary().id(), false);

        service.submitLateCheckin(STUDENT, new LateCheckinSubmission(71L, "after-reject-key-0001"));
        RequestDetail cancelled = service.submitLateCheckin(STUDENT, new LateCheckinSubmission(72L, "cancel-late-key-001"));
        service.cancel(STUDENT, cancelled.summary().id());
        service.submitLateCheckin(STUDENT, new LateCheckinSubmission(73L, "after-cancel-key-001"));

        assertThat(budgetRepository.findByStudentIdAndSemesterId(STUDENT_ID, SEMESTER_ID))
                .get().extracting(StudentLateCheckinBudgetDocument::getUsed).isEqualTo(4);
        assertThat(lateCheckinRepository.findById(cancelled.summary().id()).orElseThrow().getStatus())
                .isEqualTo(LateCheckinRequestStatus.CANCELLED);
    }

    private void assertOneSuccessAndOneConflict(List<Future<?>> futures) throws Exception {
        int successes = 0;
        int conflicts = 0;
        for (Future<?> future : futures) {
            try {
                future.get(45, TimeUnit.SECONDS);
                successes++;
            } catch (ExecutionException error) {
                assertThat(error.getCause()).isInstanceOf(ConflictException.class);
                conflicts++;
            }
        }
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
    }

    private void clearOutbox() {
        mongoTemplate.getCollection(OUTBOX_COLLECTION).deleteMany(new Document());
    }

    private List<OutboxRecord> actualOutboxEvents(String eventType) {
        return outboxStorage.findPending(100).stream()
                .filter(event -> event.eventType().equals(eventType)).toList();
    }

    private void writePresentThroughPairCoordinator(long lessonId) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                transactionTemplate.execute(status -> {
                    pairWriteCoordinator.lock(SEMESTER_ID, STUDENT_ID, lessonId, GROUP_ID, clock.instant());
                    AttendanceDocument current = attendanceRepository
                            .findByLessonIdAndUserId(lessonId, STUDENT_ID).orElseThrow();
                    current.setStatus(AttendanceStatus.PRESENT);
                    current.setSource(AttendanceSource.STUDENT_GEO);
                    current.setUpdatedAt(clock.instant());
                    attendanceRepository.save(current);
                    return null;
                });
                return;
            } catch (RuntimeException error) {
                if (!isTransientTransactionError(error) || attempt == 4) {
                    throw error;
                }
                lastFailure = error;
                try {
                    Thread.sleep(100L << (attempt - 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while retrying pair writer", interrupted);
                }
            }
        }
        throw lastFailure;
    }

    private static boolean isTransientTransactionError(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }

    private void ensureIndexes() {
        mongoTemplate.indexOps("attendances").ensureIndex(new Index()
                .on("lesson_id", Sort.Direction.ASC).on("user_id", Sort.Direction.ASC)
                .unique().named("uniq_lesson_user"));
        mongoTemplate.indexOps("late_checkin_requests").ensureIndex(new Index()
                .on("student_id", Sort.Direction.ASC).on("lesson_id", Sort.Direction.ASC)
                .unique().partial(PartialIndexFilter.of(Criteria.where("status")
                        .is(LateCheckinRequestStatus.PENDING.name())))
                .named("uniq_lcr_pending_student_lesson"));
        mongoTemplate.indexOps("student_request_receipts").ensureIndex(new Index()
                .on("student_id", Sort.Direction.ASC).on("command_kind", Sort.Direction.ASC)
                .on("idempotency_key", Sort.Direction.ASC).unique()
                .named("uniq_student_request_receipt"));
        mongoTemplate.indexOps("student_late_checkin_budgets").ensureIndex(new Index()
                .on("student_id", Sort.Direction.ASC).on("semester_id", Sort.Direction.ASC)
                .unique().named("uniq_student_late_budget"));
        mongoTemplate.indexOps("request_attachments").ensureIndex(new Index()
                .on("request_id", Sort.Direction.ASC).on("owner_student_id", Sort.Direction.ASC)
                .on("position", Sort.Direction.ASC).named("idx_request_attachment_owner_position"));
    }

    private static Identity student(long userId) {
        return new Identity(userId, UserRole.STUDENT, GROUP_ID, false);
    }

    private static Identity headman(long userId) {
        return new Identity(userId, UserRole.STUDENT, GROUP_ID, true);
    }

    private ExcuseTicket seedHeadmanTicket(String id, ExcuseTicketStatus status,
                                           List<LocalDate> dates, Instant createdAt,
                                           Instant updatedAt, Instant decisionAt) {
        List<Long> lessonIds = IntStream.range(0, dates.size())
                .mapToObj(index -> 500L + index)
                .toList();
        List<StudentLessonSnapshotDocument> snapshots = IntStream.range(0, dates.size())
                .mapToObj(index -> StudentLessonSnapshotDocument.builder()
                        .lessonId(lessonIds.get(index)).groupId(GROUP_ID).subjectId(1000L + index)
                        .subjectName("Subject " + index).subjectType("LECTURE")
                        .semesterId(SEMESTER_ID).lessonNumber(index + 1).date(dates.get(index))
                        .startsAt(LocalTime.of(10, 0)).endsAt(LocalTime.of(11, 0))
                        .status("closed").build())
                .toList();
        return excuseRepository.save(ExcuseTicket.builder()
                .id(id).studentId(200L).groupId(GROUP_ID).studentName("Иванов Иван")
                .lessonIds(lessonIds).semesterId(SEMESTER_ID).lessonSnapshots(snapshots)
                .excuseType(ExcuseType.ILLNESS).status(status).decisionBy(decisionAt == null ? null : 777L)
                .decisionAt(decisionAt).createdAt(createdAt).updatedAt(updatedAt).build());
    }

    private void seedAbsent(long lessonId) {
        attendanceRepository.save(AttendanceDocument.builder()
                .lessonId(lessonId).userId(STUDENT_ID).groupId(GROUP_ID)
                .subjectId(1000L + lessonId).semesterId(SEMESTER_ID)
                .lessonNumber((int) lessonId).lessonDate(LocalDate.of(2026, 9, 7))
                .status(AttendanceStatus.ABSENT).source(AttendanceSource.AUTO_SCHEDULER)
                .createdAt(START).updatedAt(START).build());
    }

    private ExcuseSubmission excuse(List<Long> lessonIds, String key, List<AttachmentInput> attachments) {
        return new ExcuseSubmission(lessonIds, ExcuseType.ILLNESS, "  reason  ", attachments, key);
    }

    private LessonSnapshot snapshot(long lessonId) {
        return new LessonSnapshot(lessonId, GROUP_ID, 1000L + lessonId, "Subject " + (1000L + lessonId),
                "LECTURE", SEMESTER_ID, (int) lessonId, LocalDate.of(2026, 9, 7),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "closed", false);
    }

    private LessonInfo compactLesson(long lessonId) {
        return LessonInfo.newBuilder().setLessonId(lessonId).setGroupId(GROUP_ID)
                .setSubjectId(1000L + lessonId).setLessonNumber((int) lessonId)
                .setDate("2026-09-07").setStartsAt("2026-09-07T10:00:00").build();
    }

    private LessonResponse fullLesson(long lessonId) {
        return LessonResponse.newBuilder().setId(lessonId).setGroupId(GROUP_ID)
                .setSubjectId(1000L + lessonId).setLessonNumber((int) lessonId)
                .setSemesterId(SEMESTER_ID)
                .setDate("2026-09-07").setStartTime("10:00").setEndTime("11:00")
                .setStatus(lessonStatuses.getOrDefault(lessonId, "closed"))
                .setIsBlockedByHeadman(blockedLessonIds.contains(lessonId)).build();
    }

    private static byte[] pdfBytes(int size) {
        byte[] bytes = new byte[size];
        byte[] header = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }
}
