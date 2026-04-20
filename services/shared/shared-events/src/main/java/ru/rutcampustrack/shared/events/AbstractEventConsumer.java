package ru.rutcampustrack.shared.events;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Базовый consumer (NEW-60): извлекает {@code trace_id} из прилетевшего
 * {@link DomainEvent}, кладёт в MDC перед вызовом handler'а и гарантированно
 * очищает MDC после (даже при исключении).
 *
 * <p>Без привязки к AMQP — подключение к Rabbit listener будет сделано в M02.
 */
public abstract class AbstractEventConsumer {

    public static final String MDC_TRACE_ID = AbstractEventPublisher.MDC_TRACE_ID;

    /**
     * Выполняет handler в MDC-контексте события. Если {@code trace_id} события
     * null — MDC не трогается вообще (чтобы не затереть существующий контекст).
     *
     * @param event   прилетевшее событие.
     * @param handler действие, которое надо выполнить в MDC-контексте.
     * @param <T>     тип события.
     */
    protected <T extends DomainEvent> void withTraceContext(T event, Consumer<T> handler) {
        runInTraceContext(event.getTraceId(), () -> handler.accept(event));
    }

    /**
     * Map-based вариант для consumer'ов, которые принимают envelope как
     * {@code Map<String,Object>} (исторически — все 3 RabbitListener'а в
     * сервисах). Извлекает {@code trace_id} из envelope, выполняет runnable,
     * восстанавливает MDC.
     */
    protected void withTraceContext(Map<String, Object> envelope, Runnable handler) {
        String traceId = envelope == null ? null : (String) envelope.get("trace_id");
        runInTraceContext(traceId, handler);
    }

    private void runInTraceContext(String traceId, Runnable handler) {
        String previous = null;
        boolean replaced = false;
        if (traceId != null) {
            previous = MDC.get(MDC_TRACE_ID);
            MDC.put(MDC_TRACE_ID, traceId);
            replaced = true;
        }
        try {
            handler.run();
        } finally {
            if (replaced) {
                if (previous == null) {
                    MDC.remove(MDC_TRACE_ID);
                } else {
                    MDC.put(MDC_TRACE_ID, previous);
                }
            }
        }
    }
}
