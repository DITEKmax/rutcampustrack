package ru.rutcampustrack.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket configuration for notification-web.
 *
 * <p>Per D-09: STOMP endpoint /ws with SockJS fallback.
 * Per D-02: JwtHandshakeInterceptor validates ?token= at HTTP Upgrade level.
 * Per D-04: Simple in-memory broker on /topic — group topics: /topic/group/{groupId}.
 * Per D-10: Default Spring heartbeat (10s server, 10s client) — no custom tuning needed.
 * Per Pitfall 6: setApplicationDestinationPrefixes is NOT called —
 *   this service never receives messages FROM clients via STOMP SEND frames.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // D-09: STOMP endpoint /ws with SockJS fallback
        // D-02: JwtHandshakeInterceptor validates ?token= at HTTP Upgrade level
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // CORS for web panel connecting from different origin
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // D-04: Simple in-memory broker on /topic — group topics: /topic/group/{groupId}
        // Headman-only events use /topic/group/{groupId}/headman (Pitfall 2 avoidance)
        config.enableSimpleBroker("/topic");
        // Pitfall 6: Do NOT call setApplicationDestinationPrefixes
        // — this service never receives messages FROM clients
    }
}
