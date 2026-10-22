package ru.rutcampustrack.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClients;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;

import ru.rutcampustrack.notification.config.PushMongoConfig;

/** Production startup indexes against a private real Mongo database. */
class PushSubscriptionRepositoryIT {
    @Test
    void pushMongoConfig_hasUniqUserEndpointIndex() {
        try (MongoDBContainer mongo = new MongoDBContainer("mongo:7").withReuse(false)) {
            mongo.start();
            try (var client = MongoClients.create(mongo.getReplicaSetUrl());
                    var context = new AnnotationConfigApplicationContext()) {
                MongoTemplate template =
                        new MongoTemplate(
                                client,
                                "push_indexes_"
                                        + java.util.UUID.randomUUID().toString().replace("-", ""));
                context.registerBean(MongoTemplate.class, () -> template);
                context.register(PushMongoConfig.class);
                context.refresh();
                var collection = template.getCollection("push_subscriptions");
                var indexes = collection.listIndexes().into(new java.util.ArrayList<>());
                Document unique =
                        indexes.stream()
                                .filter(index -> Boolean.TRUE.equals(index.getBoolean("unique")))
                                .findFirst()
                                .orElseThrow();
                assertThat(unique.get("key", Document.class).entrySet())
                        .containsExactly(
                                java.util.Map.entry("user_id", 1),
                                java.util.Map.entry("endpoint", 1));
                assertThat(indexes)
                        .anySatisfy(
                                index ->
                                        assertThat(index.get("key"))
                                                .isEqualTo(new Document("group_id", 1)));
                assertThat(indexes)
                        .anySatisfy(
                                index ->
                                        assertThat(index.get("key"))
                                                .isEqualTo(new Document("last_seen", 1)));
                collection.insertOne(
                        new Document("user_id", 1L).append("endpoint", "synthetic-endpoint"));
                assertThatThrownBy(
                                () ->
                                        collection.insertOne(
                                                new Document("user_id", 1L)
                                                        .append("endpoint", "synthetic-endpoint")))
                        .isInstanceOf(com.mongodb.MongoWriteException.class);
                collection.insertOne(
                        new Document("user_id", 2L).append("endpoint", "synthetic-endpoint"));
                assertThat(collection.countDocuments()).isEqualTo(2);
                var reminderIndexes =
                        template.getCollection("reminder_attendance_state")
                                .listIndexes()
                                .into(new java.util.ArrayList<>());
                assertThat(reminderIndexes)
                        .anySatisfy(
                                index -> {
                                    assertThat(index.get("key", Document.class).entrySet())
                                            .containsExactly(
                                                    java.util.Map.entry("lesson_id", 1),
                                                    java.util.Map.entry("user_id", 1));
                                    assertThat(index.getBoolean("unique")).isTrue();
                                });
                assertThat(reminderIndexes)
                        .anySatisfy(
                                index ->
                                        assertThat(index.get("key"))
                                                .isEqualTo(new Document("lesson_id", 1)));
            }
        }
    }
}
