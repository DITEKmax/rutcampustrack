package ru.rutcampustrack.shared.outbox;

/**
 * Callback для отправки одного события во внешний транспорт (обычно
 * RabbitMQ). {@code OutboxPublisherJob} вызывает эту функцию для каждой
 * pending-записи.
 *
 * <p>Реализация в сервисе (Группа 5): shim вокруг {@code RabbitTemplate}
 * который шлёт payload в {@code rut-uit.events} fanout exchange. Сервис
 * решает как десериализовать JSON (или отдать как есть, если транспорт
 * принимает text).
 *
 * <p>Исключения из sender'а ловит {@code OutboxPublisherJob} и помечает
 * запись {@link OutboxStatus#FAILED}.
 */
@FunctionalInterface
public interface OutboxEventSender {
    /**
     * @param eventType тип события (для логов / routing)
     * @param payload   сериализованный JSON event body
     * @throws Exception любая ошибка транспорта → markFailed
     */
    void send(String eventType, String payload) throws Exception;
}
