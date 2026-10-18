package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TeacherLookupResponse;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateAvatarRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserSummaryResponse;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.util.List;

/**
 * REST API contract for user management. Mappings are defined here only — the controller class
 * implements this interface.
 */
@Tag(name = "Users", description = "Управление пользователями системы")
@RequestMapping("/academic/users")
public interface UserApi {

    @Operation(
            summary = "Создать пользователя",
            description =
                    "Создаёт нового пользователя с автоматической генерацией логина и начального"
                            + " пароля. Только ADMIN.")
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
                responseCode = "201",
                description = "Пользователь создан",
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
                description = "Конфликт данных",
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
    @PostMapping
    ResponseEntity<EntityModel<UserCreatedResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request);

    @Operation(summary = "Получить пользователя по ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Пользователь найден"),
        @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> getUser(@PathVariable Long id);

    @Operation(
            summary = "Список пользователей",
            description =
                    "Постраничный список с опциональным поиском и фильтрами. Только ADMIN. "
                            + "Пример: /academic/users?search=иван&role=STUDENT&page=0&size=20")
    @ApiResponse(responseCode = "200", description = "Список пользователей")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<UserResponse>>> listUsers(
            @Parameter(
                            description =
                                    "Case-insensitive поиск по login / ФИО / telegramId (contains)."
                                            + " BUG-006-1.")
                    @RequestParam(required = false)
                    String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String roleStatus,
            Pageable pageable,
            PagedResourcesAssembler<UserResponse> assembler);

    @Operation(
            summary = "Добавить или обновить роль пользователя",
            description = "Атомарно изменяет один durable role grant. Только ADMIN.")
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
                description = "Роль сохранена",
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
                description = "Недопустимая роль или статус",
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
                responseCode = "404",
                description = "Пользователь не найден",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Конфликт role-data",
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
    @PutMapping("/{id}/roles/{role}")
    ResponseEntity<EntityModel<UserResponse>> updateRoleGrant(
            @PathVariable Long id,
            @PathVariable String role,
            @Valid @RequestBody RoleGrantUpdateRequest request);

    @Operation(summary = "Полное обновление пользователя (PUT). Только ADMIN.")
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
                description = "Пользователь обновлён",
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
                responseCode = "404",
                description = "Пользователь не найден",
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
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> updateUser(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request);

    @Operation(
            summary = "Частичное обновление пользователя (PATCH)",
            description =
                    "Обновляет только переданные поля. Используется для смены головы, перевода,"
                            + " статуса.")
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
                description = "Пользователь обновлён",
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
                responseCode = "404",
                description = "Пользователь не найден",
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
    @PatchMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> patchUser(
            @PathVariable Long id, @Valid @RequestBody PatchUserRequest request);

    @Operation(summary = "Устаревший маршрут: используйте защищённое архивирование")
    @ApiResponses({
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
                description = "Требуется защищённый POST /{id}/archive: protectedroute_required",
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
    @DeleteMapping("/{id}")
    ResponseEntity<Void> archiveUser(@PathVariable Long id);

    @Operation(summary = "Предпросмотр архивирования; количества наблюдаются отдельно по сервисам")
    @GetMapping("/{id}/archive-preview")
    ResponseEntity<ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.Preview>
            previewUserArchive(@PathVariable Long id);

    @Operation(summary = "Архивировать пользователя по подтверждённому предпросмотру")
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
    @PostMapping("/{id}/archive")
    ResponseEntity<Void> archiveUserProtected(
            @PathVariable Long id,
            @Valid @RequestBody
                    ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest
                            request);

    @Operation(summary = "Восстановить аккаунт без активных полномочий; роли назначаются заново")
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
    @PostMapping("/{id}/restore")
    ResponseEntity<Void> restoreUser(
            @PathVariable Long id,
            @Valid @RequestBody
                    ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.RestoreRequest
                            request);

    @Operation(summary = "Перевести студента в другую группу")
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
                description = "Студент переведён",
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
                responseCode = "404",
                description = "Пользователь или группа не найдены",
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
    @PostMapping("/{id}/transfer")
    ResponseEntity<EntityModel<UserResponse>> transferStudent(
            @PathVariable Long id, @Valid @RequestBody TransferStudentRequest request);

    @Operation(summary = "Получить профиль текущего пользователя (из X-User-Id header)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Профиль получен"),
        @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @GetMapping("/me")
    ResponseEntity<EntityModel<UserResponse>> getMe();

    @Operation(
            summary = "Сменить аватар текущего пользователя (BUG-004)",
            description =
                    "Сохраняет идентификатор пресета. Передайте пустой avatarId чтобы сбросить.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Аватар обновлён"),
        @ApiResponse(responseCode = "400", description = "Неверный avatarId"),
        @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @PatchMapping("/me/avatar")
    ResponseEntity<EntityModel<UserResponse>> updateMyAvatar(
            @Valid @RequestBody UpdateAvatarRequest request);

    @Operation(
            summary = "Список преподавателей (без постраничной разбивки)",
            description =
                    "Возвращает всех активных преподавателей. Доступно студентам (для выбора"
                            + " преподавателя предмета).")
    @ApiResponse(responseCode = "200", description = "Список преподавателей")
    @GetMapping("/teachers")
    ResponseEntity<CollectionModel<EntityModel<UserResponse>>> listTeachers();

    @Operation(
            summary = "Поиск преподавателей для предмета",
            description =
                    "Постраничный поиск по фрагменту ФИО или табельного номера. Возвращает только"
                        + " пользователей с действующим durable TEACHER grant; доступно студентам.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница преподавателей"),
        @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @GetMapping("/teachers/search")
    ResponseEntity<PagedModel<EntityModel<TeacherLookupResponse>>> searchTeachers(
            @Parameter(description = "Фрагмент ФИО или табельного номера")
                    @RequestParam(required = false)
                    String search,
            Pageable pageable,
            PagedResourcesAssembler<TeacherLookupResponse> assembler);

    @Operation(
            summary = "Batch-резолв display-имён пользователей по ID",
            description =
                    "Возвращает только {id, fullName} для каждого найденного пользователя. Доступно"
                        + " STUDENT/TEACHER/ADMIN — намеренно не отдаёт PII"
                        + " (login/email/telegramId/role). Используется для подстановки имён в"
                        + " audit-местах (cancelledBy, и т.п.). Cap: max 100 ids на один запрос."
                        + " Несуществующие ID молча пропускаются.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Список найденных профилей (порядок не гарантируется)"),
        @ApiResponse(responseCode = "400", description = "Превышен лимит 100 ids или ids пуст")
    })
    @GetMapping("/by-ids")
    ResponseEntity<CollectionModel<EntityModel<UserSummaryResponse>>> getUsersByIds(
            @Parameter(description = "Список ID пользователей через запятую (1..100 элементов)")
                    @RequestParam
                    List<Long> ids);
}
