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

// NEW-34 (M01) + M11 G0.2: shared-web = Spring Boot starter-like модуль.
// Содержит Spring beans (GlobalExceptionHandler, JacksonConfig,
// SharedOpenApiCustomizer, AdminActionAspect) + SharedWebAutoConfiguration
// (регистрируется через META-INF/spring/AutoConfiguration.imports).
// DTO-типы (ErrorResponse, FieldError, InvalidParam) вынесены в
// shared-web-api и приходят через `api(...)` — consumers видят их
// транзитивно.
dependencies {
    // M11 G0.2: единый источник DTO-типов для всего backend + транзитив
    // для consumers (academic/schedule/attendance/notification/auth).
    api(project(":services:shared:shared-web-api"))

    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // M11 G0.9: api (не compileOnly), потому что SharedWebAutoConfiguration
    // регистрирует GlobalExceptionHandler с @ExceptionHandler(AccessDeniedException.class).
    // Spring разрешает этот класс при создании bean'а; сервисы без
    // spring-security-core в classpath получат NoClassDefFoundError.
    // spring-security-core — маленький (~200KB), даёт только AccessDeniedException,
    // GrantedAuthority и т.п. (никаких SecurityFilterChain / OAuth2).
    api("org.springframework.security:spring-security-core")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.slf4j:slf4j-api")

    // Hibernate Validator + AOP — внутренние зависимости модуля (runtime-impl для валидации,
    // aspectjweaver для @AdminAction заглушки). Приносим только то, что НЕ входит в обычный
    // spring-boot-starter-web (чтобы не ломать resolution в сервисе).
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.aspectj:aspectjweaver")

    // OpenApiCustomizer (M06 M01 заглушка + M11 G1 наполнение) + swagger-core
    // (ModelConverters для registerErrorResponseSchema) — compileOnly, т.к.
    // springdoc активируется только если consumer имеет springdoc starter.
    compileOnly(libs.springdoc.openapi.starter.common)
    compileOnly("io.swagger.core.v3:swagger-core-jakarta:2.2.27")

    // Tests: здесь как обычное веб-приложение, нужен полный starter.
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.springdoc.openapi.starter.common)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
