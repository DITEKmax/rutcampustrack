package ru.rutcampustrack.notification.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * MongoDB index configuration for push_subscriptions collection.
 *
 * Per D-05: Compound unique index on (user_id, endpoint) prevents duplicate subscriptions.
 * Index on group_id enables efficient fanout queries by Plan 03 PushService.
 *
 * @Lazy on MongoTemplate injection avoids circular dependency with Spring Data auto-configuration.
 */
@Configuration
public class PushMongoConfig {

    @Lazy
    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void initIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("push_subscriptions");
        ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("endpoint", Sort.Direction.ASC)
                .unique()
                .named("uniq_user_endpoint"));
        ops.ensureIndex(new Index()
                .on("group_id", Sort.Direction.ASC)
                .named("idx_group_id"));
        // M05 G7 (NEW-148): ускоряет cleanup-запрос
        // DELETE WHERE last_seen < (now - 90d).
        ops.ensureIndex(new Index()
                .on("last_seen", Sort.Direction.ASC)
                .named("idx_last_seen"));
    }
}
