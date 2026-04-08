package ru.rutcampustrack.notification.config;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtHandshakeInterceptorTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    private JwtHandshakeInterceptor createInterceptor() {
        JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor();
        interceptor.setPublicKey(publicKey);
        return interceptor;
    }

    private String buildJwt(long userId, long groupId, String role, boolean isHeadman, Instant expiry) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer("rutcampustrack-auth")
                .audience().add("rutcampustrack").and()
                .claim("group_id", groupId)
                .claim("role", role)
                .claim("is_headman", isHeadman)
                .expiration(Date.from(expiry))
                .signWith(privateKey)
                .compact();
    }

    @Test
    void validJwt_setsSessionAttributes() throws Exception {
        JwtHandshakeInterceptor interceptor = createInterceptor();
        String jwt = buildJwt(123L, 42L, "STUDENT", false, Instant.now().plusSeconds(300));

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setQueryString("token=" + jwt);
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(mockRequest),
                new ServletServerHttpResponse(mockResponse),
                null,
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).containsEntry("user_id", 123L);
        assertThat(attributes).containsEntry("group_id", 42L);
        assertThat(attributes).containsEntry("role", "STUDENT");
        assertThat(attributes).containsEntry("is_headman", false);
    }

    @Test
    void validJwt_headmanTrue() throws Exception {
        JwtHandshakeInterceptor interceptor = createInterceptor();
        String jwt = buildJwt(200L, 10L, "STUDENT", true, Instant.now().plusSeconds(300));

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setQueryString("token=" + jwt);
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(mockRequest),
                new ServletServerHttpResponse(mockResponse),
                null,
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).containsEntry("is_headman", true);
    }

    @Test
    void missingToken_rejectsWithUnauthorized() throws Exception {
        JwtHandshakeInterceptor interceptor = createInterceptor();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        // No query string at all
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(mockRequest),
                new ServletServerHttpResponse(mockResponse),
                null,
                attributes
        );

        assertThat(result).isFalse();
        assertThat(mockResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void invalidToken_rejectsWithUnauthorized() throws Exception {
        JwtHandshakeInterceptor interceptor = createInterceptor();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setQueryString("token=garbage.invalid.jwt");
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(mockRequest),
                new ServletServerHttpResponse(mockResponse),
                null,
                attributes
        );

        assertThat(result).isFalse();
        assertThat(mockResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void expiredToken_rejectsWithUnauthorized() throws Exception {
        JwtHandshakeInterceptor interceptor = createInterceptor();
        String jwt = buildJwt(123L, 42L, "STUDENT", false, Instant.now().minusSeconds(60));

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setQueryString("token=" + jwt);
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(mockRequest),
                new ServletServerHttpResponse(mockResponse),
                null,
                attributes
        );

        assertThat(result).isFalse();
        assertThat(mockResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void nullQuery_rejectsWithUnauthorized() throws Exception {
        JwtHandshakeInterceptor interceptor = createInterceptor();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        // queryString is null by default in MockHttpServletRequest
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                new ServletServerHttpRequest(mockRequest),
                new ServletServerHttpResponse(mockResponse),
                null,
                attributes
        );

        assertThat(result).isFalse();
        assertThat(mockResponse.getStatus()).isEqualTo(401);
    }
}
