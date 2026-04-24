package ru.rutcampustrack.shared.web.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import ru.rutcampustrack.shared.web.audit.AdminActionAspect;
import ru.rutcampustrack.shared.web.config.JacksonConfig;
import ru.rutcampustrack.shared.web.config.SharedOpenApiCustomizer;
import ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler;

/**
 * M11 G0.2: единый entry-point auto-configuration'а shared-web starter'а.
 *
 * <p>Регистрируется через
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * Consumer'у достаточно {@code implementation(project(":services:shared:shared-web"))}
 * — {@code @SpringBootApplication} автоматически подхватит все shared beans
 * без {@code scanBasePackages = {"ru.rutcampustrack.shared.web"}} hack'а.
 *
 * <p>Активируется только в servlet web-приложениях
 * ({@code @ConditionalOnWebApplication(type = SERVLET)}) — api-gateway
 * (WebFlux) не получает shared handler'ы.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
        GlobalExceptionHandler.class,
        JacksonConfig.class,
        SharedOpenApiCustomizer.class,
        AdminActionAspect.class
})
public class SharedWebAutoConfiguration {
}
