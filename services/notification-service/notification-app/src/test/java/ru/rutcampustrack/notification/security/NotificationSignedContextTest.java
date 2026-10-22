package ru.rutcampustrack.notification.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.rutcampustrack.notification.contract.enums.UserRole;
import ru.rutcampustrack.shared.security.*;

class NotificationSignedContextTest {
    @Test
    void rawHeadmanAndVersionedReadOnlyIdentitySurviveDomainNormalization() {
        var context = new RequestContext();
        var filter = filter(context);
        UUID session = UUID.randomUUID();
        filter.applyInternalJwt(
                new InternalJwtClaims(11, session, 7, 9, "HEADMAN", "ACTIVE", 3L, true, true),
                "validated-token");
        assertThat(context.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(context.requireSelectedRole()).isEqualTo("HEADMAN");
        assertThat(context.getSessionId()).isEqualTo(session);
        assertThat(context.getSessionVersion()).isEqualTo(7);
        assertThat(context.getRolesVersion()).isEqualTo(9);
        assertThat(context.isReadOnly()).isTrue();
        filter.applyInternalJwt(
                new InternalJwtClaims(11, session, 7, 9, "STUDENT", "ACTIVE", 3L, true, false),
                "validated-token");
        assertThat(context.requireSelectedRole()).isEqualTo("STUDENT");
    }

    @Test
    void legacyHeadersCannotAuthorizeRoleInboxAndIncompleteVersionsFailClosed() {
        var context = new RequestContext();
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "11");
        request.addHeader("X-User-Role", "STUDENT");
        request.addHeader("X-Is-Headman", "true");
        filter(context).applyLegacyHeaders(request);
        assertThatThrownBy(context::requireSelectedRole)
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        filter(context)
                .applyInternalJwt(
                        new InternalJwtClaims(
                                11, UUID.randomUUID(), 0, 9, "STUDENT", "ACTIVE", 3L, true, false),
                        "validated-token");
        assertThatThrownBy(context::requireSelectedRole)
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private static NotificationUserContextFilter filter(RequestContext context) {
        return new NotificationUserContextFilter(
                mock(InternalJwtValidator.class), mock(InternalJwtProperties.class), context, null);
    }
}
