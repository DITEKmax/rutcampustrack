package ru.rutcampustrack.shared.testcontainers;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * In-process gRPC fixture (P2-8/2): реальный gRPC server ↔ client в одном
 * JVM без сетевого stack'а. Заменяет моки `@GrpcClient` в integration-тестах
 * на настоящий round-trip с Protobuf-маршалингом.
 *
 * <p>Использование в тесте:
 * <pre>{@code
 *   GrpcInProcessFixture fixture = new GrpcInProcessFixture();
 *   fixture.startServer(new MyGrpcServiceImpl());
 *   MyServiceGrpc.MyServiceBlockingStub stub =
 *           MyServiceGrpc.newBlockingStub(fixture.channel());
 *   // ... test ...
 *   fixture.shutdown();
 * }</pre>
 *
 * <p>Имя канала уникально per fixture instance → параллельные тесты не
 * конфликтуют.
 */
public class GrpcInProcessFixture implements AutoCloseable {

    private final String channelName = "grpc-fixture-" + UUID.randomUUID();
    private Server server;
    private ManagedChannel channel;

    /**
     * Стартует in-process сервер с указанными сервисами и открывает клиентский канал.
     */
    public void startServer(BindableService... services) throws IOException {
        InProcessServerBuilder builder = InProcessServerBuilder.forName(channelName)
                .directExecutor();
        for (BindableService service : services) {
            builder.addService(service);
        }
        server = builder.build().start();
        channel = InProcessChannelBuilder.forName(channelName)
                .directExecutor()
                .build();
    }

    /** Возвращает открытый клиентский канал (после {@link #startServer}). */
    public ManagedChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("startServer() must be called first");
        }
        return channel;
    }

    /** Уникальное имя канала — пригодится если тест создаёт stub'ы вручную. */
    public String channelName() {
        return channelName;
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (server != null) {
            server.shutdownNow();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
