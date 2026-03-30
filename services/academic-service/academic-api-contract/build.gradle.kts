plugins {
    `java-library`
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencies {
    // Validation API (без Spring — чистый контракт)
    api("jakarta.validation:jakarta.validation-api:3.1.0")

    // Spring Web annotations (для @RequestMapping, @GetMapping и т.д.)
    api("org.springframework:spring-web:6.2.1")

    // Spring Data Commons (для Pageable, PagedResourcesAssembler)
    api("org.springframework.data:spring-data-commons:3.4.1")

    // Spring HATEOAS (для RepresentationModel, EntityModel)
    api("org.springframework.hateoas:spring-hateoas:2.4.1")

    // OpenAPI / Swagger annotations
    api("io.swagger.core.v3:swagger-annotations-jakarta:2.2.22")

    // Jackson annotations (для @JsonInclude и т.д.)
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
}
