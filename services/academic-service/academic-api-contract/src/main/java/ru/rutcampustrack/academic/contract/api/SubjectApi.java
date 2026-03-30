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
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.contract.dto.subject.UpdateSubjectRequest;

@Tag(name = "Subjects", description = "Управление предметами")
@RequestMapping("/academic/subjects")
public interface SubjectApi {

    @Operation(summary = "Создать предмет")
    @ApiResponse(responseCode = "201", description = "Предмет создан")
    @PostMapping
    ResponseEntity<EntityModel<SubjectResponse>> createSubject(
            @Valid @RequestBody CreateSubjectRequest request);

    @Operation(summary = "Получить предмет по ID")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Предмет не найден")
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<SubjectResponse>> getSubject(@PathVariable Long id);

    @Operation(summary = "Список предметов")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<SubjectResponse>>> listSubjects(Pageable pageable);

    @Operation(summary = "Обновить предмет")
    @ApiResponse(responseCode = "200", description = "Предмет обновлён")
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<SubjectResponse>> updateSubject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubjectRequest request);

    @Operation(summary = "Удалить предмет")
    @ApiResponse(responseCode = "204", description = "Предмет удалён")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteSubject(@PathVariable Long id);
}
