package ru.rutcampustrack.schedule.event;

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
 * RabbitMQ configuration for Schedule Service event publishing.
 * <p>
 * Per D-07: declares durable, non-auto-delete fanout exchange "rut-uit.events".
 * Per D-08: Jackson2JsonMessageConverter uses the shared Spring-managed ObjectMapper
 *           (already has JavaTimeModule from JacksonAutoConfiguration — do NOT create new ObjectMapper).
 * Per Pitfall 1: RabbitTemplate does NOT set channelTransacted=true to avoid message loss with AFTER_COMMIT.
 * Per Pitfall 3: Injects the base Spring Boot ObjectMapper, not CacheConfig's custom ObjectMapper
 *                (which has NON_FINAL default typing that adds @class fields to AMQP messages).
 * <p>
 * Bean method named scheduleEventsExchange() to avoid Spring bean name clash if both services
 * load in same test context (avoids conflict with academic-service's academicEventsExchange bean).
 */
@Configuration
public class RabbitConfig {

    @Bean
    public FanoutExchange scheduleEventsExchange() {
        return new FanoutExchange("rut-uit.events", true, false);
    }

    /**
     * DLQ infrastructure for schedule-service consumer. Mirrors attendance-service
     * pattern — poison messages land in a dedicated queue instead of blocking
     * main consumer.
     */
    @Bean
    public DirectExchange scheduleDlqExchange() {
        return new DirectExchange("rut-uit.events.dlq", true, false);
    }

    @Bean
    public Queue scheduleEventsQueue() {
        return QueueBuilder.durable("schedule-service.events")
                .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                .withArgument("x-dead-letter-routing-key", "schedule-service.events.dlq")
                .build();
    }

    @Bean
    public Queue scheduleDlqQueue() {
        return QueueBuilder.durable("schedule-service.events.dlq").build();
    }

    @Bean
    public Binding scheduleQueueBinding(FanoutExchange scheduleEventsExchange,
                                         Queue scheduleEventsQueue) {
        return BindingBuilder.bind(scheduleEventsQueue).to(scheduleEventsExchange);
    }

    @Bean
    public Binding scheduleDlqBinding(DirectExchange scheduleDlqExchange,
                                       Queue scheduleDlqQueue) {
        return BindingBuilder.bind(scheduleDlqQueue)
                .to(scheduleDlqExchange)
                .with("schedule-service.events.dlq");
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Do NOT set channelTransacted=true — causes message loss with AFTER_COMMIT (Pitfall 1)
        return template;
    }
}
