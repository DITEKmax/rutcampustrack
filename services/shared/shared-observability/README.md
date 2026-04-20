# shared-observability

M04 D1 — общие утилиты наблюдаемости для всех 6 backend-сервисов.

## Что внутри

- **`MdcKeys`** — константы MDC-полей (`traceId`, `userId`, `eventType`, `internalJwtFallback`). Используются в `shared/logback-base.xml` и в коде сервисов через `MDC.put(...)`.
- **`MetricNames`** — единые имена метрик. Конвенция `<domain>.<entity>.<verb>`. Изменение списка требует обновления Grafana dashboards и `docs/alerts.md`.
- **`BusinessMetrics`** — fluent helper над `MeterRegistry`. Сервис создаёт singleton-bean.
- **`GrpcClientHealthIndicator`** — health-check для downstream gRPC через `ManagedChannel.getState()`.
- **`PublicKeyHealthIndicator`** — readiness gate для `internal-JWT` public key (KI-4 из M03b).
- **testFixtures `MetricsTestSupport`** — AssertJ-friendly helpers `assertCounter(...)`.

## Подключение

```kotlin
// services/<your-service>/build.gradle.kts
dependencies {
    implementation(project(":services:shared:shared-observability"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
```

```java
@Configuration
class ObservabilityConfig {
    @Bean
    BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new BusinessMetrics(registry);
    }

    @Bean
    HealthIndicator academicGrpcHealth(@Qualifier("academicChannel") ManagedChannel ch) {
        return new GrpcClientHealthIndicator("academic-service", ch);
    }
}
```

## Что НЕ поставляет

- OTLP exporter / `micrometer-tracing-bridge-otel` — сервис подключает сам в `*-app/build.gradle.kts` (тяжёлые зависимости, нагрузка на CI-тесты не оправдана).
- Spring Boot autoconfig — модуль остаётся чистым `java-library` (паттерн M03a `shared-security`).

## Связь с другими shared-модулями

- `shared-logback` — JSON-encoder читает MDC-поля, имена которых заданы здесь.
- `shared-events` — `AbstractEventEnvelope` использует `MdcKeys.TRACE_ID` для извлечения trace_id из MDC при publish (Группа 6).
- `shared-security` — `DualModeUserContextFilter` инкрементирует `internalJwtFallbackCounter` при KI-2 silent fallback (Группа 8).
