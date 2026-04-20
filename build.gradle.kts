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
