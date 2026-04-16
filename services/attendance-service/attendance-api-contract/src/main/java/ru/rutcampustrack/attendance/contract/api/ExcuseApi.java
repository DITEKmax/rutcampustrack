package ru.rutcampustrack.attendance.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.attendance.contract.dto.excuse.CreateExcuseRequest;
import ru.rutcampustrack.attendance.contract.dto.excuse.ExcuseTicketResponse;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.exception.ErrorResponse;

/**
 * Contract interface for excuse-ticket REST API (Phase 59, D-04..D-09).
 * Mappings declared here — controllers implement this interface.
 */
@Tag(name = "Excuses", description = "Тикеты о пропуске занятий")
@RequestMapping("/attendance/excuses")
public interface ExcuseApi {

    @Operation(
            summary = "Создать тикет о пропуске",
            description = "STUDENT создаёт тикет со списком lessonIds своей группы, причиной и комментарием."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Тикет создан"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Староста не создаёт тикеты (D-12) или доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "На один из уроков уже есть активный тикет (D-11)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    ResponseEntity<EntityModel<ExcuseTicketResponse>> createExcuse(
            @Valid @RequestBody CreateExcuseRequest request
    );

    @Operation(
            summary = "Создать тикет о пропуске с файлом",
            description = "STUDENT создаёт тикет и прикладывает документ (до 10 МБ). "
                    + "Файл пересылается старосте через notification-bot и не сохраняется на сервере."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Тикет создан, файл поставлен в очередь на отправку"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос или файл",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Староста не создаёт тикеты (D-12) или доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "На один из уроков уже есть активный тикет (D-11)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Файл больше 10 МБ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<EntityModel<ExcuseTicketResponse>> createExcuseWithFile(
            @Valid @RequestPart("request") CreateExcuseRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    );

    @Operation(
            summary = "Мои тикеты",
            description = "STUDENT видит свои тикеты, сортировка по createdAt desc (D-05)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список тикетов"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    ResponseEntity<PagedModel<EntityModel<ExcuseTicketResponse>>> getMyTickets(
            Pageable pageable,
            @RequestParam(required = false) ExcuseTicketStatus status
    );

    @Operation(
            summary = "Тикеты группы",
            description = "Староста (STUDENT + is_headman) видит тикеты своей группы (D-06)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список тикетов группы"),
            @ApiResponse(responseCode = "403", description = "Не староста или чужая группа (D-14)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/group/{groupId}")
    ResponseEntity<PagedModel<EntityModel<ExcuseTicketResponse>>> getGroupTickets(
            @PathVariable Long groupId,
            Pageable pageable,
            @RequestParam(required = false) ExcuseTicketStatus status
    );

    @Operation(
            summary = "Детали тикета",
            description = "STUDENT — только свой; headman — только своей группы (D-08)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Тикет найден"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён (D-14)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Тикет не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<ExcuseTicketResponse>> getTicketById(@PathVariable String id);

    @Operation(
            summary = "Одобрить/отклонить тикет",
            description = "Староста (STUDENT + is_headman) принимает решение по тикету своей группы (D-07). "
                    + "Запрещено решать собственный тикет (D-13) и повторно менять решение (D-18)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Решение принято"),
            @ApiResponse(responseCode = "400", description = "Некорректный статус (только APPROVED или REJECTED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Не староста или чужая группа",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Тикет не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Свой тикет (D-13) или решение уже принято (D-18)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/status")
    ResponseEntity<EntityModel<ExcuseTicketResponse>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateExcuseStatusRequest request
    );
}
