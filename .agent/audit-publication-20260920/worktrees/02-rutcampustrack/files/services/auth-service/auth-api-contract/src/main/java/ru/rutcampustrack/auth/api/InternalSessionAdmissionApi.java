package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.AuthAdmissionRequest;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;

@Hidden
@Tag(name = "Internal authentication", description = "Service-to-service session admission")
@RequestMapping("/internal/auth")
public interface InternalSessionAdmissionApi {

    @Operation(summary = "Admit an access token against live session authority")
    @ApiResponse(responseCode = "200", description = "Current live session claims and internal token")
    @ApiResponse(responseCode = "401", description = "Access token is invalid or not eligible for admission")
    @ApiResponse(responseCode = "503", description = "Session authority is unavailable")
    @PostMapping("/admit")
    ResponseEntity<AuthAdmissionResponse> admit(
            @Valid @RequestBody AuthAdmissionRequest request);
}
