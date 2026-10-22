package ru.rutcampustrack.academic.exportjobs;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.academic.contract.api.AcademicExportJobApi;
import ru.rutcampustrack.academic.contract.dto.exportjobs.AcademicExportJobModels.*;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.security.RequireRole;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class AcademicExportJobController implements AcademicExportJobApi {
    private final ExportJobService jobs;
    private final RequestContext context;

    public AcademicExportJobController(ExportJobService jobs, RequestContext context) {
        this.jobs = jobs;
        this.context = context;
    }

    @Override
    @RequireRole(
            value = {UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT},
            adminShared = true)
    public ResponseEntity<Status> create(Create request) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(jobs.enqueue(actor(), request).status());
    }

    @Override
    @RequireRole(
            value = {UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT},
            adminShared = true)
    public ResponseEntity<Status> status(UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(jobs.status(actor(), id).status());
    }

    @Override
    @RequireRole(
            value = {UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT},
            adminShared = true)
    public ResponseEntity<byte[]> file(UUID id) {
        return download(jobs.file(actor(), id));
    }

    @Override
    @RequireRole(
            value = {UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT},
            adminShared = true)
    public ResponseEntity<ReportDownloadTicketResponse> ticket(UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(jobs.ticket(actor(), id));
    }

    private InternalJwtClaims actor() {
        var actor = context.getClaims();
        if (actor == null) throw ExportJobRepository.error(HttpStatus.UNAUTHORIZED);
        return actor;
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
