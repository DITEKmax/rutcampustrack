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

// M11 G0.1: shared-web-api — единый источник DTO-типов API-контрактов.
// Пустая Spring-зависимость (compileOnly на jackson-annotations,
// swagger-annotations-jakarta, jakarta.validation-api). Потребители: все
// *-api-contract модули и shared-web-starter.
dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("io.swagger.core.v3:swagger-annotations-jakarta:2.2.22")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
