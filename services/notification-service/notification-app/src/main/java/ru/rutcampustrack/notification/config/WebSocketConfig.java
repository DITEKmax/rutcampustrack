package ru.rutcampustrack.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket configuration for notification-web.
 *
 * <p>Per D-09: STOMP endpoint /ws with SockJS fallback.
 * M03b Группа 4: {@link TicketHandshakeInterceptor} validates
 * {@code ?ticket=<uuid>} at HTTP Upgrade (replaces legacy
 * {@code JwtHandshakeInterceptor} which accepted raw JWT in URL).
 * Per D-04: Simple in-memory broker on /topic — group topics: /topic/group/{groupId}.
 * Per D-10: Default Spring heartbeat (10s server, 10s client) — no custom tuning needed.
 * Per Pitfall 6: setApplicationDestinationPrefixes is NOT called —
 *   this service never receives messages FROM clients via STOMP SEND frames.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TicketHandshakeInterceptor ticketHandshakeInterceptor;
    private final SubscriptionAuthInterceptor subscriptionAuthInterceptor;

    @Value("${notification.ws.allowed-origins:http://localhost:5173,http://localhost:4200,http://localhost:3000,https://ruttrack.site}")
    private String allowedOrigins;

    public WebSocketConfig(TicketHandshakeInterceptor ticketHandshakeInterceptor,
                           SubscriptionAuthInterceptor subscriptionAuthInterceptor) {
        this.ticketHandshakeInterceptor = ticketHandshakeInterceptor;
        this.subscriptionAuthInterceptor = subscriptionAuthInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // D-09: STOMP endpoint /ws with SockJS fallback.
        // M03b Группа 4: ticket-based handshake (single-use opaque UUID).
        // IMP-06: Restrict CORS to configured origins instead of wildcard.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .addInterceptors(ticketHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // IMP-01: Validate subscription destinations against user's group
        registration.interceptors(subscriptionAuthInterceptor);
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
