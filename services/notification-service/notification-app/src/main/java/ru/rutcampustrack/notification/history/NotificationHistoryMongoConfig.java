package ru.rutcampustrack.notification.history;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import ru.rutcampustrack.shared.events.IdempotencyGuard;
import ru.rutcampustrack.shared.events.IdempotencyStore;
import ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
 *
 * <p><b>M13 G6 fail-fast:</b> после ensureIndex явно проверяем
 * {@code getIndexInfo()} — все 3 ожидаемых индекса должны присутствовать,
 * иначе бросаем {@link IllegalStateException} и Spring контекст падает.
 * Это защищает от silent-no-op регрессии в будущих версиях Spring Data
 * MongoDB / Mongo driver.
 */
@Configuration
@EnableTransactionManagement
@Slf4j
public class NotificationHistoryMongoConfig {

    private static final String COLLECTION = "notification_history";

    static final String IDX_USER_SENT_DESC = "idx_user_sent_desc";
    static final String IDX_USER_READ = "idx_user_read";
    static final String IDX_TTL_SENT_AT = "ttl_sent_at";

    private static final Set<String> EXPECTED_INDEXES = Set.of(
            IDX_USER_SENT_DESC, IDX_USER_READ, IDX_TTL_SENT_AT);

    private final MongoTemplate mongoTemplate;
    private final int ttlDays;

    public NotificationHistoryMongoConfig(MongoTemplate mongoTemplate,
                                          @Value("${notification.history.ttl-days:30}") int ttlDays) {
        this.mongoTemplate = mongoTemplate;
        this.ttlDays = ttlDays;
    }

    /**
     * M13 G7: требуется для {@code @Transactional} на consumer-path'ах
     * notification-web (notification_history + outbox-side atomicity, если
     * появится — на данный момент notification-web пишет только history,
     * но MongoTransactionManager нужен для future-proof + symmetry с
     * attendance).
     */
    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    /**
     * M13 G8: consumer-side dedup по {@code event_id} для notification-web
     * (как для основного {@link ru.rutcampustrack.notification.event.EventConsumer},
     * так и для {@link NotificationHistoryConsumer}). Индексы создаются в
     * {@link #initIndexes()} вместе с TTL.
     */
    @Bean
    public IdempotencyStore idempotencyStore(MongoTemplate mongoTemplate) {
        return new MongoIdempotencyStore(mongoTemplate);
    }

    @Bean
    public IdempotencyGuard idempotencyGuard(IdempotencyStore store) {
        return new IdempotencyGuard(store);
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
                .named(IDX_USER_SENT_DESC));

        String i2 = ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("read_at", Sort.Direction.ASC)
                .named(IDX_USER_READ));

        String i3 = ops.ensureIndex(new Index()
                .on("sent_at", Sort.Direction.ASC)
                .expire(Duration.ofDays(ttlDays))
                .named(IDX_TTL_SENT_AT));

        log.info("notification_history indexes ensured: {}, {}, {} (TTL {} days)",
                i1, i2, i3, ttlDays);

        verifyIndexes(ops);

        // M13 G8 — consumer-side dedup. Compound unique
        // (consumer_id, event_id) + idx_ecp_cleanup на отдельной коллекции
        // event_consumer_processed.
        new MongoIdempotencyStore(mongoTemplate).ensureIndexes();
    }

    /**
     * M13 G6 fail-fast: после ensureIndex читаем текущие index-definitions из
     * Mongo через {@code getIndexInfo()} и проверяем наличие всех 3 ожидаемых
     * по имени + TTL expireAfter у ttl_sent_at. Если хотя бы один отсутствует
     * или TTL не совпадает — бросаем IllegalStateException (Spring ApplicationReadyEvent
     * handler — exception здесь уронит контекст только если swallow'ем не
     * является listener policy; для гарантии используем
     * {@link IllegalStateException} + explicit log.error).
     */
    private void verifyIndexes(IndexOperations ops) {
        List<IndexInfo> actual = ops.getIndexInfo();
        Set<String> actualNames = actual.stream()
                .map(IndexInfo::getName)
                .collect(Collectors.toSet());

        Set<String> missing = EXPECTED_INDEXES.stream()
                .filter(name -> !actualNames.contains(name))
                .collect(Collectors.toSet());

        if (!missing.isEmpty()) {
            String msg = "notification_history missing indexes: " + missing
                    + " (actual: " + actualNames + "). "
                    + "Runbook: runbooks/mongo-indexes-verify.md";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        // TTL sanity: ttl_sent_at должен иметь expireAfter > 0.
        // Spring Data IndexInfo не даёт прямой expireAfter accessor — читаем
        // raw через collection.listIndexes (fallback на sanity-check).
        long ttlSeconds = readTtlSecondsRaw();
        long expected = Duration.ofDays(ttlDays).toSeconds();
        if (ttlSeconds != expected) {
            String msg = "notification_history ttl_sent_at expireAfterSeconds=" + ttlSeconds
                    + ", expected " + expected + " (" + ttlDays + " days). "
                    + "Возможно, индекс остался от предыдущей конфигурации — "
                    + "для изменения TTL требуется drop+recreate на пустой коллекции "
                    + "или collMod (см. runbooks/mongo-indexes-verify.md).";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("notification_history indexes verified: {} (TTL {}s)", actualNames, ttlSeconds);
    }

    private long readTtlSecondsRaw() {
        // listIndexes возвращает все index-definitions со всеми Mongo атрибутами,
        // включая expireAfterSeconds. Spring Data IndexOperations.getIndexInfo()
        // теряет это поле (не маппит в IndexInfo до M10 stack версии).
        for (Document idx : mongoTemplate.getCollection(COLLECTION).listIndexes()) {
            if (IDX_TTL_SENT_AT.equals(idx.getString("name"))) {
                Number ttl = idx.get("expireAfterSeconds", Number.class);
                return ttl != null ? ttl.longValue() : -1L;
            }
        }
        return -1L;
    }
}
