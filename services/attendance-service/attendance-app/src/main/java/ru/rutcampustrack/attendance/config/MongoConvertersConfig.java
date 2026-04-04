package ru.rutcampustrack.attendance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.util.List;

/**
 * MongoDB custom converters for enum serialization as lowercase strings (INFRA-02).
 * Declared in a separate class from MongoConfig to avoid circular dependency:
 * MongoCustomConversions is needed to create MongoTemplate, so it must NOT be
 * declared in a class that also injects MongoTemplate.
 */
@Configuration
public class MongoConvertersConfig {

    // INFRA-02: Enum serialization as lowercase strings
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new AttendanceStatusWriter(),
                new AttendanceStatusReader(),
                new AttendanceSourceWriter(),
                new AttendanceSourceReader()
        ));
    }

    @WritingConverter
    static class AttendanceStatusWriter implements Converter<AttendanceStatus, String> {
        @Override
        public String convert(AttendanceStatus source) {
            return source.name().toLowerCase();
        }
    }

    @ReadingConverter
    static class AttendanceStatusReader implements Converter<String, AttendanceStatus> {
        @Override
        public AttendanceStatus convert(String source) {
            return AttendanceStatus.valueOf(source.toUpperCase());
        }
    }

    @WritingConverter
    static class AttendanceSourceWriter implements Converter<AttendanceSource, String> {
        @Override
        public String convert(AttendanceSource source) {
            return source.name().toLowerCase();
        }
    }

    @ReadingConverter
    static class AttendanceSourceReader implements Converter<String, AttendanceSource> {
        @Override
        public AttendanceSource convert(String source) {
            return AttendanceSource.valueOf(source.toUpperCase());
        }
    }
}
