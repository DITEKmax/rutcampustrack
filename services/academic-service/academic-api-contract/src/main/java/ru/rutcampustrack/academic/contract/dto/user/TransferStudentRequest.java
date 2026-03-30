package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for transferring a student to a different group.
 * The transfer history is recorded in the student_transfers table.
 */
public record TransferStudentRequest(

        @NotNull(message = "ID новой группы обязателен")
        Long newGroupId,

        @NotBlank(message = "Причина перевода обязательна")
        String reason
) {}
