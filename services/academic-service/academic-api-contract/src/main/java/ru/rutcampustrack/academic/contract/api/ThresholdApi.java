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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.academic.contract.dto.threshold.ResolvedThresholdResponse;
import ru.rutcampustrack.academic.contract.dto.threshold.SetThresholdRequest;
import ru.rutcampustrack.academic.contract.dto.threshold.ThresholdResponse;

/**
 * REST API contract for attendance threshold configuration.
 * Three-tier hierarchy: global → group → subject (most specific wins).
 */
@Tag(name = "Thresholds", description = "Управление порогами посещаемости")
@RequestMapping("/academic/thresholds")
public interface ThresholdApi {

    @Operation(summary = "Установить глобальный порог посещаемости (ADMIN)", description = "Применяется ко всем группам и предметам, если не переопределён на уровне группы или предмета.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Порог установлен"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @PutMapping("/global")
    ResponseEntity<EntityModel<ThresholdResponse>> setGlobalThreshold(
            @Valid @RequestBody SetThresholdRequest request);

    @Operation(summary = "Установить порог для группы (HEADMAN/ADMIN)", description = "Переопределяет глобальный порог для указанной группы.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Порог установлен"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @PutMapping("/group")
    ResponseEntity<EntityModel<ThresholdResponse>> setGroupThreshold(
            @Valid @RequestBody SetThresholdRequest request);

    @Operation(summary = "Установить порог для предмета в группе (HEADMAN/ADMIN)", description = "Наиболее специфичный порог — переопределяет группу и глобальный.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Порог установлен"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @PutMapping("/subject")
    ResponseEntity<EntityModel<ThresholdResponse>> setSubjectThreshold(
            @Valid @RequestBody SetThresholdRequest request,
            @RequestParam Long subjectId);

    @Operation(summary = "Разрешить эффективный порог", description = "Возвращает применяемый порог для комбинации группа/предмет согласно иерархии.")
    @ApiResponse(responseCode = "200", description = "Эффективный порог")
    @GetMapping("/resolve")
    ResponseEntity<EntityModel<ResolvedThresholdResponse>> resolveThreshold(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long subjectId);

    @Operation(summary = "Список всех настроенных порогов")
    @ApiResponse(responseCode = "200", description = "Список порогов")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<ThresholdResponse>>> listThresholds(
            Pageable pageable,
            PagedResourcesAssembler<ThresholdResponse> assembler);
}
