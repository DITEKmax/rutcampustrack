package ru.rutcampustrack.academic.contract.dto.group;

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
public enum GroupStatus {
    ACTIVE,
    ARCHIVED,
    ALL
}
