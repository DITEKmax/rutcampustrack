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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// CRITICAL: Fix BouncyCastle signed-JAR incompatibility with Spring Boot 3.2+ loader
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    loaderImplementation = LoaderImplementation.CLASSIC
}
