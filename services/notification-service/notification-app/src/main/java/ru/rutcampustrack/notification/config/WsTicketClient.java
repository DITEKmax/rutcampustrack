package ru.rutcampustrack.notification.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;

/**
 * M03b Группа 4: клиент для consume WebSocket ticket из auth-service.
 * Вызывается {@code TicketHandshakeInterceptor} перед handshake'ом.
 *
 * <p>Защита: POST с header {@code X-Internal-Issuer-Secret} —
 * {@code InternalIssuerSecretFilter} в auth-service validates match.
 * 404 → ticket not found / already consumed / expired. Другие — rejected.</p>
 */
@Component
public class WsTicketClient {

    private static final Logger log = LoggerFactory.getLogger(WsTicketClient.class);

    private final RestClient restClient;
    private final String internalSecret;

    public WsTicketClient(WsTicketProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.authServiceUrl())
                .build();
        this.internalSecret = properties.internalIssuerSecret();
    }

    public Optional<TicketIdentity> consume(String ticket) {
        try {
            TicketIdentity response = restClient.post()
                    .uri("/internal/consume-ws-ticket")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Issuer-Secret", internalSecret)
                    .body(new ConsumeRequest(ticket))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        // пусть 404 всплывает через HttpStatusCodeException, но swallow'им auth-ошибки
                    })
                    .body(TicketIdentity.class);
            return Optional.ofNullable(response);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("WS ticket not found / already consumed");
            } else {
                log.warn("WS ticket consume failed: status={} body={}",
                        e.getStatusCode(), e.getResponseBodyAsString());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("WS ticket consume failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public record ConsumeRequest(String ticket) {}

    public record TicketIdentity(
            @JsonProperty("user_id") long userId,
            String role,
            @JsonProperty("group_id") long groupId,
            @JsonProperty("is_headman") boolean isHeadman,
            @JsonProperty("expires_at") Instant expiresAt
    ) {}
}
