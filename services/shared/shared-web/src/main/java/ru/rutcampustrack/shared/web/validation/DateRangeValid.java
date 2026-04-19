package ru.rutcampustrack.shared.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint: поле {@code from} ≤ {@code to}. В отличие от
 * {@link StartBeforeEnd} — равенство считается валидным (отчёт за один день).
 *
 * <p>{@code null} в любом из полей — пропускается.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
@Documented
public @interface DateRangeValid {

    String from() default "from";

    String to() default "to";

    String message() default "{from} не может быть позже {to}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
