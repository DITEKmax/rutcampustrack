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
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkResponse;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;

/**
 * REST API contract for homework management.
 */
@Tag(name = "Homeworks", description = "Управление домашними заданиями")
@RequestMapping("/academic/homeworks")
public interface HomeworkApi {

    @Operation(summary = "Создать домашнее задание (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Задание создано"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @PostMapping
    ResponseEntity<EntityModel<HomeworkResponse>> createHomework(
            @Valid @RequestBody CreateHomeworkRequest request);

    @Operation(summary = "Получить задание по ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Задание найдено"),
            @ApiResponse(responseCode = "404", description = "Задание не найдено")
    })
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<HomeworkResponse>> getHomework(@PathVariable Long id);

    @Operation(summary = "Список заданий для группы и семестра")
    @ApiResponse(responseCode = "200", description = "Список заданий")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<HomeworkResponse>>> listHomeworks(
            @RequestParam Long groupId,
            @RequestParam Long semesterId,
            Pageable pageable,
            PagedResourcesAssembler<HomeworkResponse> assembler);

    @Operation(summary = "Полное обновление задания (PUT, HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Задание обновлено"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Задание не найдено")
    })
    @PutMapping("/{id}")
    ResponseEntity<EntityModel<HomeworkResponse>> updateHomework(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHomeworkRequest request);

    @Operation(summary = "Удалить задание (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Задание удалено"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Задание не найдено")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteHomework(@PathVariable Long id);

    @Operation(summary = "Отметить задание выполненным (STUDENT)", description = "Создаёт запись HomeworkCompletion для текущего студента.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Задание отмечено выполненным"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Задание не найдено"),
            @ApiResponse(responseCode = "409", description = "Задание уже выполнено")
    })
    @PostMapping("/{id}/complete")
    ResponseEntity<Void> markComplete(@PathVariable Long id);

    @Operation(summary = "Снять отметку выполнения (STUDENT)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отметка снята"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Задание или отметка не найдены")
    })
    @DeleteMapping("/{id}/complete")
    ResponseEntity<Void> unmarkComplete(@PathVariable Long id);
}
