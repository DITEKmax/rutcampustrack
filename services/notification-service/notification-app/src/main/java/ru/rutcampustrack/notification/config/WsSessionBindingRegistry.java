package ru.rutcampustrack.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/** Binds trusted handshake identity to each live STOMP session and removes it with the transport. */
@Component
public final class WsSessionBindingRegistry {

    private static final Logger log = LoggerFactory.getLogger(WsSessionBindingRegistry.class);
    private static final int MAX_SESSION_BINDINGS = 8192;
    private static final int MAX_LIVE_TRANSPORTS = 8192;
    private static final CloseStatus ADMISSION_DENIED = new CloseStatus(1008, "Session admission denied");
    private static final CloseStatus REGISTRY_FULL = new CloseStatus(1013, "Session capacity reached");

    private final WsTicketClient ticketClient;
    private final ConcurrentMap<String, Binding> bindings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WebSocketSession> transports = new ConcurrentHashMap<>();
    private final Semaphore bindingCapacity;
    private final Semaphore transportCapacity;

    @Autowired
    public WsSessionBindingRegistry(WsTicketClient ticketClient) {
        this(ticketClient, MAX_SESSION_BINDINGS, MAX_LIVE_TRANSPORTS);
    }

    WsSessionBindingRegistry(WsTicketClient ticketClient, int maxBindings, int maxTransports) {
        if (maxBindings < 1 || maxTransports < 1) {
            throw new IllegalArgumentException("registry capacities must be positive");
        }
        this.ticketClient = ticketClient;
        this.bindingCapacity = new Semaphore(maxBindings);
        this.transportCapacity = new Semaphore(maxTransports);
    }

    /** Records a socket only when the handshake interceptor installed its trusted identity. */
    public void transportConnected(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        if (attributes == null
                || !(attributes.get(TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE)
                instanceof WsSessionAdmissionRequest)
                || !(attributes.get(TicketHandshakeInterceptor.TRANSPORT_BINDING_ID_ATTRIBUTE)
                instanceof String bindingId)) {
            close(session, ADMISSION_DENIED);
            return;
        }
        WebSocketSession existing = transports.get(bindingId);
        if (existing != null) {
            if (existing != session) {
                close(session, ADMISSION_DENIED);
            }
            return;
        }
        if (!transportCapacity.tryAcquire()) {
            close(session, REGISTRY_FULL);
            return;
        }
        existing = transports.putIfAbsent(bindingId, session);
        if (existing != null) {
            transportCapacity.release();
            if (existing != session) {
                close(session, ADMISSION_DENIED);
            }
        }
    }

    /** Called for STOMP CONNECT; client-supplied headers never establish this binding. */
    public boolean bindStompSession(String sessionId, Map<String, Object> attributes) {
        String transportBindingId = bindingId(attributes);
        WsSessionAdmissionRequest identity = identity(attributes);
        if (sessionId == null || sessionId.isBlank() || transportBindingId == null || identity == null) {
            closeTransport(transportBindingId, ADMISSION_DENIED);
            return false;
        }
        if (!transports.containsKey(transportBindingId)) {
            return false;
        }

        Binding candidate = new Binding(identity, transportBindingId);
        Binding existing = bindings.get(sessionId);
        if (existing != null) {
            boolean matches = existing.equals(candidate);
            if (!matches) {
                closeTransport(transportBindingId, ADMISSION_DENIED);
            }
            return matches;
        }
        if (!bindingCapacity.tryAcquire()) {
            closeTransport(transportBindingId, REGISTRY_FULL);
            return false;
        }
        existing = bindings.putIfAbsent(sessionId, candidate);
        if (existing != null) {
            bindingCapacity.release();
            boolean matches = existing.equals(candidate);
            if (!matches) {
                closeTransport(transportBindingId, ADMISSION_DENIED);
            }
            return matches;
        }
        if (!transports.containsKey(transportBindingId)
                && bindings.remove(sessionId, candidate)) {
            bindingCapacity.release();
            closeTransport(transportBindingId, ADMISSION_DENIED);
            return false;
        }
        return true;
    }

    public Optional<WsSessionAdmissionRequest> identity(String sessionId) {
        Binding binding = sessionId == null ? null : bindings.get(sessionId);
        return binding == null ? Optional.empty() : Optional.of(binding.identity());
    }

    /** Performs a fresh authority read for every caller; no successful result is cached. */
    public Optional<WsSessionAdmissionRequest> admit(String sessionId) {
        Binding binding = sessionId == null ? null : bindings.get(sessionId);
        if (binding == null) {
            return Optional.empty();
        }
        if (!ticketClient.admit(binding.identity())) {
            revoke(sessionId, binding, ADMISSION_DENIED);
            return Optional.empty();
        }
        return bindings.get(sessionId) == binding
                ? Optional.of(binding.identity())
                : Optional.empty();
    }

    @EventListener
    public void onStompDisconnect(SessionDisconnectEvent event) {
        disconnectStompSession(event.getSessionId());
    }

    public void disconnectStompSession(String sessionId) {
        Binding binding = sessionId == null ? null : bindings.remove(sessionId);
        if (binding == null) {
            return;
        }
        bindingCapacity.release();
        closeTransport(binding.transportBindingId(), CloseStatus.NORMAL);
    }

    /** Also called from the raw WebSocket decorator for close paths without a STOMP event. */
    public void transportDisconnected(WebSocketSession session) {
        String transportBindingId = bindingId(session.getAttributes());
        if (transportBindingId == null) {
            return;
        }
        if (transports.remove(transportBindingId, session)) {
            transportCapacity.release();
        }
        bindings.forEach((sessionId, binding) -> {
            if (binding.transportBindingId().equals(transportBindingId)
                    && bindings.remove(sessionId, binding)) {
                bindingCapacity.release();
            }
        });
    }

    private void revoke(String sessionId, Binding binding, CloseStatus closeStatus) {
        if (bindings.remove(sessionId, binding)) {
            bindingCapacity.release();
            closeTransport(binding.transportBindingId(), closeStatus);
        }
    }

    private void closeTransport(String bindingId, CloseStatus closeStatus) {
        if (bindingId == null) {
            return;
        }
        WebSocketSession session = transports.remove(bindingId);
        if (session != null) {
            transportCapacity.release();
            close(session, closeStatus);
        }
    }

    private static void close(WebSocketSession session, CloseStatus closeStatus) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(closeStatus);
        } catch (IOException exception) {
            log.debug("WS transport close failed: {}", exception.getClass().getSimpleName());
        }
    }

    private static WsSessionAdmissionRequest identity(Map<String, Object> attributes) {
        Object value = attributes == null
                ? null : attributes.get(TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE);
        return value instanceof WsSessionAdmissionRequest request ? request : null;
    }

    private static String bindingId(Map<String, Object> attributes) {
        Object value = attributes == null
                ? null : attributes.get(TicketHandshakeInterceptor.TRANSPORT_BINDING_ID_ATTRIBUTE);
        return value instanceof String id && !id.isBlank() ? id : null;
    }

    private record Binding(WsSessionAdmissionRequest identity, String transportBindingId) {}
}
