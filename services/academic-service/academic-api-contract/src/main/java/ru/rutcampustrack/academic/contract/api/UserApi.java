package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.enums.UserRole;

@Tag(name = "Users", description = "Управление пользователями")
@RequestMapping("/academic/users")
public interface UserApi {

    @Operation(summary = "Создать пользователя (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Пользователь создан")
    @PostMapping
    ResponseEntity<EntityModel<UserCreatedResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request);

    @Operation(summary = "Получить пользователя по ID")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> getUser(@PathVariable Long id);

    @Operation(summary = "Список пользователей")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<UserResponse>>> listUsers(
            @RequestParam(required = false) UserRole role,
            Pageable pageable);

    @Operation(summary = "Полное обновление пользователя (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request);

    @Operation(summary = "Частичное обновление пользователя (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @PatchMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> patchUser(
            @PathVariable Long id,
            @Valid @RequestBody PatchUserRequest request);

    @Operation(summary = "Архивировать пользователя (ADMIN)")
    @ApiResponse(responseCode = "204", description = "Пользователь архивирован")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> archiveUser(@PathVariable Long id);

    @Operation(summary = "Перевести студента в другую группу (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Студент переведён")
    @PostMapping("/{id}/transfer")
    ResponseEntity<EntityModel<UserResponse>> transferStudent(
            @PathVariable Long id,
            @Valid @RequestBody TransferStudentRequest request);

    @Operation(summary = "Профиль текущего пользователя")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/me")
    ResponseEntity<EntityModel<UserResponse>> getMe();
}
