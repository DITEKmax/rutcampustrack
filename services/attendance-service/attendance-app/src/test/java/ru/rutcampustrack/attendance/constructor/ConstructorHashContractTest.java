package ru.rutcampustrack.attendance.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ConstructorHashContractTest {
    @Test void actualSharedFixtureMatchesScheduleIncludingBsonNumericNormalization() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String explicit = System.getProperty("constructor.hash.fixtures");
        Path fixture = explicit == null ? null : Path.of(explicit);
        while (fixture == null && root != null) {
            Path candidate = root.resolve("proto/fixtures/constructor-hashes-v1.json");
            if (Files.isRegularFile(candidate)) fixture = candidate;
            root = root.getParent();
        }
        assertThat(fixture).as("frozen producer fixture is required").isNotNull();
        var json = new ObjectMapper();
        for (var vector : json.readTree(fixture.toFile()).get("vectors")) {
            Object value = json.readValue(vector.get("inputJson").asText(), Object.class);
            assertThat(ConstructorHashes.canonical(value)).isEqualTo(vector.get("canonical").asText());
            assertThat(HexFormat.of().formatHex(ConstructorHashes.hash(value).toByteArray())).isEqualTo(vector.get("sha256").asText());
        }
    }
}
