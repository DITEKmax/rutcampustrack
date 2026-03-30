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
import ru.rutcampustrack.academic.contract.dto.semester.*;

@Tag(name = "Semesters", description = "Управление семестрами")
@RequestMapping("/academic/semesters")
public interface SemesterApi {

    @Operation(summary = "Создать семестр (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Семестр создан")
    @PostMapping
    ResponseEntity<EntityModel<SemesterResponse>> createSemester(
            @Valid @RequestBody CreateSemesterRequest request);

    @Operation(summary = "Получить семестр по ID")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Семестр не найден")
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<SemesterResponse>> getSemester(@PathVariable Long id);

    @Operation(summary = "Список семестров")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<SemesterResponse>>> listSemesters(Pageable pageable);

    @Operation(summary = "Обновить семестр (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<SemesterResponse>> updateSemester(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSemesterRequest request);

    @Operation(summary = "Удалить семестр с подтверждением (ADMIN)")
    @ApiResponse(responseCode = "204", description = "Семестр удалён")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteSemester(
            @PathVariable Long id,
            @Valid @RequestBody DeleteSemesterRequest request);

    @Operation(summary = "Активировать семестр (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Семестр активирован")
    @PatchMapping("/{id}/activate")
    ResponseEntity<EntityModel<SemesterResponse>> activateSemester(@PathVariable Long id);
}
