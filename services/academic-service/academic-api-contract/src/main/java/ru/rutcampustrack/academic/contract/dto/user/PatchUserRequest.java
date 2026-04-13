package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;

/**
 * Request DTO for partial update of a user (PATCH semantics).
 * All fields are optional — only non-null fields are applied.
 */
public record PatchUserRequest(

        @Size(max = 128)
        String lastName,

        @Size(max = 128)
        String firstName,

        @Size(max = 128)
        String middleName,

        Boolean isHeadman,

        Long groupId,

        String employeeNumber,

        Long telegramId,

        AccountStatus status
) {}
