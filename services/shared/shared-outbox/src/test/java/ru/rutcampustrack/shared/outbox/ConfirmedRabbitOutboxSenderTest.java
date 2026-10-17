package ru.rutcampustrack.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ReturnListener;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class ConfirmedRabbitOutboxSenderTest {
    @Test
    void actualTemplateSetsMandatoryWireFlagAndSurfacesReturnWithoutGlobalCallback()
            throws Exception {
        CachingConnectionFactory connectionFactory = mock(CachingConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.getRabbitConnectionFactory())
                .thenReturn(new com.rabbitmq.client.ConnectionFactory());
        when(connectionFactory.isSimplePublisherConfirms()).thenReturn(true);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(false)).thenReturn(channel);
        when(channel.waitForConfirms(5000)).thenReturn(true);
        AtomicReference<ReturnListener> listener = new AtomicReference<>();
        AtomicBoolean mandatory = new AtomicBoolean();
        doAnswer(
                        invocation -> {
                            listener.set(invocation.getArgument(0));
                            return null;
                        })
                .when(channel)
                .addReturnListener(any(ReturnListener.class));
        doAnswer(
                        invocation -> {
                            boolean wireMandatory = invocation.getArgument(2);
                            mandatory.set(wireMandatory);
                            if (wireMandatory) {
                                // Match RabbitMQ's documented basic.return-before-basic.ack
                                // ordering.
                                listener.get()
                                        .handleReturn(
                                                312,
                                                "NO_ROUTE",
                                                invocation.getArgument(0),
                                                invocation.getArgument(1),
                                                invocation.getArgument(3),
                                                invocation.getArgument(4));
                            }
                            return null;
                        })
                .when(channel)
                .basicPublish(
                        any(String.class),
                        any(String.class),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        any(com.rabbitmq.client.AMQP.BasicProperties.class),
                        any(byte[].class));
        RabbitTemplate actualTemplate = new RabbitTemplate(connectionFactory);
        var sender = new ConfirmedRabbitOutboxSender(actualTemplate);

        Throwable failure =
                catchThrowable(
                        () ->
                                sender.send(
                                        "delivery.proof",
                                        "{\"event_id\":\"d6db1656-32db-4415-af45-48e0b2dd7342\"}"));

        assertThat(mandatory.get()).as("actual basicPublish mandatory wire flag").isTrue();
        assertThat(failure)
                .isInstanceOf(OutboxDeliveryException.class)
                .hasMessageContaining("BROKER_UNROUTABLE");
        verify(channel).removeReturnListener(listener.get());
        assertThat(
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                actualTemplate, "returnsCallback"))
                .isNull();

        // Already-queued legacy carriers can lack an AMQP message ID. A transport correlation
        // fallback must still enable mandatory, without changing the body/business identity.
        mandatory.set(false);
        byte[] body =
                "{\"event_id\":\"d6db1656-32db-4415-af45-48e0b2dd7342\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Message legacy = new Message(body, new org.springframework.amqp.core.MessageProperties());
        Throwable legacyFailure =
                catchThrowable(() -> sender.sendToQueue("legacy.retry.proof", legacy));
        assertThat(mandatory.get()).as("legacy carrier mandatory wire flag").isTrue();
        assertThat(legacyFailure)
                .isInstanceOf(OutboxDeliveryException.class)
                .hasMessageContaining("BROKER_UNROUTABLE");
        assertThat(legacy.getMessageProperties().getMessageId()).isNull();
        assertThat(legacy.getBody()).isEqualTo(body);
        verify(channel).removeReturnListener(listener.get());
    }

    @Test
    void timeoutKeepsNativeSecurityConfigurationAndAlwaysRemovesScopedReturnListener()
            throws Exception {
        com.rabbitmq.client.ConnectionFactory nativeFactory =
                new com.rabbitmq.client.ConnectionFactory();
        var socketFactory = javax.net.ssl.SSLSocketFactory.getDefault();
        nativeFactory.setSocketFactory(socketFactory);
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(nativeFactory);
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitOperations operations = mock(RabbitOperations.class);
        Channel channel = mock(Channel.class);
        when(template.getConnectionFactory()).thenReturn(connectionFactory);
        when(template.invoke(any()))
                .thenAnswer(
                        invocation -> {
                            RabbitOperations.OperationsCallback<?> callback =
                                    invocation.getArgument(0);
                            return callback.doInRabbit(operations);
                        });
        when(operations.execute(any()))
                .thenAnswer(
                        invocation -> {
                            ChannelCallback<?> callback = invocation.getArgument(0);
                            return callback.doInRabbit(channel);
                        });
        when(operations.waitForConfirms(5000))
                .thenThrow(new AmqpException("synthetic confirm timeout"));
        var sender = new ConfirmedRabbitOutboxSender(template);
        long beforePublication = System.currentTimeMillis();

        assertThatThrownBy(
                        () ->
                                sender.send(
                                        "delivery.proof",
                                        "{\"event_id\":\"d6db1656-32db-4415-af45-48e0b2dd7342\"}"))
                .isInstanceOf(AmqpException.class);

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlation =
                ArgumentCaptor.forClass(CorrelationData.class);
        verify(operations)
                .send(eq("rut-uit.events"), eq(""), message.capture(), correlation.capture());
        assertThat(message.getValue().getMessageProperties().getTimestamp()).isNotNull();
        assertThat(message.getValue().getMessageProperties().getTimestamp().getTime())
                .isBetween(beforePublication, System.currentTimeMillis());
        assertThat(message.getValue().getMessageProperties().getMessageId())
                .isEqualTo("d6db1656-32db-4415-af45-48e0b2dd7342");
        assertThat(correlation.getValue().getId())
                .isEqualTo(message.getValue().getMessageProperties().getMessageId());

        ArgumentCaptor<ReturnListener> listener = ArgumentCaptor.forClass(ReturnListener.class);
        verify(channel).addReturnListener(listener.capture());
        verify(channel).removeReturnListener(listener.getValue());
        assertThat(nativeFactory.getSocketFactory()).isSameAs(socketFactory);
        assertThat(nativeFactory.getConnectionTimeout()).isEqualTo(3000);
        assertThat(nativeFactory.getHandshakeTimeout()).isEqualTo(3000);
        assertThat(nativeFactory.getChannelRpcTimeout()).isEqualTo(3000);
        assertThat(connectionFactory.getCloseTimeout()).isEqualTo(3000);
        assertThat(
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                connectionFactory, "channelCheckoutTimeout"))
                .isEqualTo(3000L);
        connectionFactory.destroy();
    }
}
