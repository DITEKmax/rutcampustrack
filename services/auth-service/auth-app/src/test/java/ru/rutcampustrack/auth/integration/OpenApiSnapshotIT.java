package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * M12 G5 (extends M11 G3): OpenAPI ↔ runtime conformance check для auth-service.
 *
 * <p>Тест GET'ает {@code /api-docs} у running app и сравнивает с committed snapshot'ом в {@code
 * docs/openapi/auth.json}. При расхождении падает с инструкцией обновить snapshot через {@code
 * -Popenapi.snapshot.update=true}.
 *
 * <p>M12 binary-compatibility gate: после M12 split и M11 @Schema policy, runtime /api-docs должен
 * совпадать с ручным baseline из {@code docs/openapi/auth.json}. Internal endpoints (@Hidden) не
 * должны присутствовать в public spec.
 */
class OpenApiSnapshotIT extends AbstractIntegrationTest {

    private static final String SERVICE_NAME = "auth";
    // CWD при запуске gradle test — services/auth-service/auth-app/,
    // repo root = ../../../ относительно неё.
    private static final Path SNAPSHOT_PATH =
            Paths.get("../../..")
                    .resolve("docs")
                    .resolve("openapi")
                    .resolve(SERVICE_NAME + ".json")
                    .toAbsolutePath()
                    .normalize();

    @Autowired TestRestTemplate restTemplate;

    @Test
    void apiDocsMatchesSnapshot() throws IOException {
        String actual = fetchAndNormalize();

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.createDirectories(SNAPSHOT_PATH.getParent());
            Files.writeString(SNAPSHOT_PATH, actual, StandardCharsets.UTF_8);
            System.out.println("[openapi-snapshot] Updated " + SNAPSHOT_PATH);
            return;
        }

        assertThat(SNAPSHOT_PATH)
                .as(
                        "Snapshot file missing — run with -Popenapi.snapshot.update=true to create"
                            + " %s",
                        SNAPSHOT_PATH)
                .exists();

        String expected = Files.readString(SNAPSHOT_PATH, StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        assertThat(semantic(mapper.readTree(actual)))
                .as(
                        "OpenAPI spec drifted from committed snapshot. To regenerate: ./gradlew"
                            + " :services:auth-service:auth-app:integrationTest --tests"
                            + " OpenApiSnapshotIT -Popenapi.snapshot.update=true")
                .isEqualTo(semantic(mapper.readTree(expected)));
    }

    private String fetchAndNormalize() throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(response.getBody());
        assertThat(root.path("openapi").asText()).startsWith("3.");
        assertThat(root.path("paths").isObject()).isTrue();
        assertThat(root.path("paths").isEmpty()).isFalse();
        assertThat(root.path("components").path("schemas").isObject()).isTrue();
        root.path("paths")
                .fieldNames()
                .forEachRemaining(
                        path ->
                                assertThat(path)
                                        .as(
                                                "Internal routes must stay outside the public"
                                                    + " OpenAPI contract")
                                        .doesNotStartWith("/internal/"));
        // springdoc включает `servers: [{url: http://localhost:<random>}]` — волатильно,
        // RANDOM_PORT меняется каждый запуск. Снимаем из snapshot.
        root.remove("servers");

        ObjectWriter writer =
                mapper.writer()
                        .with(SerializationFeature.INDENT_OUTPUT)
                        .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        return writer.writeValueAsString(root) + "\n";
    }

    private static com.fasterxml.jackson.databind.JsonNode semantic(
            com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            // Only documentation metadata is ignored; all schema and security keywords remain.
            for (String metadata : java.util.List.of("summary", "description")) {
                if (copy.path(metadata).isTextual()) copy.remove(metadata);
            }
            if (copy.path("externalDocs").has("url")) copy.remove("externalDocs");
            copy.fields()
                    .forEachRemaining(
                            entry -> copy.set(entry.getKey(), semantic(entry.getValue())));
            return copy;
        }
        if (node.isArray()) {
            var copy = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> copy.add(semantic(value)));
            return copy;
        }
        return node;
    }
}
