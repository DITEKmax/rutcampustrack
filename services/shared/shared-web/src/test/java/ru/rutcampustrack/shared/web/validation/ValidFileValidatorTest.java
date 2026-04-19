package ru.rutcampustrack.shared.web.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidFileValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cleanup() {
        factory.close();
    }

    /** Default limits: 10 MiB, any media type. */
    record Upload(@ValidFile MultipartFile file) {}

    /** Restricted: 1 KiB, only image/png or image/jpeg. */
    record ImageUpload(@ValidFile(maxSizeBytes = 1024, allowedMediaTypes = {"image/png", "image/jpeg"}) MultipartFile file) {}

    @Test
    @DisplayName("null и empty файл — валидны (use @NotNull для обязательности)")
    void nullAndEmpty() {
        assertThat(validator.validate(new Upload(null))).isEmpty();
        assertThat(validator.validate(new Upload(new MockMultipartFile("f", new byte[0])))).isEmpty();
    }

    @Test
    @DisplayName("Размер в пределах лимита — валиден")
    void sizeWithinLimit() {
        byte[] data = new byte[512];
        MultipartFile f = new MockMultipartFile("f", "a.png", "image/png", data);
        assertThat(validator.validate(new ImageUpload(f))).isEmpty();
    }

    @Test
    @DisplayName("Размер превышает лимит — violation с сообщением про размер")
    void sizeExceedsLimit() {
        byte[] data = new byte[2048];
        MultipartFile f = new MockMultipartFile("f", "big.png", "image/png", data);
        Set<ConstraintViolation<ImageUpload>> violations = validator.validate(new ImageUpload(f));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("превышает").contains("1024");
    }

    @Test
    @DisplayName("Content-Type не в allowedMediaTypes — violation")
    void contentTypeNotAllowed() {
        MultipartFile f = new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[100]);
        Set<ConstraintViolation<ImageUpload>> violations = validator.validate(new ImageUpload(f));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("application/pdf");
    }

    @Test
    @DisplayName("Content-Type case-insensitive match")
    void contentTypeCaseInsensitive() {
        MultipartFile f = new MockMultipartFile("f", "a.png", "IMAGE/PNG", new byte[100]);
        assertThat(validator.validate(new ImageUpload(f))).isEmpty();
    }

    @Test
    @DisplayName("allowedMediaTypes пустой — любой тип валиден")
    void anyTypeAllowed() {
        MultipartFile f = new MockMultipartFile("f", "x", "application/octet-stream", new byte[100]);
        assertThat(validator.validate(new Upload(f))).isEmpty();
    }

    @Test
    @DisplayName("Content-Type null когда есть ограничение — violation")
    void nullContentType() {
        MultipartFile f = new MockMultipartFile("f", "a.png", null, new byte[100]);
        assertThat(validator.validate(new ImageUpload(f))).hasSize(1);
    }
}
