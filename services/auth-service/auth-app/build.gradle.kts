plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

dependencies {
    // M12 — наш контракт
    implementation(project(":services:auth-service:auth-api-contract"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // M04 — shared-observability (MdcKeys + BusinessMetrics + HealthIndicators) + shared-events (D5(a))
    implementation(project(":services:shared:shared-observability"))
    implementation(project(":services:shared:shared-events"))

    // M11 G0.7: shared-web Spring Boot starter (catch-all Spring MVC handler
    // через @AutoConfiguration). Auth-domain handler с
    // @Order(HIGHEST_PRECEDENCE) обрабатывает только domain исключения.
    implementation(project(":services:shared:shared-web"))

    // M04 QA7 — shared-logback (JSON-вывод + masking через logback-base.xml)
    implementation(project(":services:shared:shared-logback"))

    // M04 QA2 — distributed tracing OTel + OTLP exporter → grafana/tempo
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Lombok (только для entity и внутренних классов, НЕ для DTO контракта)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.flywaydb:flyway-core")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // M08 Группа 1 (P2-8/1) — IntegrationTestNamingRule + ArchUnit
    testImplementation(testFixtures(project(":services:shared:shared-test-containers")))

    // M09 G2 (08 P0-2) — contract-тест otp.requested против JSON Schema
    testImplementation(libs.json.schema.validator)

    // M12 G4 — AuthApiContractTest: controllers implement contract interfaces
    testImplementation(libs.archunit.junit5)

    // M13 G10 — InMemorySpanExporter + SimpleSpanProcessor для ActuatorSamplingIT
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.43.0")
}
