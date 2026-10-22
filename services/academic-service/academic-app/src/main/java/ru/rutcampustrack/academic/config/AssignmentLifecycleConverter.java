package ru.rutcampustrack.academic.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.rutcampustrack.academic.assignment.AssignmentLifecycle;
import ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException;

@Converter
public class AssignmentLifecycleConverter
        implements AttributeConverter<AssignmentLifecycle, String> {
    @Override
    public String convertToDatabaseColumn(AssignmentLifecycle value) {
        if (value == null) throw new UnknownAssignmentCodeException();
        return value.getCode();
    }

    @Override
    public AssignmentLifecycle convertToEntityAttribute(String code) {
        return AssignmentLifecycle.fromCode(code);
    }
}
