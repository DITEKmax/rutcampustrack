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
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

/** Internal live-session check for an already authenticated WebSocket binding. */
@Hidden
@Tag(name = "Internal", description = "Service-to-service authentication endpoints")
@RequestMapping("/internal/auth")
public interface InternalWsSessionAdmissionApi {

    @Operation(summary = "Check live WebSocket session authority")
    @ApiResponse(responseCode = "204", description = "The bound selected identity is still live")
    @ApiResponse(responseCode = "401", description = "The bound session or selected identity is no longer valid")
    @ApiResponse(responseCode = "503", description = "Session authority is unavailable")
    @PostMapping("/admit-ws-session")
    ResponseEntity<Void> admitWsSession(@Valid @RequestBody WsSessionAdmissionRequest request);
}
