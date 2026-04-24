package ru.rutcampustrack.auth.integration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.auth.dto.InternalIssueRequest;
import ru.rutcampustrack.auth.dto.InternalIssueResponse;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.service.JwtService;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InternalIssuerIT extends AbstractIntegrationTest {

    private static final String SECRET = "test-internal-issuer-secret-32-bytes-or-more-for-test-env";
    private static final String SECRET_HEADER = "X-Internal-Issuer-Secret";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void validSecret_returnsSignedInternalToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SECRET_HEADER, SECRET);
        InternalIssueRequest body = new InternalIssueRequest(42L, "ADMIN", 7L, true);

        ResponseEntity<InternalIssueResponse> response = restTemplate.exchange(
                "/internal/issue-internal-jwt", HttpMethod.POST,
                new HttpEntity<>(body, headers), InternalIssueResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().expiresAt()).isNotNull();

        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(fetchPublicKey())
                .requireIssuer("rutcampustrack-auth")
                .requireAudience(JwtService.INTERNAL_JWT_AUDIENCE)
                .build()
                .parseSignedClaims(response.getBody().token());

        Claims claims = parsed.getPayload();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(((Number) claims.get("group_id")).longValue()).isEqualTo(7L);
        assertThat(claims.get("is_headman", Boolean.class)).isTrue();
    }

    @Test
    void missingSecret_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        InternalIssueRequest body = new InternalIssueRequest(42L, "ADMIN", null, false);

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/issue-internal-jwt", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongSecret_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SECRET_HEADER, "wrong-secret-value-with-padding-to-32-bytes");
        InternalIssueRequest body = new InternalIssueRequest(42L, "ADMIN", null, false);

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/issue-internal-jwt", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedBody_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SECRET_HEADER, SECRET);
        // userId=null violates @NotNull
        Map<String, Object> body = Map.of("role", "ADMIN");

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/issue-internal-jwt", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void studentWithoutGroup_acceptsNullGroupId() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SECRET_HEADER, SECRET);
        InternalIssueRequest body = new InternalIssueRequest(100L, "TEACHER", null, false);

        ResponseEntity<InternalIssueResponse> response = restTemplate.exchange(
                "/internal/issue-internal-jwt", HttpMethod.POST,
                new HttpEntity<>(body, headers), InternalIssueResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
    }

    private PublicKey fetchPublicKey() throws Exception {
        ResponseEntity<PublicKeyResponse> response = restTemplate.getForEntity(
                "/auth/public-key", PublicKeyResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String pem = response.getBody().publicKey()
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
