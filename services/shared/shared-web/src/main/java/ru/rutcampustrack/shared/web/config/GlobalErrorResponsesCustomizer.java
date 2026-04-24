package ru.rutcampustrack.shared.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M11 G1 (P2-2/1): единый OpenAPI customizer для всех сервисов —
 * добавляет 7 стандартных error responses (400/401/403/404/409/429/500)
 * ко всем operations с ссылкой на shared {@link ErrorResponse} schema
 * и media type {@code application/problem+json}.
 *
 * <p>Поведение:
 * <ul>
 *   <li>Операции без {@code @ApiResponses(...)} — получают все 7
 *       стандартных responses</li>
 *   <li>Операции с существующими @ApiResponse (например
 *       {@code 404 "Пользователь не найден"}) — сохраняют описание,
 *       но получают content schema + остальные стандартные responses</li>
 *   <li>Success responses (200/201/204) — не трогаются</li>
 * </ul>
 *
 * <p>Регистрируется автоматически через {@link SharedOpenApiCustomizer}
 * когда springdoc-openapi-starter-common на classpath.
 *
 * @see SharedOpenApiCustomizer
 */
@Configuration
@ConditionalOnClass(OpenApiCustomizer.class)
public class GlobalErrorResponsesCustomizer {

    /** Media type для RFC 9457 Problem Details body. */
    public static final String PROBLEM_JSON = "application/problem+json";

    /** Имя shared ErrorResponse schema в OpenAPI components. */
    public static final String ERROR_RESPONSE_SCHEMA = "ErrorResponse";

    /**
     * Стандартные HTTP статусы ошибок + дефолтные descriptions (если
     * operation не переопределила свой description).
     * LinkedHashMap сохраняет порядок в generated OpenAPI spec.
     */
    private static final Map<String, String> DEFAULT_ERROR_RESPONSES = new LinkedHashMap<>();

    static {
        DEFAULT_ERROR_RESPONSES.put("400", "Ошибка валидации запроса");
        DEFAULT_ERROR_RESPONSES.put("401", "Требуется аутентификация");
        DEFAULT_ERROR_RESPONSES.put("403", "Доступ запрещён");
        DEFAULT_ERROR_RESPONSES.put("404", "Ресурс не найден");
        DEFAULT_ERROR_RESPONSES.put("409", "Конфликт данных");
        DEFAULT_ERROR_RESPONSES.put("429", "Превышен лимит запросов");
        DEFAULT_ERROR_RESPONSES.put("500", "Внутренняя ошибка сервера");
    }

    /**
     * Customizer применяется ПОСЛЕ per-endpoint annotations (
     * {@link Ordered#LOWEST_PRECEDENCE}), но ДО user-defined customizers
     * с более высоким приоритетом. Существующие responses не
     * перезаписываются; добавляются только недостающие + content schema.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public OpenApiCustomizer globalErrorResponsesCustomizer() {
        return openApi -> {
            registerErrorResponseSchema(openApi);
            applyToAllOperations(openApi);
        };
    }

    /** Регистрирует shared {@link ErrorResponse} + referenced schemas
     *  (включая {@link ru.rutcampustrack.shared.web.api.exception.FieldError})
     *  в components.schemas. Без этого $ref в fieldErrors[].items указывает
     *  на несуществующий component → openapi-typescript ломается. */
    private static void registerErrorResponseSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Components components = openApi.getComponents();
        io.swagger.v3.core.converter.ResolvedSchema resolved =
                io.swagger.v3.core.converter.ModelConverters.getInstance()
                        .readAllAsResolvedSchema(ErrorResponse.class);
        if (components.getSchemas() == null
                || !components.getSchemas().containsKey(ERROR_RESPONSE_SCHEMA)) {
            components.addSchemas(ERROR_RESPONSE_SCHEMA, resolved.schema);
        }
        // referencedSchemas включает FieldError (вложенный класс из
        // shared-web-api) — springdoc controller-scan не добирается до
        // модуля api, поэтому ручная регистрация обязательна.
        if (resolved.referencedSchemas != null) {
            resolved.referencedSchemas.forEach((name, schema) -> {
                if (!components.getSchemas().containsKey(name)) {
                    components.addSchemas(name, schema);
                }
            });
        }
    }

    /** Применяет стандартные error responses ко всем operations. */
    private static void applyToAllOperations(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    ApiResponses responses = operation.getResponses();
                    if (responses == null) {
                        responses = new ApiResponses();
                        operation.setResponses(responses);
                    }
                    for (Map.Entry<String, String> entry : DEFAULT_ERROR_RESPONSES.entrySet()) {
                        mergeErrorResponse(responses, entry.getKey(), entry.getValue());
                    }
                })
        );
    }

    /**
     * Merge правило:
     * <ul>
     *   <li>Статус отсутствует → создать с default description + content</li>
     *   <li>Статус существует, но без content → обогатить content (schema)</li>
     *   <li>Статус существует с content → не трогать (per-endpoint override)</li>
     * </ul>
     */
    private static void mergeErrorResponse(ApiResponses responses,
                                           String status,
                                           String defaultDescription) {
        ApiResponse existing = responses.get(status);
        if (existing == null) {
            ApiResponse created = new ApiResponse()
                    .description(defaultDescription)
                    .content(problemJsonContent());
            responses.addApiResponse(status, created);
            return;
        }
        if (existing.getContent() == null || existing.getContent().isEmpty()) {
            existing.setContent(problemJsonContent());
        }
    }

    /** Строит {@code Content} с media-type {@value #PROBLEM_JSON} +
     *  {@code $ref: #/components/schemas/ErrorResponse}. */
    private static Content problemJsonContent() {
        MediaType mediaType = new MediaType().schema(
                new Schema<>().$ref("#/components/schemas/" + ERROR_RESPONSE_SCHEMA));
        return new Content().addMediaType(PROBLEM_JSON, mediaType);
    }
}
