package ru.rutcampustrack.mobilebff.security;

import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class MobileRequestContext {
    private InternalJwtClaims claims;
    private String token;

    public void authenticate(InternalJwtClaims claims, String token) {
        this.claims = claims;
        this.token = token;
    }

    public InternalJwtClaims claims() {
        if (claims == null) throw new IllegalStateException("Mobile request is not authenticated");
        return claims;
    }

    public String token() {
        if (token == null) throw new IllegalStateException("Mobile request is not authenticated");
        return token;
    }
}
