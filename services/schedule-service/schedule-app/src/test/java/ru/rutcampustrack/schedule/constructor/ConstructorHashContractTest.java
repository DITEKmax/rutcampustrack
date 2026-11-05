package ru.rutcampustrack.schedule.constructor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConstructorHashContractTest {
    @Test
    void canonicalJsonMatchesSharedNumericAndImmutableSnapshotVectors() throws Exception {
        var json = new ObjectMapper();
        try (var input = getClass().getResourceAsStream("/constructor-hashes-v1.json")) {
            assertThat(input).isNotNull();
            for (var vector : json.readTree(input).get("vectors")) {
                var value = json.readTree(vector.get("inputJson").asText());
                assertThat(ConstructorHashes.canonical(value))
                        .as(vector.get("name").asText())
                        .isEqualTo(vector.get("canonical").asText());
                assertThat(ConstructorHashes.hex(ConstructorHashes.json(value)))
                        .as(vector.get("name").asText())
                        .isEqualTo(vector.get("sha256").asText());
            }
        }
    }
}
