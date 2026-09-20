plugins { `java-library` }

group = "ru.rutcampustrack"
version = "0.1.0"

dependencies {
    api("jakarta.validation:jakarta.validation-api:3.1.0")
    api("org.springframework:spring-web:6.2.19")
    // M10 G2 — Pageable / PagedResourcesAssembler для NotificationApi.
    api("org.springframework.data:spring-data-commons:3.5.13")
    api("org.springframework.hateoas:spring-hateoas:2.5.3")
    api("io.swagger.core.v3:swagger-annotations-jakarta:2.2.22")
    api("com.fasterxml.jackson.core:jackson-annotations:2.21")
}
