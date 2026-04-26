package ru.rutcampustrack.shared.web.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * M14 G4 (CSO HIGH-06): fail-fast на отсутствующие critical secrets в env.
 *
 * <p>Spring Boot 3.x не fail-fast на unresolved YAML placeholders
 * (issues spring-boot#10463 / #18816) — `${SECRET}` без default остаётся
 * literal `"${SECRET}"` string, deploy без `--env-file` поднимется на
 * dev fallback'ах либо broken literal. Operator не получает immediate
 * signal что забыл env-файл.
 *
 * <p>Этот validator работает поверх Spring placeholder resolution —
 * напрямую проверяет `Environment.getProperty(envName)` для каждого
 * required secret из CSV property `rutcampustrack.security.required-env-vars`,
 * и кидает {@link IllegalStateException} с явным actionable сообщением
 * если хоть один не задан.
 *
 * <h3>Активация per-service</h3>
 *
 * В {@code application.yml} каждого сервиса:
 * <pre>{@code
 * rutcampustrack:
 *   security:
 *     required-env-vars: REDIS_PASSWORD,RABBITMQ_PASSWORD,INTERNAL_ISSUER_SECRET
 * }</pre>
 *
 * <h3>Test / local profile bypass</h3>
 *
 * Validator skip'ается если активен профиль {@code test} либо {@code local}.
 * Test'ы используют testcontainers + {@code @DynamicPropertySource},
 * local dev — fallback values из YAML. Production deploy не задаёт
 * `SPRING_PROFILES_ACTIVE` (default profile) → validator срабатывает.
 *
 * <h3>Регистрация</h3>
 *
 * Через {@code META-INF/spring.factories} как {@link EnvironmentPostProcessor}.
 * Подхватывается всеми Spring Boot приложениями — включая
 * api-gateway (WebFlux), который не подключает {@code SharedWebAutoConfiguration}
 * (servlet-only).
 */
public class RequiredSecretsValidator implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RequiredSecretsValidator.class);

    static final String REQUIRED_VARS_PROPERTY = "rutcampustrack.security.required-env-vars";
    static final String SKIP_PROFILE_TEST = "test";
    static final String SKIP_PROFILE_LOCAL = "local";

    /**
     * JUnit Jupiter API class — присутствует на classpath ТОЛЬКО в test scope
     * (build.gradle.kts добавляет JUnit как `testImplementation`). Production
     * deploy не имеет JUnit на classpath. Детекция через `Class.forName` —
     * universal test-context indicator, не зависит от {@code @ActiveProfiles}.
     */
    private static final String JUNIT_TEST_CLASS = "org.junit.jupiter.api.Test";

    /**
     * Property для отключения JUnit-skip в unit-тестах самого validator'а
     * (нам нужно verify "throws when missing" путь). Production deploy
     * никогда это не выставляет (а если и выставит — JUnit отсутствует,
     * skip-detection вообще не активен).
     */
    static final String SKIP_JUNIT_DETECTION_PROPERTY =
            "rutcampustrack.security.required-secrets-validator.skip-junit-detection";

    /**
     * Cached result: is JUnit on classpath? (вычисляется один раз, не на
     * каждый EnvironmentPostProcessor invocation). Package-private для
     * verification в unit-тестах.
     */
    static final boolean JUNIT_ON_CLASSPATH = isClassPresent(JUNIT_TEST_CLASS);

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, RequiredSecretsValidator.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        // M14 G9: универсальный test detection через JUnit на classpath.
        // Production deploy → JUnit отсутствует → validator активен.
        // Любой test (gradle test/integrationTest, IDE run, CI) → JUnit
        // присутствует → skip независимо от @ActiveProfiles. Решает
        // regression в IT'ах без `@ActiveProfiles("test")` (M14 G9 found
        // 14 таких IT в notification-service / api-gateway / academic /
        // auth — добавлять в каждый @ActiveProfiles не масштабируется
        // и хрупко).
        //
        // Unit-тесты этого validator'а сами устанавливают
        // SKIP_JUNIT_DETECTION_PROPERTY=true, чтобы verify "throws when
        // missing" путь — это безопасно, потому что property в production
        // никогда не set, а если set — JUnit всё равно отсутствует
        // (skip-detection не имеет эффекта).
        boolean skipJunitDetection = Boolean.parseBoolean(
                environment.getProperty(SKIP_JUNIT_DETECTION_PROPERTY, "false"));
        if (JUNIT_ON_CLASSPATH && !skipJunitDetection) {
            log.debug("RequiredSecretsValidator: skip (JUnit on classpath — test context)");
            return;
        }

        Set<String> activeProfiles = Set.of(environment.getActiveProfiles());
        if (activeProfiles.contains(SKIP_PROFILE_TEST)
                || activeProfiles.contains(SKIP_PROFILE_LOCAL)) {
            log.debug("RequiredSecretsValidator: skip (active profiles = {})", activeProfiles);
            return;
        }

        String requiredCsv = environment.getProperty(REQUIRED_VARS_PROPERTY, "");
        if (requiredCsv.isBlank()) {
            log.debug("RequiredSecretsValidator: skip (property {} not set / empty)",
                    REQUIRED_VARS_PROPERTY);
            return;
        }

        List<String> requiredNames = Arrays.stream(requiredCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> missing = requiredNames.stream()
                .filter(name -> {
                    String value = environment.getProperty(name);
                    return value == null || value.isBlank();
                })
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "M14 G4 (CSO HIGH-06): required environment variables are not set: "
                            + missing
                            + ". These secrets must be provided via `--env-file .env.prod` "
                            + "or explicit `-e VAR=value`. "
                            + "If running tests, ensure @ActiveProfiles(\"test\") is set on the test class."
            );
        }

        log.info("RequiredSecretsValidator: all {} required secrets present in environment",
                requiredNames.size());
    }
}
