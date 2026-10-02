package ru.rutcampustrack.academic.contract.dto.homework;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;

/**
 * Request DTO for full replacement update of a homework (PUT semantics).
 */
@Schema(description = "Запрос на полное обновление домашнего задания (PUT)")
public record UpdateHomeworkRequest(

        @Schema(description = "Название задания",
                example = "Интегралы 1-5",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255)
        @NotBlank(message = "Название задания обязательно")
        @Size(max = 255)
        String title,

        @Schema(description = "Описание задания (опционально)",
                example = "Решить задачи 1-5 из учебника Демидовича",
                maxLength = 4000)
        @Size(max = 4000)
        String description,

        @Schema(description = "Ссылка на материалы (опционально)",
                example = "https://example.com/homework.pdf",
                maxLength = 2048)
        @Size(max = 2048)
        String link,
        HomeworkBindingMode bindingMode,
        LocalDate lessonDate,
        @Min(1) @Max(8) Integer lessonNumber,
        @NotNull UUID requestKey,
        @NotNull @Min(1) Long expectedRevision
) {
    public UpdateHomeworkRequest(String title, String description, String link) {
        this(title, description, link, null, null, null, null, null);
    }

    public UpdateHomeworkRequest(String title, String description, String link, UUID requestKey, Long expectedRevision) {
        this(title, description, link, null, null, null, requestKey, expectedRevision);
    }

    public boolean hasPlacementFields() {
        return bindingMode != null || lessonDate != null || lessonNumber != null;
    }

    @AssertTrue(message = "Изменение ДЗ требует requestKey и expectedRevision")
    @JsonIgnore
    public boolean isPlacementCommandValid() {
        return requestKey != null && expectedRevision != null;
    }
}
