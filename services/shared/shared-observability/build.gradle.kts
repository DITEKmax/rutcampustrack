plugins {
    `java-library`
    `java-test-fixtures`
    id("io.spring.dependency-management")
}

group = "ru.rutcampustrack.shared"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

// M04 D1 — shared-observability модуль.
// Поставляет:
//   * MdcKeys — единые имена MDC-полей (traceId, userId, eventType, internalJwtFallback).
//   * BusinessMetrics — fluent helper над MeterRegistry для @Counted-эквивалентов.
//   * GrpcClientHealthIndicator — ping downstream через gRPC channel state.
//   * PublicKeyHealthIndicator — readiness gate для api-gateway (KI-4 из M03b).
//   * ActuatorTracingExcludeFilter + SharedObservabilityAutoConfiguration — M13 G10
//     drop'ает /actuator/** spans на этапе экспорта (через SpanExportingPredicate).
//     Активируется только если micrometer-tracing есть в classpath
//     (т.е. в *-app/build.gradle.kts подключён micrometer-tracing-bridge-otel).
//     Modules без tracing-deps просто игнорируют auto-config через @ConditionalOnClass.
//
// НЕ поставляет: OTLP exporter, micrometer-tracing-bridge — это сервис подключает сам
// в *-app/build.gradle.kts (тяжёлые зависимости, не нужны в каждом тесте).
// micrometer-tracing идёт compileOnly — auto-config компилируется, runtime
// активен только при наличии tracing stack у consumer'а.
dependencies {
    api("io.micrometer:micrometer-core")
    compileOnly("org.springframework.boot:spring-boot-actuator")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.slf4j:slf4j-api")
    compileOnly("io.grpc:grpc-api:1.63.0")
    // M13 G10 — SpanExportingPredicate / FinishedSpan interface для
    // ActuatorTracingExcludeFilter. micrometer-tracing 1.4.1 совпадает с
    // транзитивной версией bridge-otel 1.4.1 в *-app модулях.
    compileOnly("io.micrometer:micrometer-tracing:1.4.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("io.grpc:grpc-api:1.63.0")
    testImplementation("io.grpc:grpc-inprocess:1.63.0")
    // M13 G10 — для unit-тестов фильтра (FinishedSpan через Mockito).
    testImplementation("io.micrometer:micrometer-tracing:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testFixturesApi("io.micrometer:micrometer-core")
    testFixturesApi("com.tngtech.archunit:archunit-junit5:1.3.0")
    testFixturesImplementation("org.assertj:assertj-core")
}
