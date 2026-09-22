package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ADMIN create contract for the registry. The canonical display name is
 * assembled by Academic from the two code parts; clients do not send name or
 * current course.
 */
@Schema(description = "Создание группы в реестре ADMIN")
public record CreateAdminGroupRequest(
        @Schema(description = "Буквенная часть кода группы", example = "ИВТ",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 4)
        @NotBlank(message = "Буквенный код группы обязателен")
        @Pattern(regexp = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}$",
                message = "Буквенный код: 2–4 кириллических символа")
        @Size(max = 4, message = "Буквенный код не должен превышать 4 символа")
        String alphabeticCode,

        @Schema(description = "Трёхзначная цифровая часть; первая цифра — текущий курс",
                example = "311", requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^\\d{3}$")
        @NotBlank(message = "Цифровой код группы обязателен")
        @Pattern(regexp = "^\\d{3}$", message = "Цифровой код должен содержать 3 цифры")
        String numericCode,

        @Schema(description = "Срок обучения в годах", example = "4",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        @NotNull(message = "Срок обучения обязателен")
        @Min(value = 1, message = "Срок обучения должен быть положительным")
        Integer trainingDurationYears
) {}
