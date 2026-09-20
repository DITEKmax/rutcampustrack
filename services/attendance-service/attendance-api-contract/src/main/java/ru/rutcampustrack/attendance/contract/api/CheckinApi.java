package ru.rutcampustrack.attendance.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinResponse;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

/**
 * Contract interface for geo-checkin API (D-06).
 * Mappings declared here — controller implements this interface.
 */
@Tag(name = "Checkin", description = "Геоотметка студентов")
@RequestMapping("/attendance")
public interface CheckinApi {

    @Operation(
            summary = "Геоотметка студента",
            description = "Устаревший путь отключён. Используйте канонический student check-in API.",
            deprecated = true
    )
    @Deprecated(forRemoval = false)
    @ApiResponses({
            @ApiResponse(responseCode = "410", description = "Устаревший путь отключён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/checkin")
    ResponseEntity<EntityModel<CheckinResponse>> checkin(@Valid @RequestBody CheckinRequest request);
}
