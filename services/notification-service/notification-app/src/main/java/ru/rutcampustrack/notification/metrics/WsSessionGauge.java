package ru.rutcampustrack.notification.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import ru.rutcampustrack.shared.observability.MetricNames;

import java.util.concurrent.atomic.AtomicLong;

/**
 * M04 Группа 8 — gauge {@link MetricNames#ACTIVE_WS_SESSIONS}.
 *
 * <p>Считает live STOMP-сессий через {@link SessionConnectedEvent} (инкремент)
 * и {@link SessionDisconnectEvent} (декремент). Counter на handshake-уровне
 * работал бы хуже — afterHandshake не ловит disconnect, TCP-обрывы
 * оставались бы неучтёнными.
 *
 * <p>После рестарта контейнера gauge = 0 (OK — клиенты переподключаются и
 * gauge отреагирует через ~10-30 сек; overnight выравнивание через
 * heartbeat tracking не требуется для сигнала health/scale).
 */
@Component
public class WsSessionGauge {

    private final AtomicLong count = new AtomicLong(0);

    public WsSessionGauge(MeterRegistry registry) {
        Gauge.builder(MetricNames.ACTIVE_WS_SESSIONS, count, AtomicLong::get)
                .description("Активные STOMP WebSocket-сессии")
                .register(registry);
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        count.incrementAndGet();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        // decrementAndGet с guard от уйти в отрицательные (rare race при
        // multiple disconnect events на одну session).
        count.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }
}
