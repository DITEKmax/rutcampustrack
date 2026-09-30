package ru.rutcampustrack.academic.semester;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.SemesterApi;
import ru.rutcampustrack.academic.contract.api.SemesterArchiveCommandApi;
import ru.rutcampustrack.academic.contract.dto.semester.CreateSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.DeleteSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionOperationResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionPreviewResponse;
import ru.rutcampustrack.academic.contract.dto.semester.OverlapCheckResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveOperationResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveStatusResponse;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.security.RequireRole;

import java.time.LocalDate;
import java.util.UUID;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

/**
 * REST controller implementing SemesterApi contract.
 * Delegates all business logic to SemesterService.
 */
@RestController
public class SemesterController implements SemesterApi, SemesterArchiveCommandApi {

    private final SemesterService semesterService;
    private final SemesterAssembler semesterAssembler;
    private final SemesterArchiveService semesterArchiveService;
    private final SemesterDeletionService semesterDeletionService;

    public SemesterController(SemesterService semesterService,
                              SemesterAssembler semesterAssembler,
                              SemesterArchiveService semesterArchiveService) {
        this(semesterService, semesterAssembler, semesterArchiveService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SemesterController(SemesterService semesterService,
                              SemesterAssembler semesterAssembler,
                              SemesterArchiveService semesterArchiveService,
                              SemesterDeletionService semesterDeletionService) {
        this.semesterService = semesterService;
        this.semesterAssembler = semesterAssembler;
        this.semesterArchiveService = semesterArchiveService;
        this.semesterDeletionService = semesterDeletionService;
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<SemesterResponse>> createSemester(CreateSemesterRequest request) {
        Semester semester = semesterService.createSemester(request);
        return ResponseEntity.status(201).body(semesterAssembler.toModel(semester));
    }

    @Override
    public ResponseEntity<EntityModel<SemesterResponse>> getSemester(Long id) {
        Semester semester = semesterService.findSemesterById(id);
        return ResponseEntity.ok(semesterAssembler.toModel(semester));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<SemesterResponse>>> listSemesters(
            Pageable pageable,
            PagedResourcesAssembler<SemesterResponse> assembler) {
        Page<Semester> page = semesterService.listSemesters(pageable);
        Page<SemesterResponse> responsePage = page.map(semesterAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<SemesterResponse>> updateSemester(Long id, UpdateSemesterRequest request) {
        Semester semester = semesterService.updateSemester(id, request);
        return ResponseEntity.ok(semesterAssembler.toModel(semester));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<SemesterDeletionOperationResponse> deleteSemester(
            Long id, DeleteSemesterRequest request, UUID idempotencyKey) {
        final SemesterDeletionOperationResponse response;
        try {
            response = semesterDeletionService.confirm(id, request, idempotencyKey);
        } catch (StaleSemesterDeletionPreviewException stale) {
            SemesterDeletionPreviewResponse preview = stale.refreshedPreview();
            return ResponseEntity.status(409).body(new SemesterDeletionOperationResponse(
                    null, id, ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase.CANCELLED,
                    false, preview.stateVersion(), preview.counts(), "STALE_PREVIEW", null, preview));
        }
        if (response.phase() == ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase.CANCELLED
                && response.refreshedPreview() != null) {
            return ResponseEntity.status(409).body(response);
        }
        if (response.phase() == ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase.COMPLETED) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.accepted().body(response);
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<SemesterDeletionPreviewResponse> previewSemesterDeletion(Long id) {
        return ResponseEntity.ok(semesterDeletionService.preview(id));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<SemesterResponse>> activateSemester(Long id) {
        Semester semester = semesterService.activateSemester(id);
        return ResponseEntity.ok(semesterAssembler.toModel(semester));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<SemesterArchiveOperationResponse> archiveSemester(Long id, UUID idempotencyKey) {
        SemesterArchiveOperationResponse response = semesterArchiveService.archive(id, idempotencyKey);
        return operationResponse(response);
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<SemesterArchiveStatusResponse> getSemesterArchiveStatus(Long id) {
        return ResponseEntity.ok(semesterArchiveService.status(id));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<SemesterArchiveOperationResponse> restoreSemester(Long id, UUID idempotencyKey) {
        SemesterArchiveOperationResponse response = semesterArchiveService.restore(id, idempotencyKey);
        return operationResponse(response);
    }

    private static ResponseEntity<SemesterArchiveOperationResponse> operationResponse(
            SemesterArchiveOperationResponse response) {
        return switch (response.operationState()) {
            case COMPLETED -> ResponseEntity.ok(response);
            case PENDING -> ResponseEntity.accepted().body(response);
            case ERROR -> ResponseEntity.status(503).body(response);
        };
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<OverlapCheckResponse> checkOverlap(LocalDate from, LocalDate to, Long excludeId) {
        return ResponseEntity.ok(semesterService.checkOverlap(from, to, excludeId));
    }
}
