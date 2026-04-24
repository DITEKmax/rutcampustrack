package ru.rutcampustrack.attendance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * M11 G3: OpenAPI ↔ runtime conformance check. См. academic copy для docs.
 *
 * <p>Attendance использует {@link org.springframework.test.web.servlet.MockMvc}
 * из {@link AbstractAttendanceIntegrationTest} (TestRestTemplate не настроен).
 */
class OpenApiSnapshotIT extends AbstractAttendanceIntegrationTest {

    private static final String SERVICE_NAME = "attendance";
    private static final Path SNAPSHOT_PATH = Paths.get("../../..")
            .resolve("docs").resolve("api-spec").resolve(SERVICE_NAME + ".json")
            .toAbsolutePath().normalize();

    @Test
    @DisplayName("M11 G3: /api-docs matches committed snapshot")
    void apiDocsMatchesSnapshot() throws Exception {
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
                        "To regenerate: ./gradlew :services:attendance-service:attendance-app:integrationTest " +
                        "--tests OpenApiSnapshotIT -Dopenapi.snapshot.update=true")
                .isEqualTo(expected);
    }

    private String fetchAndNormalize() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs")).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).isNotBlank();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(body);
        root.remove("servers");

        ObjectWriter writer = mapper.writer()
                .with(SerializationFeature.INDENT_OUTPUT)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        return writer.writeValueAsString(root) + "\n";
    }
}
