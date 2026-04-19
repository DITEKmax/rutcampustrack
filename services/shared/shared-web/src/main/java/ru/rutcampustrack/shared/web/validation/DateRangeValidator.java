package ru.rutcampustrack.shared.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public class DateRangeValidator implements ConstraintValidator<DateRangeValid, Object> {

    private String fromField;
    private String toField;

    @Override
    public void initialize(DateRangeValid constraint) {
        this.fromField = constraint.from();
        this.toField = constraint.to();
    }

    @Override
    public boolean isValid(Object target, ConstraintValidatorContext ctx) {
        if (target == null) {
            return true;
        }
        BeanWrapper w = PropertyAccessorFactory.forBeanPropertyAccess(target);
        Object from = w.getPropertyValue(fromField);
        Object to = w.getPropertyValue(toField);
        if (from == null || to == null) {
            return true;
        }
        return !isAfter(from, to);
    }

    private static boolean isAfter(Object from, Object to) {
        if (from instanceof LocalDate f && to instanceof LocalDate t) return f.isAfter(t);
        if (from instanceof LocalDateTime f && to instanceof LocalDateTime t) return f.isAfter(t);
        if (from instanceof Instant f && to instanceof Instant t) return f.isAfter(t);
        if (from instanceof OffsetDateTime f && to instanceof OffsetDateTime t) return f.isAfter(t);
        if (from instanceof ZonedDateTime f && to instanceof ZonedDateTime t) return f.isAfter(t);
        throw new IllegalArgumentException(
                "@DateRangeValid supports LocalDate/LocalDateTime/Instant/OffsetDateTime/ZonedDateTime; got: "
                        + from.getClass().getName() + " / " + to.getClass().getName());
    }
}
