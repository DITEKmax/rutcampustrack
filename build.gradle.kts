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
