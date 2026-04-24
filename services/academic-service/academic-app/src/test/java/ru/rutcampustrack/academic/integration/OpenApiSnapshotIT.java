package ru.rutcampustrack.academic.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M11 G3: OpenAPI ↔ runtime conformance check.
 *
 * <p>Тест GET'ает {@code /api-docs} у running app и сравнивает с
 * committed snapshot'ом в {@code docs/api-spec/academic.json}. При
 * расхождении падает с инструкцией обновить snapshot через
 * {@code -Dopenapi.snapshot.update=true}.
 *
 * <p>Цель: блокировать accidental breaking changes в OpenAPI —
 * удаление endpoint'ов, изменение типов, удаление обязательных полей.
 * CI step oasdiff делает deeper semantic diff поверх того же snapshot'а.
 */
class OpenApiSnapshotIT extends AbstractAcademicIntegrationTest {

    private static final String SERVICE_NAME = "academic";
    // CWD при запуске gradle test — services/academic-service/academic-app/,
    // repo root = ../../../ относительно неё.
    private static final Path SNAPSHOT_PATH = Paths.get("../../..")
            .resolve("docs").resolve("api-spec").resolve(SERVICE_NAME + ".json")
            .toAbsolutePath().normalize();

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @DisplayName("M11 G3: /api-docs matches committed snapshot")
    void apiDocsMatchesSnapshot() throws IOException {
        String actual = fetchAndNormalize();

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.createDirectories(SNAPSHOT_PATH.getParent());
            Files.writeString(SNAPSHOT_PATH, actual, StandardCharsets.UTF_8);
            System.out.println("[openapi-snapshot] Updated " + SNAPSHOT_PATH);
            return;
        }

        assertThat(SNAPSHOT_PATH)
                .as("Snapshot file missing — run with -Dopenapi.snapshot.update=true to create %s",
                        SNAPSHOT_PATH)
                .exists();

        String expected = Files.readString(SNAPSHOT_PATH, StandardCharsets.UTF_8);
        assertThat(actual)
                .as("OpenAPI spec drifted from committed snapshot. " +
                        "To regenerate: ./gradlew :services:academic-service:academic-app:integrationTest " +
                        "--tests OpenApiSnapshotIT -Dopenapi.snapshot.update=true")
                .isEqualTo(expected);
    }

    private String fetchAndNormalize() throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-docs", String.class);
        assertThat(response.getBody()).isNotNull();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(response.getBody());
        // springdoc включает `servers: [{url: http://localhost:<random>}]` — волатильно,
        // RANDOM_PORT меняется каждый запуск. Снимаем из snapshot.
        root.remove("servers");

        ObjectWriter writer = mapper.writer()
                .with(SerializationFeature.INDENT_OUTPUT)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        return writer.writeValueAsString(root) + "\n";
    }
}
