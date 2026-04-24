package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for mass-cancel operation, reporting how many lessons were cancelled.
 * No Lombok — contract modules use plain Java records.
 */
@Schema(description = "Результат массовой отмены пар с количеством отменённых записей")
public record MassCancelResponse(

        @Schema(description = "Количество фактически отменённых пар",
                example = "12")
        int cancelledCount
) {}
