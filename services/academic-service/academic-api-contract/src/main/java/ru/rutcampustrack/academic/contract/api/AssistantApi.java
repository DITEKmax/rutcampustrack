package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.assistant.AssistantResponse;
import ru.rutcampustrack.academic.contract.dto.assistant.UpdateAssistantPermissionsRequest;

@Tag(name = "Assistants", description = "Управление помощниками старосты")
@RequestMapping("/academic/assistants")
public interface AssistantApi {

    @Operation(summary = "Назначить помощника старосты")
    @ApiResponse(responseCode = "201", description = "Помощник назначен")
    @PostMapping
    ResponseEntity<EntityModel<AssistantResponse>> assignAssistant(
            @Valid @RequestBody AssignAssistantRequest request);

    @Operation(summary = "Список активных помощников группы")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    ResponseEntity<CollectionModel<EntityModel<AssistantResponse>>> listAssistants(
            @RequestParam Long groupId);

    @Operation(summary = "Обновить права помощника")
    @ApiResponse(responseCode = "200", description = "Права обновлены")
    @PatchMapping("/{id}/permissions")
    ResponseEntity<EntityModel<AssistantResponse>> updatePermissions(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssistantPermissionsRequest request);

    @Operation(summary = "Отозвать помощника")
    @ApiResponse(responseCode = "204", description = "Помощник отозван")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> revokeAssistant(@PathVariable Long id);
}
