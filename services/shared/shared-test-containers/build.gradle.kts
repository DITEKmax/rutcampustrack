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
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainersBom.get()}")
    }
}

// NEW-158: модуль-фикстура для Testcontainers.
// Классы живут в src/testFixtures/java → потребитель подключает через
//   testImplementation(testFixtures(project(":services:shared:shared-test-containers")))
// Благодаря java-test-fixtures артефакт никогда не попадёт в main classpath сервиса по ошибке.
dependencies {
    testFixturesApi("org.testcontainers:testcontainers")
    testFixturesApi("org.testcontainers:junit-jupiter")
    testFixturesApi("org.testcontainers:postgresql")
    testFixturesApi("org.testcontainers:mongodb")
    testFixturesApi("org.testcontainers:rabbitmq")

    // gRPC in-process для GrpcInProcessFixture
    testFixturesApi(libs.grpc.inprocess)
    testFixturesApi(libs.grpc.stub)
    testFixturesApi(libs.grpc.protobuf)

    // WireMock для WireMockFixture
    testFixturesApi(libs.wiremock.standalone)

    // Spring Test для @DynamicPropertySource / DynamicPropertyRegistry
    testFixturesApi("org.springframework:spring-test")
    testFixturesApi("org.springframework.boot:spring-boot-test")

    // JUnit 5 API
    testFixturesApi("org.junit.jupiter:junit-jupiter-api")

    // JDBC + Flyway для MigrationTestUtils
    testFixturesApi("org.flywaydb:flyway-core")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
