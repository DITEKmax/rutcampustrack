package ru.rutcampustrack.shared.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint: поле {@code start} должно быть &lt; поля {@code end}.
 *
 * <p>Применим к любому классу/record с двумя полями типа {@code Temporal}
 * ({@link java.time.LocalDate}, {@link java.time.LocalDateTime},
 * {@link java.time.Instant}, {@link java.time.OffsetDateTime},
 * {@link java.time.ZonedDateTime}).
 *
 * <p>{@code null} в любом из полей — constraint пропускает (используйте
 * {@code @NotNull} для обязательности).
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StartBeforeEndValidator.class)
@Documented
public @interface StartBeforeEnd {

    /** Имя поля начала (читается через BeanWrapper). */
    String start() default "start";

    /** Имя поля конца. */
    String end() default "end";

    String message() default "{start} должен быть раньше {end}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
