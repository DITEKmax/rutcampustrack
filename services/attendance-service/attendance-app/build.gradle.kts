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
    implementation(project(":services:attendance-service:attendance-api-contract"))

    // M02 — shared-outbox + ShedLock (Mongo-storage) + shared-events (D5(a) единый envelope)
    implementation(project(":services:shared:shared-outbox"))
    implementation(project(":services:shared:shared-events"))
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.mongo)

    // M03a — shared-security (Internal JWT validator + dual-mode filter)
    implementation(project(":services:shared:shared-security"))

    // M04 — shared-observability (MdcKeys + BusinessMetrics + HealthIndicators)
    implementation(project(":services:shared:shared-observability"))

    // M04 QA7 — shared-logback (JSON-вывод + masking через logback-base.xml)
    implementation(project(":services:shared:shared-logback"))

    // M04 QA2 — distributed tracing OTel + OTLP exporter → grafana/tempo
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation(libs.json.schema.validator)
    testImplementation(testFixtures(project(":services:shared:shared-security")))
    testImplementation(testFixtures(project(":services:shared:shared-observability")))
    // M08 Группа 1 (P2-8/1) — IntegrationTestNamingRule shared rule
    testImplementation(testFixtures(project(":services:shared:shared-test-containers")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

// M08 Группа 10 / M09 Группа 3 — selective coverage gate для latecheckin/**.
// 70% LINE — pilot для stricter ratchet (QD2 + OWNER-ANSWERS P2-8/1).
// Активировано в M09 G3.5 после LateCheckinServiceTest + LateCheckinControllerIT
// + LateCheckinEventContractTest.
//
// afterEvaluate нужен т.к. task создаётся JaCoCo plugin'ом из root build.gradle.kts
// через subprojects { apply(plugin = "jacoco") }.
afterEvaluate {
    tasks.findByName("jacocoTestCoverageVerification")?.let {
        (it as JacocoCoverageVerification).violationRules {
            rule {
                element = "PACKAGE"
                includes = listOf("ru.rutcampustrack.attendance.latecheckin.*")
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.70".toBigDecimal()
                }
            }
        }
    }
}
