package ru.rutcampustrack.academic.exportjobs;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.security.RequireRole;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

/**
 * Legacy issue and bearer-free redemption retain fixed paths independent of the jobs API prefix.
 */
@RestController
public class AcademicReportTicketController {
    private final ExportJobService jobs;
    private final RequestContext context;

    public AcademicReportTicketController(ExportJobService jobs, RequestContext context) {
        this.jobs = jobs;
        this.context = context;
    }

    @PostMapping("/academic/report-download-tickets")
    @ApiResponse(responseCode = "200", description = "Report download ticket issued")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "403", description = "No current selectable report session")
    @ApiResponse(responseCode = "429", description = "Ticket issuance limit exceeded")
    @ApiResponse(
            responseCode = "503",
            description = "Current authority or ticket store unavailable",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    @RequireRole(
            value = {UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT},
            adminShared = true)
    public ResponseEntity<ReportDownloadTicketResponse> legacy(
            @Valid @RequestBody IssueReportDownloadTicketRequest report) {
        var actor = context.getClaims();
        if (actor == null) throw ExportJobRepository.error(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(jobs.legacyTicket(actor, report));
    }

    @SecurityRequirements
    @GetMapping("/academic/report-download/{ticket}")
    @ApiResponse(
            responseCode = "200",
            description = "Report artifact redeemed by its short-lived ticket",
            headers = {
                @Header(
                        name = "Content-Disposition",
                        description = "Attachment filename",
                        schema = @Schema(type = "string")),
                @Header(
                        name = "Cache-Control",
                        schema = @Schema(type = "string", allowableValues = {"no-store"}))
            },
            content = {
                @Content(
                        mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        schema = @Schema(type = "string", format = "binary")),
                @Content(
                        mediaType = "application/pdf",
                        schema = @Schema(type = "string", format = "binary")),
                @Content(
                        mediaType = "application/zip",
                        schema = @Schema(type = "string", format = "binary")),
                @Content(
                        mediaType = "image/png",
                        schema = @Schema(type = "string", format = "binary")),
                @Content(
                        mediaType = "text/html",
                        schema = @Schema(type = "string", format = "binary")),
                @Content(
                        mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        schema = @Schema(type = "string", format = "binary"))
            })
    public ResponseEntity<byte[]> ticketFile(@PathVariable String ticket) {
        return AcademicExportJobController.download(jobs.downloadTicket(ticket));
    }
}
