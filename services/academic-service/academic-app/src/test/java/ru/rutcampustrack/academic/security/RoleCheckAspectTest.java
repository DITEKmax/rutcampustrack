package ru.rutcampustrack.academic.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.exception.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for RoleCheckAspect — no Spring context, uses Mockito only.
 * Tests headman bypass logic and existing role enforcement.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleCheckAspectTest {

    @Mock
    private RequestContext requestContext;

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private RequireRole requireRole;

    @InjectMocks
    private RoleCheckAspect aspect;

    @Test
    void headmanPassesStudentRole() throws Throwable {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requireRole.value()).thenReturn(new UserRole[]{UserRole.STUDENT});

        aspect.checkRole(pjp, requireRole);

        verify(pjp).proceed();
    }

    @Test
    void headmanBlockedForAdminRole() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requireRole.value()).thenReturn(new UserRole[]{UserRole.ADMIN});

        assertThrows(AccessDeniedException.class, () -> aspect.checkRole(pjp, requireRole));
    }

    @Test
    void plainStudentPassesStudentRole() throws Throwable {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        lenient().when(requestContext.isHeadman()).thenReturn(false);
        when(requireRole.value()).thenReturn(new UserRole[]{UserRole.STUDENT});

        aspect.checkRole(pjp, requireRole);

        verify(pjp).proceed();
    }

    @Test
    void plainStudentBlockedForAdminRole() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        lenient().when(requestContext.isHeadman()).thenReturn(false);
        when(requireRole.value()).thenReturn(new UserRole[]{UserRole.ADMIN});

        assertThrows(AccessDeniedException.class, () -> aspect.checkRole(pjp, requireRole));
    }

    @Test
    void nullRoleAlwaysThrows() {
        when(requestContext.getRole()).thenReturn(null);
        lenient().when(requestContext.isHeadman()).thenReturn(true);
        when(requireRole.value()).thenReturn(new UserRole[]{UserRole.STUDENT});

        assertThrows(AccessDeniedException.class, () -> aspect.checkRole(pjp, requireRole));
    }

    @Test
    void adminPassesAdminRole() throws Throwable {
        when(requestContext.getRole()).thenReturn(UserRole.ADMIN);
        lenient().when(requestContext.isHeadman()).thenReturn(false);
        when(requireRole.value()).thenReturn(new UserRole[]{UserRole.ADMIN});

        aspect.checkRole(pjp, requireRole);

        verify(pjp).proceed();
    }

    @Test
    void teacherPassesTeacherRole() throws Throwable {
        when(requestContext.getRole()).thenReturn(UserRole.TEACHER);
        lenient().when(requestContext.isHeadman()).thenReturn(false);
        when(requireRole.value()).thenReturn(new UserRole[]{UserRole.TEACHER});

        aspect.checkRole(pjp, requireRole);

        verify(pjp).proceed();
    }
}
