package ru.rutcampustrack.mobilebff.student;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinDiagnostics;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.GeoCompletionReason;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.GeoDiagnosticSource;

import java.io.IOException;
import java.time.Instant;

/** Keep the optional diagnostics JSON consistent with its enum, integer, and ISO instant schema. */
@Configuration
public class CheckinJsonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictCheckinDiagnostics() {
        return builder -> builder.mixIn(CheckinDiagnostics.class, DiagnosticsMixin.class);
    }

    @JsonDeserialize(using = DiagnosticsDeserializer.class)
    abstract static class DiagnosticsMixin {}

    static final class DiagnosticsDeserializer extends JsonDeserializer<CheckinDiagnostics> {
        @Override
        public CheckinDiagnostics deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            try {
                if (!node.isObject()) throw new IllegalArgumentException();
                return new CheckinDiagnostics(
                        GeoDiagnosticSource.valueOf(text(node.get("source"), false)),
                        accuracy(node.get("accuracyMeters")),
                        instant(node.get("fixTimestamp")),
                        instant(node.get("receivedAt")),
                        integer(node.get("sampleCount")),
                        integer(node.get("acquisitionDurationMs")),
                        node.has("completionReason")
                                ? GeoCompletionReason.valueOf(
                                        text(node.get("completionReason"), false))
                                : null);
            } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
                throw InvalidFormatException.from(
                        parser,
                        "Некорректная диагностика геолокации",
                        null,
                        CheckinDiagnostics.class);
            }
        }

        private static String text(JsonNode value, boolean nullable) {
            if (nullable && (value == null || value.isNull())) return null;
            if (value == null || !value.isTextual()) throw new IllegalArgumentException();
            return value.textValue();
        }

        private static Instant instant(JsonNode value) {
            String text = text(value, true);
            return text == null ? null : Instant.parse(text);
        }

        private static Double accuracy(JsonNode value) {
            if (value == null || value.isNull()) return null;
            if (!value.isNumber() || !Double.isFinite(value.doubleValue()))
                throw new IllegalArgumentException();
            return value.doubleValue();
        }

        private static Integer integer(JsonNode value) {
            if (value == null) return null;
            if (!value.isIntegralNumber() || !value.canConvertToInt())
                throw new IllegalArgumentException();
            return value.intValue();
        }
    }
}
