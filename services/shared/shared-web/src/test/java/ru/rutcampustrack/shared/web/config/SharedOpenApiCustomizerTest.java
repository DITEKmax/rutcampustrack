package ru.rutcampustrack.shared.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

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

    @Test
    void publicProfileDeclaresItsSchemeAndPreservesExplicitEndpointAdmission() {
        var login = new Operation();
        var qr = new Operation();
        var confirmation = new Operation();
        var protectedMap = new Operation();
        var explicit = new Operation().security(java.util.List.of());
        OpenAPI api =
                new OpenAPI()
                        .paths(
                                new Paths()
                                        .addPathItem("/auth/login", new PathItem().post(login))
                                        .addPathItem(
                                                "/auth/qr/status",
                                                new PathItem().post(qr).get(confirmation))
                                        .addPathItem(
                                                "/api/v1/map/manifest",
                                                new PathItem().get(protectedMap))
                                        .addPathItem("/special", new PathItem().get(explicit)));
        config.sharedErrorsCustomizer().customise(api);
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(login.getSecurity()).isEmpty();
        assertThat(qr.getSecurity()).isEmpty();
        assertThat(confirmation.getSecurity())
                .singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        assertThat(protectedMap.getSecurity())
                .singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        assertThat(explicit.getSecurity()).isEmpty();
    }
}
