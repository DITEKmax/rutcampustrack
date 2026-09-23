package ru.rutcampustrack.academic.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.AssignmentApi;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignTeacherRequest;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignmentReplacementResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.dto.assignment.ReplaceAssignmentRequest;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.security.RequireRole;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class AssignmentController implements AssignmentApi {

    private final AssignmentService assignmentService;
    private final AssignmentAssembler assignmentAssembler;
    private final AssignmentReplacementService replacementService;

    public AssignmentController(AssignmentService assignmentService,
                                 AssignmentAssembler assignmentAssembler,
                                 AssignmentReplacementService replacementService) {
        this.assignmentService = assignmentService;
        this.assignmentAssembler = assignmentAssembler;
        this.replacementService = replacementService;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<AssignmentResponse>> assignTeacher(AssignTeacherRequest request) {
        Assignment assignment = assignmentService.assignTeacher(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentAssembler.toModel(assignment));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> listAssignments(
            Long groupId, Long semesterId, Pageable pageable,
            PagedResourcesAssembler<AssignmentResponse> assembler) {
        Page<AssignmentResponse> responsePage = assignmentService.listAssignments(groupId, semesterId, pageable)
                .map(assignmentAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage, EntityModel::of));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> removeAssignment(Long id, LocalDate validUntilExclusive) {
        assignmentService.removeAssignment(id, validUntilExclusive);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<AssignmentReplacementResponse> replaceAssignment(
            Long id, ReplaceAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(replacementService.replace(id, request));
    }

    @Override
    @RequireRole({UserRole.STUDENT, UserRole.ADMIN})
    public ResponseEntity<AssignmentReplacementResponse> getReplacementStatus(UUID operationId) {
        return ResponseEntity.ok(replacementService.getStatus(operationId));
    }

    @Override
    @RequireRole({UserRole.TEACHER})
    public ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> getMyAssignments(
            Pageable pageable,
            PagedResourcesAssembler<AssignmentResponse> assembler) {
        Page<AssignmentResponse> responsePage = assignmentService.getMyAssignments(pageable)
                .map(assignmentAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage, EntityModel::of));
    }
}
