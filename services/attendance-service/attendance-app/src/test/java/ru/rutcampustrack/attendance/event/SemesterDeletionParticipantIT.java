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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.marking.AttendanceAttachmentService;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/** Real Mongo proof for Attendance deletion fencing, commit replay and release. */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import({SemesterDeletionParticipantService.class, AttendanceSemesterDeletionData.class,
        SemesterArchiveFence.class, SemesterArchiveEffectService.class,
        LessonEventService.class, PairWriteCoordinator.class, AttendanceAttachmentService.class,
        SemesterDeletionParticipantIT.TestConfig.class})
class SemesterDeletionParticipantIT {

    private static final long COMMIT_SEMESTER_ID = 60L;
    private static final long ARCHIVED_SEMESTER_ID = 61L;
    private static final long ACTIVE_SEMESTER_ID = 62L;

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
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
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }

        @Bean("grpcTaskExecutor")
        TaskExecutor grpcTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired private SemesterDeletionParticipantService deletionParticipant;
    @Autowired private AttendanceSemesterDeletionData deletionData;
    @Autowired private SemesterArchiveFence fence;
    @Autowired private SemesterArchiveEffectService scheduleEffects;
    @Autowired private LessonEventService lessonEventService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ScheduleGrpcClient scheduleGrpcClient;
    @MockitoBean private AcademicGrpcClient academicGrpcClient;
    @MockitoBean private SemesterCacheService semesterCacheService;

    @BeforeEach
    void cleanCollections() {
        for (String collection : List.of("attendances", "excuse_tickets", "late_checkin_requests",
                "request_attachments", "student_late_checkin_budgets", "student_request_receipts",
                "student_checkin_receipts", "semester_archive_fences", "semester_archive_effect_receipts",
                "semester_archive_participant_receipts", "semester_deletion_participant_operations",
                "semester_deletion_participant_receipts", "semester_deletion_tombstones",
                "lesson_transfer_receipts", "attendance_outbox")) {
            mongoTemplate.dropCollection(collection);
        }
    }

    @Test
    void commitDeletesDomainAndFileBytesAtomically_replaysAck_andTombstoneBlocksLateEffects() throws Exception {
        seedTargetDomainRows(COMMIT_SEMESTER_ID);
        mongoTemplate.insert(new Document("_id", "request-retained")
                .append("request_id", "request-60"), "student_request_receipts");
        mongoTemplate.insert(new Document("_id", "checkin-retained")
                .append("result", "ACCEPTED"), "student_checkin_receipts");
        mongoTemplate.save(fence(COMMIT_SEMESTER_ID, 3L, "OPEN", "prior-open"));

        String operationId = uuid();
        String digest = deletionData.preview(COMMIT_SEMESTER_ID).participantDigest();
        deletionParticipant.apply(command("PREPARE_DELETE", operationId, COMMIT_SEMESTER_ID, 4L, digest));
        assertThatThrownBy(() -> deletionParticipant.apply(
                command("SEAL_DELETE", operationId, COMMIT_SEMESTER_ID + 100L, 4L, digest)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(receipt(operationId, "SEAL_DELETE")).isNull();
        deletionParticipant.apply(command("SEAL_DELETE", operationId, COMMIT_SEMESTER_ID, 4L, digest));
        SemesterDeletionParticipantReceiptDocument sealed = receipt(operationId, "SEAL_DELETE");
        assertThat(sealed.getStatus()).isEqualTo("READY");
        assertThat(sealed.getAttendanceMarksCount()).isEqualTo(1L);
        assertThat(sealed.getStudentRequestsCount()).isEqualTo(2L);

        deletionParticipant.apply(command("COMMIT_DELETE", operationId, COMMIT_SEMESTER_ID, 4L, digest));

        assertThat(mongoTemplate.count(Query.query(Criteria.where("semester_id").is(COMMIT_SEMESTER_ID)),
                "attendances")).isZero();
        for (String collection : List.of("excuse_tickets", "late_checkin_requests",
                "request_attachments", "student_late_checkin_budgets")) {
            assertThat(mongoTemplate.count(Query.query(Criteria.where("semester_id").is(COMMIT_SEMESTER_ID)),
                    collection)).isZero();
        }
        assertThat(mongoTemplate.findById("request-file-60", Document.class, "request_attachments")).isNull();
        assertThat(mongoTemplate.findById("request-retained", Document.class, "student_request_receipts"))
                .isNotNull();
        assertThat(mongoTemplate.findById("checkin-retained", Document.class, "student_checkin_receipts"))
                .isNotNull();
        SemesterDeletionTombstoneDocument tombstone = mongoTemplate.findById(
                Long.toString(COMMIT_SEMESTER_ID), SemesterDeletionTombstoneDocument.class);
        assertThat(tombstone).isNotNull();
        assertThat(tombstone.getOperationId()).isEqualTo(operationId);
        assertThat(tombstone.getParticipantDigest()).isEqualTo(digest);
        assertThat(receipt(operationId, "COMMIT_DELETE").getStatus()).isEqualTo("DELETED");
        assertThat(outboxCount("semester.archive.participant.ack")).isEqualTo(3L);

        // A fresh transport envelope with the same durable identity replays the
        // saved terminal outcome; it cannot recreate deleted request rows.
        deletionParticipant.apply(command("COMMIT_DELETE", operationId, COMMIT_SEMESTER_ID, 4L, digest));
        assertThat(outboxCount("semester.archive.participant.ack")).isEqualTo(4L);
        Document replayedAck = latestOutbox("semester.archive.participant.ack");
        Document ackEnvelope = Document.parse(replayedAck.getString("payload"));
        assertThat(((Document) ackEnvelope.get("payload")).getString("status")).isEqualTo("DELETED");
        assertThat(((Number) ((Document) ((Document) ackEnvelope.get("payload")).get("counts"))
                .get("attendanceMarks")).longValue()).isEqualTo(1L);

        // The permanent tombstone remains authoritative if its companion
        // fence row is lost, for both direct writes and delayed Schedule events.
        mongoTemplate.dropCollection("semester_archive_fences");
        assertThat(deletionData.previewWithFenceVersion(COMMIT_SEMESTER_ID).observedFenceVersion())
                .isEqualTo(4L);
        assertThatThrownBy(() -> fence.lockWritable(COMMIT_SEMESTER_ID, Instant.now()))
                .isInstanceOf(ConflictException.class);
        String lateEventId = uuid();
        scheduleEffects.apply(scheduleEffect(lateEventId, COMMIT_SEMESTER_ID), () ->
                mongoTemplate.insert(new Document("_id", "late-mark")
                        .append("semester_id", COMMIT_SEMESTER_ID), "attendances"));
        assertThat(mongoTemplate.findById(lateEventId + ":ATTENDANCE",
                SemesterArchiveEffectReceiptDocument.class).getResult()).isEqualTo("ERROR");
        assertThat(mongoTemplate.findById("late-mark", Document.class, "attendances")).isNull();
        assertThat(deletionData.preview(COMMIT_SEMESTER_ID).attendanceMarks()).isZero();
    }

    @Test
    void prepareDrainsAcceptedScheduleEffectAndReleaseRestoresArchivedAndActiveStatesAtNewEpoch() {
        seedTargetDomainRows(ARCHIVED_SEMESTER_ID);
        mongoTemplate.save(fence(ARCHIVED_SEMESTER_ID, 7L, "ARCHIVE_SEALED", "old-archive"));

        String archivedOperation = uuid();
        String archivedDigest = deletionData.preview(ARCHIVED_SEMESTER_ID).participantDigest();
        deletionParticipant.apply(command("PREPARE_DELETE", archivedOperation,
                ARCHIVED_SEMESTER_ID, 8L, archivedDigest));
        assertThat(fenceState(ARCHIVED_SEMESTER_ID)).isEqualTo("DELETE_PREPARING");

        String delayedScheduleEvent = uuid();
        scheduleEffects.apply(scheduleEffect(delayedScheduleEvent, ARCHIVED_SEMESTER_ID), () ->
                mongoTemplate.insert(new Document("_id", "drained-mark")
                        .append("lesson_id", 900L)
                        .append("user_id", 701L)
                        .append("group_id", 90L)
                        .append("semester_id", ARCHIVED_SEMESTER_ID)
                        .append("status", "PRESENT"), "attendances"));
        assertThat(mongoTemplate.findById(delayedScheduleEvent + ":ATTENDANCE",
                SemesterArchiveEffectReceiptDocument.class).getResult()).isEqualTo("APPLIED");

        deletionParticipant.apply(command("SEAL_DELETE", archivedOperation,
                ARCHIVED_SEMESTER_ID, 8L, archivedDigest));
        SemesterDeletionParticipantReceiptDocument changedSeal = receipt(archivedOperation, "SEAL_DELETE");
        assertThat(changedSeal.getStatus()).isEqualTo("PENDING");
        assertThat(changedSeal.getBlockingReason()).isEqualTo("ATTENDANCE_DELETE_PREVIEW_CHANGED");
        deletionParticipant.apply(command("RELEASE_DELETE", archivedOperation,
                ARCHIVED_SEMESTER_ID, 8L, archivedDigest));
        assertThat(receipt(archivedOperation, "RELEASE_DELETE").getStatus()).isEqualTo("RELEASED");
        assertFence(ARCHIVED_SEMESTER_ID, 8L, "ARCHIVE_SEALED");
        assertThat(mongoTemplate.findById("drained-mark", Document.class, "attendances")).isNotNull();

        mongoTemplate.save(fence(ACTIVE_SEMESTER_ID, 2L, "OPEN", "old-active"));
        String activeOperation = uuid();
        String activeDigest = deletionData.preview(ACTIVE_SEMESTER_ID).participantDigest();
        deletionParticipant.apply(command("PREPARE_DELETE", activeOperation,
                ACTIVE_SEMESTER_ID, 3L, activeDigest));
        deletionParticipant.apply(command("RELEASE_DELETE", activeOperation,
                ACTIVE_SEMESTER_ID, 3L, activeDigest));
        assertThat(receipt(activeOperation, "RELEASE_DELETE").getStatus()).isEqualTo("RELEASED");
        assertFence(ACTIVE_SEMESTER_ID, 3L, "OPEN");
    }

    @Test
    void deletingMarkBeforeSemesterPurgeAlsoRemovesItsLegacyFile_withoutDeletingForeignOwnerBytes() {
        long semesterId = 63L;
        long studentId = 763L;
        long lessonId = 3063L;
        String pairKey = PairWriteCoordinator.pairId(studentId, lessonId);
        mongoTemplate.insert(new Document("_id", "mark-before-semester-delete")
                .append("lesson_id", lessonId)
                .append("user_id", studentId)
                .append("group_id", 90L)
                .append("semester_id", semesterId)
                .append("status", "PRESENT"), "attendances");
        mongoTemplate.insert(new Document("_id", "legacy-file-owned-by-mark")
                .append("request_id", pairKey)
                .append("owner_student_id", studentId)
                .append("group_id", 90L)
                .append("name", "legacy.pdf")
                .append("size", 3L)
                .append("sha256", "e".repeat(64))
                .append("data", new Binary(new byte[]{4, 5, 6})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "same-key-foreign-owner")
                .append("request_id", pairKey)
                .append("owner_student_id", studentId + 1L)
                .append("group_id", 90L)
                .append("name", "foreign.pdf")
                .append("size", 1L)
                .append("sha256", "f".repeat(64))
                .append("data", new Binary(new byte[]{7})), "request_attachments");

        lessonEventService.processLessonsDeleted(List.of(lessonId), semesterId);

        assertThat(mongoTemplate.findById("mark-before-semester-delete", Document.class, "attendances")).isNull();
        assertThat(mongoTemplate.findById("legacy-file-owned-by-mark", Document.class, "request_attachments"))
                .isNull();
        assertThat(mongoTemplate.findById("same-key-foreign-owner", Document.class, "request_attachments"))
                .isNotNull();

        String digestAfterLessonDeletion = deletionData.preview(semesterId).participantDigest();
        String operationId = uuid();
        deletionParticipant.apply(command("PREPARE_DELETE", operationId, semesterId, 1L,
                digestAfterLessonDeletion));
        deletionParticipant.apply(command("SEAL_DELETE", operationId, semesterId, 1L,
                digestAfterLessonDeletion));
        deletionParticipant.apply(command("COMMIT_DELETE", operationId, semesterId, 1L,
                digestAfterLessonDeletion));
        assertThat(mongoTemplate.findById("legacy-file-owned-by-mark", Document.class, "request_attachments"))
                .isNull();
        assertThat(mongoTemplate.findById("same-key-foreign-owner", Document.class, "request_attachments"))
                .isNotNull();
    }

    private void seedTargetDomainRows(long semesterId) {
        long studentId = semesterId + 700L;
        long lessonId = semesterId + 3000L;
        mongoTemplate.insert(new Document("_id", "mark-" + semesterId)
                .append("lesson_id", lessonId)
                .append("user_id", studentId)
                .append("group_id", 90L)
                .append("semester_id", semesterId)
                .append("status", "PRESENT"), "attendances");
        mongoTemplate.insert(new Document("_id", "request-" + semesterId)
                .append("student_id", studentId)
                .append("group_id", 90L)
                .append("semester_id", semesterId)
                .append("lesson_ids", List.of(lessonId))
                .append("status", "SUBMITTED"), "excuse_tickets");
        mongoTemplate.insert(new Document("_id", "late-request-" + semesterId)
                .append("student_id", studentId)
                .append("group_id", 90L)
                .append("lesson_id", lessonId)
                .append("semester_id", semesterId)
                .append("status", "PENDING"), "late_checkin_requests");
        mongoTemplate.insert(new Document("_id", "request-file-" + semesterId)
                .append("request_id", "request-" + semesterId)
                .append("owner_student_id", studentId)
                .append("group_id", 90L)
                .append("semester_id", semesterId)
                .append("name", "request.pdf")
                .append("size", 3L)
                .append("sha256", "a".repeat(64))
                .append("state", "ACTIVE")
                .append("data", new Binary(new byte[]{1, 2, 3})), "request_attachments");
        mongoTemplate.insert(new Document("_id", studentId + ":" + semesterId)
                .append("student_id", studentId)
                .append("semester_id", semesterId)
                .append("limit", 4)
                .append("used", 1), "student_late_checkin_budgets");
    }

    private static SemesterArchiveFenceDocument fence(long semesterId, long version, String state, String operationId) {
        return SemesterArchiveFenceDocument.builder()
                .id(Long.toString(semesterId))
                .semesterId(semesterId)
                .stateVersion(version)
                .operationId(operationId)
                .barrierState(state)
                .writeFence(0L)
                .updatedAt(Instant.now())
                .build();
    }

    private Map<String, Object> command(String command, String operationId,
                                        long semesterId, long stateVersion, String digest) {
        return Map.of(
                "event_type", "semester.archive.participant.command",
                "event_id", uuid(),
                "event_version", 1,
                "source", "academic-service",
                "trace_id", uuid(),
                "payload", Map.of(
                        "operation_id", operationId,
                        "semester_id", semesterId,
                        "state_version", stateVersion,
                        "command", command,
                        "expected_participant_digest", digest));
    }

    private static Map<String, Object> scheduleEffect(String eventId, long semesterId) {
        return Map.of(
                "event_type", "lesson.cancelled",
                "event_id", eventId,
                "event_version", 1,
                "source", "schedule-service",
                "trace_id", uuid(),
                "payload", Map.of("semester_id", semesterId));
    }

    private SemesterDeletionParticipantReceiptDocument receipt(String operationId, String command) {
        return mongoTemplate.findById(operationId + ":" + command,
                SemesterDeletionParticipantReceiptDocument.class);
    }

    private String fenceState(long semesterId) {
        return mongoTemplate.findById(Long.toString(semesterId), SemesterArchiveFenceDocument.class)
                .getBarrierState();
    }

    private void assertFence(long semesterId, long version, String state) {
        SemesterArchiveFenceDocument actual = mongoTemplate.findById(
                Long.toString(semesterId), SemesterArchiveFenceDocument.class);
        assertThat(actual.getStateVersion()).isEqualTo(version);
        assertThat(actual.getBarrierState()).isEqualTo(state);
    }

    private long outboxCount(String eventType) {
        return mongoTemplate.count(Query.query(Criteria.where("event_type").is(eventType)), "attendance_outbox");
    }

    private Document latestOutbox(String eventType) {
        return mongoTemplate.findOne(Query.query(Criteria.where("event_type").is(eventType))
                        .with(org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "_id")),
                Document.class, "attendance_outbox");
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
