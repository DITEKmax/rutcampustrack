package ru.rutcampustrack.attendance.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchResponse;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkResponse;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

/**
 * Contract interface for manual attendance marking API (D-11).
 * Mappings declared here — controller implements this interface.
 */
@Tag(name = "Marking", description = "Ручная отметка старостой")
@RequestMapping("/attendance")
public interface MarkingApi {

    @Operation(
            summary = "Ручная отметка посещаемости",
            description = "Староста устанавливает статус посещаемости для студента на паре."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус успешно установлен"),
            @ApiResponse(responseCode = "400", description = "Неверный статус или запрос",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён — не старosta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пара или студент не найдены",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/lessons/{lessonId}/students/{userId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EntityModel<MarkResponse>> mark(
            @PathVariable Long lessonId,
            @PathVariable Long userId,
            @Valid @RequestBody MarkRequest request);

    @Operation(
            summary = "Ручная отметка с одним вложением",
            description = "Тот же индивидуальный PUT в multipart/form-data: JSON-part request "
                    + "и необязательный file. Вложение допускается только для EXCUSED."
    )
    @PutMapping(value = "/lessons/{lessonId}/students/{userId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<EntityModel<MarkResponse>> markWithFile(
            @PathVariable Long lessonId,
            @PathVariable Long userId,
            @Valid @RequestPart("request") MarkRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file);

    @Operation(
            summary = "Снять индивидуальную отметку",
            description = "Удаляет запись отметки для пары и студента после повторной проверки scope."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отметка снята"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пара или студент не найдены",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/lessons/{lessonId}/students/{userId}")
    ResponseEntity<Void> clear(
            @PathVariable Long lessonId,
            @PathVariable Long userId);

    @Operation(summary = "Скачать вложение индивидуальной отметки")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вложение",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Вложение не найдено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/lessons/{lessonId}/students/{userId}/attachment")
    ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long lessonId,
            @PathVariable Long userId);

    @Operation(
            summary = "Пакетная отметка посещаемости (M05 P2-10/4)",
            description = "Староста отмечает N студентов одним запросом. "
                    + "Pseudo-atomic: authorization-check'и выполняются до любого "
                    + "write'а. При любой ошибке авторизации весь batch отклонён."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Все отметки сохранены"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос "
                    + "(пустой batch, размер > 100, CANCELLED status)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён — "
                    + "не староста, пара чужой группы или студент не в группе",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пара не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/marks/batch")
    ResponseEntity<MarkBatchResponse> markBatch(@Valid @RequestBody MarkBatchRequest request);
}
