package ru.rutcampustrack.schedule.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import net.devh.boot.grpc.server.serverfactory.NettyGrpcServerFactory;
import net.devh.boot.grpc.server.service.GrpcServiceDiscoverer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

/** Connect to the actual Spring-registered local server, including its advice and interceptors. */
public final class TestGrpcTransport implements AutoCloseable {
    public static final class Port {
        private volatile int value;

        @EventListener
        public void started(GrpcServerStartedEvent event) {
            value = event.getServer().getPort();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        NettyGrpcServerFactory grpcTestServerFactory(
                GrpcServerProperties properties,
                GrpcServiceDiscoverer services,
                List<GrpcServerConfigurer> configurers) {
            // The starter resolves properties port 0 with a find-close-bind probe.
            // Pass 0 directly to Netty so the OS allocates the port while binding.
            var factory =
                    new NettyGrpcServerFactory(properties, configurers) {
                        @Override
                        public int getPort() {
                            return 0;
                        }
                    };
            services.findGrpcServices().forEach(factory::addService);
            return factory;
        }

        @Bean
        Port grpcTestPort() {
            return new Port();
        }
    }

    private final ManagedChannel channel;
    final ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    TestGrpcTransport(Port port) {
        this(port, "synthetic-test-grpc");
    }

    TestGrpcTransport(Port port, String secret) {
        if (port.value <= 0)
            throw new IllegalStateException("registered gRPC server did not start");
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", port.value).usePlaintext().build();
        Metadata headers = new Metadata();
        if (secret != null)
            headers.put(Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER), secret);
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
