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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkResponse;
import ru.rutcampustrack.attendance.contract.exception.ErrorResponse;

/**
 * Contract interface for manual attendance marking API (D-11).
 * Mappings declared here — controller implements this interface.
 */
@Tag(name = "Marking", description = "Ручная отметка старостой")
@RequestMapping("/attendance")
public interface MarkingApi {

    @Operation(
            summary = "Ручная отметка посещаемости",
            description = "Староста устанавливает статус посещаемости для студента на паре."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус успешно установлен"),
            @ApiResponse(responseCode = "400", description = "Неверный статус или запрос",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён — не старosta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пара или студент не найдены",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/lessons/{lessonId}/students/{userId}")
    ResponseEntity<EntityModel<MarkResponse>> mark(
            @PathVariable Long lessonId,
            @PathVariable Long userId,
            @Valid @RequestBody MarkRequest request);
}
