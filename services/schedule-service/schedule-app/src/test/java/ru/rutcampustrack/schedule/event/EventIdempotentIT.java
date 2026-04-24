package ru.rutcampustrack.schedule.event;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.subject.SubjectDeletedCascadeService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * M13 G8 — IT для consumer-side dedup в schedule-app.
 *
 * <p>Вызывает {@link EventConsumer#onEvent} дважды с одинаковым
 * {@code event_id} и проверяет: (а) handler-эффект (cascade) выполняется
 * один раз, (б) запись в {@code event_consumer_processed} тоже одна.
 *
 * <p>RabbitTemplate в schedule IT — {@code @MockitoBean}, поэтому
 * вызываем consumer напрямую (как Spring AMQP сделал бы после deserialize).
 */
class EventIdempotentIT extends AbstractScheduleIntegrationTest {

    @Autowired
    private EventConsumer consumer;

    @MockitoBean
    private SubjectDeletedCascadeService cascadeService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanProcessed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createQuery(
                        "DELETE FROM ScheduleEventConsumerProcessedEntity e "
                                + "WHERE e.consumerId = :c")
                        .setParameter("c", EventConsumer.CONSUMER_ID)
                        .executeUpdate()
        );
    }

    private Map<String, Object> envelope(UUID eventId, long subjectId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "subject.deleted");
        envelope.put("event_id", eventId.toString());
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("payload", Map.of("subject_id", subjectId));
        return envelope;
    }

    private long claimsForEvent(UUID eventId) {
        return ((Number) entityManager.createQuery(
                "SELECT COUNT(e) FROM ScheduleEventConsumerProcessedEntity e "
                        + "WHERE e.consumerId = :c AND e.eventId = :id")
                .setParameter("c", EventConsumer.CONSUMER_ID)
                .setParameter("id", eventId)
                .getSingleResult()).longValue();
    }

    @Test
    void duplicateDelivery_handlerCalledOnce() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId, 42L);

        consumer.onEvent(envelope);
        consumer.onEvent(envelope);

        verify(cascadeService, times(1)).cascade(42L);
        assertThat(claimsForEvent(eventId)).isEqualTo(1L);
    }

    @Test
    void differentEventIds_handlerCalledTwice() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        consumer.onEvent(envelope(id1, 100L));
        consumer.onEvent(envelope(id2, 100L));

        verify(cascadeService, times(2)).cascade(100L);
        assertThat(claimsForEvent(id1)).isEqualTo(1L);
        assertThat(claimsForEvent(id2)).isEqualTo(1L);
    }
}
