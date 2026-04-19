package ru.rutcampustrack.shared.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SharedOpenApiCustomizerTest {

    private final SharedOpenApiCustomizer config = new SharedOpenApiCustomizer();

    @Test
    @DisplayName("M01 заглушка — customizer создаётся и не падает на пустом OpenAPI")
    void customizerIsNoOpButSafe() {
        OpenApiCustomizer customizer = config.sharedErrorsCustomizer();
        assertThat(customizer).isNotNull();
        OpenAPI api = new OpenAPI();
        assertThatNoException().isThrownBy(() -> customizer.customise(api));
    }
}
