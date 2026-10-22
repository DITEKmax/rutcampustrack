package ru.rutcampustrack.schedule.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import java.util.concurrent.TimeUnit;

/** Connect to the actual Spring-registered local server, including its advice and interceptors. */
public final class TestGrpcTransport implements AutoCloseable {
    public static final class Port {
        private volatile int value;

        @EventListener
        public void started(GrpcServerStartedEvent event) {
            value = event.getPort();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        Port grpcTestPort() {
            return new Port();
        }
    }

    private final ManagedChannel channel;
    final ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    TestGrpcTransport(Port port) {
        if (port.value <= 0)
            throw new IllegalStateException("registered gRPC server did not start");
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", port.value).usePlaintext().build();
        Metadata headers = new Metadata();
        headers.put(
                Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                "synthetic-test-grpc");
        stub =
                ScheduleGrpcServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                        .withDeadlineAfter(3, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws InterruptedException {
        channel.shutdownNow();
        channel.awaitTermination(3, TimeUnit.SECONDS);
    }
}
