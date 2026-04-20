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

// NEW-34: чистый java-library, без autoconfig, без starter'ов.
// Jackson + SLF4J провайдит сервис-потребитель.
dependencies {
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("com.fasterxml.jackson.core:jackson-annotations")
    compileOnly("org.slf4j:slf4j-api")
    // M04 D5(a) — DomainEventEnvelope extends Spring's ApplicationEvent,
    // чтобы @TransactionalEventListener мог подхватить. Сервис приносит Spring.
    compileOnly("org.springframework:spring-context")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
