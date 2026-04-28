package ru.rutcampustrack.schedule.config;

import com.fasterxml.jackson.databind.MapperFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCaseInsensitiveEnums() {
        return builder -> builder.featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverterFactory(new StringToEnumConverterFactory());
    }

    private static final class StringToEnumConverterFactory
            implements ConverterFactory<String, Enum<?>> {

        @Override
        @NonNull
        @SuppressWarnings({"rawtypes", "unchecked"})
        public <T extends Enum<?>> Converter<String, T> getConverter(@NonNull Class<T> targetType) {
            return new StringToEnum(targetType);
        }

        private static final class StringToEnum<T extends Enum<T>> implements Converter<String, T> {
            private final Class<T> enumType;

            StringToEnum(Class<?> enumType) {
                @SuppressWarnings("unchecked")
                Class<T> t = (Class<T>) enumType;
                this.enumType = t;
            }

            @Override
            public T convert(@NonNull String source) {
                if (source.isBlank()) {
                    return null;
                }
                return Enum.valueOf(enumType, source.trim().toUpperCase());
            }
        }
    }
}
