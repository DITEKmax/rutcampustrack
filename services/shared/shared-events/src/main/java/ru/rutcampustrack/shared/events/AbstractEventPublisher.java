package ru.rutcampustrack.shared.events;

import org.slf4j.MDC;

import java.time.Instant;

/**
 * Базовый publisher (NEW-60): заполняет стандартные поля {@link DomainEvent}
 * из MDC/reflection перед отправкой. Без привязки к AMQP — интеграция с
 * RabbitMQ делается в M02.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Подкласс/вызывающий создаёт event ({@code new LessonStartedEvent(lessonId)}).</li>
 *   <li>Вызывает {@link #fillDefaults(DomainEvent)} — обогащает event'ом из MDC
 *       (trace_id), reflection'а (event_version), clock'а (occurred_at) и
 *       конструктора publisher'а (source).</li>
 *   <li>Сериализует и отправляет в транспорт.</li>
 * </ol>
 *
 * <p>Уже-заданные значения не перезаписываются — если caller явно указал
 * trace_id/event_version/occurred_at/source, publisher их не трогает.
 */
public abstract class AbstractEventPublisher {

    /** MDC key для correlation id (совпадает с shared-web GlobalExceptionHandler). */
    public static final String MDC_TRACE_ID = "traceId";

    private final String source;

    protected AbstractEventPublisher(String source) {
        this.source = source;
    }

    /**
     * Обогащает event стандартными полями in-place. Вызывается подклассом
     * publisher'а непосредственно перед сериализацией.
     *
     * @param event событие с опционально уже заполненными полями; null-поля
     *              будут заполнены значениями по умолчанию.
     * @param <T>   тип события
     * @return тот же event (fluent-использование).
     */
    protected <T extends DomainEvent> T fillDefaults(T event) {
        if (event.getEventVersion() == null) {
            event.setEventVersion(resolveEventVersion(event.getClass()));
        }
        if (event.getTraceId() == null) {
            event.setTraceId(MDC.get(MDC_TRACE_ID));
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(Instant.now());
        }
        if (event.getSource() == null) {
            event.setSource(source);
        }
        return event;
    }

    /**
     * Читает {@link EventVersion} через reflection. Отсутствие аннотации = 1.
     * Ищет аннотацию по всей цепочке наследования (включая абстрактные классы).
     */
    static int resolveEventVersion(Class<?> eventClass) {
        Class<?> current = eventClass;
        while (current != null && current != Object.class) {
            EventVersion annotation = current.getAnnotation(EventVersion.class);
            if (annotation != null) {
                return annotation.value();
            }
            current = current.getSuperclass();
        }
        return 1;
    }
}
