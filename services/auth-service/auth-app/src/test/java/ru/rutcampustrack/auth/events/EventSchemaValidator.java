package ru.rutcampustrack.auth.events;

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
 * M09 G2 — auth-аналог academic/schedule/attendance EventSchemaValidator.
 * Auth-service лежит на уровне {@code services/auth-service}, поэтому 2
 * уровня вверх до repo root (academic/schedule/attendance — 3 уровня).
 */
public final class EventSchemaValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Path schemasDir() {
        return Path.of("").toAbsolutePath()
                .getParent()   // services
                .getParent()   // repo root
                .resolve("event-schemas");
    }

    public static JsonSchema loadSchema(String filename) {
        URI uri = schemasDir().resolve(filename).toUri();
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(uri);
    }

    public static Set<ValidationMessage> validate(String schemaFilename, String jsonPayload) throws IOException {
        JsonSchema schema = loadSchema(schemaFilename);
        JsonNode node = MAPPER.readTree(jsonPayload);
        return schema.validate(node);
    }

    private EventSchemaValidator() {
    }
}
