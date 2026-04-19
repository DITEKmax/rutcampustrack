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
