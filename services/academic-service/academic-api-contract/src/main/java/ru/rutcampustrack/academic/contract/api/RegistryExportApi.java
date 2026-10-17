package ru.rutcampustrack.academic.contract.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.academic.contract.dto.export.RegistryExportModels;

@RequestMapping("/academic/registry-exports")
public interface RegistryExportApi {
    @GetMapping("/groups/formats")
    ResponseEntity<List<RegistryExportModels.FormatOption>> groupFormats();

    @GetMapping("/users/formats")
    ResponseEntity<List<RegistryExportModels.FormatOption>> userFormats();

    @PostMapping("/groups")
    ResponseEntity<byte[]> groups(
            @Valid @RequestBody RegistryExportModels.Groups request,
            @RequestHeader(value = "X-Export-Job-Id", required = false) UUID jobId,
            @RequestHeader(value = "X-Export-Job-Lease", required = false) UUID leaseId);

    @PostMapping("/users")
    ResponseEntity<byte[]> users(
            @Valid @RequestBody RegistryExportModels.Users request,
            @RequestHeader(value = "X-Export-Job-Id", required = false) UUID jobId,
            @RequestHeader(value = "X-Export-Job-Lease", required = false) UUID leaseId);
}
