package ru.rutcampustrack.attendance.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * MongoDB index configuration for the Attendance Service.
 * Separated from MongoConvertersConfig to avoid circular dependency:
 * MongoCustomConversions is needed to create MongoTemplate, so it must be
 * declared in a configuration class that does NOT depend on MongoTemplate.
 */
@Configuration
public class MongoConfig {

    @Lazy
    @Autowired
    private MongoTemplate mongoTemplate;

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
    }
}
