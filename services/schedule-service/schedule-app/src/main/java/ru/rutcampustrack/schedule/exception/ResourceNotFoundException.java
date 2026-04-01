package ru.rutcampustrack.schedule.exception;

/**
 * Thrown when a requested resource does not exist.
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String entityName;
    private final String fieldName;
    private final Object fieldValue;

    public ResourceNotFoundException(String entityName, String fieldName, Object fieldValue) {
        super(entityName + " not found by " + fieldName + ": " + fieldValue);
        this.entityName = entityName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getFieldValue() {
        return fieldValue;
    }
}
