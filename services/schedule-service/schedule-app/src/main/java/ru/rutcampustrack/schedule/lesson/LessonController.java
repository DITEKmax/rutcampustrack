package ru.rutcampustrack.schedule.lesson;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.schedule.contract.api.LessonApi;
import ru.rutcampustrack.schedule.contract.dto.lesson.CancelLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.GeoBlockRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.LessonResponse;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.security.RequireRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for lesson operations and schedule view.
 * Implements LessonApi contract interface — HTTP mappings are defined in the interface only.
 * Delegates all business logic to LessonService.
 */
@RestController
public class LessonController implements LessonApi {

    private final LessonService lessonService;
    private final LessonTransferService lessonTransferService;
    private final LessonAssembler lessonAssembler;

    public LessonController(LessonService lessonService,
                            LessonTransferService lessonTransferService,
                            LessonAssembler lessonAssembler) {
        this.lessonService = lessonService;
        this.lessonTransferService = lessonTransferService;
        this.lessonAssembler = lessonAssembler;
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<TransferLessonResponse> transferLesson(Long id, TransferLessonRequest request) {
        TransferLessonResponse result = lessonTransferService.transfer(id, request);
        if ("ERROR".equals(result.state())) {
            return ResponseEntity.status(409)
                    .location(java.net.URI.create("/schedule/lesson-transfers/" + result.operationId()))
                    .build();
        }
        if ("COMPLETED".equals(result.state())) {
            return ResponseEntity.ok()
                    .location(java.net.URI.create("/schedule/lesson-transfers/" + result.operationId()))
                    .body(result);
        }
        return ResponseEntity.accepted()
                .location(java.net.URI.create("/schedule/lesson-transfers/" + result.operationId()))
                .body(result);
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<TransferLessonResponse> getLessonTransfer(UUID operationId) {
        return ResponseEntity.ok(lessonTransferService.status(operationId));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<LessonResponse>> cancelLesson(Long id, CancelLessonRequest request) {
        LessonWithItem result = lessonService.cancelLesson(id, request);
        return ResponseEntity.ok(lessonAssembler.toModel(result));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<LessonResponse>> restoreLesson(Long id) {
        LessonWithItem result = lessonService.restoreLesson(id);
        return ResponseEntity.ok(lessonAssembler.toModel(result));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<LessonResponse>> toggleGeoBlock(Long id, GeoBlockRequest request) {
        LessonWithItem result = lessonService.toggleGeoBlock(id, request);
        return ResponseEntity.ok(lessonAssembler.toModel(result));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<LessonResponse>> blockLesson(Long id) {
        LessonWithItem result = lessonService.blockLessonByHeadman(id);
        return ResponseEntity.ok(lessonAssembler.toModel(result));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<LessonResponse>> unblockLesson(Long id) {
        LessonWithItem result = lessonService.unblockLessonByHeadman(id);
        return ResponseEntity.ok(lessonAssembler.toModel(result));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<LessonResponse>>> getLessons(
            Long groupId,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<LessonStatus> status,
            Pageable pageable,
            PagedResourcesAssembler<LessonResponse> assembler) {
        Page<LessonWithItem> page = lessonService.getLessonsForGroup(groupId, dateFrom, dateTo, status, pageable);
        Map<Long, LessonTransferWriter.TransferState> transferStates = lessonTransferService.statesForLessons(
                page.getContent().stream().map(row -> row.lesson().getId()).toList());
        Page<LessonResponse> responsePage = page.map(row -> lessonAssembler.toResponse(
                row, transferStates.get(row.lesson().getId())));
        PagedModel<EntityModel<LessonResponse>> pagedModel = assembler.toModel(
                responsePage, resp -> EntityModel.of(resp));
        return ResponseEntity.ok(pagedModel);
    }
}
