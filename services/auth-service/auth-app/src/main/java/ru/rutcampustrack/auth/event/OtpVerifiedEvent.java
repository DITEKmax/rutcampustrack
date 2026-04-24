package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Публикуется после успешной верификации OTP-кода (вход в веб-панель).
 * Потребляется notification-bot для удаления Telegram-сообщений с кодом
 * и исходного запроса пользователя.
 */
public class OtpVerifiedEvent extends DomainEvent {

    public record Payload(@JsonProperty("telegram_id") Long telegramId) {}

    public OtpVerifiedEvent(Object source, Long telegramId) {
        super(source, "otp.verified", new Payload(telegramId));
    }
}
