package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for full replacement update of a group (PUT semantics).
 *
 * <p>BUG-006-5: единое поле {@code name}. Ручное переименование оставляет группу активной, поэтому
 * разрешён только активный формат. План 58-06 блокирует PUT для архивных групп на уровне сервиса.
 */
@Schema(description = "Запрос на полное обновление группы (PUT)")
public record UpdateGroupRequest(
        @Schema(
                        description = "Название группы в формате ХХ(х)-NNN",
                        example = "УИТ-311",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        maxLength = 8,
                        pattern = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}-[1-5]\\d{2}$")
                @NotBlank(message = "Название группы обязательно")
                @Pattern(
                        regexp = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}-[1-5]\\d{2}$",
                        message = "Формат: ХХ(х)-NNN, первая цифра от 1 до 5 (пример: УИТ-311)")
                @Size(max = 8, message = "Название не должно превышать 8 символов")
                String name,
        @Schema(description = "Флаг активности группы (false = архив)", example = "true")
                boolean active,
        @Schema(
                        description =
                                "Явный исправленный срок обучения; при отсутствии сохраняется прежний",
                        minimum = "1",
                        maximum = "5")
                @Min(value = 1, message = "Срок обучения должен быть от 1 до 5 лет")
                @Max(value = 5, message = "Срок обучения должен быть от 1 до 5 лет")
                Integer trainingDurationYears) {
    public UpdateGroupRequest(String name, boolean active) {
        this(name, active, null);
    }
}
