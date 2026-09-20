package ru.rutcampustrack.attendance.studentrequest;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.Binary;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestKind;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
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
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentInput;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.ExcuseSubmission;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.LateCheckinSubmission;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.LessonSnapshot;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.RequestDetail;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.RequestPage;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLateCheckinBudgetDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentRequestReceiptDocument;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
@Import({StudentRequestService.class, PairWriteCoordinator.class,
        StudentRequestDomainIT.TestConfig.class})
class StudentRequestDomainIT {

    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final String DATABASE = "rct_student_role_02_requests_" + RUN_ID;
    private static final String CONTAINER_NAME = "rct-student-role-02-requests-" + RUN_ID;
    private static final long STUDENT_ID = 100L;
    private static final long GROUP_ID = 10L;
    private static final long SEMESTER_ID = 30L;
    private static final Instant START = Instant.parse("2026-09-07T08:00:00Z");
    private static final Identity STUDENT = new Identity(STUDENT_ID, UserRole.STUDENT, GROUP_ID, false);

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
        @Bean
        MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
            return new MongoTransactionManager(factory);
        }

        @Bean
        TransactionTemplate transactionTemplate(MongoTransactionManager manager) {
            return new TransactionTemplate(manager);
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

    @jakarta.annotation.Resource
    StudentRequestService service;
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

    @MockitoBean
    ScheduleGrpcClient scheduleGrpcClient;
    @MockitoBean
    AcademicGrpcClient academicGrpcClient;
    @MockitoBean
    SemesterCacheService semesterCacheService;
    @MockitoBean
    ExcuseEventPublisher excuseEventPublisher;
    @MockitoBean
    LateCheckinEventPublisher lateCheckinEventPublisher;

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
        reset(scheduleGrpcClient, academicGrpcClient, semesterCacheService,
                excuseEventPublisher, lateCheckinEventPublisher);
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
                .find(Filters.eq("_id", detail.summary().id())).first();
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
    void attachmentExpiresLogicallyAndDownloadClearsBytes() {
        seedAbsent(42L);
        RequestDetail detail = service.submitExcuse(STUDENT, excuse(List.of(42L), "expiry-file-key-0001", List.of(
                new AttachmentInput("proof.pdf", "application/pdf", pdfBytes(64)))));
        String attachmentId = attachmentRepository.findAll().getFirst().getId();

        assertThat(service.download(STUDENT, detail.summary().id(), attachmentId).bytes()).hasSize(64);

        clock.set(detail.attachments().getFirst().expiresAt());
        assertThatThrownBy(() -> service.download(STUDENT, detail.summary().id(), attachmentId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(error -> assertThat(((org.springframework.web.server.ResponseStatusException) error)
                        .getStatusCode().value()).isEqualTo(410));
        RequestAttachmentDocument expired = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(expired.getState()).isEqualTo(AttachmentState.EXPIRED);
        assertThat(expired.getData()).isNull();
    }

    @Test
    void mixedOwnerListAndOptionsExposeBothPendingKindsWithoutPeerData() {
        seedAbsent(50L);
        seedAbsent(51L);
        RequestDetail excuse = service.submitExcuse(STUDENT, excuse(List.of(50L), "mixed-excuse-key-01", List.of()));
        RequestDetail late = service.submitLateCheckin(STUDENT, new LateCheckinSubmission(51L, "mixed-late-key-0001"));

        RequestPage ownerPage = service.list(STUDENT, RequestBucket.OPEN, 0, 20);
        assertThat(ownerPage.totalElements()).isEqualTo(2);
        assertThat(ownerPage.content()).extracting(summary -> summary.kind())
                .containsExactlyInAnyOrder(StudentRequestKind.EXCUSE, StudentRequestKind.LATE_CHECKIN);

        assertThatThrownBy(() -> service.get(student(200L), excuse.summary().id()))
                .isInstanceOf(ru.rutcampustrack.attendance.exception.AccessDeniedException.class);
        assertThat(service.list(student(200L), RequestBucket.OPEN, 0, 20).content()).isEmpty();

        LessonSnapshot snapshot = snapshot(50L);
        var options = service.options(STUDENT, List.of(snapshot));
        assertThat(options.lessons()).singleElement().satisfies(option -> {
            assertThat(option.pendingRequests()).extracting(ref -> ref.kind())
                    .containsExactly(StudentRequestKind.EXCUSE);
            assertThat(option.excuseEligible()).isFalse();
        });
        assertThat(late.summary().kind()).isEqualTo(StudentRequestKind.LATE_CHECKIN);
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
