package ru.rutcampustrack.notification.history;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
 */
@Configuration
public class NotificationHistoryMongoConfig {

    @Lazy
    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${notification.history.ttl-days:30}")
    private int ttlDays;

    @PostConstruct
    public void initIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("notification_history");

        ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("sent_at", Sort.Direction.DESC)
                .named("idx_user_sent_desc"));

        ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("read_at", Sort.Direction.ASC)
                .named("idx_user_read"));

        ops.ensureIndex(new Index()
                .on("sent_at", Sort.Direction.ASC)
                .expire(Duration.ofDays(ttlDays))
                .named("ttl_sent_at"));
    }
}
