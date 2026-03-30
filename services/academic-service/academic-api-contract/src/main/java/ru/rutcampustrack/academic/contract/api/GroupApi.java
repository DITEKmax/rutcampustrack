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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;

/**
 * REST API contract for student group management.
 */
@Tag(name = "Groups", description = "Управление учебными группами")
@RequestMapping("/academic/groups")
public interface GroupApi {

    @Operation(summary = "Создать группу (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Группа создана"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "409", description = "Группа с таким кодом уже существует")
    })
    @PostMapping
    ResponseEntity<EntityModel<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request);

    @Operation(summary = "Получить группу по ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Группа найдена"),
            @ApiResponse(responseCode = "404", description = "Группа не найдена")
    })
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<GroupResponse>> getGroup(@PathVariable Long id);

    @Operation(summary = "Список групп с фильтром по активности")
    @ApiResponse(responseCode = "200", description = "Список групп")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<GroupResponse>>> listGroups(
            @RequestParam(required = false) Boolean active,
            Pageable pageable,
            PagedResourcesAssembler<GroupResponse> assembler);

    @Operation(summary = "Полное обновление группы (PUT, ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Группа обновлена"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Группа не найдена")
    })
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<GroupResponse>> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupRequest request);

    @Operation(summary = "Удалить группу (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Группа удалена"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Группа не найдена"),
            @ApiResponse(responseCode = "409", description = "Группа содержит студентов")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteGroup(@PathVariable Long id);

    @Operation(summary = "Участники группы текущего пользователя (STUDENT/HEADMAN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список участников"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @GetMapping("/my/members")
    ResponseEntity<PagedModel<EntityModel<UserResponse>>> getMyGroupMembers(
            Pageable pageable,
            PagedResourcesAssembler<UserResponse> assembler);
}
