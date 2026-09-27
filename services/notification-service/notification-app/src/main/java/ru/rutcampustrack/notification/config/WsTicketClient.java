package ru.rutcampustrack.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketRequest;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

import java.time.Duration;
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
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(1500);
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Issuer-Secret";

    private final RestClient restClient;
    private final String internalSecret;

    public WsTicketClient(WsTicketProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(properties.authServiceUrl())
                .requestFactory(requestFactory)
                .build();
        this.internalSecret = properties.internalIssuerSecret();
    }

    public Optional<ConsumeWsTicketResponse> consume(String ticket) {
        try {
            ConsumeWsTicketResponse response = restClient.post()
                    .uri("/internal/consume-ws-ticket")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_SECRET_HEADER, internalSecret)
                    .body(new ConsumeWsTicketRequest(ticket))
                    .retrieve()
                    .body(ConsumeWsTicketResponse.class);
            if (response != null) {
                response.admissionRequest();
            }
            return Optional.ofNullable(response);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("WS ticket not found / already consumed");
            } else {
                log.warn("WS ticket consume failed: status={}", e.getStatusCode());
            }
            return Optional.empty();
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("WS ticket consume failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public boolean admit(WsSessionAdmissionRequest identity) {
        try {
            var response = restClient.post()
                    .uri("/internal/auth/admit-ws-session")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_SECRET_HEADER, internalSecret)
                    .body(identity)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().value() == 204;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.debug("WS session admission rejected: status={}", e.getStatusCode());
            } else {
                log.warn("WS session admission failed: status={}", e.getStatusCode());
            }
            return false;
        } catch (RestClientException e) {
            log.warn("WS session admission unavailable: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
