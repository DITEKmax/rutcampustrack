package ru.rutcampustrack.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import ru.rutcampustrack.shared.web.time.EducationalTime;

/** Loaded exclusively from the explicit disposable test classpath, never component-scanned. */
public final class ClockInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Set<String> SERVICES =
            Set.of("academic-service", "schedule-service", "attendance-service", "mobile-bff");
    private static final String MARKER = "RCT_E2E_DISPOSABLE";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!"true".equals(System.getProperty("rct.e2e.clock.enabled"))
                || !SERVICES.contains(
                        context.getEnvironment().getProperty("spring.application.name"))) {
            throw new IllegalStateException(
                    "Teaching clock requires explicit disposable educational-service opt-in");
        }
        String configuredFile = System.getProperty("rct.e2e.clock.file");
        if (configuredFile == null || !Path.of(configuredFile).isAbsolute()) {
            throw new IllegalStateException("Teaching clock requires an absolute fixture file");
        }
        Path file = Path.of(configuredFile).normalize();
        if (file.getParent() == null) {
            throw new IllegalStateException("Teaching clock requires a fixture directory");
        }
        try {
            Path marker = file.getParent().resolve("disposable-target");
            if (!Files.isRegularFile(marker)
                    || Files.size(marker) > 64
                    || !MARKER.equals(Files.readString(marker).strip())) {
                throw new IllegalStateException(
                        "Teaching clock requires the disposable-target marker");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Teaching clock requires the disposable-target marker");
        }
        FileClock clock = new FileClock(file, ZoneId.of("Europe/Moscow"));
        clock.instant(); // Fail startup, never substitute real time on a malformed fixture.
        if (!(context instanceof GenericApplicationContext generic)) {
            throw new IllegalStateException(
                    "Teaching clock requires the supported Boot application context");
        }
        generic.registerBean(
                "rctE2eEducationalTime",
                EducationalTime.class,
                () -> new EducationalTime(clock),
                definition -> definition.setPrimary(true));
    }
}
