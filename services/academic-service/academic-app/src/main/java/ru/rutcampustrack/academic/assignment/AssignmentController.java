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
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.security.RequireRole;

@RestController
public class AssignmentController implements AssignmentApi {

    private final AssignmentService assignmentService;
    private final AssignmentAssembler assignmentAssembler;

    public AssignmentController(AssignmentService assignmentService,
                                 AssignmentAssembler assignmentAssembler) {
        this.assignmentService = assignmentService;
        this.assignmentAssembler = assignmentAssembler;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<AssignmentResponse>> assignTeacher(AssignTeacherRequest request) {
        TeacherSubjectGroup assignment = assignmentService.assignTeacher(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentAssembler.toModel(assignment));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> listAssignments(
            Long groupId, Long semesterId, Pageable pageable,
            PagedResourcesAssembler<AssignmentResponse> assembler) {
        Page<TeacherSubjectGroup> page = assignmentService.listAssignments(groupId, semesterId, pageable);
        Page<AssignmentResponse> responsePage = page.map(assignmentAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> removeAssignment(Long id) {
        assignmentService.removeAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({UserRole.TEACHER})
    public ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> getMyAssignments(
            Pageable pageable,
            PagedResourcesAssembler<AssignmentResponse> assembler) {
        Page<TeacherSubjectGroup> page = assignmentService.getMyAssignments(pageable);
        Page<AssignmentResponse> responsePage = page.map(assignmentAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }
}
