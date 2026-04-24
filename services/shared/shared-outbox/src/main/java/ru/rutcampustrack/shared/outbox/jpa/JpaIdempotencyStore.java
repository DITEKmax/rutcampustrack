package ru.rutcampustrack.shared.outbox.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rutcampustrack.shared.events.IdempotencyStore;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA-реализация {@link IdempotencyStore} для academic + schedule (PG).
 *
 * <p>Параметризуется классом-подтипом {@link IdempotencyEntity} через
 * конструктор (используется в {@link #deleteProcessedBefore} через
 * {@code entityName} в JPQL).
 *
 * <p>{@link #tryClaim} использует PostgreSQL-native
 * {@code INSERT ... ON CONFLICT DO NOTHING} — атомарная операция,
 * не бросающая исключений на UNIQUE-conflict (важно: классический
 * {@code persist + flush} → {@code catch DataIntegrityViolationException}
 * помечает Spring tx как rollback-only, и handler-tx не может коммитнуться).
 *
 * <p>Транзакционность: caller гарантирует {@code @Transactional} на handler-
 * методе. Если основная логика handler'а упадёт — claim тоже откатится,
 * RabbitMQ redelivery сможет retry.
 */
public class JpaIdempotencyStore<E extends IdempotencyEntity> implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(JpaIdempotencyStore.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final Class<E> entityClass;
    private final String entityName;

    public JpaIdempotencyStore(Class<E> entityClass) {
        this.entityClass = entityClass;
        this.entityName = entityClass.getSimpleName();
    }

    /** For tests / DI frameworks pre-injecting EntityManager. */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean tryClaim(String consumerId, UUID eventId) {
        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException("consumerId must be non-blank");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must be non-null");
        }
        // PostgreSQL native UPSERT — атомарный insert-or-skip без бросания
        // PSQLException на UNIQUE-conflict. Это критично: ловить
        // DataIntegrityViolationException + return false помечает Spring tx
        // как rollback-only (Hibernate flush failure), и handler-tx не может
        // коммитнуться. ON CONFLICT DO NOTHING избегает PSQLException
        // полностью; updated row count = 1 (новая запись) либо 0 (conflict).
        int updated = entityManager.createNativeQuery(
                "INSERT INTO event_consumer_processed (consumer_id, event_id, processed_at) "
                        + "VALUES (?, ?, ?) "
                        + "ON CONFLICT (consumer_id, event_id) DO NOTHING")
                .setParameter(1, consumerId)
                .setParameter(2, eventId)
                .setParameter(3, Timestamp.from(Instant.now()))
                .executeUpdate();
        if (updated == 0) {
            log.debug("Idempotency claim conflict for {}|{} (already processed)",
                    consumerId, eventId);
            return false;
        }
        return true;
    }

    @Override
    public long deleteProcessedBefore(Instant before) {
        return entityManager.createQuery(
                        "DELETE FROM " + entityName + " e WHERE e.processedAt < :cutoff")
                .setParameter("cutoff", before)
                .executeUpdate();
    }
}
