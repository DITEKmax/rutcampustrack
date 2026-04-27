package ru.rutcampustrack.shared.web.audit;

/**
 * M16 G6 — SPI для записи audit log entry. Implementation предоставляется
 * сервисом, который владеет соответствующим storage (например academic-app
 * → JdbcAuditLogStorage с DataSource'ом academic_db).
 *
 * <p>Если bean не зарегистрирован — {@link AdminActionAspect} логирует
 * audit только в slf4j (warn-уровень) с {@code "[audit-storage missing]"}
 * префиксом. Это позволяет shared-web стартовать в сервисах без БД
 * (api-gateway не нужен audit) без compile-time зависимости.
 *
 * <p>Контракт implementation:
 * <ul>
 *   <li>Не должен бросать exception в caller (aspect catch'ит и логирует).</li>
 *   <li>Storage write — best-effort: лог "лучше неполный чем падающий ADMIN endpoint".</li>
 *   <li>Должен быть thread-safe.</li>
 *   <li>Latency-budget: < 50ms p95 (audit row write не должен blocking
 *       основной endpoint). При больших объёмах — async batch внутри impl.</li>
 * </ul>
 */
public interface AuditLogStorage {

    /**
     * Сохраняет audit entry. Best-effort — exceptions внутри логируются,
     * но не пробрасываются.
     */
    void store(AuditLogEntry entry);
}
