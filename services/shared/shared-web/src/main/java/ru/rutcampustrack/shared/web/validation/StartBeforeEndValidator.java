package ru.rutcampustrack.shared.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public class StartBeforeEndValidator implements ConstraintValidator<StartBeforeEnd, Object> {

    private String startField;
    private String endField;

    @Override
    public void initialize(StartBeforeEnd constraint) {
        this.startField = constraint.start();
        this.endField = constraint.end();
    }

    @Override
    public boolean isValid(Object target, ConstraintValidatorContext ctx) {
        if (target == null) {
            return true;
        }
        BeanWrapper w = PropertyAccessorFactory.forBeanPropertyAccess(target);
        Object start = w.getPropertyValue(startField);
        Object end = w.getPropertyValue(endField);
        if (start == null || end == null) {
            return true;
        }
        return isBefore(start, end);
    }

    private static boolean isBefore(Object start, Object end) {
        if (start instanceof LocalDate s && end instanceof LocalDate e) return s.isBefore(e);
        if (start instanceof LocalDateTime s && end instanceof LocalDateTime e) return s.isBefore(e);
        if (start instanceof Instant s && end instanceof Instant e) return s.isBefore(e);
        if (start instanceof OffsetDateTime s && end instanceof OffsetDateTime e) return s.isBefore(e);
        if (start instanceof ZonedDateTime s && end instanceof ZonedDateTime e) return s.isBefore(e);
        throw new IllegalArgumentException(
                "@StartBeforeEnd supports LocalDate/LocalDateTime/Instant/OffsetDateTime/ZonedDateTime; got: "
                        + start.getClass().getName() + " / " + end.getClass().getName());
    }
}
