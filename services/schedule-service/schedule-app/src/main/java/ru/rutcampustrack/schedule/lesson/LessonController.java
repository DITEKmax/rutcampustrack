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
import ru.rutcampustrack.schedule.contract.dto.lesson.MassCancelRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.MassCancelResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.security.RequireRole;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for lesson operations and schedule view.
 * Implements LessonApi contract interface — HTTP mappings are defined in the interface only.
 * Delegates all business logic to LessonService.
 */
@RestController
public class LessonController implements LessonApi {

    private final LessonService lessonService;
    private final LessonAssembler lessonAssembler;

    public LessonController(LessonService lessonService, LessonAssembler lessonAssembler) {
        this.lessonService = lessonService;
        this.lessonAssembler = lessonAssembler;
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
    public ResponseEntity<MassCancelResponse> massCancelLessons(MassCancelRequest request) {
        int count = lessonService.massCancelLessons(request);
        return ResponseEntity.ok(new MassCancelResponse(count));
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
        Page<LessonResponse> responsePage = page.map(lessonAssembler::toResponse);
        PagedModel<EntityModel<LessonResponse>> pagedModel = assembler.toModel(
                responsePage, resp -> EntityModel.of(resp));
        return ResponseEntity.ok(pagedModel);
    }
}
