package ru.rutcampustrack.shared.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level constraint для {@code MultipartFile}: проверяет размер и Content-Type.
 *
 * <p>{@code null} и пустой файл ({@code isEmpty()==true}) — валидны. Для
 * обязательности использовать в паре с {@code @NotNull}.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidFileValidator.class)
@Documented
public @interface ValidFile {

    /** Максимальный размер файла в байтах. По умолчанию 10 MiB (соответствует spring.servlet.multipart.max-file-size). */
    long maxSizeBytes() default 10L * 1024L * 1024L;

    /**
     * Разрешённые MIME-типы. Пустой массив = любой тип.
     * Сравнение case-insensitive по Content-Type из {@code MultipartFile}.
     */
    String[] allowedMediaTypes() default {};

    String message() default "Файл не соответствует требованиям (размер/тип)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
