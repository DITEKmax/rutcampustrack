package ru.rutcampustrack.shared.web.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * M13 G3: единый потолок {@code spring.data.web.pageable.max-page-size=100}
 * для всех сервисов, подключающих shared-web. Устраняет риск OOM/N+1
 * из {@code ?size=1000000} запросов.
 *
 * <p>Работает через {@link EnvironmentPostProcessor} (registered в
 * {@code META-INF/spring.factories}), чтобы shared-web мог задать
 * дефолт без прямой зависимости на spring-data-commons
 * (Pageable живёт там, не в spring-web).
 *
 * <p>Property source с самым низким приоритетом — любой сервис может
 * override'нуть в своём {@code application.yml} (например, schedule
 * уже имеет {@code max-page-size: 200} для week-range queries).
 */
public class PageableDefaultsPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "sharedWebPageableDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Map<String, Object> defaults = Map.of(
                "spring.data.web.pageable.max-page-size", "100"
        );
        environment.getPropertySources()
                .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }
}
