package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.assistant.AssistantResponse;
import ru.rutcampustrack.academic.contract.dto.assistant.UpdateAssistantPermissionsRequest;

/**
 * REST API contract for headman assistant management.
 */
@Tag(name = "Assistants", description = "Управление помощниками старосты")
@RequestMapping("/academic/assistants")
public interface AssistantApi {

    @Operation(summary = "Назначить помощника старосты (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Помощник назначен"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Студент или группа не найдены"),
            @ApiResponse(responseCode = "409", description = "Студент уже является помощником в этой группе")
    })
    @PostMapping
    ResponseEntity<EntityModel<AssistantResponse>> assignAssistant(
            @Valid @RequestBody AssignAssistantRequest request);

    @Operation(summary = "Список помощников старосты для группы")
    @ApiResponse(responseCode = "200", description = "Список помощников")
    @GetMapping
    ResponseEntity<CollectionModel<EntityModel<AssistantResponse>>> listAssistants(
            @RequestParam Long groupId);

    @Operation(summary = "Обновить права помощника (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Права обновлены"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Помощник не найден")
    })
    @PatchMapping("/{id}/permissions")
    ResponseEntity<EntityModel<AssistantResponse>> updatePermissions(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssistantPermissionsRequest request);

    @Operation(summary = "Отозвать помощника (HEADMAN/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Помощник отозван"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Помощник не найден")
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> revokeAssistant(@PathVariable Long id);
}
