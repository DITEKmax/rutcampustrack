package ru.rutcampustrack.academic.contract.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.academic.contract.dto.group.GroupDeletionModels.*;

import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(
        name = "Group deletion",
        description = "Окончательное удаление одной группы с сохранением аккаунтов")
@RequestMapping("/academic")
public interface GroupDeletionApi {
    @io.swagger.v3.oas.annotations.Operation(operationId = "previewGroupDeletion")
    @GetMapping("/groups/{id}/deletion-preview")
    ResponseEntity<Preview> preview(@PathVariable long id);

    @io.swagger.v3.oas.annotations.Operation(operationId = "confirmGroupDeletion")
    @PostMapping("/groups/{id}/deletions")
    ResponseEntity<Operation> confirm(
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody ConfirmRequest request);

    @io.swagger.v3.oas.annotations.Operation(
            operationId = "resolveGroupDeletion",
            summary = "Разрешить исход удаления по исходному UUID",
            description =
                    "Возвращает исходную принятую операцию. Если приём ещё не состоялся, атомарно"
                        + " сохраняет CANCELLED/NOT_ACCEPTED и исключает поздний приём этого UUID."
                        + " Не отменяет уже принятую операцию. Пароль и повторное подтверждение"
                        + " удаления не передаются.")
    @PostMapping("/groups/{id}/deletions/{operationId}/resolve")
    ResponseEntity<Operation> resolve(@PathVariable long id, @PathVariable UUID operationId);

    @io.swagger.v3.oas.annotations.Operation(operationId = "getGroupDeletionStatus")
    @GetMapping("/group-deletions/{operationId}")
    ResponseEntity<Operation> status(@PathVariable("operationId") UUID operationId);
}
