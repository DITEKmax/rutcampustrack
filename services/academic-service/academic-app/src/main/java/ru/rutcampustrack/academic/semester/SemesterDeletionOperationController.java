package ru.rutcampustrack.academic.semester;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.SemesterDeletionApi;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionOperationResponse;
import ru.rutcampustrack.academic.security.RequireRole;

@RestController
public class SemesterDeletionOperationController implements SemesterDeletionApi {

    private final SemesterDeletionService service;

    public SemesterDeletionOperationController(SemesterDeletionService service) {
        this.service = service;
    }

    @Override
    @RequireRole(
            value = {ADMIN},
            admin = {"SEMESTERS_MANAGE", "DATA_DELETE"})
    public ResponseEntity<SemesterDeletionOperationResponse> getSemesterDeletionOperation(
            UUID operationId) {
        return ResponseEntity.ok(service.status(operationId));
    }
}
