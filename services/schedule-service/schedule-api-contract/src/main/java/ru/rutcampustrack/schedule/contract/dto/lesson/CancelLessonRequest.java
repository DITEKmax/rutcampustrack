package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for cancelling a single lesson.
 * No Lombok — contract modules use plain Java records.
 */
@Schema(description = "Запрос на отмену одной пары")
public record CancelLessonRequest(

        @Schema(description = "Причина отмены пары",
                example = "Преподаватель болен",
                maxLength = 512,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 512)
        String reason
) {}
