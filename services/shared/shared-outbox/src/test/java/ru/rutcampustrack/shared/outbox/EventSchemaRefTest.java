package ru.rutcampustrack.shared.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M02 Группа 7 — networknt/json-schema-validator понимает относительный
 * {@code $ref: "_common.json#/$defs/..."} из event-schemas/*.json.
 *
 * <p>networknt резолвит relative-refs относительно baseURI загруженной
 * схемы — мы передаём {@code file://.../event-schemas/lesson.started.json}
 * и библиотека сама загрузит {@code _common.json} из той же директории.
 * Никаких дополнительных URI-mappings не требуется.
 */
class EventSchemaRefTest {

    /**
     * event-schemas/ в корне репо. Тест запускается из
     * services/shared/shared-outbox/ → подъём на 3 уровня.
     */
    private Path schemasDir() {
        return Path.of("").toAbsolutePath()
                .getParent()   // services/shared
                .getParent()   // services
                .getParent()   // repo root
                .resolve("event-schemas");
    }

    private JsonSchema loadSchema(String filename) {
        URI schemaUri = schemasDir().resolve(filename).toUri();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        return factory.getSchema(schemaUri);
    }

    @Test
    void lessonStarted_validPayload_passesValidation() throws Exception {
        JsonSchema schema = loadSchema("lesson.started.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree("""
                {
                  "event_type": "lesson.started",
                  "event_id": "550e8400-e29b-41d4-a716-446655440000",
                  "occurred_at": "2026-04-19T12:00:00Z",
                  "payload": {
                    "lesson_id": 1,
                    "group_id": 10,
                    "subject_id": 100,
                    "lesson_number": 3,
                    "start_time": "10:10:00",
                    "end_time": "11:45:00",
                    "room": "A-201"
                  }
                }
                """);

        Set<ValidationMessage> errors = schema.validate(json);
        assertThat(errors).as("valid payload must pass").isEmpty();
    }

    @Test
    void lessonStarted_invalidLessonNumber_9_failsViaCommonRef() throws Exception {
        // lesson_number=9 нарушает $defs/lessonNumber.maximum=8.
        // Если $ref не резолвится — validator либо падает с unresolved ref
        // (тест упадёт), либо пропускает ограничение (и тест упадёт по
        // isNotEmpty).
        JsonSchema schema = loadSchema("lesson.started.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree("""
                {
                  "event_type": "lesson.started",
                  "event_id": "550e8400-e29b-41d4-a716-446655440000",
                  "occurred_at": "2026-04-19T12:00:00Z",
                  "payload": {
                    "lesson_id": 1,
                    "group_id": 10,
                    "subject_id": 100,
                    "lesson_number": 9,
                    "start_time": "10:10:00",
                    "end_time": "11:45:00"
                  }
                }
                """);

        Set<ValidationMessage> errors = schema.validate(json);
        assertThat(errors)
                .as("lesson_number=9 must violate _common.json#/$defs/lessonNumber.maximum")
                .isNotEmpty();
    }

    @Test
    void commonJson_declaresExpectedDefs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(schemasDir().resolve("_common.json").toFile());
        JsonNode defs = root.get("$defs");
        assertThat(defs).isNotNull();
        assertThat(defs.has("eventId")).isTrue();
        assertThat(defs.has("occurredAt")).isTrue();
        assertThat(defs.has("traceId")).isTrue();
        assertThat(defs.has("eventVersion")).isTrue();
        assertThat(defs.has("lessonNumber")).isTrue();
    }
}
