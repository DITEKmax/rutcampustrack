plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // M04 — shared-observability (MdcKeys + BusinessMetrics + HealthIndicators)
    implementation(project(":services:shared:shared-observability"))

    // M04 QA7 — shared-logback (JSON-вывод + masking через logback-base.xml)
    implementation(project(":services:shared:shared-logback"))

    // M14 G4 v2 — shared-web для RequiredSecretsValidator (EnvironmentPostProcessor).
    // SharedWebAutoConfiguration condition'ит на ConditionalOnWebApplication(SERVLET)
    // и в WebFlux gateway не активируется — но EnvironmentPostProcessor работает
    // независимо (registered через META-INF/spring.factories). Транзитивно
    // приходят shared-web-api DTO + spring-security-core (~200KB) — overhead
    // оправдан, гарантирует CSO HIGH-06 mitigation в gateway.
    implementation(project(":services:shared:shared-web"))

    // M04 QA2 — distributed tracing OTel + OTLP exporter → grafana/tempo
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6")

    // JWT validation (публичный ключ)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // M03a: Caffeine cache для InternalJwtIssuerClient (per-user token cache 4 min).
    implementation("com.github.ben-manes.caffeine:caffeine")

    // M03a Group 9: Redis-backed rate-limiting (spring-cloud-gateway RedisRateLimiter).
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.wiremock:wiremock-standalone:3.9.1")
    // M03a Group 12: real Redis для RateLimitIT / FailOpenIT
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

extra["springCloudVersion"] = "2024.0.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainersBom.get()}")
    }
}
