package ru.rutcampustrack.auth.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.event.PasswordChangedEvent;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordChangedContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void passwordChangedEvent_matchesMetadataOnlySchema() throws Exception {
        PasswordChangedEvent event = new PasswordChangedEvent(this, 123456789L);
        event.setEventId(UUID.randomUUID());
        event.setEventVersion(1);
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");

        String json = mapper.writeValueAsString(event);
        Set<ValidationMessage> errors = EventSchemaValidator.validate("password.changed.json", json);
        var payload = mapper.readTree(json).path("payload");

        assertThat(errors).as("password.changed содержит только адрес доставки").isEmpty();
        assertThat(payload.size()).isEqualTo(1);
        assertThat(payload.has("telegram_id")).isTrue();
    }
}
