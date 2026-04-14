package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.format.annotation.DateTimeFormat;
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
import ru.rutcampustrack.academic.contract.dto.semester.CreateSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.DeleteSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.OverlapCheckResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterResponse;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;

import java.time.LocalDate;

/**
 * REST API contract for academic semester management.
 */
@Tag(name = "Semesters", description = "Управление семестрами")
@RequestMapping("/academic/semesters")
public interface SemesterApi {

    @Operation(summary = "Создать семестр (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Семестр создан"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @PostMapping
    ResponseEntity<EntityModel<SemesterResponse>> createSemester(
            @Valid @RequestBody CreateSemesterRequest request);

    @Operation(summary = "Получить семестр по ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Семестр найден"),
            @ApiResponse(responseCode = "404", description = "Семестр не найден")
    })
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<SemesterResponse>> getSemester(@PathVariable Long id);

    @Operation(summary = "Список семестров")
    @ApiResponse(responseCode = "200", description = "Список семестров")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<SemesterResponse>>> listSemesters(
            Pageable pageable,
            PagedResourcesAssembler<SemesterResponse> assembler);

    @Operation(summary = "Полное обновление семестра (PUT, ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Семестр обновлён"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Семестр не найден")
    })
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<SemesterResponse>> updateSemester(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSemesterRequest request);

    @Operation(summary = "Удалить семестр с подтверждением (ADMIN)", description = "Требует фразу подтверждения для защиты от случайного удаления активного семестра.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Семестр удалён"),
            @ApiResponse(responseCode = "400", description = "Неверная фраза подтверждения"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Семестр не найден")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteSemester(
            @PathVariable Long id,
            @Valid @RequestBody DeleteSemesterRequest request);

    @Operation(summary = "Активировать семестр (ADMIN)", description = "Деактивирует все остальные семестры и активирует указанный.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Семестр активирован"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Семестр не найден")
    })
    @PatchMapping("/{id}/activate")
    ResponseEntity<EntityModel<SemesterResponse>> activateSemester(@PathVariable Long id);

    @Operation(summary = "Проверка пересечения дат семестра (ADMIN)",
            description = "Используется frontend async-валидатором semester-dialog для предварительной проверки дат перед сабмитом.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Результат проверки"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @GetMapping("/overlap")
    ResponseEntity<OverlapCheckResponse> checkOverlap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long excludeId);
}
