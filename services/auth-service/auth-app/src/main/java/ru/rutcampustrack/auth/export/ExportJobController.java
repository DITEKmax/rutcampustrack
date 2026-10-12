package ru.rutcampustrack.auth.export;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.ExportJobApi;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.SessionAdmissionException;

@RestController
public class ExportJobController implements ExportJobApi {
    private final ExportJobService jobs;

    public ExportJobController(ExportJobService jobs) {
        this.jobs = jobs;
    }

    @Override
    public ResponseEntity<ExportJobModels.Status> create(
            Authentication actor, ExportJobModels.Create request) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(jobs.enqueue(principal(actor), request));
    }

    @Override
    public ResponseEntity<ExportJobModels.Status> status(Authentication actor, UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(jobs.status(principal(actor), id));
    }

    @Override
    public ResponseEntity<byte[]> file(Authentication actor, UUID id) {
        return download(jobs.file(principal(actor), id));
    }

    @Override
    public ResponseEntity<ReportDownloadTicketResponse> ticket(Authentication actor, UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(jobs.ticket(principal(actor), id));
    }

    static SessionPrincipal principal(Authentication actor) {
        if (actor == null || !(actor.getPrincipal() instanceof SessionPrincipal principal))
            throw new SessionAdmissionException(SessionAdmissionException.Code.INVALID_SESSION);
        return principal;
    }

    static ResponseEntity<byte[]> download(ExportJobService.Download file) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file.content());
    }
}
