package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.*;

@Tag(name = "QR login", description = "Browser-bound LOGIN challenge with explicit authenticated approval")
@RequestMapping("/auth/qr")
public interface QrLoginApi {
    @Operation(summary = "Issue or reissue a browser-bound LOGIN challenge")
    @PostMapping("/challenges")
    ResponseEntity<QrLoginIssueResponse> issue(@Valid @RequestBody QrLoginIssueRequest request, HttpServletRequest httpRequest);

    @Operation(summary = "Read LOGIN status using the issuing browser capability")
    @PostMapping("/status")
    ResponseEntity<QrLoginStatusResponse> status(@Valid @RequestBody QrLoginProofRequest request, HttpServletRequest httpRequest);

    @Operation(summary = "Exchange an approved challenge for the normal Auth session")
    @PostMapping("/exchange")
    ResponseEntity<TokenResponse> exchange(@Valid @RequestBody QrLoginProofRequest request);

    @Operation(summary = "Preview browser login without approving it")
    @PostMapping("/preview")
    ResponseEntity<QrLoginPreviewResponse> preview(@Valid @RequestBody QrLoginApprovalRequest request, Authentication authentication);

    @Operation(summary = "Explicitly approve or reject browser login")
    @PostMapping("/decision")
    ResponseEntity<QrLoginStatusResponse> decide(@Valid @RequestBody QrLoginDecisionRequest request, Authentication authentication);
}
