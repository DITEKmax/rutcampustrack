package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Публикуется при вызове {@code POST /auth/otp/request} (M09 G2, 08 P0-2).
 * Раньше Auth Service возвращал {@code code} в HTTP body — оно
 * просачивалось в логи/proxy/APM и ослабляло security-model OTP
 * (секрет известен только owner'у telegram-аккаунта).
 *
 * <p>Потребляется notification-bot: consumer читает
 * {@code payload.code} и вызывает {@code bot.send_message(telegram_id, code)}.
 *
 * <p>Retry на стороне клиента → auth перезаписывает Redis-код (TTL 5 мин)
 * и публикует новое событие. Bot получает свежий код; старый выпадает
 * по TTL. Самоисцеляющийся flow (M09 NOTES Q1 вариант C).
 */
public class OtpRequestedEvent extends DomainEvent {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Payload(
            @JsonProperty("telegram_id") Long telegramId,
            @JsonProperty("code") String code,
            @JsonProperty("ttl_seconds") Integer ttlSeconds,
            @JsonProperty("purpose") String purpose,
            @JsonProperty("challenge_id") String challengeId,
            @JsonProperty("attempts_remaining") Integer attemptsRemaining
    ) {}

    public OtpRequestedEvent(Object source, Long telegramId, String code, Integer ttlSeconds) {
        this(source, telegramId, code, ttlSeconds, "login", null, null);
    }

    public OtpRequestedEvent(
            Object source,
            Long telegramId,
            String code,
            Integer ttlSeconds,
            String purpose,
            String challengeId,
            Integer attemptsRemaining
    ) {
        super(source, "otp.requested", new Payload(
                telegramId, code, ttlSeconds, purpose, challengeId, attemptsRemaining));
    }
}
