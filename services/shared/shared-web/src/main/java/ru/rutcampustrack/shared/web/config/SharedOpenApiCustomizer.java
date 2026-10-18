package ru.rutcampustrack.shared.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/** Public HTTP access-token profile; internal transports are excluded by their API owners. */
@Configuration
@ConditionalOnClass(OpenApiCustomizer.class)
public class SharedOpenApiCustomizer {
    private static final Set<String> PUBLIC_AUTH =
            Set.of("/auth/login", "/auth/refresh", "/auth/logout", "/auth/tma", "/auth/public-key");
    private static final Set<String> PUBLIC_QR =
            Set.of("/auth/qr/challenges", "/auth/qr/status", "/auth/qr/exchange");

    @Bean
    public OpenApiCustomizer sharedErrorsCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) openApi.setComponents(new Components());
            if (openApi.getComponents().getSecuritySchemes() == null
                    || !openApi.getComponents().getSecuritySchemes().containsKey("bearerAuth")) {
                openApi.getComponents()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Public access token. Gateway validates the current"
                                                    + " session and supplies service identity."));
            }
            if (openApi.getPaths() == null) return;
            openApi.getPaths()
                    .forEach(
                            (path, item) ->
                                    item.readOperationsMap()
                                            .forEach(
                                                    (method, operation) -> {
                                                        if (operation.getSecurity() == null) {
                                                            boolean publicAuth =
                                                                    PUBLIC_AUTH.contains(path)
                                                                            || path.startsWith(
                                                                                    "/auth/otp/")
                                                                            || path.startsWith(
                                                                                    "/auth/password-reset/")
                                                                            || (method
                                                                                            == PathItem
                                                                                                    .HttpMethod
                                                                                                    .POST
                                                                                    && PUBLIC_QR
                                                                                            .contains(
                                                                                                    path));
                                                            operation.setSecurity(
                                                                    publicAuth
                                                                            ? List.of()
                                                                            : List.of(
                                                                                    new SecurityRequirement()
                                                                                            .addList(
                                                                                                    "bearerAuth")));
                                                        }
                                                    }));
        };
    }
}
