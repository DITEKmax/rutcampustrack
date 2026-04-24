package ru.rutcampustrack.auth.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.event.OtpRequestedEvent;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M09 G2 (08 P0-2) — contract-тест publisher-стороны для {@link OtpRequestedEvent}:
 * сериализуем через Jackson (тот же ObjectMapper, что в {@link ru.rutcampustrack.auth.event.RabbitConfig}),
 * затем валидируем JSON против {@code event-schemas/otp.requested.json}.
 *
 * <p>Auth-service не использует shared-outbox (см. DECISIONS D4 M09) —
 * события публикуются fire-and-forget через {@code DomainEventListener}.
 * Поэтому тест делает фактическое превращение event → JSON тем же
 * конвертером, что прод-путь, и валидирует против зафиксированной schema.
 */
class OtpRequestedContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void otpRequestedEvent_matchesSchema() throws Exception {
        OtpRequestedEvent event = new OtpRequestedEvent(this, 123456789L, "123456", 300);
        // Эмулируем fillDefaults (AbstractEventPublisher) — прод-путь это делает
        // в @EventListener до convertAndSend.
        event.setEventId(UUID.randomUUID());
        event.setEventVersion(1);
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");

        String json = mapper.writeValueAsString(event);

        Set<ValidationMessage> errors = EventSchemaValidator.validate("otp.requested.json", json);
        assertThat(errors).as("otp.requested envelope должен валидироваться schema'ой").isEmpty();
    }

    @Test
    void otpRequestedEvent_rejectsMissingCode() throws Exception {
        OtpRequestedEvent event = new OtpRequestedEvent(this, 123456789L, null, 300);
        event.setEventId(UUID.randomUUID());
        event.setEventVersion(1);
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");

        String json = mapper.writeValueAsString(event);

        // payload.code marked as required + pattern ^[0-9]{6}$ — null value
        // удалён JsonInclude.NON_NULL → required-проверка срабатывает.
        Set<ValidationMessage> errors = EventSchemaValidator.validate("otp.requested.json", json);
        assertThat(errors).as("code отсутствует → schema-violation").isNotEmpty();
    }

    @Test
    void otpRequestedEvent_rejectsNonSixDigitCode() throws Exception {
        OtpRequestedEvent event = new OtpRequestedEvent(this, 123456789L, "ABCDEF", 300);
        event.setEventId(UUID.randomUUID());
        event.setEventVersion(1);
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");

        String json = mapper.writeValueAsString(event);

        Set<ValidationMessage> errors = EventSchemaValidator.validate("otp.requested.json", json);
        assertThat(errors).as("code не 6-digit → pattern-violation").isNotEmpty();
    }
}
