plugins {
    `java-library`
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencies {
    api("jakarta.validation:jakarta.validation-api:3.1.0")
    api("org.springframework:spring-web:6.2.1")
    // Spring Data Commons (for Pageable, PagedResourcesAssembler)
    api("org.springframework.data:spring-data-commons:3.4.1")
    api("org.springframework.hateoas:spring-hateoas:2.4.1")
    api("io.swagger.core.v3:swagger-annotations-jakarta:2.2.22")
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
}
