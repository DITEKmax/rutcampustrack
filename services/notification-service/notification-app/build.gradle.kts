import org.springframework.boot.loader.tools.LoaderImplementation

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencies {
    implementation(project(":services:notification-service:notification-api-contract"))

    // M01 Shared Foundations — первый сервис-потребитель shared-web/events/logback
    implementation(project(":services:shared:shared-web"))
    implementation(project(":services:shared:shared-events"))
    implementation(project(":services:shared:shared-logback"))
    // M13 G8 — MongoIdempotencyStore (consumer-side dedup)
    implementation(project(":services:shared:shared-outbox"))

    // M03a — shared-security (Internal JWT validator + dual-mode filter)
    implementation(project(":services:shared:shared-security"))

    // M04 — shared-observability (MdcKeys + BusinessMetrics + HealthIndicators)
    implementation(project(":services:shared:shared-observability"))

    // M04 QA2 — distributed tracing OTel + OTLP exporter → grafana/tempo
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Spring Security Core — для shared-web AccessDeniedException handler.
    // Full spring-boot-starter-security добавляется только при реальной SecurityFilterChain
    // (в M03). Сейчас нужен только класс AccessDeniedException на classpath.
    implementation("org.springframework.security:spring-security-core")

    // Existing
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // JWT validation (same as api-gateway — public key verification for WebSocket handshake)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // NEW: Web Push delivery
    implementation("nl.martijndwars:web-push:5.1.2")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    // Apache HttpClient and jose4j transitively pulled by web-push at runtime;
    // needed at compile time because WebPushDeliveryService catches HttpResponseException (410)
    // and PushService.send() throws JoseException as a checked exception
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("org.bitbucket.b_c:jose4j:0.7.9")

    // NEW: MongoDB for push_subscriptions
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // Shared user notification prefs for notification-web and notification-bot.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // M10 G4 — Caffeine local L1 cache для unread-count endpoint.
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // M05 G7 (NEW-148): ShedLock для cleanupStalePushSubs scheduled job.
    // Mongo provider переиспользует существующий MongoTemplate (коллекция
    // `shedLock` автосоздаётся).
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.mongo)

    // NEW: AOP for @RequireRole
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // NEW: HATEOAS + Validation for contract
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // NEW: OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.httpcomponents:httpclient:4.5.13")
    testImplementation(testFixtures(project(":services:shared:shared-test-containers")))
    testImplementation(testFixtures(project(":services:shared:shared-security")))
    // M10 G3: awaitility для eventual consistency assert в Rabbit→Mongo IT.
    testImplementation("org.awaitility:awaitility:4.2.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// CRITICAL: Fix BouncyCastle signed-JAR incompatibility with Spring Boot 3.2+ loader
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    loaderImplementation = LoaderImplementation.CLASSIC
}
