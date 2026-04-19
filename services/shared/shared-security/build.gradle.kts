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

// M03a Группа 2 — shared-security module.
// NEW-34 + DECISIONS 2026-04-19: чистый java-library, без autoconfig.
// Сервис-потребитель сам подключает компоненты через @ComponentScan
// (или явные @Bean для InternalJwtValidator / PublicKeyProvider).
// Приватный ключ НЕ ВИДЕН этому модулю — token exchange паттерн,
// signing живёт только в auth-service.
dependencies {
    // JWT parsing — validator side.
    api("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Spring-аннотации + OncePerRequestFilter. compileOnly — сервис приносит.
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.annotation:jakarta.annotation-api")
    compileOnly("org.slf4j:slf4j-api")

    // Testing — используем starter-web (servlet) т.к. RestClient не требует webflux.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Test fixtures для других модулей (InternalJwtTestFactory)
    testFixturesApi("io.jsonwebtoken:jjwt-api:0.12.6")
    testFixturesRuntimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    testFixturesRuntimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
}
