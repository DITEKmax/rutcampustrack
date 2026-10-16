package ru.rutcampustrack.academic.subjectdeletion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.*;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.Duration;
import java.util.UUID;

/** Forward only the exact accepted signed token; secret-bearing bodies never become diagnostics. */
@Component
public class AuthSubjectDeletionClient {
    private final RestTemplate http;
    private final RequestContext context;
    private final String base, secret;

    public AuthSubjectDeletionClient(
            RestTemplateBuilder builder,
            RequestContext context,
            @Value("${rutcampustrack.security.internal-jwt.auth-service-url}") String base,
            @Value("${rutcampustrack.security.internal-issuer-client.secret}") String secret) {
        this.http =
                builder.setConnectTimeout(Duration.ofSeconds(3))
                        .setReadTimeout(Duration.ofSeconds(3))
                        .build();
        this.context = context;
        this.base = base.replaceAll("/+$", "");
        this.secret = secret;
    }

    public Challenge challenge(
            SubjectDeletionAcademicData.Target target, ChallengeRequest request) {
        return post(
                "challenge",
                new ChallengeInput(
                        context.getInternalToken(),
                        request.password(),
                        binding(target, request.operationId(), request.previewDigest())),
                Challenge.class);
    }

    public UUID verify(SubjectDeletionAcademicData.Target target, ConfirmRequest request) {
        var response =
                post(
                        "verify",
                        new VerifyInput(
                                context.getInternalToken(),
                                binding(target, request.operationId(), request.previewDigest()),
                                request.challengeId(),
                                request.code()),
                        Verification.class);
        if (!response.accepted() || response.receiptId() == null)
            throw SubjectDeletionAcademicData.conflict();
        return response.receiptId();
    }

    private <T> T post(String action, Object body, Class<T> type) {
        if (secret == null || secret.isBlank() || context.getInternalToken() == null)
            throw unavailable();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Issuer-Secret", secret);
        try {
            var response =
                    http.exchange(
                            base + "/internal/auth/subject-deletion/" + action,
                            HttpMethod.POST,
                            new HttpEntity<>(body, headers),
                            type);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null)
                throw unavailable();
            return response.getBody();
        } catch (HttpStatusCodeException rejected) {
            int status = rejected.getStatusCode().value();
            if (!java.util.Set.of(400, 401, 403, 409, 429).contains(status)) throw unavailable();
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(status), "Подтверждение удаления не принято");
        } catch (RestClientException unavailable) {
            throw unavailable();
        }
    }

    private static Binding binding(
            SubjectDeletionAcademicData.Target target, UUID operation, String digest) {
        return new Binding(target.subject(), target.semester(), target.group(), operation, digest);
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Подтверждение удаления недоступно");
    }

    private record Binding(
            long subjectId,
            long semesterId,
            long groupId,
            UUID operationId,
            String previewDigest) {}

    private record ChallengeInput(String internalToken, String password, Binding binding) {
        @Override
        public String toString() {
            return "SubjectDeletionChallenge[redacted]";
        }
    }

    private record VerifyInput(
            String internalToken, Binding binding, String challengeId, String code) {
        @Override
        public String toString() {
            return "SubjectDeletionVerification[redacted]";
        }
    }

    private record Verification(boolean accepted, UUID receiptId) {}
}
