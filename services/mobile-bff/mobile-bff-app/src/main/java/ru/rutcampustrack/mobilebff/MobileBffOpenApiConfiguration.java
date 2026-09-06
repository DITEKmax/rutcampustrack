package ru.rutcampustrack.mobilebff;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps Springdoc's polymorphic geo model acyclic.
 *
 * <p>Swagger's inheritance converter otherwise emits
 * GeoInput.oneOf -> CoordinatesGeo.allOf -> GeoInput. The runtime Java model
 * remains sealed and Jackson-discriminated; only the derived OpenAPI shape is
 * normalized to the equivalent flat discriminated union expected by clients.
 */
@Configuration(proxyBeanMethods = false)
class MobileBffOpenApiConfiguration {

    private static final String COORDINATES = "CoordinatesGeo";
    private static final String UNAVAILABLE = "UnavailableGeo";

    @Bean
    OpenApiCustomizer flattenGeoInputSchemas() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null || components.getSchemas() == null) {
                return;
            }

            flattenConcreteSchema(components, COORDINATES, "COORDINATES");
            flattenConcreteSchema(components, UNAVAILABLE, "UNAVAILABLE");
            wrapNullableReference(components, "StudentSession", "group");
            wrapNullableReference(components, "StudentSession", "semester");
            wrapNullableReference(components, "TodayLesson", "attendance");
            wrapNullableReference(components, "TodayLesson", "request");
            wrapNullableReference(components, "StudentCheckinAck", "attendance");
            wrapNullableReference(components, "StudentCheckinAck", "request");

            Schema<?> union = components.getSchemas().get("GeoInput");
            if (union == null) {
                throw new IllegalStateException("Springdoc did not export GeoInput");
            }
            union.setProperties(null);
            union.setRequired(null);
            union.setDiscriminator(new Discriminator()
                    .propertyName("kind")
                    .mapping("COORDINATES", "#/components/schemas/" + COORDINATES)
                    .mapping("UNAVAILABLE", "#/components/schemas/" + UNAVAILABLE));
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void flattenConcreteSchema(Components components, String name, String discriminatorValue) {
        Schema schema = components.getSchemas().get(name);
        if (schema == null) {
            throw new IllegalStateException("Springdoc did not export " + name);
        }

        List<Schema> allOf = schema.getAllOf();
        if (allOf == null) {
            return;
        }
        Schema inline = allOf.stream()
                .filter(candidate -> candidate.get$ref() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        name + " has no inline schema to flatten"));

        Map<String, Schema> properties = new LinkedHashMap<>();
        if (inline.getProperties() != null) {
            properties.putAll(inline.getProperties());
        }
        Schema kind = properties.get("kind");
        if (kind == null) {
            throw new IllegalStateException(name + " has no kind discriminator property");
        }
        kind.setEnum(List.of(discriminatorValue));
        ObjectSchema flattened = new ObjectSchema();
        flattened.setDescription(schema.getDescription());
        flattened.setProperties(properties);
        flattened.setRequired(schema.getRequired());
        components.addSchemas(name, flattened);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void wrapNullableReference(Components components, String schemaName, String propertyName) {
        Schema parent = components.getSchemas().get(schemaName);
        if (parent == null || parent.getProperties() == null) {
            throw new IllegalStateException("Springdoc did not export " + schemaName);
        }
        Schema property = (Schema) parent.getProperties().get(propertyName);
        if (property == null || property.get$ref() == null) {
            throw new IllegalStateException(
                    "Springdoc did not export " + schemaName + "." + propertyName + " as a reference");
        }

        Schema reference = new Schema().$ref(property.get$ref());
        ComposedSchema nullableReference = new ComposedSchema();
        nullableReference.setNullable(true);
        nullableReference.addAllOfItem(reference);
        parent.getProperties().put(propertyName, nullableReference);
    }
}
