package ru.rutcampustrack.academic.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.rutcampustrack.academic.assignment.AssignmentOperationState;
import ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException;

@Converter
public class AssignmentOperationStateConverter
        implements AttributeConverter<AssignmentOperationState, String> {
    @Override
    public String convertToDatabaseColumn(AssignmentOperationState value) {
        if (value == null) throw new UnknownAssignmentCodeException();
        return value.getCode();
    }

    @Override
    public AssignmentOperationState convertToEntityAttribute(String code) {
        return AssignmentOperationState.fromCode(code);
    }
}
