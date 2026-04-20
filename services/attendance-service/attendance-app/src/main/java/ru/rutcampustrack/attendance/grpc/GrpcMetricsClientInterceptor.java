package ru.rutcampustrack.attendance.grpc;

import io.micrometer.core.instrument.MeterRegistry;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import ru.rutcampustrack.shared.observability.GrpcClientMetricsInterceptor;

/**
 * M05 G8 (NEW-149): attendance-app-специфичная обёртка
 * {@link GrpcClientMetricsInterceptor} — регистрируется на все outgoing
 * gRPC-call'ы как global interceptor (parallel к {@code GrpcSecretClientInterceptor}).
 *
 * <p>Pattern аналогичен {@code GrpcSecretClientInterceptor}: shared-observability
 * поставляет чистую implementation, а `@GrpcGlobalClientInterceptor`
 * wrapping происходит в каждом *-app модуле (там где net.devh dependency).
 */
@GrpcGlobalClientInterceptor
public class GrpcMetricsClientInterceptor extends GrpcClientMetricsInterceptor {

    public GrpcMetricsClientInterceptor(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }
}
