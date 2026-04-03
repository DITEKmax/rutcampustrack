package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.FanoutExchange;
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
