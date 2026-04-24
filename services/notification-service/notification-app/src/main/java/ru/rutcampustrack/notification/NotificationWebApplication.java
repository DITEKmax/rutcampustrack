package ru.rutcampustrack.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry-point notification-service.
 *
 * <p>M01: component scan расширен до {@code ru.rutcampustrack} чтобы
 * подхватить shared-web beans (GlobalExceptionHandler, JacksonConfig,
 * SharedOpenApiCustomizer, AdminActionAspect).
 *
 * <p>M11 G0.8: убран hack {@code scanBasePackages = {..., "ru.rutcampustrack.shared.web"}}.
 * Теперь shared-web beans подключаются через
 * {@code SharedWebAutoConfiguration} +
 * {@code META-INF/spring/AutoConfiguration.imports} (Spring Boot 3 idiom).
 * Default {@code @SpringBootApplication} достаточно.
 */
@SpringBootApplication
public class NotificationWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationWebApplication.class, args);
    }
}
