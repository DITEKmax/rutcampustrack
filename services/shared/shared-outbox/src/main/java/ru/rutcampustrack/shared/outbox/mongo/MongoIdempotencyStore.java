package ru.rutcampustrack.shared.outbox.mongo;

import com.mongodb.MongoWriteException;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.shared.events.IdempotencyStore;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.data.domain.Sort.Direction.ASC;

/**
 * MongoDB-реализация {@link IdempotencyStore} для attendance + notification-web
 * (M13 G8).
 *
 * <p>Коллекция {@code event_consumer_processed}: документ
 * {@code {consumer_id, event_id, processed_at}} + compound unique index
 * на {@code (consumer_id, event_id)}. {@link #tryClaim} делает
 * {@code insertOne} и ловит duplicate-key — это атомарная операция,
 * не требующая транзакции.
 *
 * <p>Caller всё равно оборачивает handler в {@code @Transactional} —
 * на replica set tryClaim участвует в multi-doc tx (см. M13 G7),
 * откатываясь вместе с handler'ом при rollback.
 */
public class MongoIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(MongoIdempotencyStore.class);

    public static final String DEFAULT_COLLECTION = "event_consumer_processed";

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoIdempotencyStore(MongoTemplate mongoTemplate, String collectionName) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    public MongoIdempotencyStore(MongoTemplate mongoTemplate) {
        this(mongoTemplate, DEFAULT_COLLECTION);
    }

    /**
     * Создаёт индексы (вызывать на старте приложения):
     * <ul>
     *   <li>compound unique {@code (consumer_id, event_id)} —
     *       обеспечивает атомарный insert-or-fail.</li>
     *   <li>{@code (processed_at)} — cleanup-job фильтрует старые записи.</li>
     * </ul>
     */
    public void ensureIndexes() {
        mongoTemplate.indexOps(collectionName).ensureIndex(
                new Index()
                        .on("consumer_id", ASC)
                        .on("event_id", ASC)
                        .unique()
                        .named("uniq_consumer_event"));
        mongoTemplate.indexOps(collectionName).ensureIndex(
                new Index()
                        .on("processed_at", ASC)
                        .named("idx_ecp_cleanup"));
    }

    @Override
    public boolean tryClaim(String consumerId, UUID eventId) {
        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException("consumerId must be non-blank");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must be non-null");
        }
        Document doc = new Document()
                .append("consumer_id", consumerId)
                .append("event_id", eventId.toString())
                .append("processed_at", Date.from(Instant.now()));
        try {
            mongoTemplate.getCollection(collectionName).insertOne(doc);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException
                 | com.mongodb.DuplicateKeyException ex) {
            log.debug("Idempotency claim conflict for {}|{} (already processed)",
                    consumerId, eventId);
            return false;
        } catch (MongoWriteException ex) {
            // E11000 duplicate key — единственный ожидаемый MongoWriteException
            // на insertOne с unique-индексом. Прочие коды (e.g. WriteConcernError)
            // должны проброситься, чтобы caller увидел сбой.
            if (ex.getError().getCategory()
                    == com.mongodb.ErrorCategory.DUPLICATE_KEY) {
                log.debug("Idempotency claim conflict for {}|{} (already processed)",
                        consumerId, eventId);
                return false;
            }
            throw ex;
        }
    }

    @Override
    public long deleteProcessedBefore(Instant before) {
        Query q = Query.query(Criteria.where("processed_at").lt(Date.from(before)));
        DeleteResult res = mongoTemplate.getCollection(collectionName)
                .deleteMany(q.getQueryObject());
        return res.getDeletedCount();
    }
}
