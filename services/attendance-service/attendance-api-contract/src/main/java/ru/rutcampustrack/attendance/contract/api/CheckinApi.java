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
import ru.rutcampustrack.attendance.contract.exception.ErrorResponse;

/**
 * Contract interface for geo-checkin API (D-06).
 * Mappings declared here — controller implements this interface.
 */
@Tag(name = "Checkin", description = "Геоотметка студентов")
@RequestMapping("/attendance")
public interface CheckinApi {

    @Operation(
            summary = "Геоотметка студента",
            description = "Студент отправляет координаты. Система проверяет геофенс, активную пару и временное окно."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отметка успешно поставлена"),
            @ApiResponse(responseCode = "403", description = "Геоотметка заблокирована для данной пары",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Активная пара не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Отметка уже существует",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Координаты вне зоны геофенса",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Превышен лимит запросов",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/checkin")
    ResponseEntity<EntityModel<CheckinResponse>> checkin(@Valid @RequestBody CheckinRequest request);
}
