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
    private final PagedResourcesAssembler<TeacherSubjectGroup> pagedAssembler;

    public AssignmentController(AssignmentService assignmentService,
                                 AssignmentAssembler assignmentAssembler,
                                 PagedResourcesAssembler<TeacherSubjectGroup> pagedAssembler) {
        this.assignmentService = assignmentService;
        this.assignmentAssembler = assignmentAssembler;
        this.pagedAssembler = pagedAssembler;
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
            Long groupId, Long semesterId, Pageable pageable) {
        Page<TeacherSubjectGroup> page = assignmentService.listAssignments(groupId, semesterId, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assignmentAssembler));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> removeAssignment(Long id) {
        assignmentService.removeAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({UserRole.TEACHER})
    public ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> getMyAssignments(Pageable pageable) {
        Page<TeacherSubjectGroup> page = assignmentService.getMyAssignments(pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assignmentAssembler));
    }
}
