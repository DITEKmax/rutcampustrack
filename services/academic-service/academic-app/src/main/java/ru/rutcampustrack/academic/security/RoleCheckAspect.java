package ru.rutcampustrack.academic.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.exception.AccessDeniedException;

import java.util.Arrays;

/**
 * AOP aspect that enforces @RequireRole on controller methods.
 * Reads the actual role from RequestContext (request-scoped proxy) and compares to required roles.
 */
@Aspect
@Component
public class RoleCheckAspect {

    private final RequestContext requestContext;

    public RoleCheckAspect(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
        UserRole[] required = requireRole.value();
        UserRole actual = requestContext.getRole();
        if (actual == null || !Arrays.asList(required).contains(actual)) {
            throw new AccessDeniedException("Требуется одна из ролей: " + Arrays.toString(required));
        }
        return pjp.proceed();
    }
}
