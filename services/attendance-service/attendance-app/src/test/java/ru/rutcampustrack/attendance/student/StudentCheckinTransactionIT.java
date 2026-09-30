package ru.rutcampustrack.attendance.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import com.networknt.schema.ValidationMessage;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
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
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.event.SemesterArchiveFence;
import ru.rutcampustrack.attendance.event.SemesterArchiveFenceDocument;
import ru.rutcampustrack.attendance.geofence.GeofenceService;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.checkin.AttendanceWritePortImpl;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinService;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.marking.MarkingService;
import ru.rutcampustrack.attendance.marking.AttendanceAttachmentService;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.studentrequest.AttachmentState;
import ru.rutcampustrack.attendance.studentrequest.RequestAttachmentRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.attendance.student.StudentCheckinException.Code;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.*;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;
import ru.rutcampustrack.attendance.events.EventSchemaValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import({
        StudentCheckinService.class,
        SemesterArchiveFence.class,
        PairWriteCoordinator.class,
        AttendanceWritePortImpl.class,
        LateCheckinService.class,
        MarkingService.class,
        AttendanceAttachmentService.class,
        StudentCheckinTransactionIT.TestConfig.class
})
class StudentCheckinTransactionIT {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
            return new MongoTransactionManager(factory);
        }

        @Bean
        TransactionTemplate transactionTemplate(MongoTransactionManager manager) {
            return new TransactionTemplate(manager);
        }

        @Bean
        MutableClock clock() {
            return new MutableClock(Instant.parse("2026-09-06T07:00:00Z"), ZoneId.of("Europe/Moscow"));
        }

        @Bean("grpcTaskExecutor")
        TaskExecutor grpcTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = new AtomicReference<>(instant);
            this.zone = zone;
        }

        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId value) { return new MutableClock(instant(), value); }
        @Override public Instant instant() { return instant.get(); }
    }

    @jakarta.annotation.Resource
    StudentCheckinService service;
    @jakarta.annotation.Resource
    AttendanceRepository attendanceRepository;
    @jakarta.annotation.Resource
    LateCheckinRepository lateCheckinRepository;
    @jakarta.annotation.Resource
    CheckinPairStateRepository pairRepository;
    @jakarta.annotation.Resource
    StudentCheckinReceiptRepository receiptRepository;
    @jakarta.annotation.Resource
    MongoTemplate mongoTemplate;
    @jakarta.annotation.Resource
    MutableClock clock;
    @jakarta.annotation.Resource
    PairWriteCoordinator pairWriteCoordinator;
    @jakarta.annotation.Resource
    TransactionTemplate transactionTemplate;
    @jakarta.annotation.Resource
    LateCheckinService lateCheckinService;
    @jakarta.annotation.Resource
    MarkingService markingService;
    @jakarta.annotation.Resource
    AttendanceWritePortImpl attendanceWritePort;
    @jakarta.annotation.Resource
    RequestAttachmentRepository attachmentRepository;

    @MockitoBean
    GeofenceService geofence;
    @MockitoBean
    AttendanceEventPublisher attendanceEvents;
    @MockitoBean
    LateCheckinEventPublisher lateCheckinEvents;
    @MockitoBean
    BusinessMetrics metrics;
    @MockitoBean
    ScheduleGrpcClient scheduleGrpcClient;
    @MockitoBean
    AcademicGrpcClient academicGrpcClient;
    @MockitoBean
    RequestContext requestContext;
    @MockitoBean
    SemesterCacheService semesterCacheService;

    private final Identity student = new Identity(100, "STUDENT", 10L, false, "Иван Иванов", false);

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("attendances");
        mongoTemplate.dropCollection("late_checkin_requests");
        mongoTemplate.dropCollection("student_checkin_pairs");
        mongoTemplate.dropCollection("student_checkin_receipts");
        mongoTemplate.dropCollection("attendance_outbox");
        mongoTemplate.dropCollection("request_attachments");
        mongoTemplate.dropCollection("semester_archive_fences");
        mongoTemplate.indexOps(AttendanceDocument.class).ensureIndex(new Index()
                .on("lesson_id", Sort.Direction.ASC).on("user_id", Sort.Direction.ASC)
                .unique().named("uniq_lesson_user"));
        mongoTemplate.indexOps("late_checkin_requests").ensureIndex(new Index()
                .on("student_id", Sort.Direction.ASC).on("lesson_id", Sort.Direction.ASC)
                .unique().partial(PartialIndexFilter.of(
                        org.springframework.data.mongodb.core.query.Criteria.where("status").is("PENDING")))
                .named("uniq_lcr_pending_student_lesson"));
        mongoTemplate.indexOps("student_checkin_receipts").ensureIndex(new Index()
                .on("student_id", Sort.Direction.ASC).on("lesson_id", Sort.Direction.ASC)
                .on("idempotency_key", Sort.Direction.ASC).unique()
                .named("uniq_student_lesson_idempotency_key"));
        clock.set(Instant.parse("2026-09-06T07:00:00Z"));
        reset(geofence, attendanceEvents, lateCheckinEvents, metrics,
                scheduleGrpcClient, academicGrpcClient, requestContext, semesterCacheService);
        when(metrics.checkinCounter(anyString())).thenReturn(mock(Counter.class));
        when(metrics.lateCheckinCreatedCounter()).thenReturn(mock(Counter.class));
        LessonResponse scheduleLesson = LessonResponse.newBuilder()
                .setId(1L).setGroupId(10L).setSubjectId(20L).setSemesterId(30L).setLessonNumber(2)
                .setDate("2026-09-06").setStartTime("10:00").setEndTime("11:00")
                .setStatus("active").build();
        when(scheduleGrpcClient.getLessonById(1L)).thenReturn(scheduleLesson);
        when(scheduleGrpcClient.requireAttendanceMutationReady(1L, 10L)).thenReturn(scheduleLesson);
        when(academicGrpcClient.getSubjectsByIds(List.of(20L))).thenReturn(java.util.Map.of(20L, "Предмет"));
        when(academicGrpcClient.isHeadman(any(), any()))
                .thenReturn(HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());
        when(semesterCacheService.getActiveSemesterId()).thenReturn(30L);
    }

    @Test
    void archivedSemesterFenceRejectsGeoCheckinWithoutAttendanceOrRequestWrites() {
        mongoTemplate.insert(SemesterArchiveFenceDocument.builder()
                .id("30")
                .semesterId(30L)
                .stateVersion(1L)
                .operationId("11111111-1111-4111-8111-111111111111")
                .barrierState("ARCHIVE_PREPARING")
                .writeFence(0L)
                .updatedAt(clock.instant())
                .build());
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);

        assertThatThrownBy(() -> service.checkin(student, lesson(), "archive-geo-key-0001",
                new Coordinates(55.75, 37.61)))
                .isInstanceOf(ConflictException.class);

        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(pairRepository.findAll()).isEmpty();
        assertThat(receiptRepository.findAll()).isEmpty();
        assertThat(lateCheckinRepository.findAll()).isEmpty();
        verifyNoInteractions(attendanceEvents, lateCheckinEvents);
    }

    @Test
    void unavailableGeoPersistsOnePendingRequestReceiptCooldownAndOutboxCallAtomically() {
        Ack ack = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));

        assertThat(ack.outcome()).isEqualTo(Outcome.PENDING_CONFIRMATION);
        assertThat(lateCheckinRepository.findAll()).singleElement()
                .satisfies(request -> {
                    assertThat(request.getStatus()).isEqualTo(LateCheckinRequestStatus.PENDING);
                    assertThat(request.getId()).isEqualTo(ack.request().id());
                });
        assertThat(receiptRepository.findAll()).hasSize(1);
        List<CheckinPairStateDocument> pairStates = pairRepository.findAll();
        assertThat(pairStates).hasSize(2);
        assertThat(pairStates).filteredOn(state -> state.getStudentId() == 100L).singleElement()
                .extracting(CheckinPairStateDocument::getRetryAt)
                .isEqualTo(Instant.parse("2026-09-06T07:05:00Z"));
        assertThat(pairStates).filteredOn(state -> state.getStudentId() == 0L).singleElement()
                .satisfies(state -> assertThat(state.getId()).isEqualTo(
                        PairWriteCoordinator.lessonFenceId(1L)));
        verify(lateCheckinEvents).publishRequested(any(), any(), anyInt(), any(), any());
    }

    @Test
    void readOnlyCheckinAndReplayRejectBothGeoVariantsBeforeAnySideEffect() {
        Identity readOnly = new Identity(100, "STUDENT", 10L, false, "Иван Иванов", true);

        for (Geo geo : List.of(
                new Coordinates(55.75, 37.61),
                new Unavailable("TIMEOUT"))) {
            assertThatThrownBy(() -> service.checkin(
                    readOnly, lesson(), "readonly-key-0001", geo))
                    .isInstanceOf(StudentCheckinException.class)
                    .satisfies(error -> assertThat(((StudentCheckinException) error).code())
                            .isEqualTo(Code.OUT_OF_SCOPE));
            assertThatThrownBy(() -> service.replay(
                    readOnly, 1L, "readonly-key-0001", geo))
                    .isInstanceOf(StudentCheckinException.class)
                    .satisfies(error -> assertThat(((StudentCheckinException) error).code())
                            .isEqualTo(Code.OUT_OF_SCOPE));
        }

        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(pairRepository.findAll()).isEmpty();
        assertThat(receiptRepository.findAll()).isEmpty();
        assertThat(lateCheckinRepository.findAll()).isEmpty();
        verifyNoInteractions(geofence, attendanceEvents, lateCheckinEvents,
                scheduleGrpcClient, academicGrpcClient, semesterCacheService);
    }

    @Test
    void activePendingRequestBlocksGeoRetriesBeforeAndAfterCooldownWithoutExtendingIt() {
        Ack first = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        clock.set(Instant.parse("2026-09-06T07:04:59.999Z"));
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);

        Ack beforeCooldownExpiry = service.checkin(
                student, lesson(), "key-000000000002", new Coordinates(55.75, 37.61));
        assertThat(beforeCooldownExpiry.outcome()).isEqualTo(Outcome.PENDING_CONFIRMATION);
        assertThat(beforeCooldownExpiry.request().id()).isEqualTo(first.request().id());
        assertThat(beforeCooldownExpiry.retryAt()).isNull();

        clock.set(Instant.parse("2026-09-06T07:10:00Z"));
        Ack afterCooldownExpiry = service.checkin(
                student, lesson(), "key-000000000003", new Coordinates(55.75, 37.61));
        assertThat(afterCooldownExpiry.outcome()).isEqualTo(Outcome.PENDING_CONFIRMATION);
        assertThat(afterCooldownExpiry.request().id()).isEqualTo(first.request().id());
        assertThat(afterCooldownExpiry.retryAt()).isNull();
        assertThat(service.checkin(
                student, lesson(), "key-000000000003", new Coordinates(55.75, 37.61)))
                .isEqualTo(afterCooldownExpiry);
        assertThat(lateCheckinRepository.findAll()).hasSize(1);
        assertThat(lateCheckinRepository.findById(first.request().id()).orElseThrow())
                .satisfies(request -> {
                    assertThat(request.getOrigin()).isEqualTo(LateCheckinRequestOrigin.AUTO_GEO_FAILURE);
                    assertThat(request.getSubjectName()).isEqualTo("Алгебра");
                    assertThat(request.getSubjectType()).isEqualTo("SEMINAR");
                    assertThat(request.getStartsAt()).isEqualTo(LocalTime.of(10, 0));
                    assertThat(request.getEndsAt()).isEqualTo(LocalTime.of(11, 0));
                });
        assertThat(receiptRepository.findAll()).hasSize(3);
        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(pairRepository.findById(PairWriteCoordinator.pairId(100L, 1L)).orElseThrow().getRetryAt())
                .isEqualTo(first.retryAt());
        verifyNoInteractions(geofence, attendanceEvents);
        verify(lateCheckinEvents, times(1)).publishRequested(any(), any(), anyInt(), any(), any());
    }

    @Test
    void rejectedRequestGetsNewIdAtBoundary() {
        Ack first = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        var request = lateCheckinRepository.findById(first.request().id()).orElseThrow();
        request.setStatus(LateCheckinRequestStatus.REJECTED);
        request.setUpdatedAt(Instant.parse("2026-09-06T07:01:00Z"));
        lateCheckinRepository.save(request);
        clock.set(Instant.parse("2026-09-06T07:05:00Z"));

        Ack second = service.checkin(student, lesson(), "key-000000000002", new Unavailable("TIMEOUT"));
        assertThat(second.request().id()).isNotEqualTo(first.request().id());
        assertThat(lateCheckinRepository.findAll()).hasSize(2);
    }

    @Test
    void successfulGeoRetryRemainsPendingUntilHeadmanDecision() {
        Ack first = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        clock.set(Instant.parse("2026-09-06T07:05:00Z"));
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);

        Ack second = service.checkin(student, lesson(), "key-000000000002", new Coordinates(55.75, 37.61));

        assertThat(second.outcome()).isEqualTo(Outcome.PENDING_CONFIRMATION);
        assertThat(second.request().id()).isEqualTo(first.request().id());
        assertThat(second.retryAt()).isNull();
        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(receiptRepository.findAll()).hasSize(2);
        assertThat(lateCheckinRepository.findById(first.request().id()).orElseThrow().getStatus())
                .isEqualTo(LateCheckinRequestStatus.PENDING);
        assertThat(pairRepository.findById(PairWriteCoordinator.pairId(100L, 1L)).orElseThrow().getRetryAt())
                .isEqualTo(first.retryAt());
        verifyNoInteractions(geofence, attendanceEvents);
        verify(lateCheckinEvents, times(1)).publishRequested(any(), any(), anyInt(), any(), any());
        verify(lateCheckinEvents, never()).publishDecided(any(), any(), anyInt(), any(), any());
    }

    @Test
    void replayReturnsOriginalAckAfterWindowClosedAndDifferentPayloadIsRejected() {
        Ack first = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        clock.set(Instant.parse("2026-09-06T08:30:00Z"));

        Ack replay = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        assertThat(replay).isEqualTo(first);
        assertThatThrownBy(() -> service.checkin(
                student, lesson(), "key-000000000001", new Unavailable("POSITION_UNAVAILABLE")))
                .isInstanceOfSatisfying(StudentCheckinException.class,
                        error -> assertThat(error.code()).isEqualTo(Code.IDEMPOTENCY_PAYLOAD_MISMATCH));
    }

    @Test
    void replayFromMongoPreservesAckWhenClockHasNanoseconds() {
        clock.set(Instant.parse("2026-09-06T07:00:00.123456789Z"));

        Ack first = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        Ack replay = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));

        assertThat(first.serverNow()).isEqualTo(Instant.parse("2026-09-06T07:00:00.123Z"));
        assertThat(replay).isEqualTo(first);
    }

    @Test
    void exactReceiptReplaysButNewKeysAreBlockedDuringArchiveTransitions() {
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);
        service.checkin(student, lesson(), "key-000000000001", new Coordinates(55.75, 37.61));
        var pairId = PairWriteCoordinator.pairId(100L, 1L);
        var lessonFenceId = PairWriteCoordinator.lessonFenceId(1L);
        var pairFenceBefore = pairRepository.findById(pairId).orElseThrow();
        var lessonFenceBefore = pairRepository.findById(lessonFenceId).orElseThrow();

        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is("30")), new Update()
                .set("state_version", 1L)
                .set("operation_id", "11111111-1111-4111-8111-111111111111")
                .set("barrier_state", "ARCHIVE_PREPARING")
                .set("updated_at", clock.instant()), SemesterArchiveFenceDocument.class);

        Ack prepareReplay = service.checkin(student, lesson(), "key-000000000001",
                new Coordinates(55.75, 37.61));
        assertThat(prepareReplay.outcome()).isEqualTo(Outcome.PRESENT);
        assertThatThrownBy(() -> service.checkin(student, lesson(), "key-000000000002",
                new Unavailable("TIMEOUT")))
                .isInstanceOf(ConflictException.class);

        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is("30")),
                new Update().set("barrier_state", "ARCHIVE_SEALED"), SemesterArchiveFenceDocument.class);

        Ack sealedReplay = service.checkin(student, lesson(), "key-000000000001",
                new Coordinates(55.75, 37.61));
        assertThat(sealedReplay.outcome()).isEqualTo(Outcome.PRESENT);
        assertThatThrownBy(() -> service.checkin(student, lesson(), "key-000000000003",
                new Unavailable("TIMEOUT")))
                .isInstanceOf(ConflictException.class);

        assertThat(pairRepository.findById(pairId).orElseThrow().getFence())
                .isEqualTo(pairFenceBefore.getFence());
        assertThat(pairRepository.findById(lessonFenceId).orElseThrow().getFence())
                .isEqualTo(lessonFenceBefore.getFence());
        assertThat(receiptRepository.findAll()).hasSize(1);
        verifyNoMoreInteractions(lateCheckinEvents);
    }

    @Test
    void manualHeadmanAbsenceRejectsBeforeGeofenceAndWithoutPairMutation() {
        attendanceRepository.save(AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L)
                .status(AttendanceStatus.ABSENT).source(AttendanceSource.HEADMAN)
                .createdAt(clock.instant()).updatedAt(clock.instant()).build());

        assertThatThrownBy(() -> service.checkin(
                student, lesson(), "key-000000000001", new Coordinates(55.75, 37.61)))
                .isInstanceOfSatisfying(StudentCheckinException.class,
                        error -> assertThat(error.code()).isEqualTo(Code.MANUAL_ABSENCE_REQUIRES_APPEAL));
        verifyNoInteractions(geofence);
        assertThat(pairRepository.findAll()).isEmpty();
    }

    @Test
    void outboxFailureRollsBackAttendancePairAndReceipt() {
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);
        doThrow(new IllegalStateException("outbox insert failed")).when(attendanceEvents).publishMarked(any());

        assertThatThrownBy(() -> service.checkin(
                student, lesson(), "key-000000000001", new Coordinates(55.75, 37.61)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(pairRepository.findAll()).isEmpty();
        assertThat(receiptRepository.findAll()).isEmpty();
    }

    @Test
    void realOutboxCommitsOnceAndReplayDoesNotDuplicateEvent() {
        MongoOutboxStorage outbox = new MongoOutboxStorage(mongoTemplate, "attendance_outbox");
        StudentCheckinService realService = serviceWith(outbox);

        Ack first = realService.checkin(
                student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        Ack replay = realService.checkin(
                student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));

        assertThat(replay).isEqualTo(first);
        assertThat(outbox.findPending(10)).singleElement()
                .satisfies(record -> assertThat(record.eventType()).isEqualTo("late_checkin.requested"));
        assertThat(lateCheckinRepository.findAll()).hasSize(1);
        assertThat(receiptRepository.findAll()).hasSize(1);
    }

    @Test
    void realOutboxInsertFailureRollsBackAndSameCommandCanSucceedLater() {
        FailingAfterInsertOutbox outbox = new FailingAfterInsertOutbox(mongoTemplate);
        StudentCheckinService realService = serviceWith(outbox);
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);
        outbox.failAfterSave = true;

        assertThatThrownBy(() -> realService.checkin(
                student, lesson(), "key-000000000001", new Coordinates(55.75, 37.61)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after insert");
        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(pairRepository.findAll()).isEmpty();
        assertThat(receiptRepository.findAll()).isEmpty();
        assertThat(outbox.findPending(10)).isEmpty();

        outbox.failAfterSave = false;
        Ack retry = realService.checkin(
                student, lesson(), "key-000000000001", new Coordinates(55.75, 37.61));
        assertThat(retry.outcome()).isEqualTo(Outcome.PRESENT);
        assertThat(outbox.findPending(10)).singleElement()
                .satisfies(record -> {
                    assertThat(record.eventType()).isEqualTo("attendance.marked");
                    Set<ValidationMessage> errors = EventSchemaValidator.validate(
                            "attendance.marked.json", record.payload());
                    assertThat(errors)
                            .as("canonical attendance.marked payload must match event schema")
                            .isEmpty();
                });
    }

    @Test
    void botDecisionUsesCanonicalActorAndAuthoritativeGroupCheckInMongoTransaction() {
        LateCheckinRequest pending = seedLateCheckinRequest(LateCheckinRequestStatus.PENDING);
        when(academicGrpcClient.isHeadman(42L, 10L))
                .thenReturn(HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());

        lateCheckinService.applyDecision(pending.getId(), 42L, true);

        assertThat(lateCheckinRepository.findById(pending.getId()).orElseThrow())
                .satisfies(request -> {
                    assertThat(request.getStatus()).isEqualTo(LateCheckinRequestStatus.APPROVED);
                    assertThat(request.getDecisionBy()).isEqualTo(42L);
                });
        assertThat(attendanceRepository.findAll()).singleElement()
                .satisfies(attendance -> assertThat(attendance.getMarkedBy()).isEqualTo(42L));
        verify(academicGrpcClient).isHeadman(42L, 10L);
        verify(lateCheckinEvents).publishDecided(any(), any(), anyInt(), any(), any());
    }

    @Test
    void legacyLateApprovalPreservesExistingPresentProvenanceAndTimestamps() {
        Instant before = Instant.parse("2026-09-06T06:59:00Z");
        AttendanceDocument existing = attendanceRepository.save(AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(20L).semesterId(30L)
                .lessonNumber(2).lessonDate(LocalDate.of(2026, 9, 6))
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(before).updatedAt(before).build());
        LateCheckinRequest pending = seedLateCheckinRequest(LateCheckinRequestStatus.PENDING);

        lateCheckinService.applyDecision(pending.getId(), 777L, true);

        assertThat(attendanceRepository.findByLessonIdAndUserId(1L, 100L)).get()
                .satisfies(attendance -> {
                    assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(attendance.getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
                    assertThat(attendance.getMarkedBy()).isNull();
                    assertThat(attendance.getCreatedAt()).isEqualTo(existing.getCreatedAt());
                    assertThat(attendance.getUpdatedAt()).isEqualTo(existing.getUpdatedAt());
                });
    }

    @Test
    void botDecisionForForeignGroupHeadmanMakesNoMongoWrites() {
        LateCheckinRequest pending = seedLateCheckinRequest(LateCheckinRequestStatus.PENDING);
        when(academicGrpcClient.isHeadman(42L, 10L))
                .thenReturn(HeadmanCheckResponse.newBuilder().setIsHeadman(false).build());

        assertThatThrownBy(() -> lateCheckinService.applyDecision(pending.getId(), 42L, true))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(lateCheckinRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(LateCheckinRequestStatus.PENDING);
        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(pairRepository.findAll()).isEmpty();
        verifyNoInteractions(lateCheckinEvents);
    }

    @Test
    void botDecisionForCancelledRequestRemainsNoOpWithoutAuthorizationOrWrites() {
        LateCheckinRequest cancelled = seedLateCheckinRequest(LateCheckinRequestStatus.CANCELLED);

        lateCheckinService.applyDecision(cancelled.getId(), 42L, true);

        assertThat(lateCheckinRepository.findById(cancelled.getId()).orElseThrow().getStatus())
                .isEqualTo(LateCheckinRequestStatus.CANCELLED);
        assertThat(attendanceRepository.findAll()).isEmpty();
        assertThat(pairRepository.findAll()).isEmpty();
        verifyNoInteractions(academicGrpcClient, lateCheckinEvents);
    }

    @Test
    void concurrentSameKeyConvergesOnOneAckAndOnePendingEvent() throws Exception {
        int count = 12;
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(count)) {
            List<Callable<Ack>> tasks = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                tasks.add(() -> {
                    start.await();
                    return service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
                });
            }
            List<Future<Ack>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            List<Ack> results = new ArrayList<>();
            for (Future<Ack> future : futures) results.add(future.get());
            assertThat(results).allMatch(result -> result.equals(results.getFirst()));
        }
        assertThat(lateCheckinRepository.findAll()).hasSize(1);
        assertThat(receiptRepository.findAll()).hasSize(1);
        verify(lateCheckinEvents, times(1)).publishRequested(any(), any(), anyInt(), any(), any());
    }

    @Test
    void staleApproveAfterGeoCancellationIsNoEffect() {
        Ack pending = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        clock.set(Instant.parse("2026-09-06T07:05:00Z"));
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);
        service.checkin(student, lesson(), "key-000000000002", new Coordinates(55.75, 37.61));

        lateCheckinService.applyDecision(pending.request().id(), 777L, true);

        assertThat(lateCheckinRepository.findById(pending.request().id()).orElseThrow().getStatus())
                .isEqualTo(LateCheckinRequestStatus.CANCELLED);
        assertThat(attendanceRepository.findByLessonIdAndUserId(1L, 100L)).get()
                .satisfies(attendance -> {
                    assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(attendance.getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
                    assertThat(attendance.getMarkedBy()).isNull();
                });
        verify(lateCheckinEvents, times(1)).publishDecided(any(), any(), anyInt(), any(), any());
    }

    @Test
    void concurrentGeoAndApproveConvergeWithoutMixedProvenance() throws Exception {
        Ack pending = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        clock.set(Instant.parse("2026-09-06T07:05:00Z"));
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);

        runConcurrent(
                () -> service.checkin(student, lesson(), "key-000000000002", new Coordinates(55.75, 37.61)),
                () -> { lateCheckinService.applyDecision(pending.request().id(), 777L, true); return null; }
        );

        var request = lateCheckinRepository.findById(pending.request().id()).orElseThrow();
        var attendance = attendanceRepository.findByLessonIdAndUserId(1L, 100L).orElseThrow();
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        if (request.getStatus() == LateCheckinRequestStatus.CANCELLED) {
            assertThat(attendance.getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
            assertThat(attendance.getMarkedBy()).isNull();
        } else {
            assertThat(request.getStatus()).isEqualTo(LateCheckinRequestStatus.APPROVED);
            assertThat(attendance.getSource()).isEqualTo(AttendanceSource.LATE_CHECKIN);
            assertThat(attendance.getMarkedBy()).isEqualTo(777L);
        }
    }

    @Test
    void concurrentGeoAndRejectNeverOverwriteGeoProvenance() throws Exception {
        Ack pending = service.checkin(student, lesson(), "key-000000000001", new Unavailable("TIMEOUT"));
        clock.set(Instant.parse("2026-09-06T07:05:00Z"));
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);

        runConcurrent(
                () -> service.checkin(student, lesson(), "key-000000000002", new Coordinates(55.75, 37.61)),
                () -> { lateCheckinService.applyDecision(pending.request().id(), 777L, false); return null; }
        );

        var request = lateCheckinRepository.findById(pending.request().id()).orElseThrow();
        assertThat(request.getStatus()).isIn(
                LateCheckinRequestStatus.CANCELLED, LateCheckinRequestStatus.REJECTED);
        assertThat(attendanceRepository.findByLessonIdAndUserId(1L, 100L)).get()
                .satisfies(attendance -> {
                    assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(attendance.getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
                    assertThat(attendance.getMarkedBy()).isNull();
                });
        verify(lateCheckinEvents, times(1)).publishDecided(any(), any(), anyInt(), any(), any());
    }

    @Test
    void concurrentGeoAndManualAbsenceNeverLeaveStaleGeoOverwrite() throws Exception {
        clock.set(Instant.parse("2026-09-06T07:00:00Z"));
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(777L);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(academicGrpcClient.getGroupMembers(10L)).thenReturn(GroupMembersResponse.newBuilder()
                .addStudents(StudentInfo.newBuilder().setUserId(100L).build()).build());
        when(academicGrpcClient.getGroupMembers(10L,
                LocalDate.of(2026, 9, 6), 30L)).thenReturn(
                GroupMembersResponse.newBuilder()
                        .setAsOfDate("2026-09-06").setSemesterId(30L)
                        .addStudents(StudentInfo.newBuilder().setUserId(100L).build()).build());

        runConcurrent(
                () -> service.checkin(student, lesson(), "key-000000000001", new Coordinates(55.75, 37.61)),
                () -> markingService.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.ABSENT)),
                Code.MANUAL_ABSENCE_REQUIRES_APPEAL
        );

        var attendance = attendanceRepository.findByLessonIdAndUserId(1L, 100L).orElseThrow();
        if (attendance.getStatus() == AttendanceStatus.ABSENT) {
            assertThat(attendance.getSource()).isEqualTo(AttendanceSource.HEADMAN);
            assertThat(attendance.getMarkedBy()).isEqualTo(777L);
        } else {
            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(attendance.getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
            assertThat(attendance.getMarkedBy()).isNull();
        }
    }

    @Test
    void manualDecisionAndGeoWritersClearJournalBlobButRetainRequestOwnedEvidence() {
        Instant now = clock.instant();
        String pairKey = PairWriteCoordinator.pairId(100L, 1L);
        RequestAttachmentDocument requestOwned = attachment("request-owned", "ticket-1", now);
        RequestAttachmentDocument journalManual = attachment("journal-manual", pairKey, now);
        attachmentRepository.save(requestOwned);
        attachmentRepository.save(journalManual);
        attendanceRepository.save(excusedAttendance("journal-manual", now));

        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(777L);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(academicGrpcClient.getGroupMembers(10L,
                LocalDate.of(2026, 9, 6), 30L)).thenReturn(
                GroupMembersResponse.newBuilder()
                        .setAsOfDate("2026-09-06").setSemesterId(30L)
                        .addStudents(StudentInfo.newBuilder().setUserId(100L).build()).build());
        markingService.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.PRESENT));

        assertThat(attachmentRepository.findById("journal-manual")).isEmpty();
        assertThat(attachmentRepository.findById("request-owned")).isPresent();
        assertThat(attendanceRepository.findByLessonIdAndUserId(1L, 100L)).get()
                .satisfies(document -> {
                    assertThat(document.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(document.getAttachmentId()).isNull();
                    assertThat(document.getExcuseReason()).isNull();
                });

        RequestAttachmentDocument journalPortClear = attachment("journal-port-clear", pairKey, now);
        attachmentRepository.save(journalPortClear);
        restorePresentAttendanceWithAttachment("journal-port-clear", now);
        attendanceWritePort.markWithLesson(
                100L, 1L, 10L, 20L, 30L, 2, LocalDate.of(2026, 9, 6),
                AttendanceStatus.EXCUSED, AttendanceSource.HEADMAN_EXCUSE, 777L);
        assertThat(attachmentRepository.findById("journal-port-clear")).isPresent();
        assertThat(attendanceRepository.findByLessonIdAndUserId(1L, 100L)).get()
                .satisfies(document -> {
                    assertThat(document.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(document.getSource()).isEqualTo(AttendanceSource.HEADMAN);
                    assertThat(document.getAttachmentId()).isEqualTo("journal-port-clear");
                    assertThat(document.getAttachmentName()).isEqualTo("journal-port-clear.pdf");
                });

        RequestAttachmentDocument journalDecision = attachment("journal-decision", pairKey, now);
        attachmentRepository.save(journalDecision);
        restoreExcusedAttendance("journal-decision", now);
        attendanceWritePort.markWithLesson(
                100L, 1L, 10L, 20L, 30L, 2, LocalDate.of(2026, 9, 6),
                AttendanceStatus.EXCUSED, AttendanceSource.HEADMAN_EXCUSE, 777L);
        assertThat(attachmentRepository.findById("journal-decision")).isPresent();
        markingService.markAttendance(1L, 100L,
                new MarkRequest(AttendanceStatus.EXCUSED,
                        ru.rutcampustrack.attendance.contract.enums.ExcuseType.ILLNESS,
                        "Причина обновлена"));
        assertThat(attachmentRepository.findById("journal-decision")).isPresent();
        LateCheckinRequest pending = LateCheckinRequest.builder()
                .id("late-decision")
                .studentId(100L).groupId(10L).lessonId(1L)
                .subjectId(20L).semesterId(30L).lessonNumber(2)
                .lessonDate(LocalDate.of(2026, 9, 6)).studentName("Иван Иванов")
                .status(LateCheckinRequestStatus.PENDING)
                .createdAt(now).updatedAt(now).build();
        lateCheckinRepository.save(pending);
        lateCheckinService.applyDecision("late-decision", 777L, true);

        assertThat(attachmentRepository.findById("journal-decision")).isEmpty();
        assertThat(attachmentRepository.findById("request-owned")).isPresent();
        assertThat(attendanceRepository.findByLessonIdAndUserId(1L, 100L)).get()
                .satisfies(document -> assertThat(document.getAttachmentId()).isNull());

        RequestAttachmentDocument journalGeo = attachment("journal-geo", pairKey, now);
        attachmentRepository.save(journalGeo);
        restoreExcusedAttendance("journal-geo", now);
        when(geofence.isWithinCampus(55.75, 37.61)).thenReturn(true);
        service.checkin(student, lesson(), "journal-geo-key-0001", new Coordinates(55.75, 37.61));

        assertThat(attachmentRepository.findById("journal-geo")).isEmpty();
        assertThat(attachmentRepository.findById("request-owned")).isPresent();
        assertThat(attendanceRepository.findByLessonIdAndUserId(1L, 100L)).get()
                .satisfies(document -> {
                    assertThat(document.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(document.getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
                    assertThat(document.getAttachmentId()).isNull();
                    assertThat(document.getExcuseType()).isNull();
                    assertThat(document.getExcuseComment()).isNull();
                });
    }

    private void restorePresentAttendanceWithAttachment(String attachmentId, Instant now) {
        AttendanceDocument document = attendanceRepository.findByLessonIdAndUserId(1L, 100L).orElseThrow();
        document.setStatus(AttendanceStatus.PRESENT);
        document.setSource(AttendanceSource.HEADMAN);
        document.setExcuseReason(null);
        document.setExcuseType(null);
        document.setExcuseComment(null);
        document.setAttachmentId(attachmentId);
        document.setAttachmentName(attachmentId + ".pdf");
        document.setAttachmentContentType("application/pdf");
        document.setAttachmentSize(4L);
        document.setUpdatedAt(now);
        attendanceRepository.save(document);
    }

    private RequestAttachmentDocument attachment(String id, String requestId, Instant now) {
        return RequestAttachmentDocument.builder()
                .id(id).requestId(requestId).ownerStudentId(100L).groupId(10L).position(0)
                .name(id + ".pdf").contentType("application/pdf").size(4L).sha256("hash")
                .state(AttachmentState.ACTIVE).data(new Binary(new byte[]{'%', 'P', 'D', 'F'}))
                .uploadedAt(now).expiresAt(now.plus(Duration.ofDays(1))).build();
    }

    private void restoreExcusedAttendance(String attachmentId, Instant now) {
        AttendanceDocument document = attendanceRepository.findByLessonIdAndUserId(1L, 100L).orElseThrow();
        document.setStatus(AttendanceStatus.EXCUSED);
        document.setSource(AttendanceSource.HEADMAN);
        document.setExcuseReason("ILLNESS");
        document.setExcuseType(ru.rutcampustrack.attendance.contract.enums.ExcuseType.ILLNESS);
        document.setAttachmentId(attachmentId);
        document.setAttachmentName(attachmentId + ".pdf");
        document.setAttachmentContentType("application/pdf");
        document.setAttachmentSize(4L);
        document.setUpdatedAt(now);
        attendanceRepository.save(document);
    }

    private AttendanceDocument excusedAttendance(String attachmentId, Instant now) {
        return AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(20L).semesterId(30L)
                .lessonNumber(2).lessonDate(LocalDate.of(2026, 9, 6))
                .status(AttendanceStatus.EXCUSED).source(AttendanceSource.HEADMAN)
                .excuseReason("ILLNESS")
                .excuseType(ru.rutcampustrack.attendance.contract.enums.ExcuseType.ILLNESS)
                .attachmentId(attachmentId).attachmentName(attachmentId + ".pdf")
                .attachmentContentType("application/pdf").attachmentSize(4L)
                .createdAt(now).updatedAt(now).build();
    }

    @Test
    void windowIsInclusiveAtBothBoundaries() {
        Lesson lesson = lesson();
        StudentCheckinService.validateWindow(lesson, Instant.parse("2026-09-06T06:55:00Z"), clock);
        StudentCheckinService.validateWindow(lesson, Instant.parse("2026-09-06T08:05:00Z"), clock);
        assertThatThrownBy(() -> StudentCheckinService.validateWindow(
                lesson, Instant.parse("2026-09-06T06:54:59.999Z"), clock))
                .isInstanceOf(StudentCheckinException.class);
        assertThatThrownBy(() -> StudentCheckinService.validateWindow(
                lesson, Instant.parse("2026-09-06T08:05:00.001Z"), clock))
                .isInstanceOf(StudentCheckinException.class);
    }

    private Lesson lesson() {
        return new Lesson(1, 10, 20, "Алгебра", "SEMINAR", 30, 2, LocalDate.of(2026, 9, 6),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "active", false);
    }

    private StudentCheckinService serviceWith(MongoOutboxStorage outbox) {
        ObjectMapper mapper = new ObjectMapper();
        return new StudentCheckinService(
                attendanceRepository,
                pairRepository,
                receiptRepository,
                lateCheckinRepository,
                pairWriteCoordinator,
                geofence,
                new AttendanceEventPublisher(outbox, mapper),
                new LateCheckinEventPublisher(outbox, mapper),
                metrics,
                transactionTemplate,
                clock
        );
    }

    private LateCheckinRequest seedLateCheckinRequest(LateCheckinRequestStatus status) {
        Instant now = clock.instant();
        return lateCheckinRepository.save(LateCheckinRequest.builder()
                .studentId(100L)
                .groupId(10L)
                .lessonId(1L)
                .subjectId(20L)
                .semesterId(30L)
                .lessonNumber(2)
                .lessonDate(LocalDate.of(2026, 9, 6))
                .studentName("Иван Иванов")
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private static void runConcurrent(Callable<?> first, Callable<?> second) throws Exception {
        runConcurrent(first, second, null);
    }

    private static void runConcurrent(Callable<?> first, Callable<?> second, Code allowedDomainCode) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> firstFuture = pool.submit(() -> { start.await(); return first.call(); });
            Future<?> secondFuture = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            for (Future<?> future : List.of(firstFuture, secondFuture)) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException error) {
                    if (error.getCause() instanceof StudentCheckinException domainError) {
                        assertThat(allowedDomainCode).isNotNull();
                        assertThat(domainError.code()).isEqualTo(allowedDomainCode);
                        continue;
                    }
                    // A Mongo transient conflict is an admissible transport-level loser;
                    // the committed winner must still leave a linearizable terminal state.
                    assertThat(error.getCause())
                            .isInstanceOfAny(DataAccessException.class, org.springframework.transaction.TransactionException.class);
                }
            }
        }
    }

    private static final class FailingAfterInsertOutbox extends MongoOutboxStorage {
        private boolean failAfterSave;

        private FailingAfterInsertOutbox(MongoTemplate mongoTemplate) {
            super(mongoTemplate, "attendance_outbox");
        }

        @Override
        public void save(String eventType, String payload) {
            super.save(eventType, payload);
            if (failAfterSave) throw new IllegalStateException("outbox failure after insert");
        }
    }
}
