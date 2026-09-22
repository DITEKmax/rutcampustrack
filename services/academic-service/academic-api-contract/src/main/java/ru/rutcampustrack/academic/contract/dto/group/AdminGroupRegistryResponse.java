package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Page plus server-side tab counts for the ADMIN registry. */
@Schema(description = "Страница реестра групп администратора")
public record AdminGroupRegistryResponse(
        List<AdminGroupResponse> items,
        int number,
        int size,
        long totalElements,
        int totalPages,
        long activeCount,
        long draftCount,
        long archivedCount
) {}
