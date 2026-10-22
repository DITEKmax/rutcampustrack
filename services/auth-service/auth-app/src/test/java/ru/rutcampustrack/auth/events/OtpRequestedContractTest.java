package ru.rutcampustrack.auth.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.ValidationMessage;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.auth.event.OtpRequestedEvent;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * M09 G2 (08 P0-2) — contract-тест publisher-стороны для {@link OtpRequestedEvent}: сериализуем
 * через Jackson (тот же ObjectMapper, что в {@link ru.rutcampustrack.auth.event.RabbitConfig}),
 * затем валидируем JSON против {@code event-schemas/otp.requested.json}.
 *
 * <p>Обычный login сохраняет прежний DomainEventListener. Subject deletion пишет те же совместимые
 * envelopes в auth_outbox атомарно с proof. Здесь проверяется фактическая сериализация producer
 * против общей schema; реальные транзакции отдельно проверяет SubjectDeletionProofHttpIT.
 */
class OtpRequestedContractTest {

    private final ObjectMapper mapper =
            new ObjectMapper()
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
        assertThat(mapper.readTree(json).path("payload").path("purpose").asText())
                .isEqualTo("login");
    }

    @Test
    void passwordResetEvent_carriesPurposeChallengeAndServerAttempts() throws Exception {
        OtpRequestedEvent event =
                new OtpRequestedEvent(
                        this, 123456789L, "123456", 120, "password_reset", "A".repeat(32), 3);
        event.setEventId(UUID.randomUUID());
        event.setEventVersion(1);
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");

        String json = mapper.writeValueAsString(event);

        Set<ValidationMessage> errors = EventSchemaValidator.validate("otp.requested.json", json);
        assertThat(errors).as("reset OTP must carry its explicit purpose metadata").isEmpty();
        assertThat(mapper.readTree(json).path("payload").path("challenge_id").asText())
                .isEqualTo("A".repeat(32));
    }

    @Test
    void passwordResetEvent_rejectsMissingChallenge() throws Exception {
        OtpRequestedEvent event =
                new OtpRequestedEvent(this, 123456789L, "123456", 120, "password_reset", null, 3);
        event.setEventId(UUID.randomUUID());
        event.setEventVersion(1);
        event.setTraceId(UUID.randomUUID().toString());
        event.setOccurredAt(OffsetDateTime.now());
        event.setSourceService("auth-service");

        String json = mapper.writeValueAsString(event);

        Set<ValidationMessage> errors = EventSchemaValidator.validate("otp.requested.json", json);
        assertThat(errors).as("password_reset must include challenge_id").isNotEmpty();
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

    @Test
    void subjectDeleteProducerAndCleanupCarryOneExactChallengeWithoutChangingLogin()
            throws Exception {
        var requested =
                new OtpRequestedEvent(
                        this, 123456789L, "123456", 120, "subject_delete", "S".repeat(32), 3);
        var verified =
                new ru.rutcampustrack.auth.event.OtpVerifiedEvent(
                        this, 123456789L, "subject_delete", "S".repeat(32));
        var login = new ru.rutcampustrack.auth.event.OtpVerifiedEvent(this, 123456789L);
        for (var event : java.util.List.of(requested, verified, login)) {
            event.setEventId(UUID.randomUUID());
            event.setEventVersion(1);
            event.setTraceId(UUID.randomUUID().toString());
            event.setOccurredAt(OffsetDateTime.now());
            event.setSourceService("auth-service");
            assertThat(
                            EventSchemaValidator.validate(
                                            event.getEventType() + ".json",
                                            mapper.writeValueAsString(event))
                                    .isEmpty())
                    .isTrue();
        }
        var oldPayload = mapper.readTree(mapper.writeValueAsString(login)).path("payload");
        assertThat(oldPayload.has("purpose") || oldPayload.has("challenge_id")).isFalse();
    }

    @Test
    void subjectDeletePurposeCannotOmitOrSubstituteUnboundChallenge() throws Exception {
        for (String challenge : java.util.List.of("", "invalid")) {
            var event =
                    new OtpRequestedEvent(
                            this, 123456789L, "123456", 120, "subject_delete", challenge, 3);
            event.setEventVersion(1);
            event.setTraceId(UUID.randomUUID().toString());
            event.setOccurredAt(OffsetDateTime.now());
            event.setSourceService("auth-service");
            assertThat(
                            EventSchemaValidator.validate(
                                            "otp.requested.json", mapper.writeValueAsString(event))
                                    .isEmpty())
                    .isFalse();
            var cleanup =
                    new ru.rutcampustrack.auth.event.OtpVerifiedEvent(
                            this, 123456789L, "subject_delete", challenge);
            cleanup.setEventVersion(1);
            cleanup.setTraceId(UUID.randomUUID().toString());
            cleanup.setOccurredAt(OffsetDateTime.now());
            cleanup.setSourceService("auth-service");
            assertThat(
                            EventSchemaValidator.validate(
                                            "otp.verified.json", mapper.writeValueAsString(cleanup))
                                    .isEmpty())
                    .isFalse();
        }
    }
}
