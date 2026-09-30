package ru.rutcampustrack.academic.contract.dto.semester;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Confirmation for irreversible semester deletion. Password is verified by
 * Auth and is never persisted in Academic.
 */
@Schema(description = "Запрос на окончательное удаление семестра")
public record DeleteSemesterRequest(
        @Schema(description = "Digest актуального предварительного расчёта",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Подтверждение обязательно")
        String previewDigest,

        @Schema(description = "Текущий пароль ADMIN; не сохраняется и не выводится в журнал",
                requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "Пароль обязателен")
        String password
) {
    @Override
    public String toString() {
        return "DeleteSemesterRequest[previewDigest=<redacted>, password=<redacted>]";
    }
}
