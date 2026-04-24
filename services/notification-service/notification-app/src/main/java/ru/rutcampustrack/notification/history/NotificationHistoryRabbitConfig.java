package ru.rutcampustrack.notification.history;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ beans для history persister queue (M10 G3 D5).
 *
 * <p>Binding: existing fanout exchange {@code rut-uit.events} → новая
 * durable queue {@code notification-web.history} с DLQ. Decoupled
 * от delivery queue {@code notification-web.events} — error в
 * persister не рушит STOMP push delivery.
 */
@Configuration
public class NotificationHistoryRabbitConfig {

    @Bean
    public Queue notificationHistoryQueue() {
        return QueueBuilder.durable("notification-web.history")
                .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                .withArgument("x-dead-letter-routing-key", "notification-web.history.dlq")
                .build();
    }

    @Bean
    public Queue notificationHistoryDlqQueue() {
        return QueueBuilder.durable("notification-web.history.dlq").build();
    }

    @Bean
    public Binding notificationHistoryBinding(FanoutExchange notificationWebEventsExchange,
                                              Queue notificationHistoryQueue) {
        return BindingBuilder.bind(notificationHistoryQueue).to(notificationWebEventsExchange);
    }

    @Bean
    public Binding notificationHistoryDlqBinding(DirectExchange notificationWebDlqExchange,
                                                 Queue notificationHistoryDlqQueue) {
        return BindingBuilder.bind(notificationHistoryDlqQueue)
                .to(notificationWebDlqExchange)
                .with("notification-web.history.dlq");
    }
}
