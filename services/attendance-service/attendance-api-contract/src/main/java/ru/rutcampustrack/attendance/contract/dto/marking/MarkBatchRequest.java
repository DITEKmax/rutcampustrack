package ru.rutcampustrack.attendance.contract.dto.marking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Batch mark request (M05 D7 / P2-10/4).
 *
 * <p>Validation-first pseudo-atomic semantics: если любой pre-check
 * (headman role, lesson/group match, student-in-group) падает — весь
 * batch отклонён без Mongo writes.
 *
 * <p>Size limits — min=1 чтобы избежать no-op requests, max=100 per
 * OWNER-ANSWERS 3829.
 */
@Schema(description = "Пакетный запрос на отметку посещаемости (от 1 до 100 записей, pseudo-atomic)")
public record MarkBatchRequest(
        @Schema(description = "Список отметок для применения одним батчем",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 1,
                maxLength = 100)
        @NotNull
        @Size(min = 1, max = 100, message = "Размер пакета должен быть от 1 до 100")
        @Valid
        List<MarkBatchItem> items
) {}
