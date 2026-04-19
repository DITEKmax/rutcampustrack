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

// NEW-34: shared-web = чистый java-library.
// Никакой Spring Boot autoconfiguration, никаких приносимых starter'ов.
// Spring / Jackson / SLF4J провайдит сервис-потребитель (через свой starter).
dependencies {
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.slf4j:slf4j-api")

    // Hibernate Validator + AOP — внутренние зависимости модуля (runtime-impl для валидации,
    // aspectjweaver для @AdminAction заглушки). Приносим только то, что НЕ входит в обычный
    // spring-boot-starter-web (чтобы не ломать resolution в сервисе).
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.aspectj:aspectjweaver")

    // OpenApiCustomizer-заглушка для M06 — компилируется только если на classpath.
    compileOnly(libs.springdoc.openapi.starter.common)

    // Tests: здесь как обычное веб-приложение, нужен полный starter.
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.springdoc.openapi.starter.common)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
