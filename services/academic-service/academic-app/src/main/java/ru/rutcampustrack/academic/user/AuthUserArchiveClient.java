package ru.rutcampustrack.academic.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.security.RequestContext;
import java.time.Duration;
import java.util.UUID;

@Component
public class AuthUserArchiveClient {
    private final RestTemplate http;
    private final RequestContext context;
    private final String url;
    private final String secret;
    public AuthUserArchiveClient(RestTemplateBuilder builder, RequestContext context,
            @Value("${rutcampustrack.security.internal-jwt.auth-service-url}") String url,
            @Value("${rutcampustrack.security.internal-issuer-client.secret}") String secret) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(3)).build();
        this.context=context; this.url=url.replaceAll("/+$", ""); this.secret=secret;
    }
    public void confirm(long target, UUID operation, String digest, String password) {
        if (context.getInternalToken()==null || context.getInternalToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Текущая сессия недействительна");
        }
        if (password==null || password.isBlank() || password.length()>1024) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "password_required");
        if (secret==null || secret.isBlank()) throw unavailable();
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Issuer-Secret", secret);
        try {
            var result = http.exchange(url+"/internal/auth/confirm-user-archive", HttpMethod.POST,
                    new HttpEntity<>(new Proof(context.getInternalToken(),password,"USER_ARCHIVE",target,operation,digest), headers), Void.class);
            if (result.getStatusCode()!=HttpStatus.NO_CONTENT) throw unavailable();
        } catch (HttpStatusCodeException failure) {
            HttpStatus status = switch (failure.getStatusCode().value()) {
                case 401 -> HttpStatus.UNAUTHORIZED; case 403 -> HttpStatus.FORBIDDEN;
                case 429 -> HttpStatus.TOO_MANY_REQUESTS; default -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            throw new ResponseStatusException(status, "Подтверждение архивирования отклонено");
        } catch (ResourceAccessException unavailable) { throw unavailable(); }
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Auth не подтвердил архивирование");
    }
    private record Proof(String internalToken,String password,String purpose,long targetId,UUID operationId,String previewDigest) {
        @Override public String toString() { return "UserArchiveProof[redacted]"; }
    }
}
