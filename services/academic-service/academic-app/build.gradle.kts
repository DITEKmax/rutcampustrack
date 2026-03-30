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
    // Наш контракт
    implementation(project(":services:academic-service:academic-api-contract"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Security crypto (BCrypt password encoding)
    implementation("org.springframework.security:spring-security-crypto")

    // AOP support (@Aspect, @Around for @RequireRole)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // gRPC (будет добавлено в Фазе 2)
    // implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")

    // Lombok (только для entity и внутренних классов, НЕ для DTO контракта)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
