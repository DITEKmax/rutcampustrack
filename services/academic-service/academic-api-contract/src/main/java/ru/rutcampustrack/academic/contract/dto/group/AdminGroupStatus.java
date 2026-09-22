package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

/** Server-computed lifecycle slice used by the ADMIN group registry. */
@Schema(description = "Разрез реестра групп администратора")
public enum AdminGroupStatus {
    ACTIVE,
    DRAFT,
    ARCHIVED
}
