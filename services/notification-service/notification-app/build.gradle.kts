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

    // Existing
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JWT validation (same as api-gateway — public key verification for WebSocket handshake)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // NEW: Web Push delivery
    implementation("nl.martijndwars:web-push:5.1.2")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // NEW: MongoDB for push_subscriptions
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // NEW: AOP for @RequireRole
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // NEW: HATEOAS + Validation for contract
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // NEW: OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// CRITICAL: Fix BouncyCastle signed-JAR incompatibility with Spring Boot 3.2+ loader
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    loaderImplementation = LoaderImplementation.CLASSIC
}
