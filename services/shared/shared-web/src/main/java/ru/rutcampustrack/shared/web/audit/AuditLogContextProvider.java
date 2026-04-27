package ru.rutcampustrack.shared.web.audit;

/**
 * M16 G6 — SPI для resolve current user + correlation context. Implementation
 * предоставляется сервисом потому что shared-web не знает про service-specific
 * RequestContext (academic_RequestContext / schedule_RequestContext / etc.).
 *
 * <p>Если bean не зарегистрирован — {@link AdminActionAspect} использует
 * fallback {@link #ANONYMOUS} (userId=null, correlationId через MDC).
 *
 * <p>Контракт implementation:
 * <ul>
 *   <li>Должен возвращать {@code null} для userId если контекст не доступен
 *       (request scope expired / system thread / тест без context).</li>
 *   <li>{@code correlationId} обычно из MDC ({@code trace_id} key из
 *       shared-observability {@code MdcKeys}). Implementation может
 *       прокинуть свою.</li>
 *   <li>Должен быть fast (< 1ms) — вызывается на каждый @AdminAction.</li>
 * </ul>
 */
public interface AuditLogContextProvider {

    Long currentUserId();

    String currentCorrelationId();

    /** Fallback no-op provider — используется когда сервис не предоставил свой. */
    AuditLogContextProvider ANONYMOUS = new AuditLogContextProvider() {
        @Override
        public Long currentUserId() { return null; }

        @Override
        public String currentCorrelationId() {
            // Защитный fallback на MDC — если service провайдер не реализован,
            // хотя бы достанем trace_id из shared-observability MDC keys.
            String mdc = org.slf4j.MDC.get("trace_id");
            return mdc;
        }
    };
}
