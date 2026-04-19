package ru.rutcampustrack.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry-point notification-service.
 *
 * <p>M01: component scan расширен до {@code ru.rutcampustrack} чтобы
 * подхватить shared-web beans (GlobalExceptionHandler, JacksonConfig,
 * SharedOpenApiCustomizer, AdminActionAspect).
 */
@SpringBootApplication(scanBasePackages = {
        "ru.rutcampustrack.notification",
        "ru.rutcampustrack.shared.web"
})
public class NotificationWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationWebApplication.class, args);
    }
}
