plugins {
    `java-library`
    id("io.spring.dependency-management")
}

group = "ru.rutcampustrack.shared"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

// M02 Группа 3 — shared-outbox module.
// NEW-34 + NEW-6: чистый java-library, без autoconfig. Сервис-потребитель
// сам инстанцирует OutboxStorage (Jpa/Mongo) и вешает @Bean для
// OutboxPublisherJob / OutboxCleanupJob через component scan.
dependencies {
    // Базовый API — минимум зависимостей в compile
    api("com.fasterxml.jackson.core:jackson-databind")
    // M04 Группа 8 — MetricNames (OUTBOX_LAG_SECONDS) используется в OutboxMetrics.
    api(project(":services:shared:shared-observability"))
    compileOnly("org.slf4j:slf4j-api")

    // Spring-аннотации (@Component/@Scheduled/@Transactional) нужны для
    // PublisherJob/CleanupJob. compileOnly: сервис приносит Spring Boot.
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-tx")
    compileOnly("org.springframework.boot:spring-boot-starter-amqp")

    // ShedLock annotation для @SchedulerLock на Publisher/CleanupJob
    compileOnly(libs.shedlock.spring)

    // JPA для OutboxEntity (абстрактный MappedSuperclass)
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    // Hibernate @JdbcTypeCode для jsonb-колонки (Postgres). Сервис-потребитель
    // приносит Hibernate через spring-boot-starter-data-jpa.
    compileOnly("org.hibernate.orm:hibernate-core")

    // Micrometer — outbox.lag / outbox.published.total / outbox.failed.total
    compileOnly("io.micrometer:micrometer-core")

    // Mongo для OutboxDocument (attendance)
    compileOnly("org.springframework.boot:spring-boot-starter-data-mongodb")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-context")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    testImplementation(libs.shedlock.spring)
    testImplementation("jakarta.persistence:jakarta.persistence-api")
    testImplementation("org.mongodb:bson")
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
