package ru.rutcampustrack.mobilebff.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.Format;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.FormatSlot;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.Plan;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.FormatState;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentMapModelsWireContractTest {

    private static final String SHA256 = "a".repeat(64);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readySlotUsesIdAndLowercaseWireValues() throws Exception {
        var slot = new FormatSlot(
                Format.png,
                FormatState.ready,
                "image/png",
                "42",
                128,
                SHA256,
                640,
                480,
                List.of(0d, 0d, 640d, 480d));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(slot));

        assertThat(json.get("format").asText()).isEqualTo("png");
        assertThat(json.get("state").asText()).isEqualTo("ready");
        assertThat(json.get("id").asText()).isEqualTo("42");
        assertThat(json.has("assetId")).isFalse();
        assertThat(json.get("bytes").asLong()).isEqualTo(128L);
        assertThat(json.get("sha256").asText()).isEqualTo(SHA256);
        assertThat(json.get("width").asInt()).isEqualTo(640);
        assertThat(json.get("height").asInt()).isEqualTo(480);
        assertThat(json.get("viewBox").size()).isEqualTo(4);
    }

    @Test
    void everyNonReadyStateKeepsExplicitNullsAndOmitsOptionalDimensions() throws Exception {
        for (var state : List.of(FormatState.absent, FormatState.processing, FormatState.failed)) {
            var slot = new FormatSlot(
                    Format.svg,
                    state,
                    "image/svg+xml",
                    null,
                    0,
                    null,
                    null,
                    null,
                    null);

            JsonNode json = mapper.readTree(mapper.writeValueAsString(slot));

            assertThat(json.get("format").asText()).isEqualTo("svg");
            assertThat(json.get("state").asText()).isEqualTo(state.name());
            assertThat(json.has("id")).isTrue();
            assertThat(json.get("id").isNull()).isTrue();
            assertThat(json.has("sha256")).isTrue();
            assertThat(json.get("sha256").isNull()).isTrue();
            assertThat(json.get("bytes").asLong()).isZero();
            assertThat(json.has("width")).isFalse();
            assertThat(json.has("height")).isFalse();
            assertThat(json.has("viewBox")).isFalse();
            assertThat(json.has("assetId")).isFalse();
        }
    }

    @Test
    void nonReadyPayloadRejectsIdShaAndPositiveBytes() {
        assertThatThrownBy(() -> slot(FormatState.absent, "invalid-id", 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> slot(FormatState.processing, null, 0, "sha"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> slot(FormatState.failed, null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumValueOfUsesLowercaseContractValues() {
        assertThat(Enum.valueOf(Format.class, "png")).isSameAs(Format.png);
        assertThat(Enum.valueOf(Format.class, "svg")).isSameAs(Format.svg);
        assertThat(Enum.valueOf(FormatState.class, "absent")).isSameAs(FormatState.absent);
        assertThat(Enum.valueOf(FormatState.class, "processing")).isSameAs(FormatState.processing);
        assertThat(Enum.valueOf(FormatState.class, "ready")).isSameAs(FormatState.ready);
        assertThat(Enum.valueOf(FormatState.class, "failed")).isSameAs(FormatState.failed);

        assertThatThrownBy(() -> Enum.valueOf(Format.class, "PNG"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Enum.valueOf(FormatState.class, "ABSENT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatSlotRequiresExactMimeTypeForEachFormat() {
        assertThatThrownBy(() -> readySlot(Format.png, "image/PNG", SHA256))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> readySlot(Format.svg, "image/png", SHA256))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readySlotRequiresCanonicalLowercaseSha256() {
        assertThatThrownBy(() -> readySlot(Format.png, "image/png", "A".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> readySlot(Format.png, "image/png", "g".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> readySlot(Format.png, "image/png", "a".repeat(63)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void planRequiresFormatSpecificSlotsInTheirFields() {
        FormatSlot png = readySlot(Format.png, "image/png", SHA256);
        FormatSlot svg = readySlot(Format.svg, "image/svg+xml", SHA256);

        assertThatThrownBy(() -> new Plan("1", "2", "3", "Floor", svg, svg))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plan("1", "2", "3", "Floor", png, png))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FormatSlot readySlot(Format format, String contentType, String sha256) {
        return new FormatSlot(
                format,
                FormatState.ready,
                contentType,
                "42",
                128,
                sha256,
                null,
                null,
                null);
    }

    private static FormatSlot slot(FormatState state, String id, long bytes, String sha256) {
        return new FormatSlot(
                Format.png,
                state,
                "image/png",
                id,
                bytes,
                sha256,
                null,
                null,
                null);
    }
}
