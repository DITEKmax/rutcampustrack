package ru.rutcampustrack.attendance.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.util.List;

@Configuration
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    public MongoConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("attendances");

        // INFRA-01: Unique compound index — idempotency for checkins
        ops.ensureIndex(new Index()
                .on("lesson_id", Sort.Direction.ASC)
                .on("user_id", Sort.Direction.ASC)
                .unique()
                .named("uniq_lesson_user"));

        // Query: student attendance per semester (RPRT-03, RPRT-04)
        ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("semester_id", Sort.Direction.ASC)
                .on("lesson_date", Sort.Direction.DESC)
                .named("idx_user_semester_date"));

        // Query: group journal (RPRT-01, RPRT-02)
        ops.ensureIndex(new Index()
                .on("group_id", Sort.Direction.ASC)
                .on("semester_id", Sort.Direction.ASC)
                .on("subject_id", Sort.Direction.ASC)
                .named("idx_group_semester_subject"));

        // Query: all records for a lesson (auto-absent, lesson view)
        ops.ensureIndex(new Index()
                .on("lesson_id", Sort.Direction.ASC)
                .named("idx_lesson_id"));
    }

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
