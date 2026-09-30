package ru.rutcampustrack.academic.semester;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.SemesterDeletionDependencyUnavailableException;
import ru.rutcampustrack.academic.security.RequestContext;

import java.util.UUID;

/** Performs the frozen live-session/password check before Academic starts a local transaction. */
@Component
public class AuthSemesterDeletionClient {

    private final RestTemplate restTemplate;
    private final RequestContext requestContext;
    private final String authServiceUrl;
    private final String issuerSecret;

    public AuthSemesterDeletionClient(RestTemplateBuilder restTemplateBuilder,
                                      RequestContext requestContext,
                                      @Value("${rutcampustrack.security.internal-jwt.auth-service-url}")
                                      String authServiceUrl,
                                      @Value("${rutcampustrack.security.internal-issuer-client.secret}")
                                      String issuerSecret) {
        this.restTemplate = restTemplateBuilder.setConnectTimeout(java.time.Duration.ofSeconds(3))
                .setReadTimeout(java.time.Duration.ofSeconds(3)).build();
        this.requestContext = requestContext;
        this.issuerSecret = issuerSecret;
        this.authServiceUrl = authServiceUrl == null ? "" : authServiceUrl.replaceAll("/+$", "");
    }

    public void confirm(long semesterId, UUID operationId, String previewDigest, String password) {
        String internalToken = requestContext.getInternalToken();
        if (internalToken == null || internalToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Текущая сессия не может подтвердить удаление семестра");
        }
        DeleteConfirmation request = new DeleteConfirmation(
                internalToken, password, semesterId, operationId, previewDigest);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (issuerSecret == null || issuerSecret.isBlank()) {
            throw new SemesterDeletionDependencyUnavailableException("Auth internal issuer credential is unavailable");
        }
        headers.set("X-Internal-Issuer-Secret", issuerSecret);
        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    authServiceUrl + "/internal/auth/confirm-semester-deletion",
                    HttpMethod.POST, new HttpEntity<>(request, headers), Void.class);
            if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
                throw new SemesterDeletionDependencyUnavailableException(
                        "Auth returned an unexpected semester deletion confirmation status");
            }
        } catch (HttpStatusCodeException rejected) {
            switch (rejected.getStatusCode().value()) {
                case 401 -> throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Текущая сессия недействительна");
                case 403 -> throw new AccessDeniedException("Пароль или роль ADMIN не подтверждены");
                case 429 -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Слишком много попыток подтверждения паролем");
                default -> throw new SemesterDeletionDependencyUnavailableException(
                        "Auth не подтвердил право на удаление семестра");
            }
        } catch (ResourceAccessException unavailable) {
            throw new SemesterDeletionDependencyUnavailableException(
                    "Auth не ответил в установленный срок");
        }
    }

    private record DeleteConfirmation(String internalToken, String password, long semesterId,
                                      UUID operationId, String previewDigest) {
        @Override
        public String toString() {
            return "DeleteConfirmation[internalToken=<redacted>, password=<redacted>, semesterId="
                    + semesterId + ", operationId=" + operationId + ", previewDigest=<redacted>]";
        }
    }
}
