package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ-конфиг Auth Service (симметричен academic-service/RabbitConfig).
 * Fanout exchange {@code rut-uit.events} — durable, non-auto-delete.
 * Jackson2JsonMessageConverter использует Spring-managed ObjectMapper,
 * уже настроенный с JavaTimeModule.
 *
 * <p>M09 G2.6: убран {@code @ConditionalOnBean(ConnectionFactory.class)} —
 * user {@code @Configuration} оценивается ДО autoconfig'а и condition всегда
 * давал false, даже когда {@code RabbitAutoConfiguration} был активен.
 * Результат: наш {@code RabbitTemplate} с Jackson-конвертером не создавался,
 * а default-autoconfig {@code RabbitTemplate} с {@code SimpleMessageConverter}
 * (byte[]/Java-serialized) попадал в {@link DomainEventListener} — event
 * публиковался, но consumer'ы не могли распарсить JSON.
 *
 * <p>Теперь configuration активна всегда, когда класс на classpath
 * (т.е. всегда — auth-service зависит от {@code spring-boot-starter-amqp}).
 * Для тестов, где {@code RabbitAutoConfiguration} excluded, bean
 * {@code ConnectionFactory} отсутствует → Spring падает при инжекте в
 * {@code rabbitTemplate()}. Поэтому application-test.yml больше НЕ
 * исключает RabbitAutoConfiguration: тестовый {@code CachingConnectionFactory}
 * создаётся с default host (= нереальный — publish будет проваливаться
 * при реальной отправке, но это fire-and-forget catch Exception в listener).
 */
@Configuration
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

    @Bean
    public DomainEventListener domainEventListener(RabbitTemplate rabbitTemplate) {
        return new DomainEventListener(rabbitTemplate);
    }
}
