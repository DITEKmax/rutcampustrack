package ru.rutcampustrack.shared.outbox.mongo;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStatus;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.springframework.data.domain.Sort.Direction.ASC;

/**
 * MongoDB реализация {@link OutboxStorage} для attendance-service (M02 Группа 4).
 *
 * <p>Работает через {@link MongoTemplate} напрямую с {@link Document} —
 * shared-outbox не поставляет {@code @Document}-класс, чтобы сервис-
 * потребитель не был обязан наследовать shared-Entity. Поля документа
 * совпадают по семантике с PG-outbox (см. {@link ru.rutcampustrack.shared.outbox.jpa.OutboxEntity}):
 *
 * <pre>
 *   _id ObjectId
 *   event_type String
 *   payload String             -- сериализованный JSON event body
 *   status String              -- pending/sent/failed
 *   retry_count int
 *   last_error String nullable
 *   created_at ISODate
 *   sent_at ISODate nullable
 * </pre>
 *
 * <p>Коллекция — параметр конструктора (у attendance это
 * {@code attendance_outbox}). Индексы создаются через
 * {@link #ensureIndexes()} — сервис вызывает из {@code ApplicationRunner}
 * (pattern — {@code AttendanceIndexInitializer}).
 */
public class MongoOutboxStorage implements OutboxStorage {

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoOutboxStorage(MongoTemplate mongoTemplate, String collectionName) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    /**
     * Создаёт индексы для hot-path'ов (вызывать при старте приложения):
     * <ul>
     *   <li>{@code (status, created_at)} — publisher'у пикать pending в FIFO-порядке.</li>
     *   <li>{@code (status, sent_at)} — cleanup'у фильтровать sent по времени.</li>
     * </ul>
     */
    public void ensureIndexes() {
        mongoTemplate.indexOps(collectionName).ensureIndex(
                new Index()
                        .on("status", ASC)
                        .on("created_at", ASC)
                        .named("idx_outbox_pending"));
        mongoTemplate.indexOps(collectionName).ensureIndex(
                new Index()
                        .on("status", ASC)
                        .on("sent_at", ASC)
                        .named("idx_outbox_sent_cleanup"));
    }

    @Override
    public void save(String eventType, String payload) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must be non-blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must be non-blank");
        }
        Document doc = new Document()
                .append("_id", new ObjectId())
                .append("event_type", eventType)
                .append("payload", payload)
                .append("status", OutboxStatus.PENDING.name().toLowerCase())
                .append("retry_count", 0)
                .append("last_error", null)
                .append("created_at", Date.from(Instant.now()))
                .append("sent_at", null);
        mongoTemplate.getCollection(collectionName).insertOne(doc);
    }

    @Override
    public List<OutboxRecord> findPending(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        Query q = Query.query(
                Criteria.where("status").is(OutboxStatus.PENDING.name().toLowerCase()))
                .with(org.springframework.data.domain.Sort.by(ASC, "created_at"))
                .limit(limit);
        List<Document> docs = mongoTemplate.find(q, Document.class, collectionName);
        List<OutboxRecord> out = new ArrayList<>(docs.size());
        for (Document d : docs) {
            Object id = d.get("_id");
            Instant createdAt = toInstant(d.get("created_at"));
            out.add(new OutboxRecord(
                    id,
                    d.getString("event_type"),
                    d.getString("payload"),
                    createdAt));
        }
        return out;
    }

    @Override
    public void markSent(Object id) {
        Query q = Query.query(Criteria.where("_id").is(id));
        Update u = new Update()
                .set("status", OutboxStatus.SENT.name().toLowerCase())
                .set("sent_at", Date.from(Instant.now()));
        UpdateResult res = mongoTemplate.updateFirst(q, u, collectionName);
        if (res.getMatchedCount() == 0) {
            throw new IllegalStateException(
                    "Outbox document not found for markSent: id=" + id);
        }
    }

    @Override
    public void markFailed(Object id, String errorMsg) {
        String truncated = errorMsg == null ? null
                : (errorMsg.length() > 2000 ? errorMsg.substring(0, 2000) : errorMsg);
        Query q = Query.query(Criteria.where("_id").is(id));
        Update u = new Update()
                .set("status", OutboxStatus.FAILED.name().toLowerCase())
                .set("last_error", truncated)
                .inc("retry_count", 1);
        UpdateResult res = mongoTemplate.updateFirst(q, u, collectionName);
        if (res.getMatchedCount() == 0) {
            throw new IllegalStateException(
                    "Outbox document not found for markFailed: id=" + id);
        }
    }

    @Override
    public long deleteSentBefore(Instant before) {
        Query q = Query.query(Criteria.where("status").is(OutboxStatus.SENT.name().toLowerCase())
                .and("sent_at").lt(Date.from(before)));
        DeleteResult res = mongoTemplate.getCollection(collectionName)
                .deleteMany(q.getQueryObject());
        return res.getDeletedCount();
    }

    @Override
    public long countPending() {
        Query q = Query.query(Criteria.where("status")
                .is(OutboxStatus.PENDING.name().toLowerCase()));
        return mongoTemplate.count(q, collectionName);
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Date d) return d.toInstant();
        if (value instanceof Instant i) return i;
        throw new IllegalStateException(
                "Unexpected type for timestamp field: " + value.getClass());
    }
}
