import java.io.File
import java.time.OffsetDateTime

plugins {
    java
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.diffplug.spotless") version "7.0.4"
}

// Author sources only. Generated stubs, historical copies and byte-sensitive
// fixtures are deliberately outside this formatter's target.
spotless {
    java {
        val authoredJava = providers.exec {
            commandLine("git", "ls-files", "-z", "--", "services/**/src/**/*.java", "tests/e2e/backend-clock/**/*.java")
        }.standardOutput.asText.get().split('\u0000').filter { path ->
            path.isNotBlank() && !path.contains("/generated/") &&
                !path.contains("/snapshots/")
        }
        target(authoredJava)
        googleJavaFormat("1.25.2").aosp()
    }
}

tasks.register("formatJava") {
    group = "formatting"
    description = "Formats tracked handwritten Java with four-space AOSP style."
    dependsOn("spotlessJavaApply")
}

tasks.register("formatJavaCheck") {
    group = "verification"
    description = "Checks tracked handwritten Java without rewriting files."
    dependsOn("spotlessJavaCheck")
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

    // Dependency security repair — keep patch overrides in one root policy.
    // Spring Boot's dependency-management plugin consumes these properties
    // for every Boot application and shared module that imports its BOM.
    extra["netty.version"] = "4.1.137.Final"
    extra["tomcat.version"] = "10.1.59"
    extra["rabbit-amqp-client.version"] = "5.33.1"
    extra["postgresql.version"] = "42.7.12"

    // Diagnostic coverage reports; test failures remain blocking.
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

    // JaCoCo aggregates unit and integration execution data for Java modules.
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

        // M11 G3 — passthrough флаг для OpenApiSnapshotIT (update mode),
        // иначе forked test JVM не видит CLI -D.
        if (project.hasProperty("openapi.snapshot.update")) {
            systemProperty("openapi.snapshot.update",
                    project.property("openapi.snapshot.update") as String)
        }

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
        "ru/rutcampustrack/**/*Application.class",
        "ru/rutcampustrack/**/*Dto.class",
        "ru/rutcampustrack/**/dto/**",
        "ru/rutcampustrack/**/config/**",
    )

    // Generated protobuf/gRPC classes are identified from the Java source
    // roots produced by the protobuf plugin. This keeps handwritten gRPC
    // implementations (for example DocumentRendererGrpcServiceImpl) in the
    // report while excluding their generated outer/nested classes.
    val generatedProtoSourceRoots = listOf(
        layout.buildDirectory.dir("generated/source/proto/main/java"),
        layout.buildDirectory.dir("generated/source/proto/main/grpc"),
    )

    fun generatedProtoClassExcludes(): List<String> = generatedProtoSourceRoots.flatMap { rootProvider ->
        val root = rootProvider.get().asFile
        if (!root.isDirectory) {
            return@flatMap emptyList()
        }
        root.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .flatMap { sourceFile ->
                val classPath = sourceFile.relativeTo(root)
                    .invariantSeparatorsPath
                    .removeSuffix(".java")
                sequenceOf(
                    "$classPath.class",
                    classPath + '$' + "*.class",
                )
            }
            .toList()
    }

    fun filteredJacocoClassDirectories(original: Set<File>) = project.files(project.provider {
        val generatedExcludes = generatedProtoClassExcludes()
        original.map { directory ->
            project.fileTree(directory) {
                exclude(jacocoExcludes + generatedExcludes)
            }
        }
    })

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
                classDirectories.setFrom(filteredJacocoClassDirectories(classDirectories.files))
            }
        }

        // check still runs both test tasks and produces the diagnostic report.
        val hasTests = sourceSets.findByName("test")?.allSource?.files?.isNotEmpty() == true
        if (hasTests) {
            tasks.named("check") {
                dependsOn("jacocoTestReport")
            }
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
        "services/auth-service/auth-app/src/main/resources",
        "services/academic-service/academic-app/src/main/resources",
        "services/schedule-service/schedule-app/src/main/resources",
        "services/attendance-service/attendance-app/src/main/resources",
        "services/mobile-bff/mobile-bff-app/src/main/resources",
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
    "services/auth-service/auth-app",
    "services/academic-service/academic-app",
    "services/schedule-service/schedule-app",
    "services/attendance-service/attendance-app",
    "services/mobile-bff/mobile-bff-app",
    "services/notification-service/notification-app",
)

fun gitOutput(vararg args: String): String {
    // M13 G25.6: try/catch вокруг ProcessBuilder.start() — в эфемерных
    // build environments (Docker multi-stage, CI без git binary) запуск
    // git выбрасывает IOException до того как мы доходим до exitValue().
    // Возвращаем "unknown" — git.properties всё равно генерируется,
    // просто без реальных значений (актуально для Docker build, где
    // .git/ исключён через .dockerignore).
    return try {
        val proc = ProcessBuilder("git", *args)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        if (proc.exitValue() == 0) out else "unknown"
    } catch (e: java.io.IOException) {
        "unknown"
    }
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

// Explicit external E2E artifact. No production source set, bootJar or assemble depends on it.
val e2eClock = sourceSets.create("e2eClock") {
    java.setSrcDirs(listOf("tests/e2e/backend-clock/src/main/java"))
}
val e2eClockControl = sourceSets.create("e2eClockControl") {
    java.setSrcDirs(listOf("tests/e2e/backend-clock/src/control/java"))
    compileClasspath += e2eClock.output
    runtimeClasspath += e2eClock.output
}
dependencies {
    add(e2eClock.compileOnlyConfigurationName,
        platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    add(e2eClock.compileOnlyConfigurationName, "org.springframework:spring-context")
    add(e2eClock.compileOnlyConfigurationName, project(":services:shared:shared-web-api"))
    add(e2eClockControl.implementationConfigurationName,
        platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    add(e2eClockControl.implementationConfigurationName, "org.springframework:spring-context")
    add(e2eClockControl.implementationConfigurationName, project(":services:shared:shared-web-api"))
}
tasks.register<Jar>("e2eClockFixtureJar") {
    group = "verification"
    description = "Builds an external opt-in teaching clock for disposable real-server E2E only."
    archiveFileName.set("clock-fixture.jar")
    destinationDirectory.set(layout.buildDirectory.dir("e2e-clock"))
    from(e2eClock.output)
}
tasks.register<JavaExec>("e2eClockFixtureControl") {
    group = "verification"
    description = "Checks injection, atomic file changes, guards and excluded service identities."
    classpath = e2eClockControl.runtimeClasspath
    mainClass.set("ru.rutcampustrack.e2e.ClockFixtureControl")
}
