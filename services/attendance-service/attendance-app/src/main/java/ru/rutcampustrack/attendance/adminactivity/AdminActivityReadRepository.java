package ru.rutcampustrack.attendance.adminactivity;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;
import ru.rutcampustrack.attendance.grpc.AdminActivityCounts;
import ru.rutcampustrack.attendance.grpc.AdminActivitySeriesBucket;
import ru.rutcampustrack.attendance.grpc.AdminActivitySnapshot;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Mongo does the counting/grouping. No domain history or personal data leaves this boundary. */
@Repository
public class AdminActivityReadRepository {
    private final MongoTemplate mongo;
    public AdminActivityReadRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public AdminActivitySnapshot read(AdminActivityWindow window) {
        Document positives = aggregate("lesson_actions", List.of(
                new Document("$match", new Document("source", "attendance-service")
                        .append("payload.action", "ATTENDANCE_SET")
                        .append("payload.outcome", new Document("$ne", "REJECTED"))
                        .append("payload.after.status", "PRESENT")
                        .append("payload.before.status", new Document("$ne", "PRESENT"))
                        .append("occurred_at", new Document("$lt", Date.from(window.observedAt())))),
                facets(window, "occurred_at", true)));
        // Collapse repeated transport events AND repeated publication history by
        // actual publication identity BEFORE applying day/week filters.
        Document homeworks = aggregate("lesson_actions", List.of(
                new Document("$match", new Document("source", "academic-service")
                        .append("payload.action", "HOMEWORK_CREATED")
                        .append("payload.after.publication_state", "ACTIVE")
                        .append("occurred_at", new Document("$lt", Date.from(window.observedAt())))),
                new Document("$project", new Document("homework_id", new Document("$ifNull",
                        List.of("$payload.homework_id", "$payload.homework.id"))).append("occurred_at", 1)),
                new Document("$match", new Document("homework_id", new Document("$gt", 0))),
                new Document("$group", new Document("_id", "$homework_id")
                        .append("occurred_at", new Document("$min", "$occurred_at"))),
                facets(window, "occurred_at", false)));
        Document excuses = submissions("excuse_tickets", window);
        Document late = submissions("late_checkin_requests", window);
        var result = AdminActivitySnapshot.newBuilder()
                .setSummary(counts("summary", null, positives, homeworks, excuses, late));
        if (window.previous() != null) result.setPrevious(counts("previous", null, positives, homeworks, excuses, late));
        for (var range : window.series()) {
            result.addSeries(AdminActivitySeriesBucket.newBuilder()
                    .setFromEpochMs(range.from().toEpochMilli()).setToEpochMs(range.to().toEpochMilli())
                    .setCounts(counts("series", range.from(), positives, homeworks, excuses, late)));
        }
        return result.build();
    }

    private Document submissions(String collection, AdminActivityWindow window) {
        return aggregate(collection, List.of(new Document("$match", new Document("created_at",
                        new Document("$lt", Date.from(window.observedAt())))),
                facets(window, "created_at", false)));
    }

    private Document facets(AdminActivityWindow w, String field, boolean geo) {
        Document facets = new Document("summary", countRange(w.summary(), field, geo));
        if (w.previous() != null) facets.append("previous", countRange(w.previous(), field, geo));
        Document trunc = new Document("date", "$" + field)
                .append("unit", w.weekly() ? "week" : "day").append("timezone", "Europe/Moscow");
        if (w.weekly()) trunc.append("startOfWeek", "Monday");
        Document group = countGroup(new Document("$dateTrunc", trunc), geo);
        facets.append("series", List.of(
                new Document("$match", bounds(new AdminActivityWindow.Range(w.series().getFirst().from(), w.observedAt()), field)),
                new Document("$group", group)));
        return new Document("$facet", facets);
    }

    private List<Document> countRange(AdminActivityWindow.Range range, String field, boolean geo) {
        return List.of(new Document("$match", bounds(range, field)),
                new Document("$group", countGroup(null, geo)));
    }
    private static Document countGroup(Object id, boolean geo) {
        Document group = new Document("_id", id).append("value", new Document("$sum", 1));
        if (geo) group.append("geo", new Document("$sum", new Document("$cond", List.of(
                new Document("$eq", List.of("$payload.after.source", "STUDENT_GEO")), 1, 0))));
        return group;
    }
    private static Document bounds(AdminActivityWindow.Range range, String field) {
        Document bounds = new Document("$lt", Date.from(range.to()));
        if (range.from() != null) bounds.append("$gte", Date.from(range.from()));
        return new Document(field, bounds);
    }
    private Document aggregate(String collection, List<Document> pipeline) {
        Document result = mongo.getCollection(collection).aggregate(pipeline)
                .maxTime(2, TimeUnit.SECONDS).first();
        return result == null ? new Document() : result;
    }
    private static AdminActivityCounts counts(String facet, Instant bucket, Document positive, Document hw,
                                               Document excuse, Document late) {
        return AdminActivityCounts.newBuilder()
                .setPositiveMarks(value(positive, facet, bucket, "value"))
                .setGeoMarks(value(positive, facet, bucket, "geo"))
                .setHomeworkPublications(value(hw, facet, bucket, "value"))
                .setRequests(value(excuse, facet, bucket, "value") + value(late, facet, bucket, "value")).build();
    }
    private static long value(Document doc, String facet, Instant bucket, String name) {
        for (Document row : doc.getList(facet, Document.class, List.of())) {
            if (bucket == null || Date.from(bucket).equals(row.getDate("_id"))) {
                Object value = row.get(name);
                return value instanceof Number count ? count.longValue() : 0L;
            }
        }
        return 0L;
    }
}
