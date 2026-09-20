package ru.rutcampustrack.mobilebff.student;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkCompletionRequest;

import java.io.IOException;

@Configuration
class HomeworkJsonConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    Jackson2ObjectMapperBuilderCustomizer strictHomeworkCompletionBoolean() {
        return builder -> builder.mixIn(HomeworkCompletionRequest.class, HomeworkCompletionRequestMixin.class);
    }

    abstract static class HomeworkCompletionRequestMixin {
        @JsonDeserialize(using = StrictBooleanDeserializer.class)
        abstract Boolean completed();
    }

    static final class StrictBooleanDeserializer extends JsonDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_TRUE)) {
                return Boolean.TRUE;
            }
            if (parser.hasToken(JsonToken.VALUE_FALSE)) {
                return Boolean.FALSE;
            }
            throw InvalidFormatException.from(parser,
                    "completed must be a JSON boolean", parser.getText(), Boolean.class);
        }
    }
}
