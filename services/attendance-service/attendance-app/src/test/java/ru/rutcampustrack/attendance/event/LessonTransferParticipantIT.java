package ru.rutcampustrack.attendance.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceWritePortImpl;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.marking.AttendanceAttachmentService;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.AttachmentState;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Mongo transaction checks for transfer remap, batch replay, conflicts and stale writes. */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import({
        LessonTransferParticipantService.class,
        LessonEventService.class,
        PairWriteCoordinator.class,
        AttendanceAttachmentService.class,
        AttendanceWritePortImpl.class,
        LessonTransferParticipantIT.TestConfig.class
})
class LessonTransferParticipantIT {

    private static final long SOURCE_LESSON_ID = 501L;
    private static final long TARGET_LESSON_ID = 502L;
    private static final long GROUP_ID = 10L;
    private static final long SEMESTER_ID = 30L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T09:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-31T09:00:00Z");

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
        OutboxStorage attendanceOutboxStorage(MongoTemplate mongoTemplate) {
            return new MongoOutboxStorage(mongoTemplate, "attendance_outbox");
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean("grpcTaskExecutor")
        TaskExecutor grpcTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired private LessonTransferParticipantService transferService;
    @Autowired private LessonEventService lessonEventService;
    @Autowired private AttendanceWritePortImpl attendanceWritePort;
    @Autowired private AttendanceAttachmentService attachmentService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ScheduleGrpcClient scheduleGrpcClient;
    @MockitoBean private AcademicGrpcClient academicGrpcClient;
    @MockitoBean private SemesterCacheService semesterCacheService;

    @BeforeEach
    void cleanCollections() {
        for (String collection : List.of("attendances", "request_attachments", "late_checkin_requests",
                "excuse_tickets", "student_checkin_pairs", "lesson_transfer_fences",
                "lesson_transfer_receipts", "attendance_outbox", "lesson_cancellation_markers")) {
            mongoTemplate.dropCollection(collection);
        }
    }

    @Test
    void firstBatchMovesOperationalDataOnce_andReplayKeepsOneReceiptAndAck() throws Exception {
        AttendanceDocument originalMark = AttendanceDocument.builder()
                .id("mark-1")
                .lessonId(SOURCE_LESSON_ID)
                .userId(100L)
                .groupId(GROUP_ID)
                .subjectId(20L)
                .semesterId(SEMESTER_ID)
                .lessonNumber(2)
                .lessonDate(LocalDate.parse("2026-09-01"))
                .status(AttendanceStatus.EXCUSED)
                .source(AttendanceSource.HEADMAN)
                .markedBy(55L)
                .excuseReason("ILLNESS")
                .excuseType(ExcuseType.ILLNESS)
                .excuseComment("medical proof")
                .attachmentId("file-1")
                .attachmentName("medical.pdf")
                .attachmentContentType("application/pdf")
                .attachmentSize(4L)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        mongoTemplate.insert(originalMark);

        byte[] proof = new byte[]{0x25, 0x50, 0x44, 0x46};
        RequestAttachmentDocument originalAttachment = RequestAttachmentDocument.builder()
                .id("file-1")
                .requestId(PairWriteCoordinator.pairId(100L, SOURCE_LESSON_ID))
                .ownerStudentId(100L)
                .groupId(GROUP_ID)
                .position(0)
                .name("medical.pdf")
                .contentType("application/pdf")
                .size((long) proof.length)
                .sha256("a".repeat(64))
                .state(AttachmentState.ACTIVE)
                .data(new Binary(proof))
                .uploadedAt(CREATED_AT)
                .expiresAt(Instant.parse("2027-08-30T09:00:00Z"))
                .build();
        mongoTemplate.insert(originalAttachment);

        LateCheckinRequest lateRequest = LateCheckinRequest.builder()
                .id("late-1")
                .studentId(102L)
                .groupId(GROUP_ID)
                .lessonId(SOURCE_LESSON_ID)
                .subjectId(20L)
                .semesterId(SEMESTER_ID)
                .lessonNumber(2)
                .lessonDate(LocalDate.parse("2026-09-01"))
                .studentName("Student 102")
                .status(LateCheckinRequestStatus.PENDING)
                .origin(LateCheckinRequestOrigin.MANUAL)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        mongoTemplate.insert(lateRequest);

        StudentLessonSnapshotDocument originalSnapshot = StudentLessonSnapshotDocument.builder()
                .lessonId(SOURCE_LESSON_ID)
                .groupId(GROUP_ID)
                .subjectId(20L)
                .subjectName("Subject")
                .semesterId(SEMESTER_ID)
                .lessonNumber(2)
                .date(LocalDate.parse("2026-09-01"))
                .startsAt(LocalTime.parse("09:00"))
                .endsAt(LocalTime.parse("10:30"))
                .status("CLOSED")
                .blocked(false)
                .build();
        ExcuseTicket excuseTicket = ExcuseTicket.builder()
                .id("excuse-1")
                .studentId(103L)
                .groupId(GROUP_ID)
                .studentName("Student 103")
                .lessonIds(List.of(SOURCE_LESSON_ID, 499L))
                .semesterId(SEMESTER_ID)
                .lessonSnapshots(List.of(originalSnapshot))
                .excuseType(ExcuseType.ILLNESS)
                .comment("Request")
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        mongoTemplate.insert(excuseTicket);

        transferService.apply(event(0, 2, List.of(binding(9_223_372_036_854_775_807L))));
        transferService.apply(event(1, 2, List.of(binding(9_223_372_036_854_775_806L))));

        AttendanceDocument movedMark = mongoTemplate.findById("mark-1", AttendanceDocument.class);
        assertThat(movedMark).isNotNull();
        assertThat(movedMark.getLessonId()).isEqualTo(TARGET_LESSON_ID);
        assertThat(movedMark.getLessonDate()).isEqualTo(LocalDate.parse("2026-09-03"));
        assertThat(movedMark.getLessonNumber()).isEqualTo(3);
        assertThat(movedMark.getId()).isEqualTo(originalMark.getId());
        assertThat(movedMark.getStatus()).isEqualTo(originalMark.getStatus());
        assertThat(movedMark.getSource()).isEqualTo(originalMark.getSource());
        assertThat(movedMark.getMarkedBy()).isEqualTo(originalMark.getMarkedBy());
        assertThat(movedMark.getExcuseComment()).isEqualTo(originalMark.getExcuseComment());
        assertThat(movedMark.getAttachmentId()).isEqualTo(originalMark.getAttachmentId());
        assertThat(movedMark.getCreatedAt()).isEqualTo(originalMark.getCreatedAt());
        assertThat(movedMark.getUpdatedAt()).isEqualTo(originalMark.getUpdatedAt());
        assertThat(mongoTemplate.count(Query.query(Criteria.where("lesson_id").is(SOURCE_LESSON_ID)),
                AttendanceDocument.class)).isZero();

        RequestAttachmentDocument movedAttachment = mongoTemplate.findById("file-1", RequestAttachmentDocument.class);
        assertThat(movedAttachment.getRequestId()).isEqualTo(PairWriteCoordinator.pairId(100L, TARGET_LESSON_ID));
        assertThat(movedAttachment.getData().getData()).containsExactly(proof);
        assertThat(movedAttachment.getName()).isEqualTo(originalAttachment.getName());
        assertThat(movedAttachment.getSha256()).isEqualTo(originalAttachment.getSha256());
        assertThat(movedAttachment.getUploadedAt()).isEqualTo(originalAttachment.getUploadedAt());
        assertThat(movedAttachment.getExpiresAt()).isEqualTo(originalAttachment.getExpiresAt());
        assertThat(attachmentService.isAvailable(SOURCE_LESSON_ID, 100L, "file-1")).isFalse();
        assertThat(attachmentService.isAvailable(TARGET_LESSON_ID, 100L, "file-1")).isTrue();

        LateCheckinRequest movedLateRequest = mongoTemplate.findById("late-1", LateCheckinRequest.class);
        assertThat(movedLateRequest.getLessonId()).isEqualTo(TARGET_LESSON_ID);
        assertThat(movedLateRequest.getLessonDate()).isEqualTo(LocalDate.parse("2026-09-03"));
        assertThat(movedLateRequest.getLessonNumber()).isEqualTo(3);
        assertThat(movedLateRequest.getStatus()).isEqualTo(LateCheckinRequestStatus.PENDING);
        assertThat(movedLateRequest.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(movedLateRequest.getUpdatedAt()).isEqualTo(UPDATED_AT);

        ExcuseTicket movedExcuse = mongoTemplate.findById("excuse-1", ExcuseTicket.class);
        assertThat(movedExcuse.getLessonIds()).containsExactly(TARGET_LESSON_ID, 499L);
        assertThat(movedExcuse.getLessonSnapshots()).containsExactly(originalSnapshot);
        assertThat(movedExcuse.getStatus()).isEqualTo(ExcuseTicketStatus.SUBMITTED);
        assertThat(movedExcuse.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(movedExcuse.getUpdatedAt()).isEqualTo(UPDATED_AT);

        assertThat(mongoTemplate.count(new Query(), LessonTransferReceiptDocument.class)).isEqualTo(1);
        LessonTransferReceiptDocument receipt = mongoTemplate.findById(
                "11111111-1111-4111-8111-111111111111", LessonTransferReceiptDocument.class);
        assertThat(receipt.getResult()).isEqualTo("APPLIED");
        assertThat(receipt.getTransferPayloadHash()).isEqualTo("a".repeat(64));
        assertThat(mongoTemplate.count(new Query(), "attendance_outbox")).isEqualTo(1);
        Document ackDocument = mongoTemplate.findOne(new Query(), Document.class, "attendance_outbox");
        assertThat(ackDocument.getString("event_type")).isEqualTo("lesson.transfer.participant.applied");
        var ack = objectMapper.readTree(ackDocument.getString("payload")).path("payload");
        assertThat(ack.path("operation_id").asText()).isEqualTo(receipt.getId());
        assertThat(ack.path("participant_receipt_id").asText()).isEqualTo(receipt.getId());
        assertThat(ack.path("participant").asText()).isEqualTo("ATTENDANCE");
        assertThat(ack.path("result").asText()).isEqualTo("APPLIED");
        assertThat(ack.path("batch_index").asInt()).isEqualTo(-1);

        lessonEventService.processLessonClosed(SOURCE_LESSON_ID, GROUP_ID);
        assertThat(mongoTemplate.findById("mark-1", AttendanceDocument.class).getStatus())
                .isEqualTo(AttendanceStatus.EXCUSED);
        assertThatThrownBy(() -> attendanceWritePort.mark(
                100L, SOURCE_LESSON_ID, GROUP_ID, AttendanceStatus.ABSENT))
                .isInstanceOf(ConflictException.class);
        assertThat(mongoTemplate.count(Query.query(Criteria.where("lesson_id").is(SOURCE_LESSON_ID)),
                AttendanceDocument.class)).isZero();
        assertThat(mongoTemplate.count(new Query(), "attendance_outbox")).isEqualTo(1);
    }

    @Test
    void conflictingTargetMarkStoresErrorReceiptWithoutChangingEitherMark() throws Exception {
        AttendanceDocument source = AttendanceDocument.builder()
                .id("source-mark")
                .lessonId(SOURCE_LESSON_ID)
                .userId(100L)
                .groupId(GROUP_ID)
                .semesterId(SEMESTER_ID)
                .lessonNumber(2)
                .lessonDate(LocalDate.parse("2026-09-01"))
                .status(AttendanceStatus.PRESENT)
                .source(AttendanceSource.STUDENT_GEO)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        AttendanceDocument target = AttendanceDocument.builder()
                .id("target-mark")
                .lessonId(TARGET_LESSON_ID)
                .userId(100L)
                .groupId(GROUP_ID)
                .semesterId(SEMESTER_ID)
                .lessonNumber(3)
                .lessonDate(LocalDate.parse("2026-09-03"))
                .status(AttendanceStatus.ABSENT)
                .source(AttendanceSource.AUTO_SCHEDULER)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        mongoTemplate.insert(source);
        mongoTemplate.insert(target);

        transferService.apply(event(0, 1, List.of()));

        assertThat(mongoTemplate.findById("source-mark", AttendanceDocument.class))
                .usingRecursiveComparison().isEqualTo(source);
        assertThat(mongoTemplate.findById("target-mark", AttendanceDocument.class))
                .usingRecursiveComparison().isEqualTo(target);
        LessonTransferReceiptDocument receipt = mongoTemplate.findById(
                "11111111-1111-4111-8111-111111111111", LessonTransferReceiptDocument.class);
        assertThat(receipt.getResult()).isEqualTo("ERROR");
        assertThat(receipt.getRetryable()).isFalse();
        assertThat(receipt.getErrorCode()).isEqualTo("TARGET_DATA_CONFLICT");
        assertThat(mongoTemplate.count(new Query(), "attendance_outbox")).isEqualTo(1);
        Document ackDocument = mongoTemplate.findOne(new Query(), Document.class, "attendance_outbox");
        var ack = objectMapper.readTree(ackDocument.getString("payload")).path("payload");
        assertThat(ack.path("result").asText()).isEqualTo("ERROR");
        assertThat(ack.path("retryable").asBoolean()).isFalse();
        assertThat(ack.path("error_code").asText()).isEqualTo("TARGET_DATA_CONFLICT");
    }

    private static Map<String, Object> event(int batchIndex, int batchCount,
                                             List<Map<String, Object>> bindings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation_id", "11111111-1111-4111-8111-111111111111");
        payload.put("request_key", "22222222-2222-4222-8222-222222222222");
        payload.put("actor_id", 55L);
        payload.put("group_id", GROUP_ID);
        payload.put("semester_id", SEMESTER_ID);
        payload.put("occurrence_id", 700L);
        payload.put("transfer_payload_hash", "a".repeat(64));
        payload.put("transfer_revision", 4L);
        payload.put("source", snapshot(SOURCE_LESSON_ID, "2026-09-01", 2, "09:00", "10:30", "Room A"));
        payload.put("target", snapshot(TARGET_LESSON_ID, "2026-09-03", 3, "11:00", "12:30", null));
        payload.put("batch_index", batchIndex);
        payload.put("batch_count", batchCount);
        payload.put("bindings", bindings);
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "lesson.transfer.requested");
        envelope.put("event_id", UUID.randomUUID().toString());
        envelope.put("event_version", 1);
        envelope.put("occurred_at", "2026-09-01T00:00:00Z");
        envelope.put("source", "schedule-service");
        envelope.put("trace_id", UUID.randomUUID().toString());
        envelope.put("payload", payload);
        return envelope;
    }

    private static Map<String, Object> snapshot(long lessonId, String date, int number,
                                                String start, String end, String room) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("lesson_id", lessonId);
        snapshot.put("schedule_item_id", 800L);
        snapshot.put("generation", 2L);
        snapshot.put("lesson_revision", 7L);
        snapshot.put("occurrence_revision", 5L);
        snapshot.put("date", date);
        snapshot.put("lesson_number", number);
        snapshot.put("start_time", start);
        snapshot.put("end_time", end);
        snapshot.put("room", room);
        return snapshot;
    }

    private static Map<String, Object> binding(long bindingId) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("binding_id", bindingId);
        binding.put("homework_id", null);
        binding.put("state", "PENDING");
        binding.put("revision", 0L);
        binding.put("actor_id", 55L);
        binding.put("request_key", "33333333-3333-4333-8333-333333333333");
        binding.put("payload_hash", "b".repeat(64));
        return binding;
    }
}
