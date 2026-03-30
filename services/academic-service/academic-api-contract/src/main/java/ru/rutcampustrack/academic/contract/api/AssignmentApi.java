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
import ru.rutcampustrack.academic.contract.dto.assignment.AssignTeacherRequest;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignmentResponse;

@Tag(name = "Assignments", description = "Привязка преподавателей к предметам и группам")
@RequestMapping("/academic/assignments")
public interface AssignmentApi {

    @Operation(summary = "Назначить преподавателя на предмет в группе")
    @ApiResponse(responseCode = "201", description = "Назначение создано")
    @PostMapping
    ResponseEntity<EntityModel<AssignmentResponse>> assignTeacher(
            @Valid @RequestBody AssignTeacherRequest request);

    @Operation(summary = "Список назначений по группе и семестру")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> listAssignments(
            @RequestParam Long groupId,
            @RequestParam Long semesterId,
            Pageable pageable);

    @Operation(summary = "Удалить назначение")
    @ApiResponse(responseCode = "204", description = "Назначение удалено")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> removeAssignment(@PathVariable Long id);

    @Operation(summary = "Мои назначения (для преподавателя)")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/my")
    ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> getMyAssignments(Pageable pageable);
}
