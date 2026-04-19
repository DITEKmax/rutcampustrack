package ru.rutcampustrack.shared.web.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Единая Jackson-конфигурация для всех сервисов (P2-4/8, P2-1/5):
 *
 * <ul>
 *   <li>{@code READ_UNKNOWN_ENUM_VALUES_AS_NULL} — неизвестный enum на входе
 *       не ломает endpoint (постепенная миграция).</li>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES=false} — будущие поля DTO не ломают
 *       старых клиентов.</li>
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS=false} — даты всегда как ISO-8601
 *       строки, не миллисекунды.</li>
 * </ul>
 *
 * <p>Требует Spring Boot в classpath сервиса (Jackson2ObjectMapperBuilderCustomizer
 * приходит из spring-boot-autoconfigure). Сам бин — обычный {@code @Bean},
 * подхватывается через component scan на {@code ru.rutcampustrack.shared.web}.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer sharedJacksonCustomizer() {
        return builder -> builder
                .featuresToEnable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .featuresToDisable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
