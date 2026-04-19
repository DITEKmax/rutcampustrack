plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.protobuf") version "0.9.4"
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

dependencies {
    implementation(project(":services:schedule-service:schedule-api-contract"))

    // M02 — shared-outbox
    implementation(project(":services:shared:shared-outbox"))

    // M03a — shared-security (Internal JWT validator + dual-mode filter)
    implementation(project(":services:shared:shared-security"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // M02 — ShedLock (distributed cluster locks для @Scheduled)
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.jdbc.template)

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.archunit.junit5)
    testImplementation(testFixtures(project(":services:shared:shared-security")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

sourceSets {
    main {
        proto {
            srcDir(rootProject.file("proto"))
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                create("grpc") { }
            }
        }
    }
}
