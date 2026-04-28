package ru.rutcampustrack.attendance.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import ru.rutcampustrack.shared.events.IdempotencyGuard;
import ru.rutcampustrack.shared.events.IdempotencyStore;
import ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore;

/**
 * MongoDB index configuration for the Attendance Service.
 * Separated from MongoConvertersConfig to avoid circular dependency:
 * MongoCustomConversions is needed to create MongoTemplate, so it must be
 * declared in a configuration class that does NOT depend on MongoTemplate.
 *
 * <p><b>M13 G7:</b> регистрирует {@link MongoTransactionManager} для
 * multi-document transactions (closes M02 CRITICAL #1 — outbox save +
 * domain save в одной tx). Требует Mongo replica set
 * (see docker-compose {@code MONGODB_REPLICA_SET_MODE=primary}).
 */
@Configuration
@EnableTransactionManagement
public class MongoConfig {

    @Lazy
    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * M13 G7: требуется для {@code @Transactional} + {@code MongoOutboxStorage}
     * atomicity. `MongoTransactionManager` оборачивает Mongo session'ы +
     * startSession/commitTransaction/abortTransaction в Spring-совместимый
     * `PlatformTransactionManager`.
     */
    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    /**
     * M13 G8: consumer-side dedup по {@code event_id}. Storage инжектится
     * в {@code EventConsumer} через {@link IdempotencyGuard}, индексы
     * создаются в {@link #initIndexes()}.
     */
    @Bean
    public IdempotencyStore idempotencyStore(MongoTemplate mongoTemplate) {
        return new MongoIdempotencyStore(mongoTemplate);
    }

    @Bean
    public IdempotencyGuard idempotencyGuard(IdempotencyStore store) {
        return new IdempotencyGuard(store);
    }

    @PostConstruct
    public void initIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("attendances");

        // INFRA-01: Unique compound index — idempotency for checkins
        ops.ensureIndex(new Index()
                .on("lesson_id", Sort.Direction.ASC)
                .on("user_id", Sort.Direction.ASC)
                .unique()
                .named("uniq_lesson_user"));

        // Query: student attendance per semester (RPRT-03, RPRT-04)
        ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("semester_id", Sort.Direction.ASC)
                .on("lesson_date", Sort.Direction.DESC)
                .named("idx_user_semester_date"));

        // Query: group journal (RPRT-01, RPRT-02)
        ops.ensureIndex(new Index()
                .on("group_id", Sort.Direction.ASC)
                .on("semester_id", Sort.Direction.ASC)
                .on("subject_id", Sort.Direction.ASC)
                .named("idx_group_semester_subject"));

        // Query: all records for a lesson (auto-absent, lesson view)
        ops.ensureIndex(new Index()
                .on("lesson_id", Sort.Direction.ASC)
                .named("idx_lesson_id"));

        // M05 Группа 1 — closes 04 P2-9.
        // Query: LateCheckinRepository.findByGroupIdAndStatusOrderByCreatedAtAsc
        // (headman dashboard late-checkins). До индекса — COLLSCAN +
        // in-memory sort по created_at.
        IndexOperations lcrOps = mongoTemplate.indexOps("late_checkin_requests");
        lcrOps.ensureIndex(new Index()
                .on("group_id", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .on("created_at", Sort.Direction.ASC)
                .named("lcr_group_status_created"));
        lcrOps.ensureIndex(new Index()
                .on("group_id", Sort.Direction.ASC)
                .on("updated_at", Sort.Direction.DESC)
                .named("lcr_group_updated"));
        lcrOps.ensureIndex(new Index()
                .on("group_id", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .on("updated_at", Sort.Direction.DESC)
                .named("lcr_group_status_updated"));

        // M13 G8 — consumer-side dedup. Compound unique
        // (consumer_id, event_id) делает MongoIdempotencyStore.tryClaim
        // атомарным insert-or-fail. idx_ecp_cleanup нужен cleanup-job'у.
        new MongoIdempotencyStore(mongoTemplate).ensureIndexes();
    }
}
