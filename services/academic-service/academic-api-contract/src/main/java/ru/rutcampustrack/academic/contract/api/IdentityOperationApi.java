package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.util.UUID;

@RequestMapping("/academic/identity-operations")
public interface IdentityOperationApi {
    @Operation(
            summary = "Проверить состояние сохранённой операции изменения пользователя или группы")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Текущее сохранённое состояние операции",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        IdentityOperationHttpModels.Operation
                                                                .class)),
                headers = {
                    @Header(
                            name = "X-Operation-Id",
                            schema = @Schema(type = "string", format = "uuid")),
                    @Header(
                            name = "Cache-Control",
                            schema = @Schema(type = "string", example = "no-store"))
                }),
        @ApiResponse(
                responseCode = "401",
                description = "Требуется действующая сессия",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Нет прав доступа",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Операция не найдена или недоступна текущему пользователю",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description =
                        "Актуальное состояние зависимости недоступно; исход операции нельзя считать"
                                + " отклонённым",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{operationId}")
    ResponseEntity<IdentityOperationHttpModels.Operation> getIdentityOperation(
            @PathVariable UUID operationId);

    @Operation(summary = "Восстановить неизменную failed-safe операцию пользователя или группы")
    @Parameter(
            name = "Idempotency-Key",
            in = ParameterIn.HEADER,
            required = false,
            description =
                    "Необязательный стабильный канонический UUID попытки. Повторять ту же команду с"
                            + " тем же ключом; без ключа сервер выдаёт X-Operation-Id.",
            schema =
                    @Schema(
                            type = "string",
                            format = "uuid",
                            pattern =
                                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Операция завершена",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        IdentityOperationHttpModels.Operation
                                                                .class)),
                headers = {
                    @Header(
                            name = "X-Operation-Id",
                            description = "UUID сохранённой операции",
                            schema = @Schema(type = "string", format = "uuid")),
                    @Header(
                            name = "Location",
                            description = "Адрес проверки состояния операции",
                            schema = @Schema(type = "string", format = "uri-reference")),
                    @Header(
                            name = "Cache-Control",
                            schema = @Schema(type = "string", example = "no-store"))
                }),
        @ApiResponse(
                responseCode = "202",
                description =
                        "Операция сохранена; проверьте Location перед повтором команды с прежним"
                                + " ключом",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        IdentityOperationHttpModels.Operation
                                                                .class)),
                headers = {
                    @Header(
                            name = "X-Operation-Id",
                            description = "UUID сохранённой операции",
                            schema = @Schema(type = "string", format = "uuid")),
                    @Header(
                            name = "Location",
                            description = "Адрес проверки состояния операции",
                            schema = @Schema(type = "string", format = "uri-reference")),
                    @Header(
                            name = "Cache-Control",
                            schema = @Schema(type = "string", example = "no-store")),
                    @Header(
                            name = "Retry-After",
                            description = "Задержка перед проверкой состояния в секундах",
                            schema = @Schema(type = "integer", example = "2", minimum = "0"))
                }),
        @ApiResponse(
                responseCode = "400",
                description = "Ошибка валидации",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Требуется действующая сессия",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Нет прав доступа",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Конфликт состояния",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description =
                        "Актуальное состояние зависимости недоступно; исход операции нельзя считать"
                                + " отклонённым",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{operationId}/reconcile")
    ResponseEntity<IdentityOperationHttpModels.Operation> reconcileGroupOperation(
            @PathVariable UUID operationId,
            @jakarta.validation.Valid
                    @org.springframework.web.bind.annotation.RequestBody(required = false)
                    IdentityOperationHttpModels.ReconcileRequest request);

    @Operation(
            summary =
                    "Получить новое подтверждение для восстановления failed-safe архивирования"
                        + " пользователя")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Текущее подтверждение архивирования",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                implementation =
                                                        ru.rutcampustrack.academic.contract.dto.user
                                                                .UserArchiveModels.Preview.class)),
                headers =
                        @Header(
                                name = "Cache-Control",
                                schema = @Schema(type = "string", example = "no-store"))),
        @ApiResponse(
                responseCode = "403",
                description = "Нет актуального права USERS_ARCHIVE",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Операция не найдена",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Нет подтверждённого failed-safe архивирования",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Актуальный snapshot недоступен",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{operationId}/reconcile-preview")
    ResponseEntity<ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.Preview>
            getArchiveReconciliationPreview(@PathVariable UUID operationId);
}
