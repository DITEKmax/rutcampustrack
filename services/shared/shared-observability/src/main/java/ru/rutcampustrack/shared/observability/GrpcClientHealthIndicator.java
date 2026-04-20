package ru.rutcampustrack.shared.observability;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * QA6 — health-check для downstream gRPC-сервиса. Триггерит соединение
 * через {@link ManagedChannel#getState(boolean)} (передаёт {@code true},
 * чтобы канал начал реконнект если был {@code IDLE}).
 *
 * <p>Маппинг состояний:
 * <ul>
 *   <li>{@code READY} → {@link Health#up()}.</li>
 *   <li>{@code IDLE} / {@code CONNECTING} → {@link Health#unknown()}
 *       (только запустились, ещё нет данных).</li>
 *   <li>{@code TRANSIENT_FAILURE} / {@code SHUTDOWN} → {@link Health#down()}.</li>
 * </ul>
 *
 * <p>Сервис регистрирует bean per-downstream:
 * <pre>{@code
 * @Bean
 * HealthIndicator academicGrpcHealthIndicator(@Qualifier("academicChannel") ManagedChannel ch) {
 *     return new GrpcClientHealthIndicator("academic-service", ch);
 * }
 * }</pre>
 */
public class GrpcClientHealthIndicator implements HealthIndicator {

    private final String downstream;
    private final ManagedChannel channel;
    private final long stateProbeWaitMillis;

    public GrpcClientHealthIndicator(String downstream, ManagedChannel channel) {
        this(downstream, channel, 200L);
    }

    public GrpcClientHealthIndicator(String downstream, ManagedChannel channel, long stateProbeWaitMillis) {
        this.downstream = Objects.requireNonNull(downstream, "downstream");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.stateProbeWaitMillis = stateProbeWaitMillis;
    }

    @Override
    public Health health() {
        ConnectivityState state = channel.getState(true);
        Health.Builder builder = switch (state) {
            case READY -> Health.up();
            case IDLE, CONNECTING -> {
                if (stateProbeWaitMillis > 0) {
                    try {
                        channel.notifyWhenStateChanged(state, () -> {
                        });
                        TimeUnit.MILLISECONDS.sleep(stateProbeWaitMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ConnectivityState after = channel.getState(false);
                    yield after == ConnectivityState.READY ? Health.up() : Health.unknown();
                }
                yield Health.unknown();
            }
            case TRANSIENT_FAILURE, SHUTDOWN -> Health.down();
        };
        return builder
                .withDetail("downstream", downstream)
                .withDetail("state", state.name())
                .build();
    }
}
