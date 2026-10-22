package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.academic.contract.dto.exportjobs.AcademicExportJobModels.*;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketResponse;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.util.UUID;

/** Gateway retains the legacy public alias; Academic owns every operation. */
@RequestMapping("/academic/export-jobs")
public interface AcademicExportJobApi {
    @PostMapping
    @ApiResponse(
            responseCode = "202",
            description = "Durable report accepted; poll the returned job until ready or error",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Status.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Current authority or durable export storage unavailable",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<Status> create(@Valid @RequestBody Create request);

    @GetMapping("/{id}")
    ResponseEntity<Status> status(@PathVariable UUID id);

    @GetMapping("/{id}/file")
    @ApiResponse(
            responseCode = "200",
            description = "Current authorized report artifact",
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
    ResponseEntity<byte[]> file(@PathVariable UUID id);

    @PostMapping("/{id}/ticket")
    ResponseEntity<ReportDownloadTicketResponse> ticket(@PathVariable UUID id);
}
