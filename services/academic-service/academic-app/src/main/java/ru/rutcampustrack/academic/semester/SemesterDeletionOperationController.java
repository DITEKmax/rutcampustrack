package ru.rutcampustrack.academic.semester;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.SemesterDeletionApi;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionOperationResponse;
import ru.rutcampustrack.academic.security.RequireRole;

import java.util.UUID;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

@RestController
public class SemesterDeletionOperationController implements SemesterDeletionApi {

    private final SemesterDeletionService service;

    public SemesterDeletionOperationController(SemesterDeletionService service) {
        this.service = service;
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<SemesterDeletionOperationResponse> getSemesterDeletionOperation(UUID operationId) {
        return ResponseEntity.ok(service.status(operationId));
    }
}
