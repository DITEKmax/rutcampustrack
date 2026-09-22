package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Additive ADMIN projection of one durable role grant. */
@Schema(name = "RoleGrantViewResponse")
public record RoleGrantViewResponse(
        String role,
        String status,
        Long groupId,
        String groupName,
        boolean selectable,
        boolean readOnly,
        List<String> applicableStatuses,
        boolean canUpdate,
        String blockedReason
) {
    public RoleGrantViewResponse {
        applicableStatuses = applicableStatuses == null ? List.of() : List.copyOf(applicableStatuses);
    }
}
