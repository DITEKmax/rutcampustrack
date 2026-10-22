package ru.rutcampustrack.shared.logback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ProductionLoggingLevelsTest {
    private static List<String> violations(Resource resource) throws Exception {
        List<String> result = new ArrayList<>();
        for (var source : new YamlPropertySourceLoader().load("production-gate", resource)) {
            Object activation = source.getProperty("spring.config.activate.on-profile");
            if (activation != null
                    && !Profiles.of(activation.toString())
                            .matches(
                                    profile ->
                                            profile.equals("prod") || profile.equals("default"))) {
                continue;
            }
            for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                if ((name.equals("logging.level.ru.rutcampustrack")
                                || name.startsWith("logging.level.ru.rutcampustrack."))
                        && "DEBUG"
                                .equals(
                                        String.valueOf(source.getProperty(name))
                                                .trim()
                                                .toUpperCase(Locale.ROOT))) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    @Test
    void defaultAndProductionPackageLevelsNeverEnableDebug() throws Exception {
        Path root = Path.of(System.getProperty("rct.repository.root", "../../.."));
        List<String> failures = new ArrayList<>();
        try (var files = Files.walk(root.resolve("services"))) {
            for (Path file :
                    files.filter(Files::isRegularFile)
                            .filter(
                                    path ->
                                            path.toString()
                                                    .replace('\\', '/')
                                                    .contains("/src/main/resources/"))
                            .filter(
                                    path ->
                                            path.getFileName().toString().equals("application.yml")
                                                    || path.getFileName()
                                                            .toString()
                                                            .equals("application-prod.yml"))
                            .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
                            .toList()) {
                if (!violations(new FileSystemResource(file)).isEmpty()) {
                    failures.add(root.relativize(file).toString());
                }
            }
        }
        assertThat(failures).as("Default/prod package DEBUG violations (paths only)").isEmpty();
    }

    @Test
    void equivalentYamlLevelsAndDevelopmentExemptionUseEffectiveProperties() throws Exception {
        for (String yaml :
                List.of(
                        "logging.level.ru.rutcampustrack: \"DEBUG\"\n",
                        "logging:\n  level:\n    ru.rutcampustrack: 'debug' # production\n",
                        "logging:\n"
                            + "  level:\n"
                            + "    ru:\n"
                            + "      rutcampustrack:\n"
                            + "        gateway: DEBUG\n")) {
            assertThat(violations(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
                    .hasSize(1);
        }
        for (String yaml :
                List.of(
                        "# logging.level.ru.rutcampustrack: DEBUG\n"
                            + "logging.level.ru.rutcampustrack: INFO\n",
                        "spring.config.activate.on-profile: dev\n"
                            + "logging.level.ru.rutcampustrack: DEBUG\n")) {
            assertThat(violations(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
                    .isEmpty();
        }
    }
}
