package ru.rutcampustrack.academic.contract.dto.export;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

/** Optional selectors must never turn an unknown filter into an unfiltered export. */
public final class StrictRegistryEnumDeserializer extends JsonDeserializer<Enum<?>> implements ContextualDeserializer {
    private final Class<? extends Enum> enumType;
    public StrictRegistryEnumDeserializer() { this.enumType = null; }
    private StrictRegistryEnumDeserializer(Class<? extends Enum> enumType) { this.enumType = enumType; }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        return new StrictRegistryEnumDeserializer(property.getType().getRawClass().asSubclass(Enum.class));
    }

    @Override
    public Enum<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (Enum<?>) context.handleUnexpectedToken(enumType, parser);
        }
        try {
            return Enum.valueOf(enumType, parser.getText());
        } catch (IllegalArgumentException unknown) {
            // Do not include an arbitrary rejected string in the response or exception message.
            return context.reportInputMismatch(enumType, "Unknown registry selector code");
        }
    }
}
