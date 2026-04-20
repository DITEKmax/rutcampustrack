package ru.rutcampustrack.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    record LessonStartedPayload(Long lessonId) {
    }

    @EventVersion(2)
    static class LessonStartedEvent extends DomainEvent {
        LessonStartedEvent(Long lessonId) {
            super("lesson.started", new LessonStartedPayload(lessonId));
        }
    }

    @Test
    @DisplayName("snake_case сериализация для envelope-полей + payload")
    void snakeCaseSerialization() throws Exception {
        LessonStartedEvent event = new LessonStartedEvent(42L);
        event.setEventVersion(2);
        event.setTraceId("abc-123");
        event.setOccurredAt(OffsetDateTime.parse("2026-04-19T10:00:00Z"));
        event.setSourceService("schedule-service");

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"event_type\":\"lesson.started\"")
                .contains("\"event_id\":")
                .contains("\"event_version\":2")
                .contains("\"trace_id\":\"abc-123\"")
                .contains("\"occurred_at\":\"2026-04-19T10:00:00Z\"")
                .contains("\"source\":\"schedule-service\"")
                .contains("\"payload\":{\"lessonId\":42}")
                .doesNotContain("\"eventVersion\"")
                .doesNotContain("\"traceId\"")
                .doesNotContain("\"occurredAt\"");
    }

    @Test
    @DisplayName("Null-поля envelope скрыты (@JsonInclude NON_NULL)")
    void nullEnvelopeFieldsHidden() throws Exception {
        LessonStartedEvent event = new LessonStartedEvent(42L);
        // event_type/event_id/payload — заданы конструктором.
        // event_version/trace_id/occurred_at/source — null.

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"event_type\":\"lesson.started\"")
                .contains("\"event_id\":")
                .contains("\"payload\":{\"lessonId\":42}")
                .doesNotContain("event_version")
                .doesNotContain("trace_id")
                .doesNotContain("occurred_at")
                .doesNotContain("\"source\"");
    }
}
