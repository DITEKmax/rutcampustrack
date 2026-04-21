package ru.rutcampustrack.shared.observability;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * M05 G8 (NEW-149): Micrometer-инструментация всех outgoing gRPC-вызовов.
 *
 * <p>Регистрирует на каждый RPC:
 * <ul>
 *   <li>{@code grpc.client.duration} — {@link Timer} histogram, измеряет
 *       время от {@code start()} до {@code onClose()}. Теги: {@code service},
 *       {@code method}, {@code status} (OK, UNAVAILABLE, ...).</li>
 *   <li>{@code grpc.client.requests_total} — counter через Timer.count(),
 *       разбит по {@code status} для error-rate в Grafana.</li>
 * </ul>
 *
 * <p>Работает полностью на client-side — не требует server-side
 * инструментации. Per-сервис бин (`attendance`, `schedule`, `academic`)
 * регистрируется как {@code @GrpcGlobalClientInterceptor}; этот класс
 * предоставляет только чистую имплементацию без Spring / net.devh deps
 * (классы shared-observability нейтральны к net.devh, чтобы не тянуть
 * transitive-deps в модуль).
 *
 * <p>Tag values нормализованы через {@link MethodDescriptor#getFullMethodName()}
 * (например, {@code ru.rutcampustrack.schedule.grpc.ScheduleGrpcService/GetActiveLesson})
 * — из него извлекаются short-service и method имена.
 */
public class GrpcClientMetricsInterceptor implements ClientInterceptor {

    private static final String TIMER_NAME = "grpc.client.duration";

    private final MeterRegistry meterRegistry;

    /**
     * M06 G8d (bug-hunter 5.1): Timer cache keyed by (service|method|status).
     * Избегаем Timer.builder().register() на каждый call — lookup в
     * MeterRegistry с tag-parsing ≈ 5-15μs overhead per RPC. После cache'а —
     * O(1) получение готового Timer.
     *
     * Key-space bounded ≤ |services| × |methods| × |Status.Code|
     * (~3 × 10 × 17 = ~500 entries) — heap-safe без eviction.
     */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    public GrpcClientMetricsInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        String fullName = method.getFullMethodName();
        int slash = fullName.indexOf('/');
        String serviceName = slash > 0 ? shortServiceName(fullName.substring(0, slash)) : fullName;
        String methodName = slash > 0 ? fullName.substring(slash + 1) : "";

        ClientCall<ReqT, RespT> delegate = next.newCall(method, callOptions);
        return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // M06 G8d (bug-hunter 5.3): startNs фиксируется в start(),
                // не в interceptCall. interceptCall выполняется в caller-
                // thread до попадания в executor; start() — ближе к
                // actual RPC dispatch. Разница <1ms в normal case, но
                // корректнее по семантике "call duration".
                long startNs = System.nanoTime();
                Listener<RespT> instrumented = new ForwardingClientCallListener
                        .SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        long elapsedNs = System.nanoTime() - startNs;
                        String statusCode = status.getCode().name();
                        Timer timer = timerForTags(serviceName, methodName, statusCode);
                        timer.record(elapsedNs, TimeUnit.NANOSECONDS);
                        super.onClose(status, trailers);
                    }
                };
                super.start(instrumented, headers);
            }
        };
    }

    /**
     * Lazy Timer registration per unique (service, method, status) tuple.
     * Cache key — конкатенированная строка (избегаем аллокации List-ключа).
     */
    private Timer timerForTags(String serviceName, String methodName, String statusCode) {
        String key = serviceName + '|' + methodName + '|' + statusCode;
        Timer cached = timerCache.get(key);
        if (cached != null) {
            return cached;
        }
        return timerCache.computeIfAbsent(key, k -> Timer.builder(TIMER_NAME)
                .description("gRPC client call duration")
                .tag("service", serviceName)
                .tag("method", methodName)
                .tag("status", statusCode)
                .register(meterRegistry));
    }

    /**
     * Сокращает {@code ru.rutcampustrack.schedule.grpc.ScheduleGrpcService}
     * до {@code ScheduleGrpcService} для компактных тегов. Если формат
     * неожиданный — оставляем как есть.
     */
    private static String shortServiceName(String full) {
        int dot = full.lastIndexOf('.');
        return dot >= 0 && dot + 1 < full.length() ? full.substring(dot + 1) : full;
    }
}
