package ru.rutcampustrack.shared.outbox.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStatus;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.util.List;

/**
 * JPA реализация {@link OutboxStorage} для PG-бэкенда (academic, schedule).
 *
 * <p>Параметризуется классом-подтипом {@link OutboxEntity} через конструктор —
 * сервис инжектит свой {@code AcademicOutboxEntity.class} или
 * {@code ScheduleOutboxEntity.class}. Внутри — EntityManager queries с именем
 * этой сущности; имя таблицы Hibernate подхватывает из {@code @Table}.
 *
 * <p>Не аннотирован {@code @Repository} / {@code @Component} — сервис сам
 * инстанцирует через {@code @Bean} в config (dependency на конкретный
 * entity class). Это позволяет shared-outbox не зависеть от Spring Boot
 * autoconfig и остаётся NEW-34 compliant (component scan driven).
 *
 * <p>Транзакционность: caller сам оборачивает {@link #save} в свою
 * {@code @Transactional}. Чтения ({@link #findPending}, {@link #countPending})
 * не требуют write-tx, остальные write-методы caller тоже оборачивает.
 */
public class JpaOutboxStorage<E extends OutboxEntity> implements OutboxStorage {

    @PersistenceContext
    private EntityManager entityManager;

    private final Class<E> entityClass;
    private final String entityName;

    /**
     * @param entityClass подтип {@link OutboxEntity} ({@code AcademicOutboxEntity.class} и т.п.)
     */
    public JpaOutboxStorage(Class<E> entityClass) {
        this.entityClass = entityClass;
        this.entityName = entityClass.getSimpleName();
    }

    /** Only for tests / DI frameworks that pre-inject EntityManager. */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void save(String eventType, String payload) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must be non-blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must be non-blank");
        }
        E entity;
        try {
            entity = entityClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Outbox entity " + entityClass.getName()
                            + " must have a public no-arg constructor", e);
        }
        entity.setEventType(eventType);
        entity.setPayload(payload);
        entity.setStatus(OutboxStatus.PENDING);
        entityManager.persist(entity);
    }

    @Override
    public List<OutboxRecord> findPending(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        List<E> rows = entityManager.createQuery(
                        "SELECT e FROM " + entityName
                                + " e WHERE e.status = :status ORDER BY e.createdAt ASC",
                        entityClass)
                .setParameter("status", OutboxStatus.PENDING.name().toLowerCase())
                .setMaxResults(limit)
                .getResultList();
        return rows.stream()
                .map(r -> new OutboxRecord(
                        r.getId(),
                        r.getEventType(),
                        r.getPayload(),
                        r.getCreatedAt()))
                .toList();
    }

    @Override
    public void markSent(Object id) {
        entityManager.createQuery(
                        "UPDATE " + entityName + " e "
                                + "SET e.status = :sent, e.sentAt = :now "
                                + "WHERE e.id = :id")
                .setParameter("sent", OutboxStatus.SENT.name().toLowerCase())
                .setParameter("now", Instant.now())
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public void markFailed(Object id, String errorMsg) {
        String truncated = errorMsg == null ? null
                : (errorMsg.length() > 2000 ? errorMsg.substring(0, 2000) : errorMsg);
        entityManager.createQuery(
                        "UPDATE " + entityName + " e "
                                + "SET e.status = :failed, "
                                + "    e.retryCount = e.retryCount + 1, "
                                + "    e.lastError = :err "
                                + "WHERE e.id = :id")
                .setParameter("failed", OutboxStatus.FAILED.name().toLowerCase())
                .setParameter("err", truncated)
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public long deleteSentBefore(Instant before) {
        return entityManager.createQuery(
                        "DELETE FROM " + entityName + " e "
                                + "WHERE e.status = :sent AND e.sentAt < :cutoff")
                .setParameter("sent", OutboxStatus.SENT.name().toLowerCase())
                .setParameter("cutoff", before)
                .executeUpdate();
    }

    @Override
    public long countPending() {
        return entityManager.createQuery(
                        "SELECT COUNT(e) FROM " + entityName
                                + " e WHERE e.status = :status",
                        Long.class)
                .setParameter("status", OutboxStatus.PENDING.name().toLowerCase())
                .getSingleResult();
    }

    @Override
    public long oldestPendingAgeSeconds() {
        List<Instant> rows = entityManager.createQuery(
                        "SELECT MIN(e.createdAt) FROM " + entityName
                                + " e WHERE e.status = :status",
                        Instant.class)
                .setParameter("status", OutboxStatus.PENDING.name().toLowerCase())
                .getResultList();
        Instant oldest = rows.isEmpty() ? null : rows.get(0);
        if (oldest == null) {
            return 0L;
        }
        long age = Instant.now().getEpochSecond() - oldest.getEpochSecond();
        return Math.max(age, 0L);
    }
}
