package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Фильтр по жизненному циклу группы для {@code GET /academic/groups?status=}.
 *
 * <p>BUG-006-6 / план 58-06:
 * <ul>
 *   <li>{@link #ACTIVE} — {@code is_active=true} (по умолчанию; 99% запросов из админки).</li>
 *   <li>{@link #ARCHIVED} — {@code is_active=false} (архив выпусков).</li>
 *   <li>{@link #ALL} — без фильтра (экспорт/аудит).</li>
 * </ul>
 */
@Schema(description = "Фильтр по жизненному циклу группы (ACTIVE — по умолчанию, ARCHIVED — архив, ALL — без фильтра)")
public enum GroupStatus {
    @Schema(description = "Только активные группы (is_active=true)")
    ACTIVE,
    @Schema(description = "Только архивные группы (is_active=false)")
    ARCHIVED,
    @Schema(description = "Без фильтра по статусу (все группы)")
    ALL
}
