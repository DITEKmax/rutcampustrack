package ru.rutcampustrack.attendance.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinRequestResponse;
import ru.rutcampustrack.attendance.contract.exception.ErrorResponse;

/**
 * Contract for late-checkin request REST API.
 *
 * Flow: student's geo-checkin failed → student asks headman to confirm presence.
 * Headman receives Telegram message with inline buttons. The bot publishes the
 * decision via RabbitMQ — there is no REST endpoint for the decision itself.
 */
@Tag(name = "Late Check-in", description = "Запросы на подтверждение присутствия через старосту")
@RequestMapping("/attendance/late-checkin")
public interface LateCheckinApi {

    @Operation(
            summary = "Попросить старосту отметить",
            description = "Студент создаёт запрос на подтверждение присутствия на активной паре. "
                    + "Староста получает уведомление в Telegram с кнопками одобрения/отклонения."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Запрос создан"),
            @ApiResponse(responseCode = "403", description = "Старосты не создают запросы (самоотметка через журнал)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Занятие не найдено или не принадлежит группе студента",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Запрос на этот урок уже существует или студент уже отмечен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Пара уже не активна",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{lessonId}")
    ResponseEntity<EntityModel<LateCheckinRequestResponse>> createRequest(@PathVariable Long lessonId);
}
