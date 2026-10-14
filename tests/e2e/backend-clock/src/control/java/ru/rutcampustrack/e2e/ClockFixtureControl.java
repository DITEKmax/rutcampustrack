package ru.rutcampustrack.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import ru.rutcampustrack.shared.web.time.EducationalTime;

/** Explicit build-lane control; does not boot any service or change process/security wall time. */
public final class ClockFixtureControl {
    private static void require(boolean value) {
        if (!value) throw new AssertionError("Teaching-clock control failed");
    }

    private static void rejects(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Teaching-clock guard accepted a forbidden fixture");
    }

    private static GenericApplicationContext context(String service) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "control", Map.of("spring.application.name", service)));
        context.registerBean("systemClock", Clock.class, Clock::systemUTC);
        context.registerBean(
                "educationalTime",
                EducationalTime.class,
                () -> new EducationalTime(context.getBean("systemClock", Clock.class)));
        return context;
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("rct-clock-control-");
        Path file = directory.resolve("current-instant");
        String oldEnabled = System.getProperty("rct.e2e.clock.enabled");
        String oldFile = System.getProperty("rct.e2e.clock.file");
        Instant first = Instant.parse("2026-10-12T06:00:00Z");
        Instant second = first.plusSeconds(600);
        try {
            System.setProperty("rct.e2e.clock.file", file.toString());
            System.clearProperty("rct.e2e.clock.enabled");
            try (var disabled = context("attendance-service")) {
                rejects(() -> new ClockInitializer().initialize(disabled));
            }
            System.setProperty("rct.e2e.clock.enabled", "true");
            try (var missingMarker = context("attendance-service")) {
                rejects(() -> new ClockInitializer().initialize(missingMarker));
            }
            Files.writeString(directory.resolve("disposable-target"), "RCT_E2E_DISPOSABLE");
            Files.writeString(file, "invalid");
            try (var malformed = context("attendance-service")) {
                rejects(() -> new ClockInitializer().initialize(malformed));
            }
            Files.writeString(file, first.toString());
            for (String excluded : new String[] {"auth-service", "api-gateway"}) {
                try (var forbidden = context(excluded)) {
                    rejects(() -> new ClockInitializer().initialize(forbidden));
                }
            }
            for (String service :
                    new String[] {
                        "academic-service", "schedule-service", "attendance-service", "mobile-bff"
                    }) {
                try (var allowed = context(service)) {
                    new ClockInitializer().initialize(allowed);
                    allowed.refresh();
                    Clock injected = allowed.getBean(EducationalTime.class).clock();
                    require(
                            allowed.getBean(Clock.class)
                                    == allowed.getBean("systemClock", Clock.class));
                    require(!(allowed.getBean("systemClock", Clock.class) instanceof FileClock));
                    require(injected.instant().equals(first));
                    require(injected.getZone().equals(ZoneId.of("Europe/Moscow")));
                    Clock utc = injected.withZone(ZoneId.of("UTC"));
                    Path next = directory.resolve("next-instant");
                    Files.writeString(next, second.toString());
                    Files.move(
                            next,
                            file,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    require(injected.instant().equals(second) && utc.instant().equals(second));
                    require(utc.getZone().equals(ZoneId.of("UTC")));
                    Files.writeString(file, first.toString());
                }
            }
            // Boot discovery must enroll the fixture; a direct initialize() call cannot prove it.
            SpringApplication application = new SpringApplication(ClockFixtureControl.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setBannerMode(Banner.Mode.OFF);
            application.setLogStartupInfo(false);
            application.setApplicationContextFactory(type -> context("attendance-service"));
            application.setDefaultProperties(
                    Map.of("spring.application.name", "attendance-service"));
            try (var bootContext = application.run()) {
                Clock educational = bootContext.getBean(EducationalTime.class).clock();
                require(educational instanceof FileClock);
                require(educational.instant().equals(first));
                require(
                        bootContext.getBean(Clock.class)
                                == bootContext.getBean("systemClock", Clock.class));
                require(!(bootContext.getBean(Clock.class) instanceof FileClock));
            }
            Files.delete(file);
            rejects(() -> new FileClock(file, ZoneId.of("Europe/Moscow")).instant());
            System.out.println(
                    "Typed teaching-time context controls PASS; mixed-consumer isolation requires its own tests");
        } finally {
            if (oldEnabled == null) System.clearProperty("rct.e2e.clock.enabled");
            else System.setProperty("rct.e2e.clock.enabled", oldEnabled);
            if (oldFile == null) System.clearProperty("rct.e2e.clock.file");
            else System.setProperty("rct.e2e.clock.file", oldFile);
            Files.deleteIfExists(directory.resolve("next-instant"));
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory.resolve("disposable-target"));
            Files.deleteIfExists(directory);
        }
    }
}
