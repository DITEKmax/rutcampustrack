package ru.rutcampustrack.academic.event;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.TransactionException;

import java.util.Map;

/**
 * RabbitMQ configuration for Academic Service event publishing.
 * <p>
 * Per D-07: declares durable, non-auto-delete fanout exchange "rut-uit.events".
 * Per D-08: Jackson2JsonMessageConverter uses the shared Spring-managed ObjectMapper
 *           (already has JavaTimeModule from JacksonAutoConfiguration — do NOT create new ObjectMapper).
 * Per Pitfall 1: RabbitTemplate does NOT set channelTransacted=true to avoid message loss with AFTER_COMMIT.
 * Per Pitfall 3: Injects the base Spring Boot ObjectMapper, not CacheConfig's custom ObjectMapper
 *                (which has NON_FINAL default typing that adds @class fields to AMQP messages).
 */
@Configuration
public class RabbitConfig {

    public static final String HOMEWORK_ARCHIVE_EVENTS_QUEUE = "academic-service.homework-archive.events";
    private static final String HOMEWORK_ARCHIVE_EVENTS_DLQ = HOMEWORK_ARCHIVE_EVENTS_QUEUE + ".dlq";
    public static final String LESSON_TRANSFER_EVENTS_QUEUE = "academic-service.lesson-transfer.events";
    private static final String LESSON_TRANSFER_EVENTS_DLQ = LESSON_TRANSFER_EVENTS_QUEUE + ".dlq";
    private static final int MAX_LISTENER_ATTEMPTS = 3;
    private static final long LISTENER_INITIAL_BACKOFF_MILLIS = 100L;
    private static final long LISTENER_MAX_BACKOFF_MILLIS = 500L;

    @Bean
    public FanoutExchange academicEventsExchange() {
        return new FanoutExchange("rut-uit.events", true, false);
    }

    @Bean
    public DirectExchange academicEventsDeadLetterExchange() {
        return new DirectExchange("rut-uit.events.dlq", true, false);
    }

    @Bean
    public Queue homeworkArchiveEventsQueue() {
        return QueueBuilder.durable(HOMEWORK_ARCHIVE_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                .withArgument("x-dead-letter-routing-key", HOMEWORK_ARCHIVE_EVENTS_DLQ)
                .build();
    }

    @Bean
    public Queue homeworkArchiveEventsDeadLetterQueue() {
        return QueueBuilder.durable(HOMEWORK_ARCHIVE_EVENTS_DLQ).build();
    }

    @Bean
    public Queue lessonTransferEventsQueue() {
        return QueueBuilder.durable(LESSON_TRANSFER_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                .withArgument("x-dead-letter-routing-key", LESSON_TRANSFER_EVENTS_DLQ)
                .build();
    }

    @Bean
    public Queue lessonTransferEventsDeadLetterQueue() {
        return QueueBuilder.durable(LESSON_TRANSFER_EVENTS_DLQ).build();
    }

    @Bean
    public Binding homeworkArchiveEventsBinding(FanoutExchange academicEventsExchange,
            @Qualifier("homeworkArchiveEventsQueue") Queue homeworkArchiveEventsQueue) {
        return BindingBuilder.bind(homeworkArchiveEventsQueue).to(academicEventsExchange);
    }

    @Bean
    public Binding lessonTransferEventsBinding(FanoutExchange academicEventsExchange,
            @Qualifier("lessonTransferEventsQueue") Queue lessonTransferEventsQueue) {
        return BindingBuilder.bind(lessonTransferEventsQueue).to(academicEventsExchange);
    }

    @Bean
    public Binding homeworkArchiveEventsDeadLetterBinding(
            DirectExchange academicEventsDeadLetterExchange,
            @Qualifier("homeworkArchiveEventsDeadLetterQueue") Queue homeworkArchiveEventsDeadLetterQueue) {
        return BindingBuilder.bind(homeworkArchiveEventsDeadLetterQueue)
                .to(academicEventsDeadLetterExchange)
                .with(HOMEWORK_ARCHIVE_EVENTS_DLQ);
    }

    @Bean
    public Binding lessonTransferEventsDeadLetterBinding(
            DirectExchange academicEventsDeadLetterExchange,
            @Qualifier("lessonTransferEventsDeadLetterQueue") Queue lessonTransferEventsDeadLetterQueue) {
        return BindingBuilder.bind(lessonTransferEventsDeadLetterQueue)
                .to(academicEventsDeadLetterExchange)
                .with(LESSON_TRANSFER_EVENTS_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean(name = "rabbitListenerContainerFactory")
    @ConditionalOnBean(ConnectionFactory.class)
    public SimpleRabbitListenerContainerFactory academicRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jacksonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(academicListenerRetryPolicy())
                .backOffOptions(
                        LISTENER_INITIAL_BACKOFF_MILLIS,
                        2.0,
                        LISTENER_MAX_BACKOFF_MILLIS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    private RetryPolicy academicListenerRetryPolicy() {
        return new SimpleRetryPolicy(MAX_LISTENER_ATTEMPTS, Map.of(
                TransactionException.class, true,
                TransientDataAccessException.class, true
        ), true);
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
