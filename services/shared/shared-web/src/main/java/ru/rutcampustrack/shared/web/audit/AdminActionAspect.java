package ru.rutcampustrack.shared.web.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aspect-заглушка для {@link AdminAction} (NEW-72).
 *
 * <p>В M01 просто пропускает вызов и пишет DEBUG-строку — это позволяет
 * проверить, что аннотация действительно попадает в AOP pointcut.
 * Реальный handler (запись audit-log в Loki с userId/action/resource/outcome)
 * добавится в M04 без изменения сигнатуры этого класса.
 */
@Aspect
@Component
public class AdminActionAspect {

    private static final Logger log = LoggerFactory.getLogger(AdminActionAspect.class);

    @Around("@annotation(adminAction)")
    public Object around(ProceedingJoinPoint pjp, AdminAction adminAction) throws Throwable {
        log.debug("@AdminAction pointcut hit: action={} method={}",
                adminAction.value(),
                pjp.getSignature().toShortString());
        return pjp.proceed();
    }
}
