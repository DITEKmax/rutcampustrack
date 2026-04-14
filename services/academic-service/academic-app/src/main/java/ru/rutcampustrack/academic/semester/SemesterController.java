package ru.rutcampustrack.academic.semester;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.SemesterApi;
import ru.rutcampustrack.academic.contract.dto.semester.CreateSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.DeleteSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.OverlapCheckResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterResponse;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.security.RequireRole;

import java.time.LocalDate;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

/**
 * REST controller implementing SemesterApi contract.
 * Delegates all business logic to SemesterService.
 */
@RestController
public class SemesterController implements SemesterApi {

    private final SemesterService semesterService;
    private final SemesterAssembler semesterAssembler;

    public SemesterController(SemesterService semesterService,
                              SemesterAssembler semesterAssembler) {
        this.semesterService = semesterService;
        this.semesterAssembler = semesterAssembler;
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
    public ResponseEntity<Void> deleteSemester(Long id, DeleteSemesterRequest request) {
        semesterService.deleteSemester(id, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<SemesterResponse>> activateSemester(Long id) {
        Semester semester = semesterService.activateSemester(id);
        return ResponseEntity.ok(semesterAssembler.toModel(semester));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<OverlapCheckResponse> checkOverlap(LocalDate from, LocalDate to, Long excludeId) {
        return ResponseEntity.ok(semesterService.checkOverlap(from, to, excludeId));
    }
}
