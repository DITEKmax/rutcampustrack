package ru.rutcampustrack.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @EventVersion(2)
    static class LessonStartedEvent extends DomainEvent {
        private Long lessonId;

        public Long getLessonId() {
            return lessonId;
        }

        public void setLessonId(Long lessonId) {
            this.lessonId = lessonId;
        }
    }

    @Test
    @DisplayName("snake_case сериализация для стандартных полей")
    void snakeCaseSerialization() throws Exception {
        LessonStartedEvent event = new LessonStartedEvent();
        event.setEventVersion(2);
        event.setTraceId("abc-123");
        event.setOccurredAt(Instant.parse("2026-04-19T10:00:00Z"));
        event.setSource("schedule-service");
        event.setLessonId(42L);

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"event_version\":2")
                .contains("\"trace_id\":\"abc-123\"")
                .contains("\"occurred_at\":\"2026-04-19T10:00:00Z\"")
                .contains("\"source\":\"schedule-service\"")
                .contains("\"lessonId\":42")
                .doesNotContain("\"eventVersion\"")
                .doesNotContain("\"traceId\"")
                .doesNotContain("\"occurredAt\"");
    }

    @Test
    @DisplayName("Round-trip: snake_case deserialization")
    void snakeCaseDeserialization() throws Exception {
        String json = """
                {
                  "event_version": 2,
                  "trace_id": "abc-123",
                  "occurred_at": "2026-04-19T10:00:00Z",
                  "source": "schedule-service",
                  "lessonId": 42
                }
                """;

        LessonStartedEvent parsed = mapper.readValue(json, LessonStartedEvent.class);

        assertThat(parsed.getEventVersion()).isEqualTo(2);
        assertThat(parsed.getTraceId()).isEqualTo("abc-123");
        assertThat(parsed.getOccurredAt()).isEqualTo(Instant.parse("2026-04-19T10:00:00Z"));
        assertThat(parsed.getSource()).isEqualTo("schedule-service");
        assertThat(parsed.getLessonId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Null-поля скрыты (@JsonInclude NON_NULL)")
    void nullFieldsHidden() throws Exception {
        LessonStartedEvent event = new LessonStartedEvent();
        event.setEventVersion(1);
        // traceId, occurredAt, source, lessonId — null

        String json = mapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"event_version\":1")
                .doesNotContain("trace_id")
                .doesNotContain("occurred_at")
                .doesNotContain("source")
                .doesNotContain("lessonId");
    }
}
