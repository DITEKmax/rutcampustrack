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
import ru.rutcampustrack.academic.contract.dto.subject.AddSubjectTeacherRequest;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.contract.dto.subject.UpdateSubjectRequest;

import java.time.LocalDate;

/**
 * REST API contract for subject management.
 */
@Tag(name = "Subjects", description = "Управление предметами")
@RequestMapping("/academic/subjects")
public interface SubjectApi {

    @Operation(summary = "Создать предмет (HEADMAN)")
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

    @Operation(summary = "Полное обновление предмета (PUT, HEADMAN)")
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

    @Operation(summary = "Удалить предмет (HEADMAN)",
            description = "Удаление отклоняется при наличии назначений или ссылок расписания; force не обходит историю.")
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

    @Operation(summary = "Добавить преподавателя к предмету (HEADMAN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Преподаватель добавлен"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Предмет не найден"),
            @ApiResponse(responseCode = "409", description = "Преподаватель уже назначен")
    })
    @PostMapping("/{id}/teachers/{teacherId}")
    ResponseEntity<EntityModel<ru.rutcampustrack.academic.contract.dto.assignment.AssignmentResponse>> addTeacher(
            @PathVariable Long id,
            @PathVariable Long teacherId,
            @Valid @RequestBody AddSubjectTeacherRequest request);

    @Operation(summary = "Закрыть назначение преподавателя (HEADMAN)",
            description = "До активации общего lifecycle fence операция возвращает typed 409 без мутаций.")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "Закрытие назначения пока недоступно"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Назначение не найдено")
    })
    @DeleteMapping("/{id}/teachers/{teacherId}")
    ResponseEntity<Void> removeTeacher(
            @PathVariable Long id,
            @PathVariable Long teacherId,
            @RequestParam Long assignmentId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate validUntilExclusive);
}
