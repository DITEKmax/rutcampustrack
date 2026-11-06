package ru.rutcampustrack.schedule.item;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import ru.rutcampustrack.schedule.contract.api.ScheduleItemApi;
import ru.rutcampustrack.schedule.contract.dto.item.ConstructorOperationResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemLifecyclePreviewResponse;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemResponse;
import ru.rutcampustrack.schedule.contract.dto.item.SemesterMaterializationResponse;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.security.RequireRole;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;
import ru.rutcampustrack.shared.web.api.exception.FieldError;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for schedule template (ScheduleItem) management. Implements ScheduleItemApi
 * contract interface — HTTP mappings are defined in the interface only. Delegates all business
 * logic to ScheduleItemService.
 */
@RestController
public class ScheduleItemController implements ScheduleItemApi {

    private final ScheduleItemService scheduleItemService;
    private final ScheduleItemAssembler scheduleItemAssembler;

    public ScheduleItemController(
            ScheduleItemService scheduleItemService, ScheduleItemAssembler scheduleItemAssembler) {
        this.scheduleItemService = scheduleItemService;
        this.scheduleItemAssembler = scheduleItemAssembler;
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<ScheduleItemResponse>> createScheduleItem(
            UUID idempotencyKey, CreateScheduleItemRequest request) {
        ScheduleItem result = scheduleItemService.createScheduleItem(request, idempotencyKey);
        ScheduleItemResponse replay = scheduleItemService.createReplayResponse(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        replay == null
                                ? scheduleItemAssembler.toModel(result)
                                : scheduleItemAssembler.toModel(replay));
    }

    @Override
    public ResponseEntity<EntityModel<ScheduleItemResponse>> getScheduleItem(Long id) {
        ScheduleItem result = scheduleItemService.getScheduleItem(id);
        return ResponseEntity.ok(scheduleItemAssembler.toModel(result));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<ScheduleItemResponse>>> listScheduleItems(
            Long groupId,
            Long semesterId,
            boolean includeInactive,
            Pageable pageable,
            PagedResourcesAssembler<ScheduleItemResponse> assembler) {
        Page<ScheduleItem> page =
                scheduleItemService.listScheduleItems(
                        groupId, semesterId, includeInactive, pageable);
        Page<ScheduleItemResponse> responsePage =
                page.map(item -> scheduleItemAssembler.toModel(item).getContent());
        PagedModel<EntityModel<ScheduleItemResponse>> pagedModel =
                assembler.toModel(
                        responsePage,
                        response ->
                                EntityModel.of(
                                        response,
                                        linkTo(
                                                        methodOn(ScheduleItemController.class)
                                                                .getScheduleItem(response.getId()))
                                                .withSelfRel()));
        return ResponseEntity.ok(pagedModel);
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<ScheduleItemResponse>> updateScheduleItem(
            Long id, UUID idempotencyKey, UpdateScheduleItemRequest request) {
        ScheduleItemResponse result =
                scheduleItemService.updateScheduleItem(id, request, idempotencyKey);
        return ResponseEntity.ok(scheduleItemAssembler.toModel(result));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<?> deleteScheduleItem(
            Long id, UUID idempotencyKey, String expectedRevision) {
        var operation =
                scheduleItemService.deleteScheduleItem(id, idempotencyKey, expectedRevision);
        return operation.completed()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.accepted()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(operation);
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<ConstructorOperationResponse> getConstructorOperation(UUID operationId) {
        return ResponseEntity.ok(scheduleItemService.constructorOperation(operationId));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<SemesterMaterializationResponse> materializeSemester(Long id, UUID key) {
        return ResponseEntity.accepted().body(scheduleItemService.materializeSemester(id, key));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<SemesterMaterializationResponse> getSemesterMaterialization(
            UUID operationId) {
        return ResponseEntity.ok(scheduleItemService.materializationOperation(operationId));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<ScheduleItemLifecyclePreviewResponse> previewScheduleItem(
            Long id, boolean delete, UpdateScheduleItemRequest request) {
        return ResponseEntity.ok(scheduleItemService.previewScheduleItem(id, request, delete));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingLifecycleHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {
        ErrorResponse error =
                new ErrorResponse(
                        400,
                        ErrorResponse.PROBLEM_BASE + "missing-header",
                        "Отсутствует заголовок",
                        "Обязательный заголовок запроса отсутствует",
                        request.getRequestURI(),
                        Instant.now(),
                        org.slf4j.MDC.get("traceId"),
                        List.of(
                                new FieldError(
                                        exception.getHeaderName(), null, "Заголовок обязателен")),
                        null,
                        null);
        return ResponseEntity.badRequest()
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(error);
    }
}
