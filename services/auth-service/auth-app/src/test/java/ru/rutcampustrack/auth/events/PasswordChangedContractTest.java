package ru.rutcampustrack.auth.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.ValidationMessage;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.event.PasswordChangedEvent;

class PasswordChangedContractTest {

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void passwordChangedEvent_matchesMetadataOnlySchema() throws Exception {
        PasswordChangedEvent event = new PasswordChangedEvent(this, 17L, 3L, 2L, 123456789L);
        event.setEventId(UUID.randomUUID());
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");

        String json = mapper.writeValueAsString(event);
        Set<ValidationMessage> errors =
                EventSchemaValidator.validate("password.changed.json", json);
        var payload = mapper.readTree(json).path("payload");

        assertThat(errors)
                .as("password.changed v2 содержит только исходный UID и версии доставки")
                .isEmpty();
        assertThat(payload.size()).isEqualTo(4);
        assertThat(payload.has("telegram_id")).isTrue();
        assertThat(payload.path("user_id").asLong()).isEqualTo(17L);
        assertThat(payload.path("credentials_version").asLong()).isEqualTo(3L);
        assertThat(mapper.readTree(json).path("event_version").asInt()).isEqualTo(2);
    }

    @Test
    void unlinkedCleanupEventHasOriginalIdentityWithoutFabricatedTelegram() throws Exception {
        var event = new PasswordChangedEvent(this, 17L, 3L, 4L, null);
        event.setEventId(UUID.randomUUID());
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");
        String json = mapper.writeValueAsString(event);
        assertThat(EventSchemaValidator.validate("password.changed.json", json)).isEmpty();
        assertThat(mapper.readTree(json).path("payload").size()).isEqualTo(3);
        assertThat(mapper.readTree(json).path("payload").has("telegram_id")).isFalse();
    }

    @Test
    void explicitLegacySchemaRemainsValidButCannotPretendToHaveV2Identity() throws Exception {
        var event = new PasswordChangedEvent(this, 17L, 3L, 2L, 123456789L);
        event.setEventId(UUID.randomUUID());
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");
        var legacy = mapper.valueToTree(event);
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).put("event_version", 1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy)
                .set("payload", mapper.createObjectNode().put("telegram_id", 123456789L));
        assertThat(
                        EventSchemaValidator.validate(
                                "password.changed.json", mapper.writeValueAsString(legacy)))
                .isEmpty();
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).put("event_version", 2);
        assertThat(
                        EventSchemaValidator.validate(
                                "password.changed.json", mapper.writeValueAsString(legacy)))
                .isNotEmpty();
    }
}
