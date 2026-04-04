package ru.rutcampustrack.attendance.contract.dto.marking;

import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

/**
 * Request DTO for manual attendance marking by headman (D-11).
 * Java record — no Lombok per contract module rules.
 */
public record MarkRequest(@NotNull AttendanceStatus status) {}
