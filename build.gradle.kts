import java.time.OffsetDateTime

plugins {
    java
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "ru.rutcampustrack"
version = "0.1.0"

val javaVersion = 21

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    // M08 Группа 10 (QD2) — JaCoCo per-module 60% line gate.
    // Применяется только к Java-подпроектам. Frontend/Python coverage
    // — через vitest + pytest-cov (см. frontends/*/vitest.config.ts и
    // services/notification-bot/pytest.ini).
    apply(plugin = "jacoco")

    java {
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // M08 Группа 10 — JaCoCo config. Применяется во всех java-subprojects,
    // но verification — выборочно (see jacocoTestCoverageVerification блок
    // ниже + allprojects { plugins.withId("jacoco") } на конце файла).
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    // M08 Группа 1 (P2-8/1) — split unit-test и integration-test.
    //   ./gradlew test            — только unit (*Test, НЕ *IT)
    //   ./gradlew integrationTest — только IT (*IT)
    //   ./gradlew check           — запускает оба (default поведение)
    //
    // Параллельные CI-jobs используют `test` и `integrationTest` отдельно,
    // чтобы unit прошли быстро (< 1м), а slower *IT с Testcontainers
    // бегали в отдельной job'е.
    tasks.named<Test>("test") {
        filter {
            excludeTestsMatching("*IT")
            isFailOnNoMatchingTests = false
        }
    }

    tasks.register<Test>("integrationTest") {
        group = "verification"
        description = "Запускает только *IT тесты (Spring context, Testcontainers, gRPC in-process)."
        useJUnitPlatform()
        filter {
            includeTestsMatching("*IT")
            isFailOnNoMatchingTests = false
        }
        // Same classpath как test — тесты в src/test/java.
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        shouldRunAfter("test")

        // Отдельная отчётность, не перезаписываем test HTML/XML.
        reports {
            html.outputLocation.set(layout.buildDirectory.dir("reports/integrationTest"))
            junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/integrationTest"))
        }
    }

    tasks.named("check") {
        dependsOn("integrationTest")
    }

    // M08 Группа 10 — jacocoTestReport агрегирует execData test+integrationTest.
    // NEW-99 excludes: generated protobuf/gRPC, OpenAPI generated, Spring
    // Application bootstrap (main-less POJO coverage = шум), DTO/record
    // (автоматические getter'ы, без бизнес-логики).
    //
    // afterEvaluate обязателен: classDirectories должны быть populated
    // Java plugin'ом (sourceSets) ДО exclude-фильтрации, иначе
    // "cannot be executed in the current context" при tasks.withType {}.
    val jacocoExcludes = listOf(
        "**/generated/**",
        "**/grpc/proto/**",
        "ru/rutcampustrack/**/grpc/**/*OuterClass*.class",
        "ru/rutcampustrack/**/grpc/**/*Grpc*.class",
        "ru/rutcampustrack/**/*Application.class",
        "ru/rutcampustrack/**/*Dto.class",
        "ru/rutcampustrack/**/dto/**",
        "ru/rutcampustrack/**/config/**",
    )

    afterEvaluate {
        // jacocoTestReport / jacocoTestCoverageVerification создаются JaCoCo-плагином
        // при первой же evaluation. tasks.findByName вместо named — не все модули
        // реально содержат тесты (api-contract модули pure java-library).
        tasks.findByName("jacocoTestReport")?.let { reportTask ->
            (reportTask as JacocoReport).apply {
                dependsOn(tasks.named("test"))
                // integrationTest может отсутствовать (api-contract модули).
                tasks.findByName("integrationTest")?.let { dependsOn(it) }
                executionData.setFrom(
                    fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec")
                )
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                    csv.required.set(false)
                }
                classDirectories.setFrom(
                    classDirectories.files.map {
                        fileTree(it) { exclude(jacocoExcludes) }
                    }
                )
            }
        }

        tasks.findByName("jacocoTestCoverageVerification")?.let { verifyTask ->
            (verifyTask as JacocoCoverageVerification).apply {
                dependsOn(tasks.named("jacocoTestReport"))
                executionData.setFrom(
                    fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec")
                )
                classDirectories.setFrom(
                    classDirectories.files.map {
                        fileTree(it) { exclude(jacocoExcludes) }
                    }
                )
                violationRules {
                    // Global: 60% line per-module (OWNER-ANSWERS QD2).
                    // Baseline-модули с coverage <60% временно помечаются
                    // через per-service override (см. example в attendance-app).
                    // НЕ ослаблять глобальный порог — это противоречит D3.
                    rule {
                        element = "BUNDLE"
                        limit {
                            counter = "LINE"
                            value = "COVEREDRATIO"
                            minimum = "0.60".toBigDecimal()
                        }
                    }
                }
            }

            // M08 G10 baseline (D3) — НЕ привязываем verification к :check
            // автоматически. Модули attendance-app/academic-app/schedule-app
            // имеют baseline coverage <60% (M05-M09 добавит тесты), ранее
            // не было JaCoCo-gate'а. Активация через M08 G12:
            //   tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
            // Запуск вручную: ./gradlew jacocoTestCoverageVerification
            // CI coverage-job использует continue-on-error: true до baseline.
        }
    }
}

// M04 NEW-57 / QA1 — CI-check против регрессии DEBUG в application.yml/application-prod.yml.
// Отдельный профиль application-dev.yml имеет право на DEBUG. Здесь ловим
// только default + prod, где DEBUG = secure-by-default нарушение
// (риск утечки JWT в query / SQL / payloads через DEBUG-логи).
tasks.register("verifyNoDebugInProd") {
    group = "verification"
    description = "QA1/NEW-57 — fails build if DEBUG level set for ru.rutcampustrack in application.yml or application-prod.yml"

    val configFiles = fileTree(rootDir) {
        include("services/**/src/main/resources/application.yml")
        include("services/**/src/main/resources/application-prod.yml")
        exclude("**/build/**")
    }
    inputs.files(configFiles)

    doLast {
        val violations = mutableListOf<String>()
        val pattern = Regex("""ru\.rutcampustrack[^:]*:\s*DEBUG""")
        configFiles.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { idx, line ->
                    val trimmed = line.substringBefore('#').trim()
                    if (pattern.containsMatchIn(trimmed)) {
                        violations += "${file.relativeTo(rootDir).invariantSeparatorsPath}:${idx + 1}: $line"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "QA1 violation — DEBUG level for ru.rutcampustrack must NOT appear in default or prod configs.\n" +
                "Use application-dev.yml for DEBUG. Violations:\n  " +
                violations.joinToString("\n  ")
            )
        }
    }
}

tasks.named("check") {
    dependsOn("verifyNoDebugInProd")
}

// M04 NEW-68 / QA7 — гарантирует что каждый Spring-сервис подключает
// shared/logback-base.xml (JSON-вывод + masking). Без этого логи летят
// в plain-text → невозможно корректно фильтровать в Loki.
tasks.register("verifyLogbackJsonInAllServices") {
    group = "verification"
    description = "QA7/NEW-68 — fails build if any *-app or auth/api-gateway resources lacks logback-spring.xml with shared/logback-base.xml include"

    val expectedServices = listOf(
        "services/api-gateway/src/main/resources",
        "services/auth-service/src/main/resources",
        "services/academic-service/academic-app/src/main/resources",
        "services/schedule-service/schedule-app/src/main/resources",
        "services/attendance-service/attendance-app/src/main/resources",
        "services/notification-service/notification-app/src/main/resources",
    )
    val expectedInclude = "shared/logback-base.xml"

    inputs.files(expectedServices.map { rootDir.resolve("$it/logback-spring.xml") })

    doLast {
        val missing = mutableListOf<String>()
        expectedServices.forEach { dir ->
            val configFile = rootDir.resolve("$dir/logback-spring.xml")
            if (!configFile.exists()) {
                missing += "$dir/logback-spring.xml — file missing"
            } else if (!configFile.readText().contains(expectedInclude)) {
                missing += "$dir/logback-spring.xml — does not include $expectedInclude"
            }
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "QA7 violation — каждый Spring-сервис обязан подключать shared/logback-base.xml.\n" +
                "Missing/broken:\n  " + missing.joinToString("\n  ")
            )
        }
    }
}

tasks.named("check") {
    dependsOn("verifyLogbackJsonInAllServices")
}

// M04 NEW-67 / QA6 — генерация build/resources/main/git.properties
// для /actuator/info (Spring Boot reads git.properties автоматически).
// Inline-task без отдельного plugin'а — paranoia против supply-chain
// и снижение зависимостей.
val springBootApps = listOf(
    "services/api-gateway",
    "services/auth-service",
    "services/academic-service/academic-app",
    "services/schedule-service/schedule-app",
    "services/attendance-service/attendance-app",
    "services/notification-service/notification-app",
)

fun gitOutput(vararg args: String): String {
    val proc = ProcessBuilder("git", *args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val out = proc.inputStream.bufferedReader().readText().trim()
    proc.waitFor()
    return if (proc.exitValue() == 0) out else "unknown"
}

tasks.register("generateGitProperties") {
    group = "build"
    description = "NEW-67 — generates git.properties for /actuator/info in all Spring Boot apps"

    val outputs = springBootApps.map { rootDir.resolve("$it/src/main/resources/git.properties") }
    this.outputs.files(outputs)

    doLast {
        val sha = gitOutput("rev-parse", "HEAD")
        val shortSha = gitOutput("rev-parse", "--short", "HEAD")
        val branch = gitOutput("rev-parse", "--abbrev-ref", "HEAD")
        val commitTime = gitOutput("log", "-1", "--format=%cI")
        val buildTime = OffsetDateTime.now().toString()

        outputs.forEach { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                |# Generated by NEW-67 root :generateGitProperties task. Do not edit.
                |git.commit.id=$sha
                |git.commit.id.abbrev=$shortSha
                |git.branch=$branch
                |git.commit.time=$commitTime
                |git.build.time=$buildTime
                """.trimMargin().trim() + "\n"
            )
        }
    }
}

springBootApps.forEach { path ->
    project(":${path.replace("/", ":")}").afterEvaluate {
        tasks.findByName("processResources")?.dependsOn(":generateGitProperties")
    }
}
