package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.InternalIssueRequest;
import ru.rutcampustrack.auth.dto.InternalIssueResponse;

/**
 * M12 G3 — Internal token-exchange contract (M03a C0-1).
 *
 * <p>Вызывается api-gateway с shared secret (header X-Internal-Issuer-Secret)
 * для получения short-lived Internal JWT (audience=rutcampustrack-internal)
 * для downstream-service аутентификации. Private key никогда не покидает
 * auth-service.</p>
 *
 * <p>{@code @Hidden} — endpoint не появляется в public /v3/api-docs, nginx
 * basic-auth защищает swagger-ui в prod (M11 G4).</p>
 */
@Hidden
@Tag(name = "Internal Issuer", description = "Token exchange for downstream-service auth (M03a C0-1)")
@RequestMapping("/internal")
public interface InternalIssuerApi {

    @Operation(summary = "Issue Internal JWT",
            description = "Exchange shared-secret + claims for a signed Internal JWT (audience=rutcampustrack-internal)")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "401", description = "Invalid or missing X-Internal-Issuer-Secret")
    @PostMapping("/issue-internal-jwt")
    ResponseEntity<InternalIssueResponse> issue(@Valid @RequestBody InternalIssueRequest request);
}
