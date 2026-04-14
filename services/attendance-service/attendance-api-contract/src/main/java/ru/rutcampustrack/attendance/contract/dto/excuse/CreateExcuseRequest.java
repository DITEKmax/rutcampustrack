package ru.rutcampustrack.attendance.contract.dto.excuse;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;

import java.util.List;

/**
 * Request DTO for POST /attendance/excuses (D-04, D-15).
 * Java record — no Lombok per contract module rules.
 */
public record CreateExcuseRequest(

        @NotEmpty
        List<Long> lessonIds,

        @NotNull
        ExcuseType excuseType,

        @Size(max = 1000)
        String comment
) {}
