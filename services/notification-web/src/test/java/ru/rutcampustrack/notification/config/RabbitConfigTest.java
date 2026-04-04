package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void eventsQueue_hasDlqArguments() {
        Queue queue = config.notificationWebEventsQueue();
        assertThat(queue.getName()).isEqualTo("notification-web.events");
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "rut-uit.events.dlq")
                .containsEntry("x-dead-letter-routing-key", "notification-web.events.dlq");
    }

    @Test
    void dlqQueue_hasCorrectName() {
        Queue queue = config.notificationWebDlqQueue();
        assertThat(queue.getName()).isEqualTo("notification-web.events.dlq");
    }

    @Test
    void eventsExchange_isFanoutWithCorrectName() {
        FanoutExchange exchange = config.notificationWebEventsExchange();
        assertThat(exchange.getName()).isEqualTo("rut-uit.events");
        assertThat(exchange).isInstanceOf(FanoutExchange.class);
    }

    @Test
    void dlqExchange_isDirectWithCorrectName() {
        DirectExchange exchange = config.notificationWebDlqExchange();
        assertThat(exchange.getName()).isEqualTo("rut-uit.events.dlq");
        assertThat(exchange).isInstanceOf(DirectExchange.class);
    }

    @Test
    void jacksonConverter_isCreatedWithObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        Jackson2JsonMessageConverter converter = config.notificationWebJacksonMessageConverter(objectMapper);
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
