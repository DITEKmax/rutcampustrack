package ru.rutcampustrack.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for Notification Web event consumption.
 *
 * Per D-01: Declares durable queue 'notification-web.events' bound to fanout exchange 'rut-uit.events'.
 * Per D-02: DLQ infrastructure declared at startup — main queue routes dead letters to DLQ.
 * Per D-03: Jackson2JsonMessageConverter uses the shared Spring-managed ObjectMapper
 *           (already has JavaTimeModule from JacksonAutoConfiguration — do NOT create new ObjectMapper).
 * Per Pitfall 1: RabbitTemplate does NOT set channelTransacted=true to avoid message loss.
 *
 * Bean names are notificationWeb-specific to avoid Spring bean name clash if multiple services
 * load in same test context.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public FanoutExchange notificationWebEventsExchange() {
        return new FanoutExchange("rut-uit.events", true, false);
    }

    @Bean
    public DirectExchange notificationWebDlqExchange() {
        return new DirectExchange("rut-uit.events.dlq", true, false);
    }

    @Bean
    public Queue notificationWebEventsQueue() {
        return QueueBuilder.durable("notification-web.events")
                .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                .withArgument("x-dead-letter-routing-key", "notification-web.events.dlq")
                .build();
    }

    @Bean
    public Queue notificationWebDlqQueue() {
        return QueueBuilder.durable("notification-web.events.dlq").build();
    }

    @Bean
    public Binding notificationWebQueueBinding(FanoutExchange notificationWebEventsExchange,
                                                Queue notificationWebEventsQueue) {
        return BindingBuilder.bind(notificationWebEventsQueue).to(notificationWebEventsExchange);
    }

    @Bean
    public Binding notificationWebDlqBinding(DirectExchange notificationWebDlqExchange,
                                              Queue notificationWebDlqQueue) {
        return BindingBuilder.bind(notificationWebDlqQueue)
                .to(notificationWebDlqExchange)
                .with("notification-web.events.dlq");
    }

    @Bean
    public Jackson2JsonMessageConverter notificationWebJacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter notificationWebJacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(notificationWebJacksonMessageConverter);
        // Do NOT set channelTransacted=true — causes message loss with AFTER_COMMIT (Pitfall 1)
        return template;
    }
}
