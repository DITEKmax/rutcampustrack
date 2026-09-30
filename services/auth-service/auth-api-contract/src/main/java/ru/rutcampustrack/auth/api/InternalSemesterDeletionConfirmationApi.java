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
import ru.rutcampustrack.auth.dto.ConfirmSemesterDeletionRequest;

@Hidden
@Tag(name = "Internal authentication", description = "Semester deletion password confirmation")
@RequestMapping("/internal/auth")
public interface InternalSemesterDeletionConfirmationApi {

    @Operation(summary = "Confirm a semester deletion with the current ADMIN password")
    @ApiResponse(responseCode = "204", description = "Password proof accepted")
    @ApiResponse(responseCode = "401", description = "The bound session is no longer valid")
    @ApiResponse(responseCode = "403", description = "Password or ADMIN role was denied")
    @ApiResponse(responseCode = "429", description = "Confirmation attempts are limited")
    @ApiResponse(responseCode = "503", description = "Authentication authority is unavailable")
    @PostMapping("/confirm-semester-deletion")
    ResponseEntity<Void> confirmSemesterDeletion(
            @Valid @RequestBody ConfirmSemesterDeletionRequest request);
}
