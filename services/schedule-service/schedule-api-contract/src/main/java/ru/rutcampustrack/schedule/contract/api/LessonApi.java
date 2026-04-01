package ru.rutcampustrack.schedule.contract.api;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.schedule.contract.dto.lesson.CancelLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.GeoBlockRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.LessonResponse;
import ru.rutcampustrack.schedule.contract.dto.lesson.MassCancelRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.MassCancelResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API contract for lesson operations and schedule view.
 * HTTP mappings are defined ONLY here per contract-first rule.
 * Controllers implement this interface without duplicating annotations.
 */
@Tag(name = "Lessons", description = "Управление уроками и расписание")
@RequestMapping("/schedule")
public interface LessonApi {

    @Operation(summary = "Отменить урок (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Урок отменён"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Урок не найден"),
            @ApiResponse(responseCode = "422", description = "Недопустимый переход статуса")
    })
    @PatchMapping("/lessons/{id}/cancel")
    ResponseEntity<EntityModel<LessonResponse>> cancelLesson(
            @PathVariable Long id,
            @Valid @RequestBody CancelLessonRequest request);

    @Operation(summary = "Восстановить отменённый урок (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Урок восстановлен"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Урок не найден"),
            @ApiResponse(responseCode = "422", description = "Недопустимый переход статуса")
    })
    @PatchMapping("/lessons/{id}/restore")
    ResponseEntity<EntityModel<LessonResponse>> restoreLesson(@PathVariable Long id);

    @Operation(summary = "Массовая отмена уроков для группы (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Уроки отменены"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "503", description = "Academic Service недоступен")
    })
    @PostMapping("/lessons/mass-cancel")
    ResponseEntity<MassCancelResponse> massCancelLessons(@Valid @RequestBody MassCancelRequest request);

    @Operation(summary = "Включить/выключить гео-блокировку урока (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Гео-блокировка изменена"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Урок не найден")
    })
    @PatchMapping("/lessons/{id}/geo-block")
    ResponseEntity<EntityModel<LessonResponse>> toggleGeoBlock(
            @PathVariable Long id,
            @Valid @RequestBody GeoBlockRequest request);

    @Operation(summary = "Получить расписание группы за диапазон дат (все роли)")
    @ApiResponse(responseCode = "200", description = "Расписание группы")
    @GetMapping("/groups/{groupId}/lessons")
    ResponseEntity<PagedModel<EntityModel<LessonResponse>>> getLessons(
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) List<LessonStatus> status,
            Pageable pageable,
            PagedResourcesAssembler<LessonResponse> assembler);
}
