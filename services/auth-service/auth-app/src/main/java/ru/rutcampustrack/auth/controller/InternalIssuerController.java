package ru.rutcampustrack.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalIssuerApi;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.dto.InternalIssueRequest;
import ru.rutcampustrack.auth.dto.InternalIssueResponse;
import ru.rutcampustrack.auth.service.JwtService;

import java.time.Instant;

/**
 * M03a: token exchange endpoint. Called by api-gateway with a shared secret
 * to obtain a short-lived Internal JWT for downstream-service authentication.
 *
 * The private key never leaves auth-service — api-gateway only sees the signed JWT.
 */
@RestController
public class InternalIssuerController implements InternalIssuerApi {

    private final JwtService jwtService;
    private final InternalIssuerProperties properties;

    public InternalIssuerController(JwtService jwtService, InternalIssuerProperties properties) {
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Override
    public ResponseEntity<InternalIssueResponse> issue(InternalIssueRequest request) {
        long ttl = properties.getTokenTtlSeconds();
        String token = jwtService.generateInternalToken(
                request.userId(), request.role(), request.groupId(), request.isHeadman(), ttl);
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        return ResponseEntity.ok(new InternalIssueResponse(token, expiresAt));
    }
}
