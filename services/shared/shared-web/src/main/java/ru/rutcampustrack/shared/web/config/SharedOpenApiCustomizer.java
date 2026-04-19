package ru.rutcampustrack.shared.web.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean-заглушка для будущего M06 (P2-2/1): единый customizer OpenAPI-спеки
 * всех сервисов — описание стандартных ошибок (400, 401, 403, 500) с
 * ссылкой на shared {@code ErrorResponse} schema.
 *
 * <p>В M01 — no-op. Реальная логика добавится в M06 без ломающих изменений
 * API сервисов. Бин создаётся только если springdoc на classpath
 * ({@code @ConditionalOnClass}) — сервис без OpenAPI не получает ненужный бин.
 */
@Configuration
@ConditionalOnClass(OpenApiCustomizer.class)
public class SharedOpenApiCustomizer {

    @Bean
    public OpenApiCustomizer sharedErrorsCustomizer() {
        return openApi -> {
            // M06 (P2-2/1): обогатить описания 4xx/5xx ошибок ссылкой на
            // shared ErrorResponse schema. Пока no-op.
        };
    }
}
