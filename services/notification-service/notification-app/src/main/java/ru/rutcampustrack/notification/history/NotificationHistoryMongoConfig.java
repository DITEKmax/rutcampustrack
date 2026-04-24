package ru.rutcampustrack.notification.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.time.Duration;

/**
 * MongoDB index configuration для коллекции notification_history (M10 G3).
 *
 * <p>Индексы:
 * <ul>
 *   <li>{@code {user_id: 1, sent_at: -1}} — list per user DESC (pagination).</li>
 *   <li>{@code {user_id: 1, read_at: 1}} — unread badge count.</li>
 *   <li>{@code {sent_at: 1}} TTL — retention (см.
 *       {@code NOTIFICATION_HISTORY_TTL_DAYS}, default 30).</li>
 * </ul>
 *
 * <p>TTL меняется только на fresh volume — для dynamic tuning см.
 * {@code future-ideas.md} «collMod auto-reconciler» (v0.1).
 *
 * <p><b>Bootstrap pattern (M10 G9 hot-patch, S4):</b> @PostConstruct +
 * @Lazy MongoTemplate в Spring Data MongoDB не материализует индексы
 * на пустом namespace в Mongo 7 (silently no-op). Явно создаём
 * коллекцию через {@code createCollection} ПЕРЕД ensureIndex и
 * привязываемся к {@link ApplicationReadyEvent} вместо @PostConstruct,
 * чтобы MongoTemplate был полностью инициализирован.
 */
@Configuration
@Slf4j
public class NotificationHistoryMongoConfig {

    private static final String COLLECTION = "notification_history";

    private final MongoTemplate mongoTemplate;
    private final int ttlDays;

    public NotificationHistoryMongoConfig(MongoTemplate mongoTemplate,
                                          @Value("${notification.history.ttl-days:30}") int ttlDays) {
        this.mongoTemplate = mongoTemplate;
        this.ttlDays = ttlDays;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initIndexes() {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
            log.info("notification_history collection created");
        }

        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);

        String i1 = ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("sent_at", Sort.Direction.DESC)
                .named("idx_user_sent_desc"));

        String i2 = ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("read_at", Sort.Direction.ASC)
                .named("idx_user_read"));

        String i3 = ops.ensureIndex(new Index()
                .on("sent_at", Sort.Direction.ASC)
                .expire(Duration.ofDays(ttlDays))
                .named("ttl_sent_at"));

        log.info("notification_history indexes ensured: {}, {}, {} (TTL {} days)",
                i1, i2, i3, ttlDays);
    }
}
