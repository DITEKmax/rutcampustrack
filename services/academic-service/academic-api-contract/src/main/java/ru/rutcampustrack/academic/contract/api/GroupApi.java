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
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;

@Tag(name = "Groups", description = "Управление учебными группами")
@RequestMapping("/academic/groups")
public interface GroupApi {

    @Operation(summary = "Создать группу (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Группа создана")
    @PostMapping
    ResponseEntity<EntityModel<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request);

    @Operation(summary = "Получить группу по ID")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Группа не найдена")
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<GroupResponse>> getGroup(@PathVariable Long id);

    @Operation(summary = "Список групп")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<GroupResponse>>> listGroups(
            @RequestParam(required = false) Boolean active,
            Pageable pageable);

    @Operation(summary = "Обновить группу (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<GroupResponse>> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupRequest request);

    @Operation(summary = "Удалить группу (ADMIN)")
    @ApiResponse(responseCode = "204", description = "Группа удалена")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteGroup(@PathVariable Long id);

    @Operation(summary = "Состав моей группы (STUDENT/HEADMAN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/my/members")
    ResponseEntity<PagedModel<EntityModel<?>>> getMyGroupMembers(Pageable pageable);
}
