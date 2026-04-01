package ru.rutcampustrack.schedule.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;

import java.util.Arrays;

/**
 * AOP aspect enforcing role-based access control for @RequireRole-annotated methods.
 * Reads the current user's role from RequestContext and throws AccessDeniedException
 * if the role is null (no headers) or not among the allowed values.
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
            throw new AccessDeniedException("Required role: " + Arrays.toString(required));
        }
        return pjp.proceed();
    }
}
