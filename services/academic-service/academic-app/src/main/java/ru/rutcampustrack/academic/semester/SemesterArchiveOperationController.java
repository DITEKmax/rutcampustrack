package ru.rutcampustrack.academic.semester;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.SemesterArchiveOperationApi;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveOperationResponse;
import ru.rutcampustrack.academic.security.RequireRole;

@RestController
public class SemesterArchiveOperationController implements SemesterArchiveOperationApi {

    private final SemesterArchiveService semesterArchiveService;

    public SemesterArchiveOperationController(SemesterArchiveService semesterArchiveService) {
        this.semesterArchiveService = semesterArchiveService;
    }

    @Override
    @RequireRole(
            value = {ADMIN},
            admin = {"SEMESTERS_MANAGE"})
    public ResponseEntity<SemesterArchiveOperationResponse> getSemesterArchiveOperation(
            UUID operationId) {
        return ResponseEntity.ok(semesterArchiveService.getOperation(operationId));
    }
}
