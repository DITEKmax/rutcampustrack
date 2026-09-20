package ru.rutcampustrack.attendance.config;

import com.mongodb.MongoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.TransactionException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;

import java.util.Map;

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

    static final int MAX_LISTENER_ATTEMPTS = 3;
    static final long LISTENER_INITIAL_BACKOFF_MILLIS = 100L;
    static final long LISTENER_MAX_BACKOFF_MILLIS = 500L;

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

    /**
     * Keep transient decision-event failures retryable, while making malformed
     * or unauthorized envelopes terminate at the Attendance DLQ.  The queue's
     * dead-letter arguments are only effective after the recoverer rejects the
     * message; relying on the container default would otherwise requeue forever.
     */
    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory attendanceRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter attendanceJacksonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(attendanceJacksonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(attendanceListenerRetryPolicy())
                .backOffOptions(
                        LISTENER_INITIAL_BACKOFF_MILLIS,
                        2.0,
                        LISTENER_MAX_BACKOFF_MILLIS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    private RetryPolicy attendanceListenerRetryPolicy() {
        return new SimpleRetryPolicy(MAX_LISTENER_ATTEMPTS, Map.of(
                AcademicServiceUnavailableException.class, true,
                ScheduleServiceUnavailableException.class, true,
                TransactionException.class, true,
                TransientDataAccessException.class, true,
                MongoException.class, true
        ), true);
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
