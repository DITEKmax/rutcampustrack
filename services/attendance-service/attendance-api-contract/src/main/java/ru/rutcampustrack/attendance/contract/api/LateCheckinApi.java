package ru.rutcampustrack.attendance.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinDecisionRequest;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinRequestResponse;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

/**
 * Contract for late-checkin request REST API.
 *
 * Flow: student's geo-checkin failed → student asks headman to confirm presence.
 * Headman can decide through Telegram (bot → RabbitMQ {@code late_checkin.decision})
 * or through a web client (web-panel/PWA/mini-app) using {@link #decideRequest}.
 * Both paths share the same idempotent {@code LateCheckinService.applyDecision}.
 */
@Tag(name = "Late Check-in", description = "Запросы на подтверждение присутствия через старосту")
@RequestMapping("/attendance/late-checkin")
public interface LateCheckinApi {

    @Operation(
            summary = "Попросить старосту отметить",
            description = "Студент создаёт запрос на подтверждение присутствия на активной паре. "
                    + "Староста получает уведомление в Telegram (если привязан) и в веб-кабинете."
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

    @Operation(
            summary = "Список ожидающих запросов группы (для старосты)",
            description = "Возвращает запросы со статусом PENDING, принадлежащие группе старосты. "
                    + "Доступно только пользователю с ролью STUDENT и is_headman=true."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список запросов"),
            @ApiResponse(responseCode = "403", description = "Только староста группы",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/pending")
    ResponseEntity<CollectionModel<EntityModel<LateCheckinRequestResponse>>> listPending();

    @Operation(
            summary = "Решение старосты по запросу (веб-канал)",
            description = "Староста подтверждает или отклоняет запрос из веб-клиента. "
                    + "Эквивалент нажатия inline-кнопки в Telegram. Идемпотентно: повторный вызов "
                    + "для уже решённого запроса возвращает 200 без побочных эффектов."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Решение применено"),
            @ApiResponse(responseCode = "400", description = "Тело запроса невалидно",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Только староста той же группы может решать",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запрос не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{requestId}/decision")
    ResponseEntity<EntityModel<LateCheckinRequestResponse>> decideRequest(
            @PathVariable String requestId,
            @Valid @RequestBody LateCheckinDecisionRequest body);
}
