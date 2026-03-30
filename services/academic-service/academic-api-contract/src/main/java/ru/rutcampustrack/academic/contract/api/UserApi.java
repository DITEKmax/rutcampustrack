package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
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
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;

/**
 * REST API contract for user management.
 * Mappings are defined here only — the controller class implements this interface.
 */
@Tag(name = "Users", description = "Управление пользователями системы")
@RequestMapping("/academic/users")
public interface UserApi {

    @Operation(summary = "Создать пользователя", description = "Создаёт нового пользователя с автоматической генерацией логина и начального пароля. Только ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь создан"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "409", description = "Конфликт данных")
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

    @Operation(summary = "Список пользователей", description = "Постраничный список с фильтром по роли. Только ADMIN.")
    @ApiResponse(responseCode = "200", description = "Список пользователей")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<UserResponse>>> listUsers(
            @RequestParam(required = false) UserRole role,
            Pageable pageable,
            PagedResourcesAssembler<UserResponse> assembler);

    @Operation(summary = "Полное обновление пользователя (PUT). Только ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пользователь обновлён"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request);

    @Operation(summary = "Частичное обновление пользователя (PATCH)", description = "Обновляет только переданные поля. Используется для смены головы, перевода, статуса.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пользователь обновлён"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @PatchMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> patchUser(
            @PathVariable Long id,
            @Valid @RequestBody PatchUserRequest request);

    @Operation(summary = "Архивировать пользователя (soft delete). Только ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Пользователь архивирован"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> archiveUser(@PathVariable Long id);

    @Operation(summary = "Перевести студента в другую группу")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Студент переведён"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Пользователь или группа не найдены")
    })
    @PostMapping("/{id}/transfer")
    ResponseEntity<EntityModel<UserResponse>> transferStudent(
            @PathVariable Long id,
            @Valid @RequestBody TransferStudentRequest request);

    @Operation(summary = "Получить профиль текущего пользователя (из X-User-Id header)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Профиль получен"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @GetMapping("/me")
    ResponseEntity<EntityModel<UserResponse>> getMe();
}
