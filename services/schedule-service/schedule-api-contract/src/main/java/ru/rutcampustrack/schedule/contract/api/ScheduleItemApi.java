package ru.rutcampustrack.schedule.contract.api;

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
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemResponse;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;

/**
 * REST API contract for schedule template (ScheduleItem) management.
 * HTTP mappings are defined ONLY here per contract-first rule.
 * Controllers implement this interface without duplicating annotations.
 */
@Tag(name = "Schedule Items", description = "Управление шаблонами расписания")
@RequestMapping("/schedule/items")
public interface ScheduleItemApi {

    @Operation(summary = "Создать шаблон расписания (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Шаблон создан"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "503", description = "Academic Service недоступен")
    })
    @PostMapping
    ResponseEntity<EntityModel<ScheduleItemResponse>> createScheduleItem(
            @Valid @RequestBody CreateScheduleItemRequest request);

    @Operation(summary = "Получить шаблон по ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Шаблон найден"),
            @ApiResponse(responseCode = "404", description = "Шаблон не найден")
    })
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<ScheduleItemResponse>> getScheduleItem(@PathVariable Long id);

    @Operation(summary = "Список шаблонов расписания для группы и семестра")
    @ApiResponse(responseCode = "200", description = "Список шаблонов")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<ScheduleItemResponse>>> listScheduleItems(
            @RequestParam Long groupId,
            @RequestParam Long semesterId,
            Pageable pageable,
            PagedResourcesAssembler<ScheduleItemResponse> assembler);

    @Operation(summary = "Полное обновление шаблона (PUT, HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Шаблон обновлён"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Шаблон не найден"),
            @ApiResponse(responseCode = "409", description = "Конфликт данных"),
            @ApiResponse(responseCode = "503", description = "Academic Service недоступен")
    })
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<ScheduleItemResponse>> updateScheduleItem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateScheduleItemRequest request);

    @Operation(summary = "Деактивировать шаблон расписания (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Шаблон деактивирован"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Шаблон не найден")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteScheduleItem(@PathVariable Long id);
}
