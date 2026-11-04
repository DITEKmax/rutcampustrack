package ru.rutcampustrack.attendance.constructor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.attendance.audit.*;
import ru.rutcampustrack.attendance.config.MongoConvertersConfig;
import ru.rutcampustrack.attendance.event.*;
import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.constructor.grpc.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;

/** Replica-set transactions: history, deletion races, late accepted effects, durable closure. */
@DataMongoTest
@Testcontainers
@Import({ConstructorDeletionParticipant.class, ConstructorPastMaterializer.class,
        PairWriteCoordinator.class, SemesterArchiveFence.class, LessonActionService.class,
        ActionEventHandler.class, MongoConvertersConfig.class, ConstructorAttendanceIT.Config.class})
class ConstructorAttendanceIT {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.15")
            .withReuse(false)
            .withLabel("rct.fixture.scope", "constructor-attendance-" + UUID.randomUUID())
            .withCreateContainerCmdModifier(command -> command.getHostConfig()
                    .withMemory(1024L * 1024 * 1024)
                    .withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(27017)))
                    .withPublishAllPorts(false));
    private static final String DATABASE = "constructor_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final ByteString HASH = ConstructorHashes.hash(Map.of("fixture", 1));

    @DynamicPropertySource static void connection(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        r.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @TestConfiguration(proxyBeanMethods = false) static class Config {
        @Bean({"transactionManager", "mongoTransactionManager"}) MongoTransactionManager manager(MongoDatabaseFactory f) { return new MongoTransactionManager(f); }
        @Bean TransactionTemplate transactions(MongoTransactionManager manager) { return new TransactionTemplate(manager); }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean MongoOutboxStorage outbox(MongoTemplate m) { return new MongoOutboxStorage(m, "constructor_test_outbox"); }
        @Bean ScheduleGrpcClient schedule() { return mock(ScheduleGrpcClient.class); }
        @Bean AcademicGrpcClient academic() { return mock(AcademicGrpcClient.class); }
        @Bean LessonEventService lessons(MongoTemplate mongo, ScheduleGrpcClient schedule, AcademicGrpcClient academic, PairWriteCoordinator pairs, LessonActionService actions) {
            var service = new LessonEventService(mongo, schedule, academic, mock(ru.rutcampustrack.attendance.semester.SemesterCacheService.class), Runnable::run, pairs,
                    mock(ru.rutcampustrack.attendance.marking.AttendanceAttachmentService.class));
            service.setActions(actions); return service;
        }
    }

    @Autowired MongoTemplate mongo;
    @Autowired ConstructorDeletionParticipant deletion;
    @Autowired ConstructorPastMaterializer materializer;
    @Autowired TransactionTemplate transactions;
    @Autowired PairWriteCoordinator pairs;
    @Autowired LessonEventService lessons;
    @Autowired LessonActionService actions;
    @Autowired ActionEventHandler events;
    @Autowired ScheduleGrpcClient schedule;
    @Autowired AcademicGrpcClient academic;

    @BeforeEach void isolatedDatabase() {
        for (String name : mongo.getCollectionNames()) mongo.dropCollection(name);
        reset(schedule, academic);
        when(schedule.getLessonById(11L)).thenReturn(lesson(11, 1));
        when(academic.getGroupMembers(7L, LocalDate.parse("2026-09-01"), 9L)).thenReturn(roster());
    }

    @AfterAll static void stopOwnedFixture() { MONGO.stop(); }

    @Test void autoOnlyFamilyDeletesAllGenerationsAndLateEventsCannotRepopulate() {
        materializer.apply(batch());
        Document materialized = mongo.findOne(Query.query(Criteria.where("lesson_id").is(11L)), Document.class, "attendances");
        assertThat(materialized.getString("source")).isEqualTo("auto_scheduler");
        assertThat(materialized.getString("status")).isEqualTo("absent");
        transactions.executeWithoutResult(s -> lessons.processLessonCancelled(11L, 9L, 7L));
        Document cancelled = mongo.findOne(Query.query(Criteria.where("lesson_id").is(11L)), Document.class, "attendances");
        assertThat(cancelled.getString("source")).isEqualTo("auto_scheduler");
        assertThat(cancelled.getString("status")).isEqualTo("cancelled");
        // Historical uppercase BSON remains eligible alongside real converted lowercase rows.
        mongo.insert(mark("old-generation", 12, "ABSENT", "AUTO_SCHEDULER"), "attendances");
        mongo.insert(mark("old-cancelled-generation", 12, "CANCELLED", "AUTO_SCHEDULER").append("user_id", 22L), "attendances");
        var preparedSnapshot = lessons.prepareLessonClosed(11L, 7L, 9L);
        var c = preview();
        assertThat(c.getParticipantDigest()).hasSize(32);
        finish(c);
        assertThat(mongo.count(new Query(), "attendances")).isZero();
        assertThat(mongo.count(new Query(), "lesson_actions")).isZero();
        assertThat(pairs.isSubjectDeleted(11, 9, 7L)).isTrue();
        transactions.executeWithoutResult(s -> lessons.applyLessonClosed(preparedSnapshot));
        assertThat(materializer.apply(batch()).getLessons(0).getState()).isEqualTo("TOMBSTONED");
        transactions.executeWithoutResult(s -> events.accept(scheduleEvent("delayed-create")));
        assertThat(mongo.count(new Query(), "attendances")).isZero();
        assertThat(mongo.count(new Query(), "lesson_actions")).isZero();
        assertThat(deletion.apply(c.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT).build()).getState()).isEqualTo("DELETED");
        assertThatThrownBy(() -> deletion.apply(c.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE).build())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> pairs.lock(9, 21, 11, 7, NOW))).isInstanceOf(ru.rutcampustrack.attendance.exception.ConflictException.class);
    }

    @Test void realAndClearedHistoryRequestsOriginalSnapshotsAndUnknownHistoryProtectWholeLogicalOccurrence() {
        List<Document> histories = List.of(
                mark("manual", 11, "PRESENT", "HEADMAN"),
                new Document("_id", "cleared").append("lesson_id", 11L).append("group_id", 7L).append("semester_id", 9L).append("source", "attendance-service")
                        .append("payload", new Document("action", "ATTENDANCE_CLEARED").append("actor", new Document("user_id", 3L))),
                new Document("_id", "old-request").append("group_id", 7L).append("semester_id", 9L).append("original_lesson_ids", List.of(11L))
                        .append("effective_original_lesson_ids", List.of(99L)).append("lesson_ids", List.of(99L)),
                new Document("_id", "snapshot-request").append("group_id", 7L).append("semester_id", 9L).append("lesson_ids", List.of(99L))
                        .append("lesson_snapshots", List.of(new Document("lesson_id", 12L))),
                mark("unknown", 11, "ABSENT", "UNKNOWN"));
        for (int i=0; i<histories.size(); i++) {
            String collection = i == 1 ? "lesson_actions" : i == 2 || i == 3 ? "excuse_tickets" : "attendances";
            mongo.insert(histories.get(i), collection);
            var receipt = deletion.apply(command(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW));
            assertThat(receipt.getProtectedCount()).isEqualTo(1); // two physical generations, one occurrence
            var prepare = command(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE).toBuilder().setParticipantDigest(receipt.getParticipantDigest()).build();
            assertThatThrownBy(() -> deletion.apply(prepare)).isInstanceOf(IllegalStateException.class);
            assertThat(mongo.count(new Query(), collection)).isEqualTo(1);
            mongo.dropCollection(collection);
        }
    }

    @Test void onlyExactTrustedSchedulePayloadIsExemptAndNumericJsonRepresentationsAgree() {
        var envelope = scheduleEvent("creation");
        @SuppressWarnings("unchecked") Map<String,Object> payload = (Map<String,Object>)envelope.get("payload");
        transactions.executeWithoutResult(s -> events.accept(envelope));
        var untrusted = deletion.apply(command(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW));
        assertThat(untrusted.getProtectedCount()).isEqualTo(1);
        var c = command(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW).toBuilder()
                .setScope(command(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW).getScope().toBuilder().addAllowedSystemAuditHashes(ConstructorHashes.hash(payload))).build();
        var receipt = deletion.apply(c);
        assertThat(receipt.getProtectedCount()).isZero();
        Map<String,Object> drift = new LinkedHashMap<>(payload); drift.put("group_id", 7.0);
        assertThat(ConstructorHashes.hash(drift)).isEqualTo(ConstructorHashes.hash(payload));
        drift.put("action", "LESSON_CANCELLED");
        assertThat(ConstructorHashes.hash(drift)).isNotEqualTo(ConstructorHashes.hash(payload));
    }

    @Test void lateAuditRetainsExistingArchivedSemesterSemanticsButStillHonorsConstructorFence() {
        mongo.insert(new Document("_id", "9").append("semester_id", 9L).append("barrier_state", "ARCHIVE_SEALED"), "semester_archive_fences");
        transactions.executeWithoutResult(s -> events.accept(scheduleEvent("archived-audit")));
        assertThat(mongo.findById("archived-audit", Document.class, "lesson_actions")).isNotNull();
        mongo.dropCollection("semester_archive_fences");
        mongo.dropCollection("lesson_actions");
        var c = preview(); deletion.apply(c);
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> events.accept(scheduleEvent("fenced-audit"))))
                .isInstanceOf(ru.rutcampustrack.attendance.exception.ConflictException.class);
        assertThat(mongo.findById("fenced-audit", Document.class, "lesson_actions")).isNull();
    }

    @Test void releasedDenialBlocksDelayedPrepareAndRollbackLeavesNeitherFenceNorPartialBusinessDelete() {
        var c = preview();
        assertThat(deletion.apply(c.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE).build()).getState()).isEqualTo("RELEASED");
        assertThatThrownBy(() -> deletion.apply(c)).isInstanceOf(IllegalStateException.class);
        var next = c.toBuilder().setOperationId(UUID.randomUUID().toString()).build();
        transactions.executeWithoutResult(s -> { deletion.apply(next); s.setRollbackOnly(); });
        assertThat(deletion.isAdmitted(next)).isFalse();
        transactions.executeWithoutResult(s -> pairs.lock(9, 21, 11, 7, NOW));
    }

    @Test void preparedFenceBlocksConcurrentRealWriteAndReleasedOperationRestoresWrites() throws Exception {
        var c = preview();
        deletion.apply(c);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var write = pool.submit(() -> transactions.executeWithoutResult(s -> {
                pairs.lock(9, 21, 11, 7, NOW);
                mongo.insert(mark("concurrent", 11, "PRESENT", "HEADMAN"), "attendances");
            }));
            assertThatThrownBy(() -> write.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ru.rutcampustrack.attendance.exception.ConflictException.class);
        }
        assertThat(mongo.count(new Query(), "attendances")).isZero();
        deletion.apply(c.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE).build());
        transactions.executeWithoutResult(s -> { pairs.lock(9, 21, 11, 7, NOW); mongo.insert(mark("after-release", 11, "PRESENT", "HEADMAN"), "attendances"); });
        assertThat(deletion.apply(command(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW)).getProtectedCount()).isEqualTo(1);
    }

    @Test void closureRetriesKeepRealMarksAndRecoverCoverageAfterRetrospectiveBindingAdoption() {
        when(academic.getGroupMembers(7L, LocalDate.parse("2026-09-01"), 9L)).thenThrow(new ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException("Unavailable"));
        assertThat(materializer.apply(batch()).getLessons(0).getState()).isEqualTo("COVERAGE_UNAVAILABLE");
        assertThat(mongo.count(new Query(), "attendances")).isZero();
        reset(academic);
        when(academic.getGroupMembers(7L, LocalDate.parse("2026-09-01"), 9L)).thenReturn(roster());
        when(schedule.getLessonById(11L)).thenReturn(lesson(11, 2).toBuilder().setAssignmentId(300).setAssignedTeacherId(400).build());
        mongo.insert(mark("real", 11, "PRESENT", "STUDENT_GEO").append("user_id", 21L), "attendances");
        assertThat(materializer.apply(batch()).getLessons(0).getState()).isEqualTo("APPLIED");
        assertThat(materializer.apply(batch()).getLessons(0).getState()).isEqualTo("APPLIED");
        assertThat(mongo.count(new Query(), "attendances")).isEqualTo(2);
        assertThat(mongo.findById("real", Document.class, "attendances").getString("status")).isEqualTo("PRESENT");
        assertThat(mongo.count(Query.query(Criteria.where("payload.action").is("AUTO_ABSENT")), "lesson_actions")).isEqualTo(1);
        assertThatThrownBy(() -> materializer.apply(batch().toBuilder().setBatchHash(ConstructorHashes.hash("changed")).build())).isInstanceOf(IllegalStateException.class);
    }

    private ConstructorDeletionCommand preview() {
        var c = command(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW);
        var preview = deletion.apply(c);
        assertThat(preview.getProtectedCount()).isZero();
        return c.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE).setParticipantDigest(preview.getParticipantDigest()).build();
    }
    private void finish(ConstructorDeletionCommand c) {
        deletion.apply(c);
        deletion.apply(c.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_SEAL).build());
        deletion.apply(c.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT).build());
    }
    private static ConstructorDeletionCommand command(ConstructorDeletionAction action) {
        var scope = ConstructorDeletionScope.newBuilder().setGroupId(7).setSubjectId(8).setSemesterId(9).addScheduleItemIds(10).setScheduleManifestDigest(HASH);
        for (long id : List.of(11L,12L)) scope.addLessons(ConstructorLessonScope.newBuilder().setLessonId(id).setOccurrenceId(100).setScheduleItemId(10)
                .setLessonDate("2026-09-01").setLessonNumber(1).setLessonType("lecture").setGeneration(id-10).setLessonRevision(1).setOccurrenceRevision(1).setOriginProvenanceHash(HASH));
        return ConstructorDeletionCommand.newBuilder().setOperationId("10000000-0000-0000-0000-000000000001").setAction(action).setRequestHash(HASH).setScopeHash(HASH).setOriginalActorId(3).setScope(scope).build();
    }
    private static LessonResponse lesson(long id, long revision) {
        return LessonResponse.newBuilder().setId(id).setOccurrenceId(100).setScheduleItemId(10).setGroupId(7).setSubjectId(8).setSemesterId(9)
                .setDate("2026-09-01").setLessonNumber(1).setLessonType("lecture").setStartTime("08:00").setEndTime("09:30").setGeneration(1).setRevision(revision).setStatus("closed").build();
    }
    private static PastLessonMaterializationCommand batch() {
        return PastLessonMaterializationCommand.newBuilder().setOperationId("10000000-0000-0000-0000-000000000002").setGroupId(7).setSemesterId(9).setBatchIndex(0).setBatchCount(1).setBatchHash(HASH)
                .addLessons(PastLessonIdentity.newBuilder().setLessonId(11).setOccurrenceId(100).setExpectedRevision(1).setSnapshotHash(ConstructorHashes.lesson(lesson(11,1)))).build();
    }
    private static GroupMembersResponse roster() {
        return GroupMembersResponse.newBuilder().setAsOfDate("2026-09-01").setSemesterId(9).addStudents(StudentInfo.newBuilder().setUserId(21)).addStudents(StudentInfo.newBuilder().setUserId(22)).build();
    }
    private static Document mark(String id, long lesson, String status, String source) {
        return new Document("_id", id).append("lesson_id", lesson).append("user_id", 21L).append("group_id", 7L).append("subject_id", 8L).append("semester_id", 9L).append("status", status).append("source", source);
    }
    private static Map<String,Object> scheduleEvent(String id) {
        Map<String,Object> scope = Map.of("group_id",7L,"semester_id",9L,"lesson_id",11L);
        var p = LessonActionService.payload(7L,9L,11L,"LESSON_CREATED",LessonActionService.person(3L,"HEADMAN",null),null,null,scope,null,null,null,1);
        return Map.of("event_id",id,"event_type","schedule.action-recorded","event_version",1,"source","schedule-service","trace_id","test","occurred_at",NOW.toString(),"payload",p);
    }
}
