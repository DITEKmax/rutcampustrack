package ru.rutcampustrack.shared.web.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonConfigTest {

    private final JacksonConfig config = new JacksonConfig();

    private ObjectMapper buildMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(new JavaTimeModule());
        Jackson2ObjectMapperBuilderCustomizer customizer = config.sharedJacksonCustomizer();
        customizer.customize(builder);
        return builder.build();
    }

    enum Role { ADMIN, TEACHER }

    record DtoWithEnum(Role role) {}

    record DtoWithDate(Instant at) {}

    record DtoWithFields(String name) {}

    @Test
    @DisplayName("Unknown enum → null (не кидает InvalidFormatException)")
    void readUnknownEnumAsNull() throws Exception {
        ObjectMapper mapper = buildMapper();
        DtoWithEnum dto = mapper.readValue("{\"role\":\"UNKNOWN_ROLE\"}", DtoWithEnum.class);
        assertThat(dto.role()).isNull();
    }

    @Test
    @DisplayName("Unknown property → игнорируется, не падает")
    void failOnUnknownPropertiesDisabled() {
        ObjectMapper mapper = buildMapper();
        assertThatNoException().isThrownBy(() ->
                mapper.readValue("{\"name\":\"x\",\"extraField\":42}", DtoWithFields.class));
    }

    @Test
    @DisplayName("Даты пишутся как ISO-8601 строки, не миллисекунды")
    void datesAsIsoStrings() throws Exception {
        ObjectMapper mapper = buildMapper();
        DtoWithDate dto = new DtoWithDate(Instant.parse("2026-04-19T10:00:00Z"));
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("2026-04-19T10:00:00Z").doesNotMatch(".*\"at\":\\d+.*");
    }

    @Test
    @DisplayName("READ_UNKNOWN_ENUM_VALUES_AS_NULL — включён")
    void featureFlagsAreSet() {
        ObjectMapper mapper = buildMapper();
        assertThat(mapper.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)).isTrue();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
    }

    @Test
    @DisplayName("Sanity: без customizer'а поведение отличается (регрессионная проверка)")
    void sanityWithoutCustomizer() {
        ObjectMapper plain = new ObjectMapper();
        assertThatThrownBy(() ->
                plain.readValue("{\"name\":\"x\",\"extraField\":42}", DtoWithFields.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
