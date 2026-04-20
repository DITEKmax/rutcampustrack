package ru.rutcampustrack.attendance.contract.dto.marking;

import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

/**
 * Single entry in a batch mark request (M05 D7 / P2-10/4).
 * Все три поля required. CANCELLED status отклоняется сервисом (D-14).
 */
public record MarkBatchItem(
        @NotNull Long lessonId,
        @NotNull Long userId,
        @NotNull AttendanceStatus status
) {}
