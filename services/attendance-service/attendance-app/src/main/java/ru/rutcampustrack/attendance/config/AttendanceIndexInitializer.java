package ru.rutcampustrack.attendance.config;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One-time startup task that:
 *   1. Removes duplicate attendance documents for the same (lesson_id, user_id),
 *      keeping the most recently updated one.
 *   2. Ensures a unique compound index exists on (lesson_id, user_id) so future
 *      writers cannot create duplicates.
 *
 * Duplicates historically arose because CheckinService used raw {@code save()}
 * instead of upsert, and the collection lacked a schema-level unique constraint.
 * This runner is idempotent — on a clean collection it does nothing meaningful
 * and simply re-declares the index.
 */
@Slf4j
@Configuration
public class AttendanceIndexInitializer {

    @Bean
    ApplicationRunner ensureAttendanceUniqueIndex(MongoTemplate mongoTemplate) {
        return args -> {
            deduplicate(mongoTemplate);
            ensureIndex(mongoTemplate);
        };
    }

    private void deduplicate(MongoTemplate mongoTemplate) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.group("lesson_id", "user_id")
                        .count().as("count")
                        .addToSet("_id").as("ids"),
                Aggregation.match(Criteria.where("count").gt(1))
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(agg, AttendanceDocument.class, Document.class);

        List<String> idsToDelete = new ArrayList<>();
        for (Document group : results.getMappedResults()) {
            @SuppressWarnings("unchecked")
            List<Object> ids = (List<Object>) group.get("ids");
            if (ids == null || ids.size() <= 1) continue;

            // Fetch full docs for this (lesson_id, user_id) bucket and keep the
            // most recent by updated_at (fallback to created_at).
            List<String> stringIds = new ArrayList<>();
            for (Object id : ids) stringIds.add(id.toString());

            List<AttendanceDocument> docs = mongoTemplate.find(
                    Query.query(Criteria.where("_id").in(stringIds)),
                    AttendanceDocument.class
            );
            docs.sort(Comparator.comparing(
                    (AttendanceDocument d) -> firstNonNull(d.getUpdatedAt(), d.getCreatedAt(), Instant.EPOCH)
            ).reversed());

            // Keep docs.get(0), delete the rest.
            for (int i = 1; i < docs.size(); i++) {
                idsToDelete.add(docs.get(i).getId());
            }
        }

        if (!idsToDelete.isEmpty()) {
            long deleted = mongoTemplate.remove(
                    Query.query(Criteria.where("_id").in(idsToDelete)),
                    AttendanceDocument.class
            ).getDeletedCount();
            log.warn("Removed {} duplicate attendance documents during startup cleanup", deleted);
        } else {
            log.info("No duplicate attendance documents detected — nothing to clean up");
        }
    }

    private void ensureIndex(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(AttendanceDocument.class).ensureIndex(
                new Index()
                        .on("lesson_id", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                        .unique()
                        .named("uniq_lesson_user")
        );
        log.info("Ensured unique compound index (lesson_id, user_id) on attendances");
    }

    private static <T> T firstNonNull(T a, T b, T c) {
        if (a != null) return a;
        if (b != null) return b;
        return c;
    }
}
