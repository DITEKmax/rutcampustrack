package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;

/**
 * Request DTO for partial update of a user (PATCH semantics).
 * All fields are optional — only non-null fields are applied.
 * Used for headman assignment, group transfer, status changes, etc.
 */
public record PatchUserRequest(

        @Size(max = 255)
        String displayName,

        Boolean isHeadman,

        Long groupId,

        String employeeNumber,

        Long telegramId,

        AccountStatus status
) {}
