package ru.rutcampustrack.academic.subject;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.assignment.AssignmentAssembler;
import ru.rutcampustrack.academic.contract.api.SubjectApi;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.subject.AddSubjectTeacherRequest;
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.contract.dto.subject.UpdateSubjectRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.security.RequireRole;

import java.time.LocalDate;

@RestController
public class SubjectController implements SubjectApi {

    private final SubjectService subjectService;
    private final SubjectAssembler subjectAssembler;
    private final AssignmentAssembler assignmentAssembler;

    public SubjectController(SubjectService subjectService,
                             SubjectAssembler subjectAssembler,
                             AssignmentAssembler assignmentAssembler) {
        this.subjectService = subjectService;
        this.subjectAssembler = subjectAssembler;
        this.assignmentAssembler = assignmentAssembler;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<SubjectResponse>> createSubject(CreateSubjectRequest request) {
        Subject subject = subjectService.createSubject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectAssembler.toModel(subject));
    }

    @Override
    public ResponseEntity<EntityModel<SubjectResponse>> getSubject(Long id) {
        return ResponseEntity.ok(subjectAssembler.toModel(subjectService.getSubjectForRead(id)));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<SubjectResponse>>> listSubjects(
            Pageable pageable,
            PagedResourcesAssembler<SubjectResponse> assembler) {
        Page<SubjectResponse> responsePage = subjectService.listSubjects(pageable)
                .map(subjectAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage, EntityModel::of));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<SubjectResponse>> updateSubject(Long id, UpdateSubjectRequest request) {
        return ResponseEntity.ok(subjectAssembler.toModel(subjectService.updateSubject(id, request)));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> deleteSubject(Long id, boolean force) {
        subjectService.deleteSubject(id, force);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<AssignmentResponse>> addTeacher(
            Long id, Long teacherId, AddSubjectTeacherRequest request) {
        Assignment assignment = subjectService.addTeacher(id, teacherId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentAssembler.toModel(assignment));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> removeTeacher(Long id,
                                               Long teacherId,
                                               Long assignmentId,
                                               LocalDate validUntilExclusive) {
        subjectService.removeTeacher(id, teacherId, assignmentId, validUntilExclusive);
        return ResponseEntity.noContent().build();
    }
}
