package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.auth.dto.DevicePreferencesResponse;
import ru.rutcampustrack.auth.dto.UpdateDevicePreferencesRequest;

@Tag(
        name = "Device preferences",
        description = "Browser preferences independent of the signed-in account")
@RequestMapping("/auth/device-preferences")
public interface DevicePreferencesApi {
    String COOKIE_NAME = "__Host-rct_device";

    @Operation(summary = "Read or establish the current browser preference context")
    @ApiResponse(
            responseCode = "200",
            description = "Preference and decimal CAS revision; null theme means no choice yet")
    @ApiResponse(responseCode = "401", description = "Live bearer session required")
    @ApiResponse(
            responseCode = "503",
            description = "DEVICE_PREFERENCES_UNAVAILABLE or AUTHORITY_UNAVAILABLE")
    @GetMapping
    ResponseEntity<DevicePreferencesResponse> getDevicePreferences(
            @CookieValue(name = COOKIE_NAME, required = false) String selector,
            Authentication authentication);

    @Operation(summary = "Save a theme with compare-and-set revision")
    @ApiResponse(
            responseCode = "200",
            description = "Durably committed preference and new revision")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "401", description = "Live bearer session required")
    @ApiResponse(
            responseCode = "409",
            description =
                    "DEVICE_CONTEXT_LOST or DEVICE_REVISION_CONFLICT; read current context before retry")
    @ApiResponse(
            responseCode = "503",
            description =
                    "DEVICE_PREFERENCES_UNAVAILABLE or AUTHORITY_UNAVAILABLE; read back before retrying an unknown write")
    @PutMapping
    ResponseEntity<DevicePreferencesResponse> putDevicePreferences(
            @CookieValue(name = COOKIE_NAME, required = false) String selector,
            @Valid @RequestBody UpdateDevicePreferencesRequest request,
            Authentication authentication);
}
