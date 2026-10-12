package ru.rutcampustrack.auth.export;

import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.auth.api.InternalExportJobApi;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.model.*;

/** Every route is covered by the existing timing-safe internal-issuer secret filter. */
@RestController
public class InternalExportJobController implements InternalExportJobApi {
    private final ExportJobService jobs;

    public InternalExportJobController(ExportJobService jobs) {
        this.jobs = jobs;
    }

    @Override
    public ResponseEntity<ExportJobModels.Claim> claim() {
        return jobs.claim()
                .map(value -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value))
                .orElseGet(
                        () ->
                                ResponseEntity.noContent()
                                        .cacheControl(CacheControl.noStore())
                                        .build());
    }

    @Override
    public ResponseEntity<ExportJobModels.Status> complete(
            UUID id, ExportJobModels.Complete result) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(jobs.complete(id, result));
    }

    @Override
    public ResponseEntity<Void> fail(UUID id, ExportJobModels.Fail failure) {
        jobs.fail(id, failure);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @Override
    public ResponseEntity<Void> admit(ExportJobModels.Admit request) {
        jobs.status(principal(request.identity()), request.jobId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @Override
    public ResponseEntity<byte[]> file(UUID id, WsSessionAdmissionRequest identity) {
        return ExportJobController.download(jobs.file(principal(identity), id));
    }

    private static SessionPrincipal principal(WsSessionAdmissionRequest p) {
        try {
            return new SessionPrincipal(
                    p.userId(),
                    UUID.fromString(p.sessionId()),
                    p.sessionVersion(),
                    p.rolesVersion(),
                    AuthRole.valueOf(p.role()),
                    RoleStatus.valueOf(p.status()),
                    p.groupId(),
                    p.isHeadman(),
                    p.readOnly());
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Недействительная сессия");
        }
    }
}
