package ru.rutcampustrack.notification.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Validates JWT token provided as {@code ?token=} query parameter during WebSocket HTTP Upgrade.
 *
 * <p>Per D-02: Browser WebSocket API cannot set custom headers — query param is the standard approach.
 * Per D-01: Public key loaded from file at startup (no runtime dependency on Auth Service).
 * Per D-03: Claims are extracted into session attributes once at handshake — NOT re-validated after connection.
 */
@Component
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private RSAPublicKey publicKey;

    @Value("${notification.jwt.public-key-path}")
    private String publicKeyPath;

    @PostConstruct
    public void init() throws Exception {
        String pem = Files.readString(Path.of(publicKeyPath));
        String stripped = pem
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        this.publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        log.info("JWT public key loaded from {}", publicKeyPath);
    }

    /**
     * Package-private setter for testing — allows injecting an in-memory key pair
     * without loading from the filesystem.
     */
    void setPublicKey(RSAPublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        String token = extractTokenFromQuery(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // D-03: Extract claims into session attributes — NOT re-validated after handshake
            attributes.put("user_id", Long.parseLong(claims.getSubject()));
            // Pitfall 1: group_id may deserialize as Integer for small values — use Number cast to be safe
            attributes.put("group_id", ((Number) claims.get("group_id")).longValue());
            attributes.put("role", claims.get("role", String.class));
            attributes.put("is_headman", Boolean.TRUE.equals(claims.get("is_headman", Boolean.class)));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("WebSocket handshake rejected — invalid JWT: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler handler, Exception exception) {
        // no-op — D-03: no post-handshake JWT re-validation
    }

    private String extractTokenFromQuery(ServerHttpRequest request) {
        // Pitfall 4: Works with both raw WebSocket and SockJS session-keyed paths
        // because getRawQuery() returns the query string regardless of the path structure
        String rawQuery = request.getURI().getRawQuery();
        if (rawQuery == null) return null;
        for (String param : rawQuery.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}
