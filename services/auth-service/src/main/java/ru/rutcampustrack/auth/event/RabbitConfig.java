package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ-конфиг Auth Service (симметричен academic-service/RabbitConfig).
 * Fanout exchange {@code rut-uit.events} — durable, non-auto-delete.
 * Jackson2JsonMessageConverter использует Spring-managed ObjectMapper,
 * уже настроенный с JavaTimeModule.
 */
@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class RabbitConfig {

    public static final String EXCHANGE = "rut-uit.events";

    @Bean
    public FanoutExchange authEventsExchange() {
        return new FanoutExchange(EXCHANGE, true, false);
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
        return template;
    }
}
