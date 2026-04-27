package ru.rutcampustrack.shared.web.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Aspect для {@link AdminAction} — записывает audit entry на каждый
 * проксируемый метод.
 *
 * <p>До M16 G6 был заглушкой (log.debug). Теперь делегирует в
 * {@link AuditLogStorage} (если bean есть) и обогащает через
 * {@link AuditLogContextProvider} (current user + correlation).
 *
 * <h3>Контракты</h3>
 * <ul>
 *   <li><b>Best-effort:</b> exceptions от storage НЕ пробрасываются —
 *       проблемы аудита не должны ломать ADMIN endpoint. Логируем
 *       warn'ом и продолжаем.</li>
 *   <li><b>After-method:</b> entry создаётся ПОСЛЕ проксируемого метода
 *       — мы знаем succeeded/error состояние. На exception
 *       проксируемого метода exception всё равно пробрасывается caller'у
 *       (audit просто записывает factually что произошло).</li>
 *   <li><b>Optional dependencies:</b> {@code AuditLogStorage} и
 *       {@code AuditLogContextProvider} оба optional через
 *       {@code ObjectProvider} — shared-web grateful'но deg'ит до log-only
 *       если сервис их не предоставил.</li>
 * </ul>
 */
@Aspect
@Component
public class AdminActionAspect {

    private static final Logger log = LoggerFactory.getLogger(AdminActionAspect.class);

    private final ObjectProvider<AuditLogStorage> storageProvider;
    private final ObjectProvider<AuditLogContextProvider> contextProvider;

    public AdminActionAspect(ObjectProvider<AuditLogStorage> storageProvider,
                             ObjectProvider<AuditLogContextProvider> contextProvider) {
        this.storageProvider = storageProvider;
        this.contextProvider = contextProvider;
    }

    @Around("@annotation(adminAction)")
    public Object around(ProceedingJoinPoint pjp, AdminAction adminAction) throws Throwable {
        String action = adminAction.value();
        Throwable thrown = null;
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            recordAudit(action, pjp, thrown);
        }
        return result;
    }

    private void recordAudit(String action, ProceedingJoinPoint pjp, Throwable thrown) {
        AuditLogStorage storage = storageProvider.getIfAvailable();
        AuditLogContextProvider context = contextProvider.getIfAvailable(
                () -> AuditLogContextProvider.ANONYMOUS);

        Long userId;
        String correlationId;
        try {
            userId = context.currentUserId();
            correlationId = context.currentCorrelationId();
        } catch (Exception e) {
            // Context provider не должен падать, но defense-in-depth.
            log.warn("audit context provider failed: {}", e.toString());
            userId = null;
            correlationId = null;
        }

        AuditLogEntry entry = AuditLogEntry.builder(action)
                .userId(userId)
                .correlationId(correlationId)
                .succeeded(thrown == null)
                .errorMessage(thrown == null ? null : truncate(thrown.toString(), 500))
                .build();

        if (storage == null) {
            // M16 G6 — graceful degradation если сервис не предоставил storage.
            log.warn("[audit-storage missing] action={} user={} succeeded={} method={}",
                    action, userId, thrown == null, pjp.getSignature().toShortString());
            return;
        }

        try {
            storage.store(entry);
        } catch (Exception e) {
            log.warn("audit storage failed (entry dropped): action={} user={} err={}",
                    action, userId, e.toString());
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
