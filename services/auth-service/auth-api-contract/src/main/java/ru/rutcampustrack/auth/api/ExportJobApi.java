package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.util.UUID;

@RequestMapping("/auth/export-jobs")
public interface ExportJobApi {
    @PostMapping
    @ApiResponse(
            responseCode = "202",
            description = "Durable export accepted; poll the returned job until ready or error",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExportJobModels.Status.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Current authority or durable export storage unavailable",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<ExportJobModels.Status> create(
            Authentication actor, @Valid @RequestBody ExportJobModels.Create request);

    @GetMapping("/{id}")
    ResponseEntity<ExportJobModels.Status> status(Authentication actor, @PathVariable UUID id);

    @GetMapping("/{id}/file")
    ResponseEntity<byte[]> file(Authentication actor, @PathVariable UUID id);

    @PostMapping("/{id}/ticket")
    ResponseEntity<ReportDownloadTicketResponse> ticket(
            Authentication actor, @PathVariable UUID id);
}
