package ru.rutcampustrack.attendance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;

/**
 * RabbitMQ configuration for Attendance Service event consumption.
 *
 * <p>Per D-05: DLQ infrastructure declared at startup — main queue routes dead letters to DLQ. Per
 * D-06: Generic envelope consumer reads event_type field for routing. Per research:
 * Jackson2JsonMessageConverter uses the shared Spring-managed ObjectMapper (already has
 * JavaTimeModule from JacksonAutoConfiguration — do NOT create new ObjectMapper). Per Pitfall 1:
 * RabbitTemplate does NOT set channelTransacted=true to avoid message loss. Per Pitfall 3: Injects
 * the base Spring Boot ObjectMapper (no NON_FINAL default typing).
 *
 * <p>Bean names are attendance-specific to avoid Spring bean name clash if multiple services load
 * in same test context.
 */
@Configuration
public class RabbitConfig {

    static final int MAX_LISTENER_ATTEMPTS = 3;
    static final long LISTENER_INITIAL_BACKOFF_MILLIS = 100L;
    static final long LISTENER_MAX_BACKOFF_MILLIS = 500L;
    public static final String RETRY_QUEUE = "attendance-service.events.retry";
    static final int REDRIVE_DELAY_MILLIS = 30_000;

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
    public Queue attendanceRetryQueue() {
        // New queue only. Existing main/DLQ queue declarations and types remain compatible.
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(REDRIVE_DELAY_MILLIS)
                .deadLetterExchange("")
                .deadLetterRoutingKey("attendance-service.events")
                .build();
    }

    @Bean
    public Binding attendanceQueueBinding(
            FanoutExchange attendanceEventsExchange, Queue attendanceEventsQueue) {
        return BindingBuilder.bind(attendanceEventsQueue).to(attendanceEventsExchange);
    }

    @Bean
    public Binding attendanceDlqBinding(
            DirectExchange attendanceDlqExchange, Queue attendanceDlqQueue) {
        return BindingBuilder.bind(attendanceDlqQueue)
                .to(attendanceDlqExchange)
                .with("attendance-service.events.dlq");
    }

    @Bean
    public Jackson2JsonMessageConverter attendanceJacksonMessageConverter(
            ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Keep transient decision-event failures retryable, while making malformed or unauthorized
     * envelopes terminate at the Attendance DLQ. The queue's dead-letter arguments are only
     * effective after the recoverer rejects the message; relying on the container default would
     * otherwise requeue forever.
     */
    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory attendanceRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter attendanceJacksonMessageConverter,
            MeterRegistry registry,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {
        var retrySender =
                new ru.rutcampustrack.shared.outbox.ConfirmedRabbitOutboxSender(rabbitTemplate);
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(attendanceJacksonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setObservationEnabled(true);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .retryPolicy(attendanceListenerRetryPolicy())
                        .backOffOptions(
                                LISTENER_INITIAL_BACKOFF_MILLIS, 2.0, LISTENER_MAX_BACKOFF_MILLIS)
                        .recoverer(
                                (message, failure) -> {
                                    String reason = terminalReason(failure);
                                    if ("exhausted".equals(reason)
                                            && message.getMessageProperties()
                                                            .getHeader("rct_redrive_count")
                                                    == null) {
                                        org.springframework.amqp.core.Message retry =
                                                org.springframework.amqp.core.MessageBuilder
                                                        .fromClonedMessage(message)
                                                        .setDeliveryMode(
                                                                org.springframework.amqp.core
                                                                        .MessageDeliveryMode
                                                                        .PERSISTENT)
                                                        .setHeader("rct_redrive_count", 1)
                                                        .setHeader(
                                                                "rct_failure_code",
                                                                "transient_dependency")
                                                        .build();
                                        if (retry.getMessageProperties().getMessageId() == null) {
                                            try {
                                                String eventId =
                                                        objectMapper
                                                                .readTree(message.getBody())
                                                                .path("event_id")
                                                                .asText();
                                                retry.getMessageProperties()
                                                        .setMessageId(
                                                                java.util
                                                                        .UUID
                                                                        .fromString(eventId)
                                                                        .toString());
                                            } catch (Exception invalidIdentity) {
                                                reason = "invalid";
                                            }
                                        }
                                        if (!"invalid".equals(reason)) {
                                            try {
                                                retrySender.sendToQueue(RETRY_QUEUE, retry);
                                                registry.counter(
                                                                "attendance.events.retry.scheduled.total")
                                                        .increment();
                                                return; // ACK only after confirmed durable retry
                                                // publication.
                                            } catch (
                                                    org.springframework.amqp.AmqpException
                                                            publishFailure) {
                                                reason = "retry_publish_failed";
                                            }
                                        }
                                    }
                                    registry.counter(
                                                    "attendance.events.deadletter.total",
                                                    "reason",
                                                    reason)
                                            .increment();
                                    // Do not log the message body or the exception text. The broker
                                    // retains
                                    // the original message plus x-death reason= rejected in the
                                    // DLQ.
                                    throw new org.springframework.amqp
                                            .AmqpRejectAndDontRequeueException(
                                            "Attendance event rejected: " + reason);
                                })
                        .build());
        return factory;
    }

    private static String terminalReason(Throwable failure) {
        Throwable current = failure;
        boolean transientFailure = false;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            if (current instanceof IllegalArgumentException
                    || current
                            instanceof
                            org.springframework.amqp.support.converter.MessageConversionException) {
                return "invalid";
            }
            if ((current instanceof AcademicServiceUnavailableException academic
                            && academic.isRetryable())
                    || (current instanceof ScheduleServiceUnavailableException schedule
                            && schedule.isRetryable())
                    || current
                            instanceof
                            org.springframework.transaction.CannotCreateTransactionException
                    || current instanceof TransientDataAccessException
                    || current instanceof com.mongodb.MongoSocketException
                    || current instanceof com.mongodb.MongoTimeoutException
                    || (current instanceof MongoException mongo
                            && (mongo.hasErrorLabel("TransientTransactionError")
                                    || mongo.hasErrorLabel("UnknownTransactionCommitResult")))) {
                transientFailure = true;
            }
        }
        return transientFailure ? "exhausted" : "failure";
    }

    private RetryPolicy attendanceListenerRetryPolicy() {
        return new SimpleRetryPolicy(MAX_LISTENER_ATTEMPTS) {
            @Override
            public boolean canRetry(org.springframework.retry.RetryContext context) {
                return context.getLastThrowable() == null
                        || (context.getRetryCount() < MAX_LISTENER_ATTEMPTS
                                && "exhausted".equals(terminalReason(context.getLastThrowable())));
            }
        };
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter attendanceJacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(attendanceJacksonMessageConverter);
        template.setObservationEnabled(true);
        // Do NOT set channelTransacted=true — causes message loss with AFTER_COMMIT (Pitfall 1)
        return template;
    }
}
