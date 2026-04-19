package ru.rutcampustrack.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * M03b Группа 4: конфигурация WebSocket ticket handshake.
 * Notification-service вызывает auth-service `/internal/consume-ws-ticket`
 * с shared-secret (тот же, что использует Gateway для `/internal/issue-internal-jwt`).
 */
@ConfigurationProperties(prefix = "rutcampustrack.notification.ws-ticket")
@Validated
public record WsTicketProperties(
        @NotBlank String authServiceUrl,
        @NotBlank String internalIssuerSecret
) {
}
