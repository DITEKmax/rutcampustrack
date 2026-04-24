package ru.rutcampustrack.shared.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalErrorResponsesCustomizerTest {

    private final GlobalErrorResponsesCustomizer config = new GlobalErrorResponsesCustomizer();

    @Test
    @DisplayName("M11 G1: customizer создаётся и не падает на пустом OpenAPI")
    void customizerHandlesEmptyOpenApi() {
        OpenApiCustomizer customizer = config.globalErrorResponsesCustomizer();
        OpenAPI api = new OpenAPI();

        customizer.customise(api);

        // Schema зарегистрирована даже для пустого spec (для будущих operations)
        assertThat(api.getComponents()).isNotNull();
        assertThat(api.getComponents().getSchemas())
                .containsKey(GlobalErrorResponsesCustomizer.ERROR_RESPONSE_SCHEMA);
    }

    @Test
    @DisplayName("M11 G1: operation без responses получает все 7 стандартных error responses")
    void addsAllSevenErrorResponsesWhenEmpty() {
        OpenAPI api = openApiWithSingleOperation(new Operation());
        OpenApiCustomizer customizer = config.globalErrorResponsesCustomizer();

        customizer.customise(api);

        ApiResponses responses = api.getPaths().get("/test").getGet().getResponses();
        assertThat(responses).containsKeys("400", "401", "403", "404", "409", "429", "500");
        assertThat(responses.get("400").getDescription()).isEqualTo("Ошибка валидации запроса");
        assertThat(responses.get("500").getDescription()).isEqualTo("Внутренняя ошибка сервера");
    }

    @Test
    @DisplayName("M11 G1: все добавленные error responses имеют content=application/problem+json + $ref ErrorResponse")
    void addedResponsesHaveProblemJsonContent() {
        OpenAPI api = openApiWithSingleOperation(new Operation());
        OpenApiCustomizer customizer = config.globalErrorResponsesCustomizer();

        customizer.customise(api);

        ApiResponses responses = api.getPaths().get("/test").getGet().getResponses();
        for (String status : new String[]{"400", "401", "403", "404", "409", "429", "500"}) {
            Content content = responses.get(status).getContent();
            assertThat(content)
                    .as("status %s content", status)
                    .isNotNull()
                    .containsKey(GlobalErrorResponsesCustomizer.PROBLEM_JSON);

            MediaType mediaType = content.get(GlobalErrorResponsesCustomizer.PROBLEM_JSON);
            assertThat(mediaType.getSchema().get$ref())
                    .isEqualTo("#/components/schemas/"
                            + GlobalErrorResponsesCustomizer.ERROR_RESPONSE_SCHEMA);
        }
    }

    @Test
    @DisplayName("M11 G1: existing @ApiResponse с description сохраняется, добавляется content schema")
    void preservesExistingDescriptionEnrichesContent() {
        Operation op = new Operation();
        ApiResponses existing = new ApiResponses();
        existing.addApiResponse("404", new ApiResponse().description("Пользователь не найден"));
        op.setResponses(existing);
        OpenAPI api = openApiWithSingleOperation(op);

        config.globalErrorResponsesCustomizer().customise(api);

        ApiResponse resp404 = api.getPaths().get("/test").getGet().getResponses().get("404");
        // Custom description preserved
        assertThat(resp404.getDescription()).isEqualTo("Пользователь не найден");
        // Content enriched
        assertThat(resp404.getContent()).containsKey(GlobalErrorResponsesCustomizer.PROBLEM_JSON);
    }

    @Test
    @DisplayName("M11 G1: existing @ApiResponse с уже определённым content — не перезаписывается")
    void doesNotOverwriteExistingContent() {
        Operation op = new Operation();
        ApiResponses existing = new ApiResponses();
        MediaType custom = new MediaType();
        Content customContent = new Content().addMediaType("application/custom+json", custom);
        existing.addApiResponse("409", new ApiResponse()
                .description("Конфликт")
                .content(customContent));
        op.setResponses(existing);
        OpenAPI api = openApiWithSingleOperation(op);

        config.globalErrorResponsesCustomizer().customise(api);

        ApiResponse resp409 = api.getPaths().get("/test").getGet().getResponses().get("409");
        assertThat(resp409.getContent()).containsKey("application/custom+json");
        // Shared problem+json НЕ добавлен поверх существующего custom content
        assertThat(resp409.getContent()).doesNotContainKey(GlobalErrorResponsesCustomizer.PROBLEM_JSON);
    }

    @Test
    @DisplayName("M11 G1: success responses (200/201/204) не затрагиваются")
    void doesNotTouchSuccessResponses() {
        Operation op = new Operation();
        ApiResponses existing = new ApiResponses();
        existing.addApiResponse("200", new ApiResponse().description("OK"));
        existing.addApiResponse("201", new ApiResponse().description("Created"));
        op.setResponses(existing);
        OpenAPI api = openApiWithSingleOperation(op);

        config.globalErrorResponsesCustomizer().customise(api);

        ApiResponses responses = api.getPaths().get("/test").getGet().getResponses();
        assertThat(responses.get("200").getContent()).isNull();
        assertThat(responses.get("201").getContent()).isNull();
    }

    @Test
    @DisplayName("M11 G1: shared ErrorResponse schema зарегистрирована в components.schemas")
    void registersErrorResponseSchemaInComponents() {
        OpenAPI api = new OpenAPI();

        config.globalErrorResponsesCustomizer().customise(api);

        assertThat(api.getComponents().getSchemas())
                .containsKey(GlobalErrorResponsesCustomizer.ERROR_RESPONSE_SCHEMA);
    }

    @Test
    @DisplayName("M11 G1: customizer применяется ко всем operations всех paths")
    void appliesToAllPathsAndMethods() {
        OpenAPI api = new OpenAPI();
        api.setPaths(new Paths());

        Operation getOp = new Operation();
        Operation postOp = new Operation();
        Operation deleteOp = new Operation();

        PathItem p1 = new PathItem().get(getOp).post(postOp);
        PathItem p2 = new PathItem().delete(deleteOp);
        api.getPaths().addPathItem("/users", p1);
        api.getPaths().addPathItem("/users/{id}", p2);

        config.globalErrorResponsesCustomizer().customise(api);

        assertThat(getOp.getResponses()).containsKey("400");
        assertThat(postOp.getResponses()).containsKey("400");
        assertThat(deleteOp.getResponses()).containsKey("400");
    }

    // -- helpers --

    private static OpenAPI openApiWithSingleOperation(Operation op) {
        OpenAPI api = new OpenAPI();
        api.setPaths(new Paths());
        PathItem path = new PathItem().get(op);
        api.getPaths().addPathItem("/test", path);
        return api;
    }
}
