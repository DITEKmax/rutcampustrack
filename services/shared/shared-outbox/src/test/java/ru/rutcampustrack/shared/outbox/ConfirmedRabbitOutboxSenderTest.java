package ru.rutcampustrack.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ConfirmedRabbitOutboxSenderTest {
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
        verify(operations).send(eq("rut-uit.events"), eq(""), message.capture());
        assertThat(message.getValue().getMessageProperties().getTimestamp()).isNotNull();
        assertThat(message.getValue().getMessageProperties().getTimestamp().getTime())
                .isBetween(beforePublication, System.currentTimeMillis());
        assertThat(message.getValue().getMessageProperties().getMessageId())
                .isEqualTo("d6db1656-32db-4415-af45-48e0b2dd7342");

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
