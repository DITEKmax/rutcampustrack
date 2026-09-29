package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveOperationResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveStatusResponse;

import java.util.UUID;

@Tag(name = "Semester archive", description = "Идемпотентные команды архивации и восстановления семестра")
@RequestMapping("/academic/semesters")
public interface SemesterArchiveCommandApi {

    @Operation(summary = "Начать или продолжить архивацию семестра (ADMIN)",
            description = "UUID в Idempotency-Key закрепляет actor, semester и action. Повтор возвращает прежнюю операцию.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Архивация завершена"),
            @ApiResponse(responseCode = "202", description = "Архивация ожидает завершения принятых эффектов"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Семестр не найден"),
            @ApiResponse(responseCode = "409", description = "Переход или ключ идемпотентности конфликтует"),
            @ApiResponse(responseCode = "503", description = "Участник недоступен; операция сохранена для повтора")
    })
    @PostMapping("/{id}/archive")
    ResponseEntity<SemesterArchiveOperationResponse> archiveSemester(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey);

    @Operation(summary = "Текущее состояние архивации семестра (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние прочитано"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Семестр не найден")
    })
    @GetMapping("/{id}/archive/status")
    ResponseEntity<SemesterArchiveStatusResponse> getSemesterArchiveStatus(@PathVariable Long id);

    @Operation(summary = "Начать или продолжить восстановление семестра (ADMIN)",
            description = "Восстановление открывает разрешённые записи и не активирует семестр.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Восстановление завершено и все write gates released"),
            @ApiResponse(responseCode = "202", description = "Восстановление ожидает release всех доменов"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
            @ApiResponse(responseCode = "404", description = "Семестр не найден"),
            @ApiResponse(responseCode = "409", description = "Переход или ключ идемпотентности конфликтует"),
            @ApiResponse(responseCode = "503", description = "Участник недоступен; операция сохранена для повтора")
    })
    @PostMapping("/{id}/restore")
    ResponseEntity<SemesterArchiveOperationResponse> restoreSemester(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey);
}
