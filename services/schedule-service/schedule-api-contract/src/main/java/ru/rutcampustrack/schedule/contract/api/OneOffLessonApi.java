package ru.rutcampustrack.schedule.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.oneoff.OneOffLessonResponse;

import java.time.LocalDate;

/**
 * REST API contract for one-off lesson management (Phase 60-03, D-04/D-08/D-09/D-22).
 * HTTP mappings are defined ONLY here per contract-first rule.
 * Controllers implement this interface without duplicating annotations.
 */
@Tag(name = "One-off Lessons", description = "Разовые пары старосты")
@RequestMapping("/schedule/one-off-lessons")
public interface OneOffLessonApi {

    @Operation(summary = "Создать разовую пару (HEADMAN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Разовая пара создана"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа (не староста этой группы)"),
            @ApiResponse(responseCode = "409", description = "Слот занят активным шаблоном или дубль разовой пары"),
            @ApiResponse(responseCode = "503", description = "Academic Service недоступен")
    })
    @PostMapping
    ResponseEntity<EntityModel<OneOffLessonResponse>> createOneOffLesson(
            @Valid @RequestBody CreateOneOffLessonRequest request);

    @Operation(summary = "Список разовых пар для группы в диапазоне дат")
    @ApiResponse(responseCode = "200", description = "Список разовых пар")
    @GetMapping
    ResponseEntity<CollectionModel<EntityModel<OneOffLessonResponse>>> listOneOffLessons(
            @RequestParam Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo);

    @Operation(summary = "Удалить разовую пару (HEADMAN, любая дата D-22)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Разовая пара удалена"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Разовая пара не найдена")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteOneOffLesson(@PathVariable Long id);
}
