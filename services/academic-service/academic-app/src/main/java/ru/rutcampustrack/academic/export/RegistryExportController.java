package ru.rutcampustrack.academic.export;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.RegistryExportApi;
import ru.rutcampustrack.academic.contract.dto.export.RegistryExportModels;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.security.RequireRole;

@RestController
public class RegistryExportController implements RegistryExportApi {
    private final AdminRegistryExportService service;
    public RegistryExportController(AdminRegistryExportService service) { this.service = service; }
    @Override @RequireRole(value = UserRole.ADMIN, admin = "GROUPS_VIEW")
    public ResponseEntity<List<RegistryExportModels.FormatOption>> groupFormats() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.formats("GROUPS_VIEW"));
    }
    @Override @RequireRole(value = UserRole.ADMIN, admin = "USERS_VIEW")
    public ResponseEntity<List<RegistryExportModels.FormatOption>> userFormats() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.formats("USERS_VIEW"));
    }
    @Override @RequireRole(value = UserRole.ADMIN, admin = "GROUPS_VIEW")
    public ResponseEntity<byte[]> groups(RegistryExportModels.Groups request, UUID jobId, UUID leaseId) { return download(service.groups(request, jobId, leaseId)); }
    @Override @RequireRole(value = UserRole.ADMIN, admin = "USERS_VIEW")
    public ResponseEntity<byte[]> users(RegistryExportModels.Users request, UUID jobId, UUID leaseId) { return download(service.users(request, jobId, leaseId)); }
    private static ResponseEntity<byte[]> download(AdminRegistryExportService.Download file) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(file.contentType()))
                .header("X-Export-Row-Count", Integer.toString(file.rowCount()))
                .header("X-Export-Requires-Full", Boolean.toString(file.requiresFull()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
