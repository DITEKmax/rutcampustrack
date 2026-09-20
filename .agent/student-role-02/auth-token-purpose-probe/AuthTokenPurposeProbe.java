import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.rutcampustrack.auth.config.JwtAuthenticationFilter;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.UserRole;
import ru.rutcampustrack.auth.service.JwtService;

// Verification fixture only: no application startup, key files, Redis, DB or network.
public class AuthTokenPurposeProbe {
    static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static boolean probe(JwtAuthenticationFilter filter, String label, String token) throws Exception {
        SecurityContextHolder.clearContext();
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/ws-ticket");
            if (token != null) request.addHeader("Authorization", "Bearer " + token);
            AtomicBoolean authenticated = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                authenticated.set(auth != null && auth.isAuthenticated());
                System.out.println(label + " AUTHENTICATED=" + authenticated.get()
                        + " AUTHORITIES=" + (auth == null ? "[]" : auth.getAuthorities()));
            });
            return authenticated.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        JwtService jwt = new JwtService(new JwtProperties("UNUSED_NO_FILES", 60, 3600), null);
        set(jwt, "privateKey", keys.getPrivate());
        set(jwt, "publicKey", keys.getPublic());
        set(jwt, "keyId", "synthetic-purpose-probe");
        User user = new User();
        set(user, "id", 424242L);
        set(user, "role", UserRole.STUDENT);
        set(user, "groupId", 987L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt);
        boolean missing = probe(filter, "MISSING", null);
        boolean access = probe(filter, "ACCESS", jwt.generateAccessToken(user));
        boolean refresh = probe(filter, "REFRESH", jwt.generateRefreshToken(user));
        if (missing || !access) throw new AssertionError("Fixture controls failed");
        if (refresh) throw new AssertionError("Refresh token must not authenticate an access-token request");
    }
}
