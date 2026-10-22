package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Публикуется после успешной верификации OTP-кода (вход в веб-панель). Потребляется
 * notification-bot для удаления Telegram-сообщений с кодом и исходного запроса пользователя.
 */
public class OtpVerifiedEvent extends DomainEvent {

    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Payload(
            @JsonProperty("telegram_id") Long telegramId,
            @JsonProperty("purpose") String purpose,
            @JsonProperty("challenge_id") String challengeId) {
        public Payload(Long telegramId) {
            this(telegramId, null, null);
        }
    }

    public OtpVerifiedEvent(Object source, Long telegramId) {
        this(source, telegramId, null, null);
    }

    public OtpVerifiedEvent(Object source, Long telegramId, String purpose, String challengeId) {
        super(source, "otp.verified", new Payload(telegramId, purpose, challengeId));
    }
}
