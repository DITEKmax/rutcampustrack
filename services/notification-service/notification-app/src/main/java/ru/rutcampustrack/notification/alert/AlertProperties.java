package ru.rutcampustrack.notification.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M04 Группа 9 — настройки /internal/alert endpoint.
 *
 * @param webhookSecret shared secret между Alertmanager и
 *                      notification-web для авторизации POST запросов.
 *                      Alertmanager шлёт в заголовке
 *                      {@code Authorization: Bearer <secret>}.
 *                      M06 заменит на mTLS.
 */
@ConfigurationProperties(prefix = "rutcampustrack.alert")
public record AlertProperties(String webhookSecret) {
}
