package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for toggling geo-blocking on a lesson.
 * No Lombok — contract modules use plain Java records.
 */
@Schema(description = "Запрос на переключение геоблокировки пары")
public record GeoBlockRequest(

        @Schema(description = "Флаг геоблокировки: true — отметка по геолокации заблокирована, false — разблокирована",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Boolean blocked
) {}
