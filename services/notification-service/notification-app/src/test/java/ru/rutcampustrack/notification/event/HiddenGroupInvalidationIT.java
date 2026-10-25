package ru.rutcampustrack.notification.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import ru.rutcampustrack.notification.history.*;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;
import ru.rutcampustrack.notification.push.*;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;
import ru.rutcampustrack.shared.events.IdempotencyGuard;
import ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore;

import java.util.*;

/** Only the changed hidden-hint transaction: real claim/rollback/commit, no provider or broker. */
@DataMongoTest
@Testcontainers
@Import({EventConsumer.class, HiddenGroupInvalidationIT.Config.class})
class HiddenGroupInvalidationIT {
    private static final String DATABASE =
            "group_live_" + UUID.randomUUID().toString().replace("-", "");
    @Container static final MongoDBContainer MONGO = mongo();

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
    @EnableTransactionManagement
    static class Config {
        @Bean
        MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
            return new MongoTransactionManager(factory);
        }

        @Bean
        MongoIdempotencyStore store(MongoTemplate mongo) {
            return new MongoIdempotencyStore(mongo);
        }

        @Bean
        IdempotencyGuard guard(MongoIdempotencyStore store) {
            return new IdempotencyGuard(store);
        }
    }

    @Autowired MongoTemplate mongo;
    @Autowired MongoIdempotencyStore store;
    @Autowired MongoTransactionManager transactions;
    @Autowired EventConsumer processor;
    @MockitoBean SimpMessagingTemplate messaging;
    @MockitoBean WebPushDeliveryService push;
    @MockitoBean ReminderAttendanceStateService reminders;
    @MockitoBean NotificationAudienceService audience;
    @MockitoBean NotificationHistoryEventProcessor history;
    @MockitoBean NotificationPushIntentStore intents;
    @MockitoBean NotificationPreferencesService preferences;

    @Test
    void frozenAudienceCommitsOnceAndRollbackEmitsNothing() {
        mongo.createCollection(MongoIdempotencyStore.DEFAULT_COLLECTION);
        store.ensureIndexes();
        var envelope =
                Map.<String, Object>of(
                        "event_type",
                        "group.updated",
                        "event_id",
                        UUID.randomUUID().toString(),
                        "event_version",
                        1,
                        "source",
                        "academic-service",
                        "payload",
                        Map.of(
                                "group_id",
                                9007199254740993L,
                                "affected_user_ids",
                                List.of(42L, 11L, 42L)));
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            processor.onEvent(envelope);
                            verifyNoInteractions(messaging);
                            status.setRollbackOnly();
                        });
        assertThat(mongo.count(new Query(), MongoIdempotencyStore.DEFAULT_COLLECTION)).isZero();
        verifyNoInteractions(messaging);
        processor.onEvent(envelope);
        var signal =
                Map.of("type", "group.updated", "payload", Map.of("group_id", "9007199254740993"));
        verify(messaging).convertAndSend("/topic/user/42", signal);
        verify(messaging).convertAndSend("/topic/user/11", signal);
        assertThat(mongo.count(new Query(), MongoIdempotencyStore.DEFAULT_COLLECTION)).isEqualTo(1);
        // The production Rabbit boundary handles committed replay outside its processing tx.
        new NotificationEventConsumer(mongo, processor).onEvent(envelope);
        verify(messaging).convertAndSend("/topic/user/42", signal);
        verify(messaging).convertAndSend("/topic/user/11", signal);
        verifyNoMoreInteractions(messaging);
        verifyNoInteractions(audience, history, intents, preferences, reminders, push);
    }
}
