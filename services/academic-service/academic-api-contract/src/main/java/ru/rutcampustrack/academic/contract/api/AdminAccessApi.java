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
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessModels.*;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

@RequestMapping("/academic")
public interface AdminAccessApi {
    @GetMapping("/admin-access/catalog")
    ResponseEntity<Catalog> catalog();

    @GetMapping("/admin-access/me")
    ResponseEntity<Profile> me();

    @GetMapping("/users/{id}/admin-access")
    ResponseEntity<Profile> get(@PathVariable long id);

    @Operation(summary = "Изменить полномочия администратора")
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
                                schema = @Schema(implementation = Profile.class)),
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
    @PutMapping("/users/{id}/admin-access")
    ResponseEntity<Profile> put(@PathVariable long id, @RequestBody Put request);

    @Operation(summary = "Отозвать полномочия администратора")
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
                responseCode = "204",
                description = "Операция завершена",
                content = @Content,
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
    @DeleteMapping("/users/{id}/admin-access")
    ResponseEntity<Void> delete(@PathVariable long id, @RequestBody Delete request);
}
