package ru.rutcampustrack.academic.exportjobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.auth.dto.ExportJobModels;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Exact Notification→Academic metadata admission. Cannot issue a JWT or mutate a job. */
@RestController
@io.swagger.v3.oas.annotations.Hidden
public class ExportAdmissionBoundary {
    private final ExportJobService jobs;
    private final String expected;

    public ExportAdmissionBoundary(
            ExportJobService jobs,
            @Value("${grpc.service-identity.notification-to-academic-token:}") String expected) {
        this.jobs = jobs;
        this.expected = expected;
    }

    public boolean accepts(String provided) {
        return DirectedServiceCredential.isCanonicalToken(expected)
                && DirectedServiceCredential.isCanonicalToken(provided)
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        provided.getBytes(StandardCharsets.US_ASCII));
    }

    @PostMapping("/academic/internal/export-jobs/admit")
    public ResponseEntity<Void> admit(
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @jakarta.validation.Valid @RequestBody ExportJobModels.Admit request) {
        if (!accepts(token)) throw ExportJobRepository.error(HttpStatus.UNAUTHORIZED);
        var p = request.identity();
        jobs.status(
                new InternalJwtClaims(
                        p.userId(),
                        UUID.fromString(p.sessionId()),
                        p.sessionVersion(),
                        p.rolesVersion(),
                        p.role(),
                        p.status(),
                        p.groupId(),
                        p.isHeadman(),
                        p.readOnly()),
                request.jobId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
