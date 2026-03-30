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
import ru.rutcampustrack.academic.contract.dto.threshold.ResolvedThresholdResponse;
import ru.rutcampustrack.academic.contract.dto.threshold.SetThresholdRequest;
import ru.rutcampustrack.academic.contract.dto.threshold.ThresholdResponse;

@Tag(name = "Thresholds", description = "Управление порогами посещаемости")
@RequestMapping("/academic/thresholds")
public interface ThresholdApi {

    @Operation(summary = "Установить глобальный порог (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Порог установлен")
    @PutMapping("/global")
    ResponseEntity<EntityModel<ThresholdResponse>> setGlobalThreshold(
            @Valid @RequestBody SetThresholdRequest request);

    @Operation(summary = "Установить порог для группы (HEADMAN)")
    @ApiResponse(responseCode = "200", description = "Порог установлен")
    @PutMapping("/group")
    ResponseEntity<EntityModel<ThresholdResponse>> setGroupThreshold(
            @Valid @RequestBody SetThresholdRequest request);

    @Operation(summary = "Установить порог для предмета в группе (HEADMAN)")
    @ApiResponse(responseCode = "200", description = "Порог установлен")
    @PutMapping("/subject")
    ResponseEntity<EntityModel<ThresholdResponse>> setSubjectThreshold(
            @Valid @RequestBody SetThresholdRequest request,
            @RequestParam Long subjectId);

    @Operation(summary = "Получить актуальный порог (most-specific-wins)")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/resolve")
    ResponseEntity<EntityModel<ResolvedThresholdResponse>> resolveThreshold(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long subjectId);

    @Operation(summary = "Список всех порогов (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<ThresholdResponse>>> listThresholds(Pageable pageable);
}
