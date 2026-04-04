package ru.rutcampustrack.attendance.config;

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
 * RabbitMQ configuration for Attendance Service event consumption.
 *
 * Per D-05: DLQ infrastructure declared at startup — main queue routes dead letters to DLQ.
 * Per D-06: Generic envelope consumer reads event_type field for routing.
 * Per research: Jackson2JsonMessageConverter uses the shared Spring-managed ObjectMapper
 *               (already has JavaTimeModule from JacksonAutoConfiguration — do NOT create new ObjectMapper).
 * Per Pitfall 1: RabbitTemplate does NOT set channelTransacted=true to avoid message loss.
 * Per Pitfall 3: Injects the base Spring Boot ObjectMapper (no NON_FINAL default typing).
 *
 * Bean names are attendance-specific to avoid Spring bean name clash if multiple services
 * load in same test context.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public FanoutExchange attendanceEventsExchange() {
        return new FanoutExchange("rut-uit.events", true, false);
    }

    @Bean
    public DirectExchange attendanceDlqExchange() {
        return new DirectExchange("rut-uit.events.dlq", true, false);
    }

    @Bean
    public Queue attendanceEventsQueue() {
        return QueueBuilder.durable("attendance-service.events")
                .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                .withArgument("x-dead-letter-routing-key", "attendance-service.events.dlq")
                .build();
    }

    @Bean
    public Queue attendanceDlqQueue() {
        return QueueBuilder.durable("attendance-service.events.dlq").build();
    }

    @Bean
    public Binding attendanceQueueBinding(FanoutExchange attendanceEventsExchange,
                                           Queue attendanceEventsQueue) {
        return BindingBuilder.bind(attendanceEventsQueue).to(attendanceEventsExchange);
    }

    @Bean
    public Binding attendanceDlqBinding(DirectExchange attendanceDlqExchange,
                                         Queue attendanceDlqQueue) {
        return BindingBuilder.bind(attendanceDlqQueue)
                .to(attendanceDlqExchange)
                .with("attendance-service.events.dlq");
    }

    @Bean
    public Jackson2JsonMessageConverter attendanceJacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter attendanceJacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(attendanceJacksonMessageConverter);
        // Do NOT set channelTransacted=true — causes message loss with AFTER_COMMIT (Pitfall 1)
        return template;
    }
}
