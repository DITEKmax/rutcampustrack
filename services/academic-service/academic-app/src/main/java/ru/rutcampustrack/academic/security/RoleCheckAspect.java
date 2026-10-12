package ru.rutcampustrack.academic.security;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.exception.AccessDeniedException;

/**
 * AOP aspect that enforces role-based access control for methods annotated with {@link
 * RequireRole}. Reads the current user's role from {@link RequestContext} and throws {@link
 * AccessDeniedException} if the role is not among the allowed values.
 */
@Aspect
@Component
public class RoleCheckAspect {

    private final RequestContext requestContext;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.admin.AcademicAdminAccess adminAccess;

    public RoleCheckAspect(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
        UserRole[] required = requireRole.value();
        UserRole actual = requestContext.getRole();
        boolean headmanBypass =
                requestContext.isHeadman() && Arrays.asList(required).contains(UserRole.STUDENT);
        if (actual == null || (!Arrays.asList(required).contains(actual) && !headmanBypass)) {
            throw new AccessDeniedException("Required role: " + Arrays.toString(required));
        }
        if (actual == UserRole.ADMIN && !requireRole.adminShared()) {
            if (requireRole.admin().length == 0) adminAccess.requireFull();
            else if (requireRole.adminAny()) {
                var profile = adminAccess.current();
                if (Arrays.stream(requireRole.admin()).noneMatch(profile::allows))
                    throw ru.rutcampustrack.shared.security.admin.AdminAccessException.denied();
            } else adminAccess.require(requireRole.admin());
        }
        return pjp.proceed();
    }

    @Around(
            "within(@org.springframework.web.bind.annotation.RestController *) && execution(public * *(..)) && !@annotation(ru.rutcampustrack.academic.security.RequireRole)")
    public Object checkUnclassifiedAdmin(ProceedingJoinPoint pjp) throws Throwable {
        // Access API performs its own selected-role/FULL checks and allows catalog/me for LIMITED.
        if (requestContext.getRole() == UserRole.ADMIN
                && !(pjp.getTarget()
                        instanceof ru.rutcampustrack.academic.admin.AdminAccessController))
            adminAccess.requireFull();
        return pjp.proceed();
    }
}
