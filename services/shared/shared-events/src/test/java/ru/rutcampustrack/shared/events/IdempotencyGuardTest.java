package ru.rutcampustrack.shared.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyGuardTest {

    private InMemoryStore store;
    private IdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        guard = new IdempotencyGuard(store);
    }

    @Test
    void tryClaim_firstCall_returnsTrue() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId, "lesson.started");

        assertThat(guard.tryClaim("academic", envelope)).isTrue();
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
    void tryClaim_envelopeWithoutEventId_passesWithoutDedup() {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "legacy.event");

        // No event_id → guard returns true, store untouched.
        assertThat(guard.tryClaim("academic", envelope)).isTrue();
        assertThat(guard.tryClaim("academic", envelope)).isTrue();
        assertThat(store.size()).isZero();
    }

    @Test
    void tryClaim_malformedEventId_passesWithoutDedup() {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_id", "not-a-uuid");
        envelope.put("event_type", "lesson.started");

        assertThat(guard.tryClaim("academic", envelope)).isTrue();
        assertThat(store.size()).isZero();
    }

    @Test
    void tryClaim_nullEnvelope_passesWithoutDedup() {
        assertThat(guard.tryClaim("academic", null)).isTrue();
        assertThat(store.size()).isZero();
    }

    @Test
    void extractEventId_acceptsRawUuidObject() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_id", eventId);

        assertThat(IdempotencyGuard.extractEventId(envelope)).isEqualTo(eventId);
    }

    /**
     * Минималистичный store для unit-теста — без БД, без Spring.
     */
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
