package ru.rutcampustrack.attendance.subjectdeletion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;

import ru.rutcampustrack.attendance.audit.LessonActionService;
import ru.rutcampustrack.attendance.config.MongoConvertersConfig;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.event.SemesterArchiveFence;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.entity.*;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;

/** Replica-set Mongo transactions, real effective mapping/history/outbox, no external service. */
@DataMongoTest
@Testcontainers
@Import({
    AttendanceSubjectDeletionParticipant.class,
    ru.rutcampustrack.attendance.event.LessonTransferParticipantService.class,
    ru.rutcampustrack.attendance.studentrequest.HeadmanRequestService.class,
    PairWriteCoordinator.class,
    SemesterArchiveFence.class,
    LessonActionService.class,
    MongoConvertersConfig.class,
    AttendanceSubjectDeletionIT.Config.class
})
class AttendanceSubjectDeletionIT {
    private static final String DATABASE =
            "subject_delete_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void connection(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        r.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean({"transactionManager", "mongoTransactionManager"})
        MongoTransactionManager transactions(MongoDatabaseFactory f) {
            return new MongoTransactionManager(f);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper mapper() {
            return new ObjectMapper();
        }

        @Bean
        MongoOutboxStorage outbox(MongoTemplate mongo) {
            return new MongoOutboxStorage(mongo, "subject_delete_test_outbox");
        }
    }

    @MockitoSpyBean MongoTemplate mongo;
    @Autowired AttendanceSubjectDeletionParticipant participant;
    @Autowired PairWriteCoordinator pairs;
    @Autowired MongoTransactionManager manager;
    @Autowired ru.rutcampustrack.attendance.event.LessonTransferParticipantService transfer;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.attendance.marking.AttendanceAttachmentService markAttachments;

    @MockitoSpyBean MongoOutboxStorage outbox;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.attendance.studentrequest.StudentRequestService studentAuthority;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient schedule;

    @Autowired ru.rutcampustrack.attendance.studentrequest.HeadmanRequestService headmanReads;

    @BeforeEach
    void freshOwnedDatabase() {
        reset(mongo, outbox);
        for (String collection : mongo.getCollectionNames()) mongo.dropCollection(collection);
    }

    @Test
    void emptySubjectCommitsOrReleasesWithPersistedEmptyFencesAndStableTerminalRetries() {
        for (var action : List.of(SubjectDeletionAction.RELEASE, SubjectDeletionAction.COMMIT)) {
            var c = emptyPrepare();
            participant.apply(c);
            participant.apply(c.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
            assertThat(epoch(c).getList("fence_ids", Long.class)).isEmpty();
            var terminal = c.toBuilder().setAction(action).build();
            var receipt = participant.apply(terminal);
            assertThat(receipt.getAffectedRows()).isZero();
            assertThat(receipt.getMixedTickets()).isZero();
            assertThat(participant.apply(terminal)).isEqualTo(receipt);
            assertThat(epoch(c).getString("state"))
                    .isEqualTo(action == SubjectDeletionAction.COMMIT ? "DELETED" : "RELEASED");
            for (var late : List.of(SubjectDeletionAction.PREPARE, SubjectDeletionAction.SEAL))
                assertThatThrownBy(() -> participant.apply(c.toBuilder().setAction(late).build()))
                        .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(
                            () ->
                                    participant.apply(
                                            c.toBuilder()
                                                    .setAction(
                                                            action == SubjectDeletionAction.COMMIT
                                                                    ? SubjectDeletionAction.RELEASE
                                                                    : SubjectDeletionAction.COMMIT)
                                                    .build()))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(mongo.count(new Query(), "student_checkin_pairs")).isZero();
        assertThat(mongo.count(new Query(), "subject_delete_test_outbox")).isEqualTo(1);
    }

    @Test
    void missingMalformedDuplicateOrNonIntegralSavedFenceIdsStillRefuseCommitAndRelease() {
        var c = emptyPrepare();
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
        List<Object> malformed =
                Arrays.asList(
                        null,
                        "not-a-list",
                        List.of(11L, 11L),
                        List.of(0L),
                        List.of(-1L),
                        List.of("11"),
                        Arrays.asList((Object) null),
                        List.of(11.5),
                        List.of(Double.MAX_VALUE));
        for (Object saved : malformed) {
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(c.getOperationId())),
                    saved == null
                            ? new Update().unset("fence_ids")
                            : new Update().set("fence_ids", saved),
                    "subject_deletion_participants");
            for (var action :
                    List.of(SubjectDeletionAction.COMMIT, SubjectDeletionAction.RELEASE)) {
                assertThatThrownBy(() -> participant.apply(c.toBuilder().setAction(action).build()))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(epoch(c).getString("state")).isEqualTo("SEALED");
                assertThat(mongo.count(new Query(), "subject_delete_test_outbox")).isZero();
            }
        }
    }

    @Test
    void releaseBeforePrepareDurablyRejectsLatePhasesAndEveryChangedTupleWithoutFences() {
        seed();
        var c = prepare();
        var release = c.toBuilder().setAction(SubjectDeletionAction.RELEASE).build();
        var receipt = participant.apply(release);
        var released = epoch(c);
        assertThat(released.getString("state")).isEqualTo("RELEASED");
        assertThat(released.getList("fence_ids", Long.class)).isEmpty();
        assertThat(participant.apply(release)).isEqualTo(receipt);
        assertLateAndMismatchedCommandsRefused(c);
        assertThat(epoch(c)).isEqualTo(released);
        assertThat(mongo.findById("target-mark", Document.class, "attendances")).isNotNull();
        assertThat(mongo.count(new Query(), "student_checkin_pairs")).isZero();
        assertThat(mongo.count(new Query(), "subject_delete_test_outbox")).isZero();
        assertWritableAfterRelease();
        var fresh = prepare();
        participant.apply(fresh);
        participant.apply(fresh.toBuilder().setAction(SubjectDeletionAction.RELEASE).build());
        assertWritableAfterRelease();
    }

    @Test
    void releaseAndPrepareReadingMissingEpochRaceThroughTheSameUniqueTransactionalKey()
            throws Exception {
        seed();
        var c = prepare();
        // Avoid implicit collection creation being the source of the transaction conflict.
        mongo.createCollection("subject_deletion_participants");
        assertWritableAfterRelease();
        var bothReadMissingEpoch = new CyclicBarrier(2);
        doAnswer(
                        call -> {
                            Object result = call.callRealMethod();
                            assertThat(result).isNull();
                            bothReadMissingEpoch.await(5, TimeUnit.SECONDS);
                            return result;
                        })
                .when(mongo)
                .findById(
                        eq(c.getOperationId()),
                        eq(Document.class),
                        eq("subject_deletion_participants"));
        var pool = Executors.newFixedThreadPool(2);
        List<Future<Throwable>> attempts = new ArrayList<>();
        List<Throwable> outcomes = new ArrayList<>();
        try {
            for (var action : List.of(SubjectDeletionAction.PREPARE, SubjectDeletionAction.RELEASE))
                attempts.add(
                        pool.submit(
                                () -> {
                                    try {
                                        participant.apply(c.toBuilder().setAction(action).build());
                                        return null;
                                    } catch (RuntimeException failure) {
                                        return failure;
                                    }
                                }));
            for (var attempt : attempts) outcomes.add(attempt.get(15, TimeUnit.SECONDS));
        } finally {
            for (var attempt : attempts) if (!attempt.isDone()) attempt.cancel(true);
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            reset(mongo);
        }
        assertThat(outcomes.stream().filter(Objects::isNull).count()).isEqualTo(1);
        for (Throwable failure : outcomes) {
            if (failure == null) continue;
            assertThat(isMongoEpochRace(failure))
                    .as("unique epoch race aborts the losing transaction")
                    .isTrue();
        }
        // A failed RELEASE is retried by the coordinator; only its durable success is acknowledged.
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.RELEASE).build());
        assertThat(epoch(c).getString("state")).isEqualTo("RELEASED");
        assertLateAndMismatchedCommandsRefused(c);
        assertWritableAfterRelease();
        assertThat(mongo.findById("target-mark", Document.class, "attendances")).isNotNull();
        assertThat(mongo.count(new Query(), "subject_delete_test_outbox")).isZero();
    }

    private Document epoch(SubjectDeletionCommand c) {
        return mongo.findById(c.getOperationId(), Document.class, "subject_deletion_participants");
    }

    private void assertLateAndMismatchedCommandsRefused(SubjectDeletionCommand c) {
        for (var action :
                List.of(
                        SubjectDeletionAction.PREPARE,
                        SubjectDeletionAction.SEAL,
                        SubjectDeletionAction.COMMIT))
            assertThatThrownBy(() -> participant.apply(c.toBuilder().setAction(action).build()))
                    .isInstanceOf(IllegalStateException.class);
        List<UnaryOperator<SubjectDeletionCommand.Builder>> changes =
                List.of(
                        b -> b.setActorId(99),
                        b -> b.setSubjectId(102).clearLessons(),
                        b -> b.setSemesterId(4).clearLessons(),
                        b -> b.setGroupId(6).clearLessons(),
                        b -> b.setParticipantDigest("b".repeat(64)),
                        b -> b.setManifestDigest("b".repeat(64)),
                        SubjectDeletionCommand.Builder::clearLessons);
        for (var change : changes)
            assertThatThrownBy(
                            () ->
                                    participant.apply(
                                            change.apply(c.toBuilder())
                                                    .setAction(SubjectDeletionAction.RELEASE)
                                                    .build()))
                    .isInstanceOf(IllegalStateException.class);
    }

    private void assertWritableAfterRelease() {
        assertThat(
                        mongo.count(
                                Query.query(
                                        Criteria.where("subject_delete_operation").exists(true)),
                                "student_checkin_pairs"))
                .isZero();
        new TransactionTemplate(manager)
                .executeWithoutResult(status -> pairs.lockLessons(3L, List.of(11L, 12L), 5L, NOW));
    }

    private static boolean isMongoEpochRace(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof com.mongodb.MongoException mongoFailure
                    && (mongoFailure.hasErrorLabel("TransientTransactionError")
                            || mongoFailure.getCode() == 112
                            || mongoFailure.getCode() == 11000)) return true;
        return false;
    }

    private SubjectDeletionCommand emptyPrepare() {
        var preview =
                SubjectDeletionCommand.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setSubjectId(101)
                        .setSemesterId(3)
                        .setGroupId(5)
                        .setActorId(9)
                        .setAction(SubjectDeletionAction.PREVIEW)
                        .build();
        return preview.toBuilder()
                .setAction(SubjectDeletionAction.PREPARE)
                .setParticipantDigest(participant.apply(preview).getParticipantDigest())
                .setManifestDigest("a".repeat(64))
                .build();
    }

    @Test
    void submittedMixedTicketBlocksOtherSubjectTransferBetweenSealAndCommit() {
        seed();
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is("mixed-ticket")),
                new Update()
                        .set("status", ExcuseTicketStatus.SUBMITTED)
                        .unset("decision_by")
                        .unset("decision_at"),
                "excuse_tickets");
        var c = prepare();
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
        var before = mongo.findById("mixed-ticket", Document.class, "excuse_tickets");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("lesson_id", 12L);
        source.put("schedule_item_id", 800L);
        source.put("generation", 2L);
        source.put("lesson_revision", 7L);
        source.put("occurrence_revision", 5L);
        source.put("date", "2026-10-15");
        source.put("lesson_number", 2);
        source.put("start_time", "09:00");
        source.put("end_time", "10:30");
        source.put("room", null);
        Map<String, Object> target = new LinkedHashMap<>(source);
        target.put("lesson_id", 13L);
        target.put("date", "2026-10-16");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation_id", UUID.randomUUID().toString());
        payload.put("request_key", UUID.randomUUID().toString());
        payload.put("actor_id", 9L);
        payload.put("group_id", 5L);
        payload.put("semester_id", 3L);
        payload.put("occurrence_id", 700L);
        payload.put("transfer_payload_hash", "a".repeat(64));
        payload.put("transfer_revision", 4L);
        payload.put("source", source);
        payload.put("target", target);
        payload.put("batch_index", 0);
        payload.put("batch_count", 1);
        payload.put("bindings", List.of());
        Map<String, Object> event =
                Map.of(
                        "event_type",
                        "lesson.transfer.requested",
                        "source",
                        "schedule-service",
                        "event_id",
                        UUID.randomUUID().toString(),
                        "event_version",
                        1,
                        "trace_id",
                        "00000000000000000000000000000001",
                        "occurred_at",
                        NOW.toString(),
                        "payload",
                        payload);
        assertThatThrownBy(() -> transfer.apply(event))
                .isInstanceOf(ru.rutcampustrack.attendance.exception.ConflictException.class);
        assertThat(mongo.findById("mixed-ticket", Document.class, "excuse_tickets"))
                .isEqualTo(before);
        assertThat(
                        mongo.exists(
                                Query.query(Criteria.where("_id").is(payload.get("operation_id"))),
                                "lesson_transfer_receipts"))
                .isFalse();
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.COMMIT).build());
        var retained = mongo.findById("mixed-ticket", ExcuseTicket.class);
        assertThat(retained.getStatus()).isEqualTo(ExcuseTicketStatus.SUBMITTED);
        assertThat(retained.getLessonIds()).containsExactly(12L);
        assertThat(retained.getSubjectCoverageAdjustments()).hasSize(1);
        new TransactionTemplate(manager)
                .executeWithoutResult(status -> pairs.lockLessons(3L, List.of(12L), 5L, NOW));
        assertThat(
                        mongo.findById(
                                        PairWriteCoordinator.lessonFenceId(12L),
                                        Document.class,
                                        "student_checkin_pairs")
                                .containsKey("subject_delete_operation"))
                .isFalse();
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(
                                                status ->
                                                        pairs.lockLessons(
                                                                3L, List.of(11L), 5L, NOW)))
                .isInstanceOf(ru.rutcampustrack.attendance.exception.ConflictException.class);
    }

    @Test
    void mixedAndSoleTicketsExactMarksFilesAndReplayRemainAtomic() {
        seed();
        var c = prepare();
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
        var result =
                participant.apply(c.toBuilder().setAction(SubjectDeletionAction.COMMIT).build());
        assertThat(result.getMixedTickets()).isEqualTo(1);
        assertThat(
                        mongo.exists(
                                Query.query(Criteria.where("_id").is("target-mark")),
                                "attendances"))
                .isFalse();
        assertThat(mongo.exists(Query.query(Criteria.where("_id").is("other-mark")), "attendances"))
                .isTrue();
        assertThat(mongo.findById("sole-ticket", ExcuseTicket.class)).isNull();
        var mixed = mongo.findById("mixed-ticket", ExcuseTicket.class);
        assertThat(mixed).isNotNull();
        assertThat(mixed.getLessonIds()).containsExactly(12L);
        assertThat(mixed.getOriginalLessonIds()).containsExactly(12L);
        assertThat(mixed.getLessonSnapshots())
                .extracting(StudentLessonSnapshotDocument::getLessonId)
                .containsExactly(12L);
        assertThat(mixed.getStatus()).isEqualTo(ExcuseTicketStatus.APPROVED);
        assertThat(mixed.getDecisionBy()).isEqualTo(8L);
        assertThat(mixed.getDecisionAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(mixed.getSubjectCoverageAdjustments()).hasSize(1);
        var headman =
                new ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity(
                        9L, ru.rutcampustrack.attendance.contract.enums.UserRole.STUDENT, 5L, true);
        var archive = headmanReads.list(headman, "ARCHIVE", 0, 20, "EXCUSE", null, null, null);
        assertThat(archive.content())
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.id()).isEqualTo("mixed-ticket");
                            assertThat(row.lessonCount()).isEqualTo(1);
                            assertThat(row.coverageStart()).isEqualTo(LocalDate.of(2026, 10, 15));
                        });
        assertThat(
                        headmanReads
                                .list(
                                        headman,
                                        "ARCHIVE",
                                        0,
                                        20,
                                        "EXCUSE",
                                        null,
                                        "2026-10-14",
                                        "2026-10-14")
                                .content())
                .isEmpty();
        assertThat(
                        headmanReads
                                .list(
                                        headman,
                                        "ARCHIVE",
                                        0,
                                        20,
                                        "EXCUSE",
                                        null,
                                        "2026-10-15",
                                        "2026-10-15")
                                .content())
                .hasSize(1);

        assertThat(mongo.findById("mixed-file", Document.class, "request_attachments")).isNotNull();
        assertThat(mongo.findById("sole-file", Document.class, "request_attachments")).isNull();
        assertThat(
                        mongo.findById("own-budget", Document.class, "student_late_checkin_budgets")
                                .getInteger("used"))
                .isEqualTo(5);
        long events = mongo.count(new Query(), "subject_delete_test_outbox");
        long history = mongo.count(new Query(), "lesson_actions");
        assertThat(history).isEqualTo(1);
        assertThat(events).isEqualTo(2);
        assertThat(participant.apply(c.toBuilder().setAction(SubjectDeletionAction.COMMIT).build()))
                .isEqualTo(result);
        assertThat(mongo.count(new Query(), "subject_delete_test_outbox")).isEqualTo(events);
        assertThat(mongo.count(new Query(), "lesson_actions")).isEqualTo(history);
        assertThat(pairs.isSubjectDeleted(11, 3, 5L)).isTrue();
        assertThat(pairs.isSubjectDeleted(12, 3, 5L)).isFalse();
        assertThatThrownBy(() -> pairs.isSubjectDeleted(11, 4, 5L))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .execute(
                                                s -> {
                                                    pairs.lockLessons(3, List.of(11L), 5L, NOW);
                                                    return null;
                                                }))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void outboxRefusalRollsBackAllMixedCoverageHistoryMarksAndCommitTombstone() {
        seed();
        var c = prepare();
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
        doThrow(new IllegalStateException("Owned subject outbox control"))
                .when(outbox)
                .save(eq("dashboard.invalidated"), anyString());
        try {
            assertThatThrownBy(
                            () ->
                                    participant.apply(
                                            c.toBuilder()
                                                    .setAction(SubjectDeletionAction.COMMIT)
                                                    .build()))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            reset(outbox);
        }
        assertThat(mongo.findById("mixed-ticket", ExcuseTicket.class).getLessonIds())
                .containsExactly(11L, 12L);
        assertThat(
                        mongo.findById("mixed-ticket", ExcuseTicket.class)
                                .getSubjectCoverageAdjustments())
                .isNull();
        assertThat(mongo.findById("sole-ticket", ExcuseTicket.class)).isNotNull();
        assertThat(mongo.findById("target-mark", Document.class, "attendances")).isNotNull();
        assertThat(mongo.count(new Query(), "lesson_actions")).isZero();
        assertThat(mongo.count(new Query(), "subject_delete_test_outbox")).isZero();
        assertThat(pairs.isSubjectDeleted(11, 3, 5L)).isFalse();
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.COMMIT).build());
        assertThat(mongo.findById("mixed-ticket", ExcuseTicket.class).getLessonIds())
                .containsExactly(12L);
    }

    @Test
    void staleSnapshotForeignScopeAndPreIntentReleaseCannotDeleteOrLeaveFence() {
        seed();
        var c = prepare();
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is("target-mark")),
                new Update().set("status", "present"),
                "attendances");
        assertThatThrownBy(() -> participant.apply(c)).isInstanceOf(RuntimeException.class);
        assertThat(mongo.count(new Query(), "subject_deletion_participants")).isZero();
        var fresh = prepare();
        participant.apply(fresh);
        assertThatThrownBy(
                        () ->
                                participant.apply(
                                        fresh.toBuilder()
                                                .setGroupId(6)
                                                .setAction(SubjectDeletionAction.COMMIT)
                                                .build()))
                .isInstanceOf(RuntimeException.class);
        participant.apply(fresh.toBuilder().setAction(SubjectDeletionAction.RELEASE).build());
        new TransactionTemplate(manager)
                .execute(
                        s -> {
                            pairs.lockLessons(3, List.of(11L), 5L, NOW);
                            return null;
                        });
        assertThat(mongo.findById("mixed-ticket", ExcuseTicket.class).getLessonIds())
                .containsExactly(11L, 12L);
    }

    private SubjectDeletionCommand prepare() {
        var preview =
                SubjectDeletionCommand.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setSubjectId(101)
                        .setSemesterId(3)
                        .setGroupId(5)
                        .setActorId(9)
                        .setAction(SubjectDeletionAction.PREVIEW)
                        .addLessons(
                                SubjectDeletionLesson.newBuilder()
                                        .setLessonId(11)
                                        .setSubjectId(101)
                                        .setSemesterId(3)
                                        .setGroupId(5)
                                        .setOccurrenceId("1")
                                        .setCurrentLessonId(11))
                        .build();
        var result = participant.apply(preview);
        return preview.toBuilder()
                .setAction(SubjectDeletionAction.PREPARE)
                .setParticipantDigest(result.getParticipantDigest())
                .setManifestDigest("a".repeat(64))
                .build();
    }

    private void seed() {
        mongo.insert(
                new Document("_id", "target-mark")
                        .append("lesson_id", 11L)
                        .append("subject_id", 101L)
                        .append("group_id", 5L)
                        .append("semester_id", 3L)
                        .append("status", "absent"),
                "attendances");
        mongo.insert(
                new Document("_id", "other-mark")
                        .append("lesson_id", 12L)
                        .append("subject_id", 102L)
                        .append("group_id", 5L)
                        .append("semester_id", 3L),
                "attendances");
        var first = snapshot(11, 101);
        var second = snapshot(12, 102);
        mongo.insert(
                ExcuseTicket.builder()
                        .id("mixed-ticket")
                        .studentId(7L)
                        .groupId(5L)
                        .semesterId(3L)
                        .lessonIds(List.of(11L, 12L))
                        .originalLessonIds(List.of(11L, 12L))
                        .lessonSnapshots(List.of(first, second))
                        .status(ExcuseTicketStatus.APPROVED)
                        .decisionBy(8L)
                        .decisionAt(NOW.minusSeconds(10))
                        .createdAt(NOW.minusSeconds(30))
                        .updatedAt(NOW.minusSeconds(10))
                        .build());
        mongo.insert(
                ExcuseTicket.builder()
                        .id("sole-ticket")
                        .studentId(7L)
                        .groupId(5L)
                        .semesterId(3L)
                        .lessonIds(List.of(11L))
                        .originalLessonIds(List.of(11L))
                        .lessonSnapshots(List.of(first))
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .createdAt(NOW.minusSeconds(30))
                        .updatedAt(NOW.minusSeconds(10))
                        .build());
        for (String id : List.of("mixed", "sole"))
            mongo.insert(
                    new Document("_id", id + "-file")
                            .append("request_id", id + "-ticket")
                            .append("owner_student_id", 7L)
                            .append("group_id", 5L)
                            .append("semester_id", 3L)
                            .append("sha256", "a".repeat(64))
                            .append("data", new byte[] {1, 2}),
                    "request_attachments");
        mongo.insert(
                new Document("_id", "own-budget").append("used", 5).append("limit", 5),
                "student_late_checkin_budgets");
    }

    private StudentLessonSnapshotDocument snapshot(long lesson, long subject) {
        return StudentLessonSnapshotDocument.builder()
                .lessonId(lesson)
                .subjectId(subject)
                .groupId(5L)
                .semesterId(3L)
                .date(LocalDate.of(2026, 10, subject == 101 ? 14 : 15))
                .subjectName("Own synthetic subject")
                .subjectType("LECTURE")
                .build();
    }
}
