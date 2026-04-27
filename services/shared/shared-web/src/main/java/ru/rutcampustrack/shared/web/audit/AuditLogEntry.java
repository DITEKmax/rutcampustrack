package ru.rutcampustrack.shared.web.audit;

import java.time.Instant;
import java.util.Map;

/**
 * M16 G6 — value object для audit log entry. Передаётся в
 * {@link AuditLogStorage#store(AuditLogEntry)}.
 *
 * <p>Все поля nullable кроме {@code action} и {@code createdAt}:
 * <ul>
 *   <li>{@code userId} — может быть {@code null} если ADMIN action не requires
 *       authenticated user (system maintenance) либо если контекст не доступен
 *       (тест без RequestContext).</li>
 *   <li>{@code targetType}/{@code targetId} — необязательно. Aspect не знает
 *       какой именно ресурс затрагивает action (это знает контроллер). На
 *       v1 заполняются как {@code null}; будущая фаза может добавить
 *       extraction из method args через {@code @AuditTarget} param annotation.</li>
 *   <li>{@code correlationId} — MDC trace_id если есть. Используется для
 *       связки audit-row с distributed trace в Tempo.</li>
 *   <li>{@code extras} — произвольные ключи-значения для будущего расширения
 *       без миграции схемы (например {@code {"role": "ADMIN", "ip": "..."}}).</li>
 *   <li>{@code errorMessage} — заполняется только при {@code succeeded=false}.</li>
 * </ul>
 */
public record AuditLogEntry(
        Long userId,
        String action,
        String targetType,
        Long targetId,
        String correlationId,
        Map<String, Object> extras,
        boolean succeeded,
        String errorMessage,
        Instant createdAt
) {

    /** Builder for ergonomic construction (большинство полей optional). */
    public static Builder builder(String action) {
        return new Builder(action);
    }

    public static final class Builder {
        private Long userId;
        private final String action;
        private String targetType;
        private Long targetId;
        private String correlationId;
        private Map<String, Object> extras;
        private boolean succeeded = true;
        private String errorMessage;
        private Instant createdAt = Instant.now();

        private Builder(String action) {
            if (action == null || action.isBlank()) {
                throw new IllegalArgumentException("action is required");
            }
            this.action = action;
        }

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder targetType(String t) { this.targetType = t; return this; }
        public Builder targetId(Long id) { this.targetId = id; return this; }
        public Builder correlationId(String id) { this.correlationId = id; return this; }
        public Builder extras(Map<String, Object> e) { this.extras = e; return this; }
        public Builder succeeded(boolean s) { this.succeeded = s; return this; }
        public Builder errorMessage(String m) { this.errorMessage = m; return this; }
        public Builder createdAt(Instant t) { this.createdAt = t; return this; }

        public AuditLogEntry build() {
            return new AuditLogEntry(userId, action, targetType, targetId,
                    correlationId, extras, succeeded, errorMessage, createdAt);
        }
    }
}
