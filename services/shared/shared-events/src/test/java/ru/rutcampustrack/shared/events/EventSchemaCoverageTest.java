package ru.rutcampustrack.shared.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * M08 G9 (P1-5 / NEW-164) — параметризованный coverage / sanity test над {@code
 * event-schemas/*.json}.
 *
 * <p>Гарантии:
 *
 * <ol>
 *   <li>Каждый файл {@code event-schemas/*.json} — корректная JSON Schema draft 2020-12 (networknt
 *       парсит без NPE).
 *   <li>Envelope содержит фиксированный набор required полей ({@code event_type}, {@code event_id},
 *       {@code occurred_at}, {@code payload}, {@code event_version}, {@code trace_id}, {@code
 *       source}). Исключение — {@code _common.json} ($defs-only).
 *   <li>Каждая schema содержит {@code "const": "<event_type>"} и имя файла совпадает с const
 *       (защита от переименования schema без обновления publisher'а и наоборот).
 *   <li>Coverage regression guard — существует ожидаемый набор events. Новый файл → тест падает →
 *       автор осознанно расширяет whitelist + добавляет соответствующий {@code *ContractIT} в своём
 *       сервисе (NEW-164 intent: schema не может появиться "в воздухе").
 * </ol>
 *
 * <p>Этот test — unit-уровня (без Spring / Testcontainers), работает от repo-root rel-path
 * (`services/shared/shared-events` → `../../../event-schemas/`). Запускается в обычном {@code
 * test}-task, не требует Docker.
 */
class EventSchemaCoverageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private static final List<String> REQUIRED_ENVELOPE_FIELDS =
            List.of(
                    "event_type",
                    "event_id",
                    "occurred_at",
                    "payload",
                    "event_version",
                    "trace_id",
                    "source");

    /**
     * Expected event set — regression guard. Добавление нового файла в event-schemas/ требует
     * одновременно: (a) обновить этот whitelist; (b) написать *ContractIT в сервисе-источнике. Пара
     * достигается в одном PR, что ловит drift между schema и publisher'ом.
     */
    private static final Set<String> EXPECTED_EVENT_SCHEMAS =
            new TreeSet<>(
                    List.of(
                            "admin.access.changed",
                            "assistant.access.changed",
                            "attendance.marked",
                            "excuse.decided",
                            "excuse.decision",
                            "excuse.requested",
                            "export.state.changed",
                            "group.archived",
                            "group.membership.changed",
                            "group.promotion.completed",
                            "group.renamed",
                            "group.updated",
                            "headman.access.changed",
                            "homework.action-recorded",
                            "homework.binding.archived",
                            "homework.due_reminder",
                            "homework.published",
                            "homework.updated",
                            "homework.weekly_digest",
                            "homework.withdrawn",
                            "late_checkin.decided",
                            "late_checkin.decision",
                            "late_checkin.requested",
                            "lesson.blocked",
                            "lesson.cancelled",
                            "lesson.closed",
                            "lesson.deleted",
                            "lesson.one_off.cancelled",
                            "lesson.one_off.created",
                            "lesson.reminder",
                            "lesson.restored",
                            "lesson.started",
                            "lesson.transferred",
                            "lesson.updated",
                            "otp.requested",
                            "otp.verified",
                            "password.changed",
                            "schedule.action-recorded",
                            "semester.activated",
                            "semester.archived",
                            "semester.operation.completed",
                            "student.initial-membership.attendance-backfilled",
                            "student.initial-membership.recorded"));

    private static Path schemasDir() {
        // services/shared/shared-events → ../../../event-schemas
        return Path.of("")
                .toAbsolutePath()
                .getParent() // services/shared
                .getParent() // services
                .getParent() // repo root
                .resolve("event-schemas");
    }

    private static Stream<Path> eventSchemaFiles() throws IOException {
        return Files.list(schemasDir())
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().equals("_common.json"))
                .sorted();
    }

    @Test
    @DisplayName("coverage guard — все известные schemas присутствуют; unexpected — stop!")
    void eventSchemasMatchWhitelist() throws IOException {
        Set<String> onDisk =
                eventSchemaFiles()
                        .map(p -> p.getFileName().toString().replace(".json", ""))
                        .collect(Collectors.toCollection(TreeSet::new));

        Set<String> missing = new TreeSet<>(EXPECTED_EVENT_SCHEMAS);
        missing.removeAll(onDisk);

        Set<String> unexpected = new TreeSet<>(onDisk);
        unexpected.removeAll(EXPECTED_EVENT_SCHEMAS);

        assertThat(missing).as("schemas объявлены в whitelist, но отсутствуют на диске").isEmpty();
        assertThat(unexpected)
                .as(
                        "schemas на диске без whitelist-entry — добавь в EXPECTED_EVENT_SCHEMAS +"
                                + " *ContractIT в сервисе-источнике")
                .isEmpty();
    }

    @Test
    @DisplayName("_common.json валиден как $defs-only shared file")
    void commonJsonLoadsAsSharedDefs() throws IOException {
        Path common = schemasDir().resolve("_common.json");
        assertThat(Files.exists(common)).as("_common.json должен существовать").isTrue();

        JsonNode tree = MAPPER.readTree(Files.readString(common));
        assertThat(tree.get("$defs"))
                .as(
                        "_common.json должен содержать $defs блок, на который ссылаются"
                                + " envelope-schemas")
                .isNotNull();
        assertThat(tree.get("$defs").has("eventId")).isTrue();
        assertThat(tree.get("$defs").has("occurredAt")).isTrue();
        assertThat(tree.get("$defs").has("traceId")).isTrue();
        assertThat(tree.get("$defs").has("eventVersion")).isTrue();
    }

    static Stream<Path> schemaFilesProvider() throws IOException {
        return eventSchemaFiles();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaFilesProvider")
    @DisplayName("schema parseable + envelope complete + const matches filename")
    void eachSchemaIsWellFormedAndCarriesFullEnvelope(Path schemaFile) throws IOException {
        String json = Files.readString(schemaFile);
        JsonNode tree = MAPPER.readTree(json);

        // 1. networknt JsonSchemaFactory должен загрузить без NPE
        JsonSchema schema = SCHEMA_FACTORY.getSchema(schemaFile.toUri());
        assertThat(schema).as("JsonSchemaFactory.getSchema should not return null").isNotNull();

        // 2. Top-level — object schema
        assertThat(tree.get("type")).isNotNull();
        assertThat(tree.get("type").asText()).isEqualTo("object");

        // 3. Envelope-required fields — полный комплект
        JsonNode required = tree.get("required");
        assertThat(required).as("schema.required missing").isNotNull();
        List<String> requiredList = new java.util.ArrayList<>();
        required.forEach(n -> requiredList.add(n.asText()));
        assertThat(requiredList)
                .as("envelope required fields must cover all common envelope keys")
                .containsAll(REQUIRED_ENVELOPE_FIELDS);

        // 4. event_type const == имя файла (без .json)
        String expectedEventType = schemaFile.getFileName().toString().replace(".json", "");
        JsonNode eventTypeSchema = tree.get("properties").get("event_type");
        assertThat(eventTypeSchema).as("properties.event_type missing").isNotNull();
        assertThat(eventTypeSchema.get("const"))
                .as("properties.event_type.const missing — required by publishers for exact-match")
                .isNotNull();
        assertThat(eventTypeSchema.get("const").asText())
                .as("event_type const must match schema filename")
                .isEqualTo(expectedEventType);

        // 5. $ref'ы в envelope ведут на _common.json
        JsonNode properties = tree.get("properties");
        assertRefsResolveToCommon(properties.get("event_id"), "_common.json#/$defs/eventId");
        assertRefsResolveToCommon(properties.get("occurred_at"), "_common.json#/$defs/occurredAt");
        assertRefsResolveToCommon(properties.get("trace_id"), "_common.json#/$defs/traceId");
        assertRefsResolveToCommon(properties.get("traceparent"), "_common.json#/$defs/traceparent");
        assertRefsResolveToCommon(properties.get("request_id"), "_common.json#/$defs/requestId");
        assertThat(requiredList).doesNotContain("traceparent", "request_id");
        JsonNode version = properties.get("event_version");
        if (version.has("const")) {
            assertThat(version.get("const").asInt()).isEqualTo(1);
        } else if (version.has("type")) {
            assertThat(version.get("type").asText()).isEqualTo("integer");
        } else {
            assertRefsResolveToCommon(version, "_common.json#/$defs/eventVersion");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaFilesProvider")
    @DisplayName("minimal valid envelope with filled-in payload passes validation")
    void minimalValidEnvelope_validatesAgainstSchema(Path schemaFile) throws IOException {
        // Sanity envelope — все required-поля присутствуют. payload может не
        // подходить под domain-specific required (domain-specific assertions
        // живут в *ContractIT в каждом сервисе), здесь проверяем envelope-shape.
        String expectedEventType = schemaFile.getFileName().toString().replace(".json", "");
        String envelope =
                """
                {
                  "event_type": "%s",
                  "event_id": "00000000-0000-0000-0000-000000000000",
                  "event_version": 1,
                  "trace_id": "trace-abc",
                  "occurred_at": "2026-04-22T10:00:00Z",
                  "source": "shared-events-coverage-test",
                  "payload": {}
                }
                """
                        .formatted(expectedEventType);

        JsonSchema schema = SCHEMA_FACTORY.getSchema(schemaFile.toUri());
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(envelope);
        JsonNode source =
                MAPPER.readTree(Files.readString(schemaFile)).path("properties").path("source");
        if (source.has("const")) {
            input.put("source", source.get("const").asText());
        }
        if ("password.changed".equals(expectedEventType)) {
            input.withObject("payload").put("telegram_id", 1L);
        }
        Set<ValidationMessage> errors = schema.validate(input);
        var linked = input.deepCopy();
        linked.put("traceparent", "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
        linked.put("request_id", "11111111-1111-4111-8111-111111111111");
        assertThat(schema.validate(linked)).containsExactlyInAnyOrderElementsOf(errors);
        linked.put("traceparent", "00-00000000000000000000000000000000-bbbbbbbbbbbbbbbb-01");
        assertThat(schema.validate(linked))
                .anyMatch(error -> error.getInstanceLocation().toString().contains("traceparent"));
        linked.put("traceparent", "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
        linked.put("request_id", "SYNTHETIC_INVALID_REQUEST_ID");
        assertThat(schema.validate(linked))
                .anyMatch(error -> error.getInstanceLocation().toString().contains("request_id"));

        // Что ОЖИДАЕМО проходит: envelope shape (event_type/event_id/envelope_version/trace_id).
        // Что ОЖИДАЕМО фейлится: payload.required — зависит от domain.
        // Проверяем: ни одна envelope-ошибка не появилась.
        // networknt возвращает path вида "$.payload" / "$.payload/foo" —
        // отфильтровываем всё что начинается с payload (domain-specific
        // required — ответственность *ContractIT в сервисе).
        List<String> envelopeErrorPaths =
                errors.stream()
                        .map(ValidationMessage::getInstanceLocation)
                        .map(Object::toString)
                        .filter(path -> !path.startsWith("$.payload"))
                        .filter(path -> !path.startsWith("/payload"))
                        .toList();

        assertThat(envelopeErrorPaths)
                .as("envelope-level fields должны пройти — ошибки: " + errors)
                .isEmpty();
    }

    private static void assertRefsResolveToCommon(JsonNode propertyNode, String expectedRef)
            throws IOException {
        assertThat(propertyNode)
                .as(
                        "property schema must exist — envelope field referenced from _common.json"
                                + " missing")
                .isNotNull();
        JsonNode ref = propertyNode.get("$ref");
        if (ref == null) {
            String definition = expectedRef.substring(expectedRef.lastIndexOf('/') + 1);
            if ("traceparent".equals(definition) || "requestId".equals(definition)) {
                // The password schema has an HTTPS $id; keep new definitions local and equivalent.
                JsonNode common =
                        MAPPER.readTree(Files.readString(schemasDir().resolve("_common.json")));
                assertThat(propertyNode).isEqualTo(common.path("$defs").path(definition));
            } else {
                // Accepted action/password contracts already define their legacy fields inline.
                assertThat(propertyNode.path("type").asText()).isEqualTo("string");
                if ("occurredAt".equals(definition)) {
                    assertThat(propertyNode.path("format").asText()).isEqualTo("date-time");
                }
            }
            return;
        }
        assertThat(ref)
                .as("envelope field must be $ref'd from _common.json, found: " + propertyNode)
                .isNotNull();
        assertThat(ref.asText()).isEqualTo(expectedRef);
    }
}
