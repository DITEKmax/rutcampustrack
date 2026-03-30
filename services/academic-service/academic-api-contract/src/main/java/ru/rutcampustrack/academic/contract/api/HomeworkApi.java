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
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkResponse;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;

@Tag(name = "Homeworks", description = "Управление домашними заданиями")
@RequestMapping("/academic/homeworks")
public interface HomeworkApi {

    @Operation(summary = "Создать домашнее задание")
    @ApiResponse(responseCode = "201", description = "ДЗ создано")
    @PostMapping
    ResponseEntity<EntityModel<HomeworkResponse>> createHomework(
            @Valid @RequestBody CreateHomeworkRequest request);

    @Operation(summary = "Получить ДЗ по ID")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "ДЗ не найдено")
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<HomeworkResponse>> getHomework(@PathVariable Long id);

    @Operation(summary = "Список ДЗ по группе и семестру")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<HomeworkResponse>>> listHomeworks(
            @RequestParam Long groupId,
            @RequestParam Long semesterId,
            Pageable pageable);

    @Operation(summary = "Обновить ДЗ")
    @ApiResponse(responseCode = "200", description = "ДЗ обновлено")
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<HomeworkResponse>> updateHomework(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHomeworkRequest request);

    @Operation(summary = "Удалить ДЗ")
    @ApiResponse(responseCode = "204", description = "ДЗ удалено")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteHomework(@PathVariable Long id);

    @Operation(summary = "Отметить ДЗ как выполненное")
    @ApiResponse(responseCode = "200", description = "ДЗ отмечено выполненным")
    @PostMapping("/{id}/complete")
    ResponseEntity<Void> markComplete(@PathVariable Long id);

    @Operation(summary = "Снять отметку выполнения ДЗ")
    @ApiResponse(responseCode = "204", description = "Отметка снята")
    @DeleteMapping("/{id}/complete")
    ResponseEntity<Void> unmarkComplete(@PathVariable Long id);
}
