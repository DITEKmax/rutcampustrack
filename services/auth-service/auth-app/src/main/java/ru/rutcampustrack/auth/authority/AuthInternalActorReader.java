package ru.rutcampustrack.auth.authority;

import io.grpc.Status;
import io.jsonwebtoken.Claims;

import org.springframework.stereotype.Component;

import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.UUID;

/** Signed actor parsing is separate from the current database admission decision. */
@Component
public final class AuthInternalActorReader {
    private final JwtService jwt;

    public AuthInternalActorReader(JwtService jwt) {
        this.jwt = jwt;
    }

    public SessionPrincipal read() {
        return read(AuthActorMetadataInterceptor.ACTOR_TOKEN.get());
    }

    public SessionPrincipal read(String token) {
        try {
            Claims c = jwt.parseInternalToken(token).getPayload();
            String group = c.get("group_id", String.class);
            return new SessionPrincipal(
                    Long.parseLong(c.getSubject()),
                    UUID.fromString(c.get("sid", String.class)),
                    Long.parseLong(c.get("sv", String.class)),
                    Long.parseLong(c.get("rv", String.class)),
                    AuthRole.valueOf(c.get("role", String.class)),
                    RoleStatus.valueOf(c.get("status", String.class)),
                    group == null ? null : Long.parseLong(group),
                    c.get("is_headman", Boolean.class),
                    c.get("readOnly", Boolean.class));
        } catch (RuntimeException invalid) {
            throw Status.UNAUTHENTICATED
                    .withDescription("valid signed actor required")
                    .asRuntimeException();
        }
    }

    public static InternalJwtClaims claims(SessionPrincipal p) {
        if (p.isBootstrap())
            throw Status.PERMISSION_DENIED
                    .withDescription("selected actor required")
                    .asRuntimeException();
        return new InternalJwtClaims(
                p.userId(),
                p.sessionId(),
                p.sessionVersion(),
                p.rolesVersion(),
                p.selectedRole().name(),
                p.selectedStatus().name(),
                p.groupId(),
                p.headman(),
                p.readOnly());
    }
}
