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
import ru.rutcampustrack.academic.contract.dto.group.CreateAdminGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupRegistryResponse;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.GroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentPreviewResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanRosterResponse;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewRequest;
import ru.rutcampustrack.academic.contract.dto.group.PromotionExecuteRequest;
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

    /** Additive ADMIN registry create contract; the server assembles name/status. */
    @Operation(summary = "Создать группу в реестре ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Группа создана как черновик"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "409", description = "Код группы уже существует")
    })
    @PostMapping("/registry")
    ResponseEntity<AdminGroupResponse> createAdminGroup(
            @Valid @RequestBody CreateAdminGroupRequest request);

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

    /**
     * 58-06 / BUG-006-6: Список групп с фильтром по жизненному циклу и поиском по имени.
     *
     * <p>{@code status=ACTIVE} по умолчанию; {@code search} — ILIKE по name.
     */
    @Operation(summary = "Список групп: фильтр по статусу + поиск (ADMIN/TEACHER)")
    @ApiResponse(responseCode = "200", description = "Список групп")
    @GetMapping(params = "status")
    ResponseEntity<PagedModel<EntityModel<GroupResponse>>> listGroupsByStatus(
            @RequestParam(defaultValue = "ACTIVE") GroupStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable,
            PagedResourcesAssembler<GroupResponse> assembler);

    /** Server-computed ACTIVE/DRAFT/ARCHIVED registry with tab counts. */
    @Operation(summary = "Реестр групп ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Страница реестра групп"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @GetMapping("/registry")
    AdminGroupRegistryResponse listAdminGroups(
            @RequestParam(defaultValue = "ACTIVE") AdminGroupStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable);

    /** ADMIN roster used to choose a group headman. */
    @Operation(summary = "Состав группы для назначения старосты (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состав группы"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Группа не найдена"),
            @ApiResponse(responseCode = "409", description = "Группа архивна или содержит конфликтующие данные")
    })
    @GetMapping("/{id}/headman/roster")
    HeadmanRosterResponse getHeadmanRoster(@PathVariable Long id);

    /** Server-only consequence preview; no durable mutation. */
    @Operation(summary = "Предпросмотр назначения старосты (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Предпросмотр"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Группа или студент не найдены"),
            @ApiResponse(responseCode = "409", description = "Студент не состоит в группе")
    })
    @GetMapping("/{id}/headman/preview")
    HeadmanAssignmentPreviewResponse previewHeadman(
            @PathVariable Long id,
            @RequestParam Long studentId);

    /** Canonical CAS-protected headman mutation. */
    @Operation(summary = "Назначить старосту (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Староста назначен"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Группа или студент не найдены"),
            @ApiResponse(responseCode = "409", description = "Устаревшее состояние или конфликт назначения")
    })
    @PutMapping("/{id}/headman")
    HeadmanAssignmentResponse assignHeadman(
            @PathVariable Long id,
            @Valid @RequestBody AssignHeadmanRequest request);

    /**
     * 58-06 / BUG-006-6: Dry-run промоушена групп. Возвращает {@link PromotionSummary}
     * с планом (toPromote / toArchive / skipped / conflicts), без изменений в БД.
     * {@code groupId} отсутствует для массовой операции и задаётся для одиночной.
     */
    @Operation(summary = "Preview промоушена групп (dry-run, ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "План промоушена"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Группа не найдена"),
            @ApiResponse(responseCode = "409", description = "Нет завершённого весеннего семестра")
    })
    @PostMapping("/promote/preview")
    ResponseEntity<PromotionSummary> promotePreview(
            @Valid @RequestBody PromotionPreviewRequest request);

    /**
     * Выполнить только подтверждённый preview. Цикл, область и version должны
     * совпасть с актуальным серверным планом; устаревший запрос получает 409.
     */
    @Operation(summary = "Выполнить промоушен групп (ADMIN). Запускать после preview.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Результат промоушена"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Весенний семестр или группа не найдены"),
            @ApiResponse(responseCode = "409", description = "Предпросмотр устарел или группа уже обработана")
    })
    @PostMapping("/promote")
    ResponseEntity<PromotionSummary> promote(
            @Valid @RequestBody PromotionExecuteRequest request);

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
