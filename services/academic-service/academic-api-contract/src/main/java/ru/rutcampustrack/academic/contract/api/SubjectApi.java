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
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.contract.dto.subject.UpdateSubjectRequest;

/**
 * REST API contract for subject management.
 */
@Tag(name = "Subjects", description = "Управление предметами")
@RequestMapping("/academic/subjects")
public interface SubjectApi {

    @Operation(summary = "Создать предмет (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Предмет создан"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @PostMapping
    ResponseEntity<EntityModel<SubjectResponse>> createSubject(
            @Valid @RequestBody CreateSubjectRequest request);

    @Operation(summary = "Получить предмет по ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Предмет найден"),
            @ApiResponse(responseCode = "404", description = "Предмет не найден")
    })
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<SubjectResponse>> getSubject(@PathVariable Long id);

    @Operation(summary = "Список предметов (HEADMAN — только своя группа, ADMIN — все)")
    @ApiResponse(responseCode = "200", description = "Список предметов")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<SubjectResponse>>> listSubjects(
            Pageable pageable,
            PagedResourcesAssembler<SubjectResponse> assembler);

    @Operation(summary = "Полное обновление предмета (PUT, HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Предмет обновлён"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Предмет не найден")
    })
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<SubjectResponse>> updateSubject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubjectRequest request);

    @Operation(summary = "Удалить предмет (HEADMAN/ADMIN)",
            description = "Если у предмета есть уроки с историей посещаемости, " +
                    "возвращается 409 со счётчиками в extras. Повторный запрос " +
                    "с force=true пропускает pre-check и удаляет данные каскадно.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Предмет удалён"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Предмет не найден"),
            @ApiResponse(responseCode = "409", description = "Предмет используется в расписании (есть посещаемость)")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteSubject(@PathVariable Long id,
                                        @RequestParam(name = "force", defaultValue = "false") boolean force);

    // =========================================================================
    // Phase 60-01 / D-19: управление преподавателями существующего предмета
    // =========================================================================

    @Operation(summary = "Добавить преподавателя к предмету (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Преподаватель добавлен"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Предмет не найден"),
            @ApiResponse(responseCode = "409", description = "Преподаватель уже назначен")
    })
    @PostMapping("/{id}/teachers/{teacherId}")
    ResponseEntity<Void> addTeacher(@PathVariable Long id, @PathVariable Long teacherId);

    @Operation(summary = "Удалить преподавателя из предмета (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Преподаватель удалён"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Назначение не найдено")
    })
    @DeleteMapping("/{id}/teachers/{teacherId}")
    ResponseEntity<Void> removeTeacher(@PathVariable Long id, @PathVariable Long teacherId);
}
