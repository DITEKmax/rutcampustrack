package ru.rutcampustrack.attendance.groupdeletion;

import static org.assertj.core.api.Assertions.*;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

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
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import ru.rutcampustrack.groupdeletion.grpc.*;

import java.util.*;

/** Real replica-set transactions and actual MongoTemplate mutations through the group fence. */
@DataMongoTest
@Testcontainers
@Import({
    AttendanceGroupDeletionParticipant.class,
    GroupDeletionMongoFence.class,
    GroupDeletionMongoWrites.class,
    AttendanceGroupDeletionIT.Config.class,
    ru.rutcampustrack.attendance.student.PairWriteCoordinator.class,
    ru.rutcampustrack.attendance.event.SemesterArchiveFence.class,
    ru.rutcampustrack.attendance.event.LessonEventService.class
})
class AttendanceGroupDeletionIT {
    private static final String DATABASE =
            "group_delete_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    static final MongoDBContainer MONGO = mongo();

    private static MongoDBContainer mongo() {
        boolean bounded = "1".equals(System.getenv("RCT_GROUP_DELETION_BOUNDED"));
        var image =
                bounded
                        ? DockerImageName.parse(
                                        "bitnamilegacy/mongodb:7.0@sha256:"
                                                + "16a57fa081f18e7acd1b4de1cd74f3e294cc6de861007e6fe3a418eb61db8a54")
                                .asCompatibleSubstituteFor("mongo")
                        : DockerImageName.parse("mongo:7.0");
        var container =
                new MongoDBContainer(image)
                        .withReuse(false)
                        .withLabel("rct.fixture.owner", DATABASE)
                        .withCreateContainerCmdModifier(
                                command ->
                                        command.getHostConfig()
                                                .withMemory(512L * 1024 * 1024)
                                                .withMemorySwap(512L * 1024 * 1024)
                                                .withNanoCPUs(500_000_000L));
        if (bounded)
            container
                    .withImagePullPolicy(new CachedImagesOnlyPullPolicy())
                    .withStartupAttempts(1)
                    .withStartupTimeout(java.time.Duration.ofSeconds(90))
                    .withCreateContainerCmdModifier(
                            command -> {
                                // The cached Bitnami entrypoint cannot accept MongoDBContainer's
                                // bare --replSet command. Start the same MongoDB binary on a fresh
                                // writable path; Testcontainers still initializes docker-rs.
                                command.withEntrypoint("/bin/bash", "-ec")
                                        .withCmd(
                                                "mkdir -p /tmp/rct-group-mongo && exec"
                                                    + " /opt/bitnami/mongodb/bin/mongod --replSet"
                                                    + " docker-rs --bind_ip_all --dbpath"
                                                    + " /tmp/rct-group-mongo"
                                                    + " --wiredTigerCacheSizeGB 0.25");
                                command.getHostConfig()
                                        .withPortBindings(
                                                new PortBinding(
                                                        Ports.Binding.bindIpAndPort("127.0.0.1", 0),
                                                        new ExposedPort(27017)));
                            });
        return container;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        r.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean({"transactionManager", "mongoTransactionManager"})
        MongoTransactionManager manager(MongoDatabaseFactory factory) {
            return new MongoTransactionManager(factory);
        }
    }

    @Autowired MongoTemplate mongo;
    @Autowired AttendanceGroupDeletionParticipant participant;
    @Autowired GroupDeletionMongoFence fences;
    @Autowired MongoTransactionManager manager;
    @Autowired ru.rutcampustrack.attendance.event.LessonEventService lessonEvents;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient schedule;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.attendance.grpc.AcademicGrpcClient academic;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.attendance.semester.SemesterCacheService semesters;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.attendance.marking.AttendanceAttachmentService attachments;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.attendance.audit.LessonActionService actions;

    @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "grpcTaskExecutor")
    org.springframework.core.task.TaskExecutor executor;

    @BeforeEach
    void fixture() {
        for (String collection : mongo.getCollectionNames()) mongo.dropCollection(collection);
        for (String collection : GroupDeletionMongoFence.COLLECTIONS)
            mongo.createCollection(collection);
        mongo.createCollection(GroupDeletionMongoFence.FENCES);
        mongo.createCollection("group_deletion_participants");
        mongo.createCollection(GroupDeletionMongoFence.SCOPES);
        mongo.createCollection("group_deletion_lessons");
        mongo.insert(row("own", 10, 20), "attendances");
        mongo.insert(row("other", 11, 21), "attendances");
        mongo.insert(row("own-ticket", 10, 20), "excuse_tickets");
        mongo.insert(row("own-action", 10, 20), "lesson_actions");
        mongo.insert(
                new Document("_id", "quota")
                        .append("student_id", 7L)
                        .append("semester_id", 30L)
                        .append("used", 2),
                "student_late_checkin_budgets");
    }

    @Test
    void commitDeletesOnlyOwnGroupAndProtectsLateWritesWhileQuotaAndForeignRowsRemain() {
        var c = prepare();
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(GroupDeletionAction.SEAL).build());
        assertThatThrownBy(() -> mongo.insert(row("late", 10, 20), "attendances"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                mongo.updateFirst(
                                        Query.query(Criteria.where("_id").is("own")),
                                        new Update().set("status", "changed"),
                                        "attendances"))
                .isInstanceOf(RuntimeException.class);
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is("other")),
                new Update().set("status", "allowed"),
                "attendances");
        var commit = c.toBuilder().setAction(GroupDeletionAction.COMMIT).build();
        var receipt = participant.apply(commit);
        assertThat(mongo.count(Query.query(Criteria.where("group_id").is(10L)), "attendances"))
                .isZero();
        assertThat(mongo.count(new Query(), "excuse_tickets")).isZero();
        assertThat(mongo.count(new Query(), "lesson_actions")).isZero();
        assertThat(mongo.findById("other", Document.class, "attendances").getString("status"))
                .isEqualTo("allowed");
        assertThat(
                        mongo.findById("quota", Document.class, "student_late_checkin_budgets")
                                .getInteger("used"))
                .isEqualTo(2);
        assertThat(participant.apply(commit)).isEqualTo(receipt);
        assertThat(fences.deletedLesson(20, 30, 10L)).isTrue();
        assertThat(fences.deletedLesson(22, 31, null)).isTrue();
        assertThatThrownBy(() -> mongo.insert(row("resurrection", 10, 20), "attendances"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                participant.apply(
                                        c.toBuilder()
                                                .setAction(GroupDeletionAction.RELEASE)
                                                .build()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void staleSnapshotAndReleasedEpochCannotEraseRowsOrFenceAnotherOperation() {
        var stale = prepare();
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is("own")),
                new Update().set("status", "changed"),
                "attendances");
        assertThatThrownBy(() -> participant.apply(stale)).isInstanceOf(RuntimeException.class);
        assertThat(mongo.count(new Query(), "attendances")).isEqualTo(2);
        var c = prepare();
        var release = c.toBuilder().setAction(GroupDeletionAction.RELEASE).build();
        var receipt = participant.apply(release);
        assertThat(participant.apply(release)).isEqualTo(receipt);
        assertThatThrownBy(() -> participant.apply(c)).isInstanceOf(RuntimeException.class);
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is("own")),
                new Update().set("status", "allowed"),
                "attendances");
    }

    @Test
    void lessonOnlyCancellationInventoryAndNoRowEventCannotBypassPreparedGroup() {
        // Baseline marker and pair carry no group field; ownership comes from the exact lesson set.
        mongo.insert(
                new Document("_id", "20")
                        .append("lesson_id", 20L)
                        .append("semester_id", 30L)
                        .append("marked_at", java.util.Date.from(java.time.Instant.now())),
                "lesson_cancellation_markers");
        mongo.insert(
                new Document("_id", "lesson:20").append("lesson_id", 20L), "student_checkin_pairs");
        var c = prepare();
        var preview =
                participant.apply(c.toBuilder().setAction(GroupDeletionAction.PREVIEW).build());
        assertThat(preview.getCountsOrDefault("lesson_cancellation_markers", 0)).isEqualTo(1);
        assertThat(preview.getCountsOrDefault("student_checkin_pairs", 0)).isEqualTo(1);
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(GroupDeletionAction.SEAL).build());
        assertThatThrownBy(() -> lessonEvents.processLessonCancelled(22L, 31L))
                .isInstanceOf(RuntimeException.class);
        assertThat(mongo.findById("lesson:22", Document.class, "student_checkin_pairs")).isNull();
        assertThat(
                        mongo.count(
                                Query.query(Criteria.where("lesson_id").is(22L)),
                                "lesson_cancellation_markers"))
                .isZero();
        participant.apply(c.toBuilder().setAction(GroupDeletionAction.COMMIT).build());
        assertThat(mongo.count(new Query(), "lesson_cancellation_markers")).isZero();
        assertThat(mongo.count(new Query(), "student_checkin_pairs")).isZero();
        // Real delayed handler observes the committed lesson tombstone before creating any row.
        lessonEvents.processLessonCancelled(22L, 31L);
        assertThat(mongo.count(new Query(), "lesson_cancellation_markers")).isZero();
        assertThat(mongo.count(new Query(), "student_checkin_pairs")).isZero();
        assertThatThrownBy(() -> lessonEvents.processLessonCancelled(999L, 31L))
                .isInstanceOf(RuntimeException.class);
        assertThat(mongo.count(new Query(), "lesson_cancellation_markers")).isZero();
    }

    @Test
    void authoritativeNoRowCancellationStillWorksBeforeAnyGroupDeletion() {
        lessonEvents.processLessonCancelled(23L, 31L, 11L);
        assertThat(
                        mongo.count(
                                Query.query(Criteria.where("lesson_id").is(23L)),
                                "lesson_cancellation_markers"))
                .isEqualTo(1);
        assertThat(
                        mongo.findById("lesson:23", Document.class, "student_checkin_pairs")
                                .getLong("group_id"))
                .isEqualTo(11);
    }

    private GroupDeletionCommand prepare() {
        var preview =
                GroupDeletionCommand.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setGroupId(10)
                        .setActorId(42)
                        .setAction(GroupDeletionAction.PREVIEW)
                        .addLessons(
                                GroupDeletionLesson.newBuilder().setLessonId(20).setSemesterId(30))
                        .addLessons(
                                GroupDeletionLesson.newBuilder().setLessonId(22).setSemesterId(31))
                        .build();
        return preview.toBuilder()
                .setAction(GroupDeletionAction.PREPARE)
                .setPreviewDigest("a".repeat(64))
                .setParticipantDigest(participant.apply(preview).getParticipantDigest())
                .build();
    }

    private Document row(String id, long group, long lesson) {
        return new Document("_id", id)
                .append("group_id", group)
                .append("lesson_id", lesson)
                .append("semester_id", 30L)
                .append("status", "original");
    }
}
