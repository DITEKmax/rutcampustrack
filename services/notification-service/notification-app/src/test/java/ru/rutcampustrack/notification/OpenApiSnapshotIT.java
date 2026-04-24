package ru.rutcampustrack.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M11 G3: OpenAPI ↔ runtime conformance check for notification-web.
 *
 * <p>notification-app использует дефолтный springdoc path
 * {@code /v3/api-docs} (остальные 3 сервиса переопределяют на {@code /api-docs}).
 * Тест запрашивает оба — сохраняет тот, который ответил 200.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv"
})
class OpenApiSnapshotIT extends ContainerTestBase {

    private static final String SERVICE_NAME = "notification";
    private static final Path SNAPSHOT_PATH = Paths.get("../../..")
            .resolve("docs").resolve("openapi").resolve(SERVICE_NAME + ".json")
            .toAbsolutePath().normalize();

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(
                    OpenApiSnapshotIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    TestRestTemplate restTemplate;

    /** Mock PushService — иначе WebPushConfig парсит тестовые VAPID ключи
     *  через BouncyCastle ECCurve, которые не валидны для Base64URL decode. */
    @MockitoBean
    nl.martijndwars.webpush.PushService pushService;

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
                        "To regenerate: ./gradlew :services:notification-service:notification-app:test " +
                        "--tests OpenApiSnapshotIT -Dopenapi.snapshot.update=true")
                .isEqualTo(expected);
    }

    private String fetchAndNormalize() throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        if (response.getStatusCode().isError()) {
            response = restTemplate.getForEntity("/api-docs", String.class);
        }
        assertThat(response.getBody()).isNotNull();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(response.getBody());
        root.remove("servers");

        ObjectWriter writer = mapper.writer()
                .with(SerializationFeature.INDENT_OUTPUT)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        return writer.writeValueAsString(root) + "\n";
    }
}
