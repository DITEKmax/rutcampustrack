package ru.rutcampustrack.attendance.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.*;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.enums.*;
import ru.rutcampustrack.attendance.event.*;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.geofence.GeofenceService;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.*;
import ru.rutcampustrack.attendance.marking.AttendanceAttachmentService;
import ru.rutcampustrack.attendance.marking.MarkingService;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataMongoTest
@Testcontainers
@Import({LessonActionService.class, RequestActionRecorder.class, ActionEventHandler.class,
        LessonActionReadService.class, MarkingService.class, AttendanceEventPublisher.class,
        LateCheckinEventPublisher.class, StudentCheckinService.class, PairWriteCoordinator.class,
        SemesterArchiveFence.class, AttendanceAttachmentService.class, AttendanceReadPortImpl.class,
        AttendanceWritePortImpl.class, LateCheckinService.class, LessonEventService.class,
        ru.rutcampustrack.attendance.studentrequest.StudentRequestService.class,
        ru.rutcampustrack.attendance.excuse.ExcuseEventPublisher.class, LessonActionAuditIT.Config.class})
class LessonActionAuditIT {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }
    @TestConfiguration(proxyBeanMethods = false) static class Config {
        @Bean MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory f) { return new MongoTransactionManager(f); }
        @Bean TransactionTemplate transactionTemplate(MongoTransactionManager m) { return new TransactionTemplate(m); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-06T07:15:00Z"), ZoneId.of("Europe/Moscow")); }
        @Bean("grpcTaskExecutor") TaskExecutor executor() { return new SyncTaskExecutor(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().registerModule(new JavaTimeModule()); }
        @Bean OutboxStorage outbox(MongoTemplate m) { return new MongoOutboxStorage(m, "attendance_outbox"); }
    }
    @jakarta.annotation.Resource(name = "mongoTemplate") MongoTemplate mongo;
    @jakarta.annotation.Resource TransactionTemplate tx;
    @jakarta.annotation.Resource MarkingService marking;
    @jakarta.annotation.Resource StudentCheckinService checkin;
    @jakarta.annotation.Resource LessonActionReadService reads;
    @jakarta.annotation.Resource ActionEventHandler events;
    @jakarta.annotation.Resource LessonEventService lifecycle;
    @jakarta.annotation.Resource LateCheckinService late;
    @jakarta.annotation.Resource ru.rutcampustrack.attendance.studentrequest.StudentRequestService requests;
    @jakarta.annotation.Resource AttendanceReadPortImpl readPort;
    @jakarta.annotation.Resource ExcuseRepository excuses;
    @MockitoBean ScheduleGrpcClient schedule;
    @MockitoBean AcademicGrpcClient academic;
    @MockitoBean RequestContext actor;
    @MockitoBean SemesterCacheService semesters;
    @MockitoBean GeofenceService geofence;
    @MockitoBean BusinessMetrics metrics;
    private LessonResponse lesson;

    @BeforeEach void setup() {
        for (String collection : List.of("attendances", "lesson_actions", "attendance_outbox", "late_checkin_requests",
                "excuse_tickets", "student_checkin_pairs", "student_checkin_receipts", "request_attachments", "semester_archive_fences",
                "attendance_lesson_write_fences", "lesson_cancellations", "event_consumer_processed")) mongo.dropCollection(collection);
        mongo.indexOps(AttendanceDocument.class).ensureIndex(new org.springframework.data.mongodb.core.index.Index()
                .on("lesson_id", org.springframework.data.domain.Sort.Direction.ASC)
                .on("user_id", org.springframework.data.domain.Sort.Direction.ASC).unique());
        lesson = LessonResponse.newBuilder().setId(1).setGroupId(10).setSemesterId(30).setSubjectId(20)
                .setLessonNumber(2).setDate("2026-09-06").setStartTime("10:00").setEndTime("11:00").setStatus("active").build();
        when(schedule.getLessonById(1L)).thenReturn(lesson);
        when(schedule.requireAttendanceMutationReady(1L, 10L)).thenReturn(lesson);
        when(academic.getGroupMembers(10L, LocalDate.parse("2026-09-06"), 30L)).thenReturn(roster());
        when(academic.isHeadman(any(), any())).thenReturn(HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());
        when(academic.getSubjectsByIds(any())).thenReturn(Map.of(20L, "Предмет"));
        when(actor.getUserId()).thenReturn(200L); when(actor.getGroupId()).thenReturn(10L);
        when(actor.isHeadman()).thenReturn(true); when(actor.getRole()).thenReturn(UserRole.STUDENT);
        when(metrics.checkinCounter(anyString())).thenReturn(mock(Counter.class));
        when(metrics.lateCheckinCreatedCounter()).thenReturn(mock(Counter.class));
    }

    @Test void revisionGuardRejectsReplacedExcuseAndClearRetainsApprovedTicket() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "первая.pdf", "application/pdf",
                "%PDF-1.7\nsynthetic".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        AttendanceDocument first = marking.markAttendance(1L, 100L, excuse("первая"), file);
        AttendanceDocument replaced = marking.markAttendance(1L, 100L, excuse("вторая"));
        assertThat(replaced.getMarkRevision()).isNotEqualTo(first.getMarkRevision());
        long rows = count(); long outbox = mongo.count(new Query(), "attendance_outbox");
        assertThatThrownBy(() -> marking.clearAttendance(1L, 100L, "EXCUSED", first.getMarkRevision())).isInstanceOf(ConflictException.class);
        assertThat(count()).isEqualTo(rows); assertThat(mongo.count(new Query(), "attendance_outbox")).isEqualTo(outbox);
        assertThatThrownBy(() -> marking.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.EXCUSED,
                ExcuseType.ILLNESS, "устаревшее изменение файла", "EXCUSED", first.getMarkRevision()), file)).isInstanceOf(ConflictException.class);
        assertThat(marking.downloadAttachment(1L, 100L).bytes()).isEqualTo(file.getBytes());
        ExcuseTicket ticket = excuses.save(ExcuseTicket.builder().studentId(100L).groupId(10L).semesterId(30L)
                .lessonIds(List.of(1L)).status(ExcuseTicketStatus.APPROVED).excuseType(ExcuseType.ILLNESS).build());
        mongo.insert(ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument.builder()
                .id("approved-file").requestId(ticket.getId()).ownerStudentId(100L).groupId(10L).semesterId(30L)
                .name("evidence.pdf").contentType("application/pdf").size(1L)
                .state(ru.rutcampustrack.attendance.studentrequest.AttachmentState.ACTIVE)
                .data(new org.bson.types.Binary(new byte[]{1})).build());
        marking.clearAttendance(1L, 100L, "EXCUSED", replaced.getMarkRevision());
        assertThat(readPort.findByLessonIdAndUserId(1L, 100L)).isEmpty();
        assertThat(excuses.findById(ticket.getId())).isPresent();
        assertThat(mongo.findById("approved-file", Document.class, "request_attachments")).isNotNull();
        assertThat(count()).isEqualTo(rows + 1);
        var page = reads.read(1L, null, 10);
        assertThat(page.items()).anySatisfy(action -> {
            assertThat(action.action()).isEqualTo("ATTENDANCE_CLEARED");
            assertThat(action.before().get("excuse_comment")).isEqualTo("вторая");
            assertThat(action.after()).isNull();
            @SuppressWarnings("unchecked") Map<String, Object> attachment = (Map<String, Object>) action.before().get("attachment");
            assertThat(attachment.get("name")).isEqualTo("первая.pdf");
            assertThat(attachment.get("availability")).isEqualTo("UNAVAILABLE");
        });
    }

    @Test void noOpLegacySnapshotAndDeleteRecreateDoNotReuseRevision() {
        AttendanceDocument a = marking.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.PRESENT));
        long n = count(); long o = mongo.count(new Query(), "attendance_outbox");
        AttendanceDocument replay = marking.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.PRESENT));
        assertThat(replay.getMarkRevision()).isEqualTo(a.getMarkRevision()); assertThat(count()).isEqualTo(n);
        assertThat(mongo.count(new Query(), "attendance_outbox")).isEqualTo(o);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(a.getId())), new org.springframework.data.mongodb.core.query.Update().unset("mark_revision"), AttendanceDocument.class);
        String legacy = readPort.findByLessonIdAndUserId(1L, 100L).orElseThrow().markRevision();
        AttendanceDocument snapshot = mongo.findById(a.getId(), AttendanceDocument.class);
        assertThat(legacy).isEqualTo(AttendanceRevisions.effective(snapshot));
        snapshot.setAttachmentName("другая.pdf"); assertThat(AttendanceRevisions.effective(snapshot)).isNotEqualTo(legacy);
        marking.clearAttendance(1L, 100L, "PRESENT", legacy);
        long cleared = count(); marking.clearAttendance(1L, 100L); assertThat(count()).isEqualTo(cleared);
        AttendanceDocument recreated = marking.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.PRESENT));
        assertThat(recreated.getMarkRevision()).isNotEqualTo(a.getMarkRevision()).isNotEqualTo(legacy);
        assertThatThrownBy(() -> marking.clearAttendance(1L, 100L, "PRESENT", null)).isInstanceOf(BadRequestException.class);
    }

    @Test void guardedPutDoesNotOverwriteNewExcuseOrLaterSameStatusReason() {
        MarkRequest emptyToPresent = new MarkRequest(AttendanceStatus.PRESENT, null, null, "EMPTY", "EMPTY");
        var created = marking.markAttendance(1L, 100L, excuse("появилась после пустой клетки"));
        long n = count(); long outbox = mongo.count(new Query(), "attendance_outbox");
        assertThatThrownBy(() -> marking.markAttendance(1L, 100L, emptyToPresent)).isInstanceOf(ConflictException.class);
        assertThat(count()).isEqualTo(n); assertThat(mongo.count(new Query(), "attendance_outbox")).isEqualTo(outbox);
        marking.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.EXCUSED, ExcuseType.ILLNESS,
                "обновлена причина", "EXCUSED", created.getMarkRevision()));
        long updated = count();
        assertThatThrownBy(() -> marking.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.PRESENT,
                null, null, "EXCUSED", created.getMarkRevision()))).isInstanceOf(ConflictException.class);
        assertThat(count()).isEqualTo(updated);
        var current = readPort.findByLessonIdAndUserId(1L, 100L).orElseThrow();
        assertThat(current.excuseComment()).isEqualTo("обновлена причина");
        assertThatThrownBy(() -> marking.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.PRESENT,
                null, null, "EMPTY", "bad"))).isInstanceOf(BadRequestException.class);
        marking.clearAttendance(1L, 100L, "EXCUSED", current.markRevision());
        assertThat(marking.markAttendance(1L, 100L, emptyToPresent).getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test void acceptedGeoCapturesMeasuredReceiptAndBeforeAfterOnce() {
        lifecycle.applyLessonClosed(new LessonEventService.LessonClosedSnapshot(1L, 10L, 30L, lesson,
                LocalDate.parse("2026-09-06"), roster()));
        when(geofence.evaluate(55.75, 37.61)).thenReturn(new GeofenceService.Decision(true, 25.0, 100.0));
        var identity = new StudentCheckinModels.Identity(100, "STUDENT", 10L, false, "Иван", false);
        var coordinates = new StudentCheckinModels.Coordinates(55.75, 37.61);
        var ack = checkin.checkin(identity, studentLesson(), "geo-accepted-key-0001", coordinates);
        assertThat(ack.outcome()).isEqualTo(StudentCheckinModels.Outcome.PRESENT);
        assertThat(count()).isEqualTo(3);
        assertThat(checkin.checkin(identity, studentLesson(), "geo-accepted-key-0001", coordinates)).isEqualTo(ack);
        assertThat(count()).isEqualTo(3);
        assertThat(reads.read(1L, null, 20).items()).anySatisfy(action -> {
            assertThat(action.action()).isEqualTo("GEO_ATTEMPT"); assertThat(action.geo().verdict()).isEqualTo("ACCEPTED");
            assertThat(action.geo().distanceMeters()).isEqualTo(25.0);
            assertThat(action.before().get("status")).isEqualTo("ABSENT"); assertThat(action.after().get("status")).isEqualTo("PRESENT");
            assertThat(action.actor().userId()).isEqualTo(100L);
        });
    }

    @Test void domainAuditAndOutboxRollbackTogether() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            marking.markAttendance(1L, 100L, excuse("откат"));
            throw new IllegalStateException("synthetic post-audit failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero(); assertThat(mongo.count(new Query(), AttendanceDocument.class)).isZero();
        assertThat(mongo.count(new Query(), "attendance_outbox")).isZero();
    }

    @Test void geoReceiptPersistsDeniedDistanceReplayAndApprovedDecisionInSameHistory() {
        when(geofence.evaluate(55.75, 37.61)).thenReturn(new GeofenceService.Decision(false, 250.0, 100.0));
        var identity = new StudentCheckinModels.Identity(100, "STUDENT", 10L, false, "Иван", false);
        var coordinates = new StudentCheckinModels.Coordinates(55.75, 37.61);
        var ack = checkin.checkin(identity, studentLesson(), "geo-audit-key-00001", coordinates);
        long n = count(); assertThat(n).isEqualTo(2);
        assertThat(checkin.checkin(identity, studentLesson(), "geo-audit-key-00001", coordinates)).isEqualTo(ack);
        assertThat(count()).isEqualTo(n);
        assertThat(reads.read(1L, null, 20).items()).anySatisfy(action -> {
            assertThat(action.action()).isEqualTo("GEO_ATTEMPT"); assertThat(action.geo().distanceMeters()).isEqualTo(250.0);
            assertThat(action.geo().radiusMeters()).isEqualTo(100.0); assertThat(action.geo().verdict()).isEqualTo("OUTSIDE_RADIUS");
        });
        late.applyDecision(ack.request().id(), 200L, true);
        var current = readPort.findByLessonIdAndUserId(1L, 100L).orElseThrow();
        assertThat(current.status()).isEqualTo(AttendanceStatus.PRESENT); assertThat(current.markRevision()).isNotBlank();
        long decided = count(); late.applyDecision(ack.request().id(), 200L, true); assertThat(count()).isEqualTo(decided);
        assertThat(reads.read(1L, null, 20).items()).anySatisfy(action -> {
            assertThat(action.action()).isEqualTo("REQUEST_DECIDED"); assertThat(action.before().get("status")).isEqualTo("PENDING");
            assertThat(action.after().get("status")).isEqualTo("APPROVED"); assertThat(action.actor().userId()).isEqualTo(200L);
        });
    }

    @Test void permissionAuthorizedAssistantDecisionKeepsTrueActorRole() {
        when(geofence.evaluate(55.75, 37.61)).thenReturn(new GeofenceService.Decision(false, 200.0, 100.0));
        var student = new StudentCheckinModels.Identity(100, "STUDENT", 10L, false, "Иван", false);
        var ack = checkin.checkin(student, studentLesson(), "assistant-audit-key-001", new StudentCheckinModels.Coordinates(55.75, 37.61));
        when(academic.hasAssistantPermission(10L, "MANAGE_EXCUSES")).thenReturn(true);
        requests.decideLateCheckin(new ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity(
                201L, UserRole.STUDENT, 10L, false), ack.request().id(), true);
        assertThat(reads.read(1L, null, 20).items()).anySatisfy(action -> {
            assertThat(action.action()).isEqualTo("REQUEST_DECIDED");
            assertThat(action.actor().userId()).isEqualTo(201L); assertThat(action.actor().role()).isEqualTo("ASSISTANT");
        });
        assertThat(reads.read(1L, null, 20).items()).anySatisfy(action -> {
            assertThat(action.action()).isEqualTo("ATTENDANCE_SET"); assertThat(action.actor().role()).isEqualTo("ASSISTANT");
        });
    }

    @Test void autoMaterializationAndAuthoritativeEventRedeliveryKeepPermanentHistory() {
        lifecycle.applyLessonClosed(new LessonEventService.LessonClosedSnapshot(1L, 10L, 30L, lesson,
                LocalDate.parse("2026-09-06"), roster()));
        assertThat(count()).isEqualTo(1);
        lifecycle.applyLessonClosed(new LessonEventService.LessonClosedSnapshot(1L, 10L, 30L, lesson,
                LocalDate.parse("2026-09-06"), roster())); assertThat(count()).isEqualTo(1);
        var store = new ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore(mongo); store.ensureIndexes();
        var consumer = new EventConsumer(lifecycle, null, null, null, null, semesters, null,
                new ru.rutcampustrack.shared.events.IdempotencyGuard(store), tx);
        consumer.setActionEvents(events);
        Map<String, Object> event = event(); consumer.onEvent(event);
        consumer.onEvent(event); assertThat(count()).isEqualTo(2);
        assertThat(mongo.count(new Query(), "event_consumer_processed")).isEqualTo(1);
        mongo.remove(new Query(), "event_consumer_processed");
        consumer.onEvent(event); assertThat(count()).isEqualTo(2);
        Map<String, Object> bad = new LinkedHashMap<>(event); bad.put("source", "notification-bot");
        assertThatThrownBy(() -> consumer.onEvent(bad)).isInstanceOf(IllegalArgumentException.class);
        Map<String, Object> poison = new LinkedHashMap<>(event);
        @SuppressWarnings("unchecked") Map<String, Object> p = new LinkedHashMap<>((Map<String, Object>) event.get("payload"));
        p.put("group_id", 11L); poison.put("payload", p); poison.put("event_id", UUID.randomUUID().toString());
        assertThatThrownBy(() -> consumer.onEvent(poison)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count()).isEqualTo(2);
        assertThat(mongo.count(new Query(), "event_consumer_processed")).isEqualTo(1);
        Map<String, Object> homework = new LinkedHashMap<>(event);
        homework.put("event_id", UUID.randomUUID().toString()); homework.put("event_type", "homework.action-recorded"); homework.put("source", "academic-service");
        Map<String, Object> hwBefore = Map.of("lesson_id", 1L, "group_id", 10L, "semester_id", 30L, "revision", 1L);
        Map<String, Object> hwAfter = Map.of("lesson_id", 2L, "group_id", 10L, "semester_id", 30L, "revision", 2L);
        homework.put("payload", LessonActionService.payload(10L, 30L, 1L, "HOMEWORK_EDITED", null, null,
                hwBefore, hwAfter, null, null, Map.of("id", 90L, "revision", 2L), 2L));
        consumer.onEvent(homework); assertThat(count()).isEqualTo(3);
    }

    @Test void cursorPagesRedactSensitiveSnapshotAndReauthorizeEveryPage() {
        marking.markAttendance(1L, 100L, excuse("секрет"));
        marking.markAttendance(1L, 100L, new MarkRequest(AttendanceStatus.PRESENT));
        when(actor.isHeadman()).thenReturn(false); when(academic.hasAssistantPermission(10L, "MARK_ATTENDANCE")).thenReturn(true);
        var first = reads.read(1L, null, 1); assertThat(first.items()).hasSize(1); assertThat(first.nextCursor()).isNotNull();
        var second = reads.read(1L, first.nextCursor(), 1); assertThat(second.items()).hasSize(1);
        assertThat(first.items().getFirst().id()).isNotEqualTo(second.items().getFirst().id());
        for (var item : List.of(first.items().getFirst(), second.items().getFirst())) {
            if (item.before() != null) assertThat(item.before()).doesNotContainKeys("excuse_reason", "excuse_comment", "attachment");
            if (item.after() != null) assertThat(item.after()).doesNotContainKeys("excuse_reason", "excuse_comment", "attachment");
        }
        when(academic.hasAssistantPermission(10L, "MARK_ATTENDANCE")).thenReturn(false);
        assertThatThrownBy(() -> reads.read(1L, first.nextCursor(), 1)).isInstanceOf(AccessDeniedException.class);
        when(actor.getGroupId()).thenReturn(11L);
        assertThatThrownBy(() -> reads.read(1L, first.nextCursor(), 1)).isInstanceOf(AccessDeniedException.class);
    }

    private long count() { return mongo.count(new Query(), LessonActionDocument.class); }
    private MarkRequest excuse(String text) { return new MarkRequest(AttendanceStatus.EXCUSED, ExcuseType.ILLNESS, text); }
    private GroupMembersResponse roster() { return GroupMembersResponse.newBuilder().setAsOfDate("2026-09-06").setSemesterId(30)
            .addStudents(StudentInfo.newBuilder().setUserId(100).setDisplayName("Иван")).build(); }
    private StudentCheckinModels.Lesson studentLesson() { return new StudentCheckinModels.Lesson(1, 10, 20, "Предмет", "lecture", 30, 2,
            LocalDate.parse("2026-09-06"), LocalTime.parse("10:00"), LocalTime.parse("11:00"), "active", false); }
    private Map<String, Object> event() {
        Map<String, Object> snapshot = Map.of("lesson_id", 1L, "group_id", 10L, "semester_id", 30L, "status", "active");
        Map<String, Object> payload = LessonActionService.payload(10L, 30L, 1L, "LESSON_CREATED", null, null,
                null, snapshot, null, null, null, 1L);
        return Map.of("event_type", "schedule.action-recorded", "event_id", UUID.randomUUID().toString(), "event_version", 1,
                "occurred_at", "2026-09-06T07:00:00.123456Z", "source", "schedule-service", "trace_id", "synthetic-trace", "payload", payload);
    }
}
