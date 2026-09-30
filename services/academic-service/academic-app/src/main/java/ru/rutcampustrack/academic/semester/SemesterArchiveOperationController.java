package ru.rutcampustrack.academic.semester;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.SemesterArchiveOperationApi;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveOperationResponse;
import ru.rutcampustrack.academic.security.RequireRole;

import java.util.UUID;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

@RestController
public class SemesterArchiveOperationController implements SemesterArchiveOperationApi {

    private final SemesterArchiveService semesterArchiveService;

    public SemesterArchiveOperationController(SemesterArchiveService semesterArchiveService) {
        this.semesterArchiveService = semesterArchiveService;
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<SemesterArchiveOperationResponse> getSemesterArchiveOperation(UUID operationId) {
        return ResponseEntity.ok(semesterArchiveService.getOperation(operationId));
    }
}
