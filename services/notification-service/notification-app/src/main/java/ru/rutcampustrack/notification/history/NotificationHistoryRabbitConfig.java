package ru.rutcampustrack.notification.history;

import com.mongodb.MongoException;
import io.grpc.StatusRuntimeException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.TransactionException;

import java.util.Map;

/**
 * RabbitMQ beans для history persister queue (M10 G3 D5).
 *
 * <p>Binding: existing fanout exchange {@code rut-uit.events} → новая
 * durable queue {@code notification-web.history} с DLQ. Decoupled
 * от delivery queue {@code notification-web.events} — error в
 * persister не рушит STOMP push delivery.
 */
@Configuration
public class NotificationHistoryRabbitConfig {

    private static final int MAX_LISTENER_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 100L;
    private static final long MAX_BACKOFF_MILLIS = 1_000L;

    @Bean(name = "notificationHistoryRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory notificationHistoryRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter notificationWebJacksonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(notificationWebJacksonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(notificationHistoryRetryPolicy())
                .backOffOptions(INITIAL_BACKOFF_MILLIS, 2.0, MAX_BACKOFF_MILLIS)
                .recoverer(new org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    private RetryPolicy notificationHistoryRetryPolicy() {
        return new SimpleRetryPolicy(MAX_LISTENER_ATTEMPTS, Map.of(
                StatusRuntimeException.class, true,
                DuplicateKeyException.class, true,
                TransactionException.class, true,
                TransientDataAccessException.class, true,
                MongoException.class, true
        ), true);
    }

    @Bean
    public Queue notificationHistoryQueue() {
        return QueueBuilder.durable("notification-web.history")
                .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                .withArgument("x-dead-letter-routing-key", "notification-web.history.dlq")
                .build();
    }

    @Bean
    public Queue notificationHistoryDlqQueue() {
        return QueueBuilder.durable("notification-web.history.dlq").build();
    }

    @Bean
    public Binding notificationHistoryBinding(FanoutExchange notificationWebEventsExchange,
                                              Queue notificationHistoryQueue) {
        return BindingBuilder.bind(notificationHistoryQueue).to(notificationWebEventsExchange);
    }

    @Bean
    public Binding notificationHistoryDlqBinding(DirectExchange notificationWebDlqExchange,
                                                 Queue notificationHistoryDlqQueue) {
        return BindingBuilder.bind(notificationHistoryDlqQueue)
                .to(notificationWebDlqExchange)
                .with("notification-web.history.dlq");
    }
}
