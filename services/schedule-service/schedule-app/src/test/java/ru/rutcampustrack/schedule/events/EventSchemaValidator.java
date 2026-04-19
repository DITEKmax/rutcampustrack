package ru.rutcampustrack.schedule.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

/**
 * M02 Группа 8 — валидатор event payload'а против JSON Schema из
 * {@code event-schemas/*.json}. Использует networknt, резолвит
 * {@code $ref: "_common.json#/..."} автоматически (sibling-файл из той же
 * директории).
 *
 * <p>Используется contract-тестами этого сервиса ({@code *ContractIT}).
 */
public final class EventSchemaValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @return путь к {@code event-schemas/} относительно корня репо
     *   (services/schedule-service/schedule-app → ../../../event-schemas).
     */
    public static Path schemasDir() {
        return Path.of("").toAbsolutePath()
                .getParent()   // services/schedule-service
                .getParent()   // services
                .getParent()   // repo root
                .resolve("event-schemas");
    }

    /** Загружает schema по имени (например {@code "lesson.started.json"}). */
    public static JsonSchema loadSchema(String filename) {
        URI uri = schemasDir().resolve(filename).toUri();
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(uri);
    }

    /**
     * Валидирует JSON payload (string) против schema. Возвращает empty set
     * если payload валиден, иначе — violations.
     */
    public static Set<ValidationMessage> validate(String schemaFilename, String jsonPayload) throws IOException {
        JsonSchema schema = loadSchema(schemaFilename);
        JsonNode node = MAPPER.readTree(jsonPayload);
        return schema.validate(node);
    }

    private EventSchemaValidator() {
    }
}
