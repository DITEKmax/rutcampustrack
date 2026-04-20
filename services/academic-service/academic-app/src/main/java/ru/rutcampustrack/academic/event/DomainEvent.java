package ru.rutcampustrack.academic.event;

/**
 * Academic-service alias для {@link ru.rutcampustrack.shared.events.DomainEvent}
 * (M04 D5(a) — единый envelope across services).
 *
 * <p>Сохраняет старую сигнатуру конструктора {@code (source, eventType, payload)}
 * для backward-compat с существующими подклассами. Параметр {@code source}
 * игнорируется (Spring ApplicationEvent не требует non-null source — shared
 * родитель ставит {@code eventType} как source автоматически). Поле {@code source}
 * в JSON envelope (имя сервиса-источника) заполняется
 * {@code DomainEventListener} через {@code AbstractEventPublisher.fillDefaults}.
 */
public abstract class DomainEvent extends ru.rutcampustrack.shared.events.DomainEvent {

    protected DomainEvent(Object source, String eventType, Object payload) {
        super(eventType, payload);
    }
}
