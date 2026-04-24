package ru.rutcampustrack.shared.web.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean-заглушка для M11 G1 (P2-2/1): единый customizer OpenAPI-спеки
 * всех сервисов — описание стандартных ошибок (400, 401, 403, 404, 409,
 * 429, 500) с ссылкой на shared {@code ErrorResponse} schema.
 *
 * <p>M01 — no-op. M11 G1 — наполнение. Бин создаётся только если springdoc
 * на classpath ({@code @ConditionalOnClass}) — сервис без OpenAPI не
 * получает ненужный бин.
 */
@Configuration
@ConditionalOnClass(OpenApiCustomizer.class)
public class SharedOpenApiCustomizer {

    @Bean
    public OpenApiCustomizer sharedErrorsCustomizer() {
        return openApi -> {
            // M11 G1 (P2-2/1): обогатить описания 4xx/5xx ошибок ссылкой на
            // shared ErrorResponse schema. Пока no-op — наполнение в
            // GlobalErrorResponsesCustomizer (отдельный @Bean в G1).
        };
    }
}
