package ru.rutcampustrack.shared.events;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

class IdempotencyGuardTest {

    private InMemoryStore store;
    private IdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        guard = new IdempotencyGuard(store);
    }

    @Test
    void tryClaim_duplicateCall_returnsFalse() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId, "lesson.started");

        assertThat(guard.tryClaim("academic", envelope)).isTrue();
        assertThat(guard.tryClaim("academic", envelope)).isFalse();
    }

    @Test
    void tryClaim_differentConsumers_areIndependent() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId, "subject.deleted");

        assertThat(guard.tryClaim("academic", envelope)).isTrue();
        assertThat(guard.tryClaim("schedule", envelope)).isTrue();
    }

    @Test
    void tryClaim_eventIdAsString_parsesUuid() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_id", eventId.toString());
        envelope.put("event_type", "lesson.closed");

        assertThat(guard.tryClaim("attendance", envelope)).isTrue();
        assertThat(guard.tryClaim("attendance", envelope)).isFalse();
    }

    @Test
    void tryClaim_envelopeWithoutEventId_throwsFailClosed() {
        // M13 G24-fix-6: fail-closed — раньше fail-open пропускал legacy events.
        // После M13 G8 все publisher'ы заполняют event_id; missing = bug либо
        // forged. Caller @Transactional handler делает rollback → DLQ.
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "legacy.event");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> guard.tryClaim("academic", envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event_id");
        assertThat(store.size()).isZero();
    }

    @Test
    void tryClaim_malformedEventId_throwsFailClosed() {
        // Malformed event_id (не UUID) → extractEventId возвращает null →
        // та же fail-closed ветка что и для missing event_id.
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_id", "not-a-uuid");
        envelope.put("event_type", "lesson.started");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> guard.tryClaim("academic", envelope))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.size()).isZero();
    }

    @Test
    void tryClaim_nullEnvelope_throwsFailClosed() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> guard.tryClaim("academic", null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.size()).isZero();
    }

    @Test
    void extractEventId_acceptsRawUuidObject() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_id", eventId);

        assertThat(IdempotencyGuard.extractEventId(envelope)).isEqualTo(eventId);
    }

    @Test
    void malformedMetadataIsNeverStringifiedOrEchoedIntoLogsOrException() {
        String marker = "SYNTHETIC_PRIVATE_MARKER";
        var stringified = new AtomicBoolean();
        Object untrusted =
                new Object() {
                    @Override
                    public String toString() {
                        stringified.set(true);
                        return marker;
                    }
                };
        Logger logger = (Logger) LoggerFactory.getLogger(IdempotencyGuard.class);
        var output = new ListAppender<ILoggingEvent>();
        output.start();
        logger.addAppender(output);
        try {
            for (Object invalid : new Object[] {untrusted, marker, "1-1-1-1-1"}) {
                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () ->
                                        guard.tryClaim(
                                                "academic",
                                                Map.of(
                                                        "event_id",
                                                        invalid,
                                                        "event_type",
                                                        untrusted)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("event_id")
                        .hasMessageNotContaining(marker);
            }
            assertThat(stringified).isFalse();
            assertThat(store.size()).isZero();
            assertThat(output.list)
                    .allSatisfy(
                            event ->
                                    assertThat(event.getFormattedMessage())
                                            .doesNotContain(marker, "processing without dedup"));
        } finally {
            logger.detachAppender(output);
            output.stop();
        }
    }

    /** Минималистичный store для unit-теста — без БД, без Spring. */
    static class InMemoryStore implements IdempotencyStore {
        private final Set<String> claimed = new HashSet<>();

        @Override
        public boolean tryClaim(String consumerId, UUID eventId) {
            return claimed.add(key(consumerId, eventId));
        }

        @Override
        public long deleteProcessedBefore(Instant before) {
            int previous = claimed.size();
            claimed.clear();
            return previous;
        }

        int size() {
            return claimed.size();
        }

        private static String key(String consumerId, UUID eventId) {
            return Objects.requireNonNull(consumerId) + "|" + Objects.requireNonNull(eventId);
        }
    }

    private static Map<String, Object> envelope(UUID eventId, String eventType) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_id", eventId);
        envelope.put("event_type", eventType);
        return envelope;
    }
}
