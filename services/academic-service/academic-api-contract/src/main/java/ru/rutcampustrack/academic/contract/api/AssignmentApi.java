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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignTeacherRequest;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignmentResponse;

/**
 * REST API contract for teacher-subject-group assignment management.
 */
@Tag(name = "Assignments", description = "Назначения преподавателей на предметы и группы")
@RequestMapping("/academic/assignments")
public interface AssignmentApi {

    @Operation(summary = "Назначить преподавателя на предмет/группу/семестр (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Назначение создано"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Преподаватель/предмет/группа/семестр не найдены"),
            @ApiResponse(responseCode = "409", description = "Назначение уже существует")
    })
    @PostMapping
    ResponseEntity<EntityModel<AssignmentResponse>> assignTeacher(
            @Valid @RequestBody AssignTeacherRequest request);

    @Operation(summary = "Список назначений для группы и семестра")
    @ApiResponse(responseCode = "200", description = "Список назначений")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> listAssignments(
            @RequestParam Long groupId,
            @RequestParam Long semesterId,
            Pageable pageable,
            PagedResourcesAssembler<AssignmentResponse> assembler);

    @Operation(summary = "Удалить назначение (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Назначение удалено"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Назначение не найдено")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> removeAssignment(@PathVariable Long id);

    @Operation(summary = "Мои назначения (TEACHER)", description = "Возвращает все предметы и группы текущего преподавателя для активного семестра.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список назначений"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @GetMapping("/my")
    ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> getMyAssignments(
            Pageable pageable,
            PagedResourcesAssembler<AssignmentResponse> assembler);
}
