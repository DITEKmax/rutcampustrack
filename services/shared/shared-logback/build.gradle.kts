plugins {
    `java-library`
    id("io.spring.dependency-management")
}

group = "ru.rutcampustrack.shared"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
    }
}

// NEW-68 + NEW-34: поставляет logback-base.xml + MaskingProvider.
// Logback и slf4j уже есть в каждом Spring Boot-сервисе — не приносим повторно.
// logstash-logback-encoder — единственное, что добавляется сервису как транзитивная зависимость.
dependencies {
    compileOnly("ch.qos.logback:logback-classic")
    compileOnly("org.slf4j:slf4j-api")
    implementation(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("ch.qos.logback:logback-classic")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Existing root security gates execute the actual configuration parsers with test-only dependencies.
listOf(
    "verifyProductionLoggingLevels" to "ru.rutcampustrack.shared.logback.ProductionLoggingLevelsTest",
    "verifyServiceLogback" to "ru.rutcampustrack.shared.logback.ServiceLogbackConfigurationTest",
).forEach { (gate, testClass) ->
    tasks.register<Test>(gate) {
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        filter { includeTestsMatching(testClass) }
        systemProperty("rct.repository.root", rootDir.absolutePath)
        if (gate == "verifyProductionLoggingLevels") {
            inputs.files(rootProject.fileTree("services") {
                include("**/src/main/resources/**/application.yml")
                include("**/src/main/resources/**/application-prod.yml")
                exclude("**/build/**")
            }).withPropertyName("defaultAndProductionYaml").withPathSensitivity(PathSensitivity.RELATIVE)
        } else {
            val services = listOf(
                "api-gateway", "auth-service/auth-app", "academic-service/academic-app",
                "schedule-service/schedule-app", "attendance-service/attendance-app",
                "mobile-bff/mobile-bff-app", "notification-service/notification-app",
            )
            inputs.files(services.map { rootDir.resolve("services/$it/src/main/resources/logback-spring.xml") })
                .withPropertyName("serviceLogbackXml").withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(
                file("src/main/resources/shared/logback-base.xml"),
                file("src/main/java/ru/rutcampustrack/shared/logback/MaskingJsonProvider.java"),
            ).withPropertyName("sharedLogbackAndMasking").withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}
