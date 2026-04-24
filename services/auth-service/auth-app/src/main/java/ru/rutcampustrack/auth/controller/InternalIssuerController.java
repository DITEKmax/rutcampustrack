package ru.rutcampustrack.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
@RequestMapping("/internal")
@Tag(name = "Internal Issuer", description = "Token exchange for downstream-service auth (M03a C0-1)")
public class InternalIssuerController {

    private final JwtService jwtService;
    private final InternalIssuerProperties properties;

    public InternalIssuerController(JwtService jwtService, InternalIssuerProperties properties) {
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Operation(summary = "Issue Internal JWT",
            description = "Exchange shared-secret + claims for a signed Internal JWT (audience=rutcampustrack-internal)")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "401", description = "Invalid or missing X-Internal-Issuer-Secret")
    @PostMapping("/issue-internal-jwt")
    public ResponseEntity<InternalIssueResponse> issue(@Valid @RequestBody InternalIssueRequest request) {
        long ttl = properties.getTokenTtlSeconds();
        String token = jwtService.generateInternalToken(
                request.userId(), request.role(), request.groupId(), request.isHeadman(), ttl);
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        return ResponseEntity.ok(new InternalIssueResponse(token, expiresAt));
    }
}
