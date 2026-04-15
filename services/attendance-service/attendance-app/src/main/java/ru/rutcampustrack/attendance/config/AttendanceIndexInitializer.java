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
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.schedule.grpc.LessonInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    ApplicationRunner ensureAttendanceUniqueIndex(
            MongoTemplate mongoTemplate,
            ScheduleGrpcClient scheduleGrpcClient
    ) {
        return args -> {
            deduplicate(mongoTemplate);
            ensureIndex(mongoTemplate);
            cleanupOrphans(mongoTemplate, scheduleGrpcClient);
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

    /**
     * Removes attendance docs whose {@code lesson_id} no longer exists in schedule-service.
     * Runs on every start — it is idempotent and the dominant cost is one gRPC
     * batch lookup. If schedule-service is unreachable the cleanup is skipped
     * (warn-logged) so attendance-service still starts.
     * <p>
     * This complements the {@code lesson.deleted} event: the event catches live
     * deletes going forward; this runner catches historical orphans that slipped
     * through before the cascade existed or during broker outages.
     */
    /** Safety cap: refuse to delete more than this fraction of all attendance docs at once. */
    private static final double MAX_DELETE_RATIO = 0.5;
    /** Below this absolute count, treat any cleanup as safe (small-collection shortcut). */
    private static final long SMALL_COLLECTION_THRESHOLD = 50;

    private void cleanupOrphans(MongoTemplate mongoTemplate, ScheduleGrpcClient scheduleGrpcClient) {
        List<Long> distinctLessonIds = mongoTemplate
                .findDistinct(new Query(), "lesson_id", AttendanceDocument.class, Long.class);

        if (distinctLessonIds.isEmpty()) {
            log.info("attendance orphan-cleanup: collection empty, nothing to check");
            return;
        }

        Set<Long> alive;
        try {
            alive = scheduleGrpcClient.getLessonsByIds(distinctLessonIds).stream()
                    .map(LessonInfo::getLessonId)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (RuntimeException e) {
            log.warn("attendance orphan-cleanup: schedule-service lookup failed ({}), skipping",
                    e.getMessage());
            return;
        }

        // Safety guard #1: if schedule-service returned ZERO alive ids for a non-empty
        // attendance collection, something is catastrophically wrong (fresh Postgres
        // volume, Flyway in progress, partial gRPC failure). Never mass-delete.
        if (alive.isEmpty()) {
            log.error("attendance orphan-cleanup: schedule-service reported 0 alive lesson ids "
                    + "for {} distinct ids in Mongo — refusing to wipe attendance. Investigate "
                    + "schedule-service state before re-running.", distinctLessonIds.size());
            return;
        }

        List<Long> orphanIds = distinctLessonIds.stream()
                .filter(id -> !alive.contains(id))
                .toList();

        if (orphanIds.isEmpty()) {
            log.info("attendance orphan-cleanup: no orphan docs (checked {} lesson ids)",
                    distinctLessonIds.size());
            return;
        }

        // Safety guard #2: if we're about to delete more than half of all distinct lesson
        // ids (and collection is non-trivial), refuse. Real orphans are usually a small
        // tail; a large ratio signals a schedule-service outage or data-sync issue.
        long totalDocs = mongoTemplate.count(new Query(), AttendanceDocument.class);
        double orphanRatio = (double) orphanIds.size() / distinctLessonIds.size();
        if (totalDocs >= SMALL_COLLECTION_THRESHOLD && orphanRatio > MAX_DELETE_RATIO) {
            log.error("attendance orphan-cleanup: would delete {} of {} distinct lesson ids "
                    + "({}% — over {}% safety cap). Refusing. Investigate before re-running.",
                    orphanIds.size(), distinctLessonIds.size(),
                    Math.round(orphanRatio * 100), Math.round(MAX_DELETE_RATIO * 100));
            return;
        }

        long deleted = mongoTemplate.remove(
                Query.query(Criteria.where("lesson_id").in(orphanIds)),
                AttendanceDocument.class
        ).getDeletedCount();
        log.warn("attendance orphan-cleanup: removed {} docs for {} missing lesson ids",
                deleted, orphanIds.size());
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
