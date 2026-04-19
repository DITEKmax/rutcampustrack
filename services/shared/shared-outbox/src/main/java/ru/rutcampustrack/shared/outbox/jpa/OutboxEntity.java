package ru.rutcampustrack.shared.outbox.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import ru.rutcampustrack.shared.outbox.OutboxStatus;

import java.time.Instant;

/**
 * Абстрактный JPA MappedSuperclass для outbox-записей (M02 Группа 4).
 *
 * <p>Конкретные сервисы (academic, schedule) объявляют подклассы с
 * {@code @Entity} + {@code @Table(name = "academic_outbox" | "schedule_outbox")}.
 * Схема таблицы — см. Flyway миграции V16/V11 (Группа 3). Поля полностью
 * совпадают с таблицей:
 *
 * <pre>
 *   id BIGSERIAL PK
 *   event_type VARCHAR(128) NOT NULL
 *   payload JSONB NOT NULL         -- сериализованный JSON event body
 *   status VARCHAR(16) NOT NULL    -- pending/sent/failed
 *   retry_count INTEGER NOT NULL DEFAULT 0
 *   last_error TEXT
 *   created_at TIMESTAMPTZ NOT NULL DEFAULT now()
 *   sent_at TIMESTAMPTZ
 * </pre>
 *
 * <p>Status хранится в БД lowercase — CLAUDE.md правило. JPA делает
 * {@code @Enumerated(EnumType.STRING)} с uppercase name'ом enum'а, поэтому
 * используем явный getter/setter на String и конвертируем в enum вручную.
 * Это позволяет обойти constraint CHECK (pending/sent/failed) без
 * LowercaseEnumConverter (чтобы shared-outbox не зависел от academic-
 * специфичного конвертера).
 *
 * <p>Payload — JSONB в PG. Hibernate достаточно {@code columnDefinition =
 * "jsonb"} на String-поле — драйвер PostgreSQL сам маппит. Сериализация
 * события → JSON делается в caller (Группа 5), outbox сам по JSON не ходит.
 */
@MappedSuperclass
public abstract class OutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEntity() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = OutboxStatus.PENDING.name().toLowerCase();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OutboxStatus getStatus() {
        return status == null ? null : OutboxStatus.valueOf(status.toUpperCase());
    }

    public void setStatus(OutboxStatus status) {
        this.status = status == null ? null : status.name().toLowerCase();
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
