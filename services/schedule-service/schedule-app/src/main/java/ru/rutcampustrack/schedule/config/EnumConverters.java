package ru.rutcampustrack.schedule.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

public class EnumConverters {

    @Converter(autoApply = true)
    public static class WeekTypeConverter implements AttributeConverter<WeekType, String> {
        @Override
        public String convertToDatabaseColumn(WeekType attr) {
            return attr == null ? null : attr.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public WeekType convertToEntityAttribute(String db) {
            return db == null ? null : WeekType.valueOf(db.toUpperCase(Locale.ROOT));
        }
    }

    @Converter(autoApply = true)
    public static class LessonStatusConverter implements AttributeConverter<LessonStatus, String> {
        @Override
        public String convertToDatabaseColumn(LessonStatus attr) {
            return attr == null ? null : attr.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public LessonStatus convertToEntityAttribute(String db) {
            return db == null ? null : LessonStatus.valueOf(db.toUpperCase(Locale.ROOT));
        }
    }
}
