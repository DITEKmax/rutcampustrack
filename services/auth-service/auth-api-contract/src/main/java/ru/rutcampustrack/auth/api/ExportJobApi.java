package ru.rutcampustrack.auth.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.auth.dto.*;

@RequestMapping("/auth/export-jobs")
public interface ExportJobApi {
    @PostMapping ResponseEntity<ExportJobModels.Status> create(Authentication actor, @Valid @RequestBody ExportJobModels.Create request);
    @GetMapping("/{id}") ResponseEntity<ExportJobModels.Status> status(Authentication actor, @PathVariable UUID id);
    @GetMapping("/{id}/file") ResponseEntity<byte[]> file(Authentication actor, @PathVariable UUID id);
    @PostMapping("/{id}/ticket") ResponseEntity<ReportDownloadTicketResponse> ticket(Authentication actor, @PathVariable UUID id);
}
