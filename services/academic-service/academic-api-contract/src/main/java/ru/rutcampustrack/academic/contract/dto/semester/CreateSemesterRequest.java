package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SemesterType;

import java.time.LocalDate;

/**
 * Request DTO for creating a new academic semester.
 */
@Schema(description = "Запрос на создание нового академического семестра (ADMIN)")
public record CreateSemesterRequest(

        @Schema(description = "Совместимое название; когда указаны тип и учебный год, сервер формирует его сам",
                example = "Осенний 2026/2027",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Название семестра обязательно")
        String name,

        @Schema(description = "Дата начала семестра", example = "2026-02-01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Дата начала обязательна")
        LocalDate dateFrom,

        @Schema(description = "Дата окончания семестра", example = "2026-06-30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Дата окончания обязательна")
        LocalDate dateTo,

        @Schema(description = "Явно выбранный тип семестра; null допустим только для совместимости со старыми клиентами")
        SemesterType semesterType,

        @Schema(description = "Первый год учебного года, видимый и редактируемый в форме; вместе с типом задаёт серверное название",
                example = "2026")
        @Min(value = 1, message = "Учебный год должен быть не меньше 1")
        @Max(value = 9998, message = "Учебный год должен быть не больше 9998")
        Integer academicYear
) {
    public CreateSemesterRequest(String name, LocalDate dateFrom, LocalDate dateTo) {
        this(name, dateFrom, dateTo, null, null);
    }
}
