package ru.rutcampustrack.attendance.contract.dto.excuse;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;

/**
 * Request DTO for PATCH /attendance/excuses/{id}/status (D-07).
 * Java record — no Lombok per contract module rules.
 * Only APPROVED or REJECTED are valid statuses here (enforced in service).
 */
public record UpdateExcuseStatusRequest(

        @NotNull
        ExcuseTicketStatus status,

        @Size(max = 1000)
        String decisionComment
) {}
