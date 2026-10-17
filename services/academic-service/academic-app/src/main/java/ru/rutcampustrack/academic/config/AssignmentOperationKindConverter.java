package ru.rutcampustrack.academic.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.rutcampustrack.academic.assignment.AssignmentOperationKind;
import ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException;

@Converter
public class AssignmentOperationKindConverter
        implements AttributeConverter<AssignmentOperationKind, String> {
    @Override
    public String convertToDatabaseColumn(AssignmentOperationKind value) {
        if (value == null) throw new UnknownAssignmentCodeException();
        return value.getCode();
    }

    @Override
    public AssignmentOperationKind convertToEntityAttribute(String code) {
        return AssignmentOperationKind.fromCode(code);
    }
}
