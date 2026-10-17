package ru.rutcampustrack.auth.config;

import jakarta.persistence.AttributeConverter;
import java.util.Locale;

/**
 * Universal JPA converter: Java Enum <-> lowercase string in PostgreSQL.
 *
 * <p>Usage: create a concrete subclass for each enum.
 *
 * <pre>
 * {@literal @}Converter(autoApply = true)
 * public class UserRoleConverter extends LowercaseEnumConverter{@literal <}UserRole{@literal >} {
 *     public UserRoleConverter() { super(UserRole.class); }
 * }
 * </pre>
 *
 * In DB: "admin", "teacher", "student" In Java: UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>>
        implements AttributeConverter<E, String> {

    // Persisted names are immutable codes; host locale must not change them.
    private final Class<E> enumClass;

    protected LowercaseEnumConverter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumClass, dbData.toUpperCase(Locale.ROOT));
    }
}
