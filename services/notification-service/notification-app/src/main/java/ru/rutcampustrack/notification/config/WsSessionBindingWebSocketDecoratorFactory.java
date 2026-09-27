package ru.rutcampustrack.notification.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

/** Connects raw WebSocket close callbacks to the STOMP session binding lifecycle. */
@Component
public final class WsSessionBindingWebSocketDecoratorFactory implements WebSocketHandlerDecoratorFactory {

    private final WsSessionBindingRegistry registry;

    public WsSessionBindingWebSocketDecoratorFactory(WsSessionBindingRegistry registry) {
        this.registry = registry;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                super.afterConnectionEstablished(session);
                registry.transportConnected(session);
            }

            @Override
            public void afterConnectionClosed(
                    WebSocketSession session,
                    org.springframework.web.socket.CloseStatus closeStatus
            ) throws Exception {
                try {
                    super.afterConnectionClosed(session, closeStatus);
                } finally {
                    registry.transportDisconnected(session);
                }
            }
        };
    }
}
