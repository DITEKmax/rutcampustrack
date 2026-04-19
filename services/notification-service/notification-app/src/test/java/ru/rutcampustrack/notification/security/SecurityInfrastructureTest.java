package ru.rutcampustrack.notification.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.rutcampustrack.notification.contract.enums.UserRole;
import ru.rutcampustrack.notification.exception.AccessDeniedException;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityInfrastructureTest {

    @Mock
    private ProceedingJoinPoint pjp;

    private RequestContext requestContext;
    private RoleCheckAspect roleCheckAspect;
    private NotificationUserContextFilter filter;

    @BeforeEach
    void setUp() {
        requestContext = new RequestContext();
        roleCheckAspect = new RoleCheckAspect(requestContext);

        // M03a: build NotificationUserContextFilter with in-memory keypair for legacy header tests.
        InternalJwtTestFactory factory = new InternalJwtTestFactory();
        InternalJwtProperties props = new InternalJwtProperties(null, 0, 0, true, null, null, null);
        PublicKeyProvider keyProvider = new PublicKeyProvider(props) {
            @Override
            public void init() {
            }

            @Override
            public void refresh() {
            }

            @Override
            public java.security.PublicKey getPublicKey() {
                return factory.publicKey();
            }
        };
        InternalJwtValidator validator = new InternalJwtValidator(keyProvider, props);
        filter = new NotificationUserContextFilter(validator, props, requestContext);
    }

    // Test 1: RoleCheckAspect allows method execution when request has STUDENT role and @RequireRole({STUDENT}) is present
    @Test
    void roleCheckAspect_allowsStudentWhenRequireRoleStudent() throws Throwable {
        requestContext.setRole(UserRole.STUDENT);
        RequireRole annotation = createRequireRole(UserRole.STUDENT);
        when(pjp.proceed()).thenReturn("ok");

        Object result = roleCheckAspect.checkRole(pjp, annotation);

        assertThat(result).isEqualTo("ok");
        verify(pjp).proceed();
    }

    // Test 2: RoleCheckAspect throws AccessDeniedException when request has TEACHER role and @RequireRole({STUDENT}) is present
    @Test
    void roleCheckAspect_rejectsTeacherWhenRequireRoleStudent() throws Throwable {
        requestContext.setRole(UserRole.TEACHER);
        RequireRole annotation = createRequireRole(UserRole.STUDENT);

        assertThatThrownBy(() -> roleCheckAspect.checkRole(pjp, annotation))
                .isInstanceOf(AccessDeniedException.class);
        verify(pjp, never()).proceed();
    }

    // Test 3: NotificationUserContextFilter populates RequestContext.userId from X-User-Id legacy header (dual-mode)
    @Test
    void userContextFilter_populatesUserIdFromLegacyHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/push/subscribe");
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Role", "STUDENT");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(requestContext.getUserId()).isEqualTo(42L);
        assertThat(requestContext.getRole()).isEqualTo(UserRole.STUDENT);
    }

    // Test 4: NotificationUserContextFilter populates RequestContext.groupId from X-Group-Id legacy header
    @Test
    void userContextFilter_populatesGroupIdFromLegacyHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/push/subscribe");
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Role", "STUDENT");
        request.addHeader("X-Group-Id", "7");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(requestContext.getGroupId()).isEqualTo(7L);
    }

    // Helper to create @RequireRole annotation proxy
    private RequireRole createRequireRole(UserRole... roles) {
        return new RequireRole() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequireRole.class;
            }

            @Override
            public UserRole[] value() {
                return roles;
            }
        };
    }
}
