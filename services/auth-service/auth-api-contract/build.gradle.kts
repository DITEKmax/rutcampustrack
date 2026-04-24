plugins {
    `java-library`
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencies {
    // M11 G0.3: единый источник ErrorResponse/FieldError для всех контрактов
    api(project(":services:shared:shared-web-api"))

    // Validation API (без Spring — чистый контракт)
    api("jakarta.validation:jakarta.validation-api:3.1.0")

    // Spring Web annotations (для @RequestMapping, @GetMapping и т.д.)
    api("org.springframework:spring-web:6.2.1")

    // M12 G3: auth-service controllers принимают Authentication + HttpServletRequest
    // в interface signatures. Поэтому контракту нужны spring-security-core + servlet-api.
    // Не прокачиваем до Spring Boot starter — контракт остаётся java-library.
    api("org.springframework.security:spring-security-core:6.4.2")
    api("jakarta.servlet:jakarta.servlet-api:6.1.0")

    // OpenAPI / Swagger annotations
    api("io.swagger.core.v3:swagger-annotations-jakarta:2.2.22")

    // Jackson annotations (для @JsonInclude и т.д.)
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
}
