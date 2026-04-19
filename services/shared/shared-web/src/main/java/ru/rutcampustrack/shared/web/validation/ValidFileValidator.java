package ru.rutcampustrack.shared.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Locale;

public class ValidFileValidator implements ConstraintValidator<ValidFile, MultipartFile> {

    private long maxSizeBytes;
    private String[] allowedMediaTypes;

    @Override
    public void initialize(ValidFile constraint) {
        this.maxSizeBytes = constraint.maxSizeBytes();
        this.allowedMediaTypes = constraint.allowedMediaTypes();
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext ctx) {
        if (file == null || file.isEmpty()) {
            return true;
        }
        if (file.getSize() > maxSizeBytes) {
            customMessage(ctx, "Файл превышает максимальный размер " + maxSizeBytes + " байт");
            return false;
        }
        if (allowedMediaTypes.length > 0) {
            String contentType = file.getContentType();
            if (contentType == null || !matchesAny(contentType, allowedMediaTypes)) {
                customMessage(ctx, "Content-Type '" + contentType + "' не разрешён");
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAny(String contentType, String[] allowed) {
        String ct = contentType.toLowerCase(Locale.ROOT);
        return Arrays.stream(allowed)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(ct::equals);
    }

    private static void customMessage(ConstraintValidatorContext ctx, String msg) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
    }
}
