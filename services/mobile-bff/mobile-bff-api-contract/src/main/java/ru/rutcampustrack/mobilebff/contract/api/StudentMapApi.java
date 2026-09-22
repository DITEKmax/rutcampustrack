package ru.rutcampustrack.mobilebff.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.Format;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.ManifestResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels.PlanResponse;

import java.util.UUID;

@Tag(name = "Student campus map", description = "Authorized campus map metadata and private assets")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/student/map")
public interface StudentMapApi {

    @Operation(summary = "Read the authorized campus map manifest")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordered campus map manifest"),
            @ApiResponse(responseCode = "304", description = "Manifest unchanged"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session"),
            @ApiResponse(responseCode = "503", description = "Map catalog unavailable")
    })
    @GetMapping("/manifest")
    ResponseEntity<ManifestResponse> getManifest(
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch);

    @Operation(summary = "Read the current plan metadata for one authorized floor")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan metadata, including independent format states"),
            @ApiResponse(responseCode = "204", description = "No plan published for the floor"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session"),
            @ApiResponse(responseCode = "403", description = "Floor is outside the current student scope"),
            @ApiResponse(responseCode = "404", description = "Building or floor not found"),
            @ApiResponse(responseCode = "503", description = "Map catalog unavailable")
    })
    @GetMapping("/buildings/{buildingId}/floors/{floorId}/plan")
    ResponseEntity<PlanResponse> getFloorPlan(
            @PathVariable String buildingId,
            @PathVariable String floorId);

    @Operation(summary = "Stream one validated private map asset")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Validated asset bytes"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session"),
            @ApiResponse(responseCode = "403", description = "Asset is outside the current student scope"),
            @ApiResponse(responseCode = "404", description = "Asset not found"),
            @ApiResponse(responseCode = "409", description = "Asset is processing or failed validation")
    })
    @GetMapping("/buildings/{buildingId}/floors/{floorId}/plans/{version}/assets/{format}/{assetId}")
    ResponseEntity<Resource> readAsset(
            @PathVariable String buildingId,
            @PathVariable String floorId,
            @PathVariable String version,
            @PathVariable Format format,
            @PathVariable String assetId);

    @Operation(summary = "Record one explicit floor-open intent")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Open intent accepted or already counted"),
            @ApiResponse(responseCode = "400", description = "Invalid idempotency key"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session"),
            @ApiResponse(responseCode = "403", description = "Floor is outside the current student scope"),
            @ApiResponse(responseCode = "404", description = "Building or floor not found"),
            @ApiResponse(responseCode = "409", description = "Intent is already bound to another floor"),
            @ApiResponse(responseCode = "503", description = "Map usage is unavailable")
    })
    @PostMapping("/buildings/{buildingId}/floors/{floorId}/opens")
    ResponseEntity<Void> recordFloorOpen(
            @PathVariable String buildingId,
            @PathVariable String floorId,
            @RequestHeader(name = "Idempotency-Key") @NotNull UUID intentId);
}


