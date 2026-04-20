package ru.rutcampustrack.schedule.grpc;

import io.micrometer.core.instrument.MeterRegistry;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import ru.rutcampustrack.shared.observability.GrpcClientMetricsInterceptor;

/**
 * M05 G8 (NEW-149): schedule-app обёртка {@link GrpcClientMetricsInterceptor}.
 * Регистрирует micrometer histogram/counter на все outgoing gRPC-call'ы
 * из schedule-service (к academic-service).
 */
@GrpcGlobalClientInterceptor
public class GrpcMetricsClientInterceptor extends GrpcClientMetricsInterceptor {

    public GrpcMetricsClientInterceptor(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }
}
