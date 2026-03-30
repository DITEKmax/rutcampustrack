package ru.rutcampustrack.academic.contract.dto.user;

import ru.rutcampustrack.academic.contract.enums.AccountStatus;

public record PatchUserRequest(
        String displayName,
        Boolean isHeadman,
        Long groupId,
        String employeeNumber,
        Long telegramId,
        AccountStatus status
) {}
