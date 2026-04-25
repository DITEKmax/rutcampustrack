package ru.rutcampustrack.academic.subject;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.SubjectApi;
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.contract.dto.subject.UpdateSubjectRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.security.RequireRole;

@RestController
public class SubjectController implements SubjectApi {

    private final SubjectService subjectService;
    private final SubjectAssembler subjectAssembler;

    public SubjectController(SubjectService subjectService,
                              SubjectAssembler subjectAssembler) {
        this.subjectService = subjectService;
        this.subjectAssembler = subjectAssembler;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<SubjectResponse>> createSubject(CreateSubjectRequest request) {
        Subject subject = subjectService.createSubject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectAssembler.toModel(subject));
    }

    @Override
    public ResponseEntity<EntityModel<SubjectResponse>> getSubject(Long id) {
        // M13 G9 — getSubjectForRead делает groupId-check для STUDENT
        Subject subject = subjectService.getSubjectForRead(id);
        return ResponseEntity.ok(subjectAssembler.toModel(subject));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<SubjectResponse>>> listSubjects(
            Pageable pageable,
            PagedResourcesAssembler<SubjectResponse> assembler) {
        Page<Subject> page = subjectService.listSubjects(pageable);
        Page<SubjectResponse> responsePage = page.map(subjectAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<SubjectResponse>> updateSubject(Long id, UpdateSubjectRequest request) {
        Subject subject = subjectService.updateSubject(id, request);
        return ResponseEntity.ok(subjectAssembler.toModel(subject));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> deleteSubject(Long id, boolean force) {
        subjectService.deleteSubject(id, force);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> addTeacher(Long id, Long teacherId) {
        subjectService.addTeacher(id, teacherId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> removeTeacher(Long id, Long teacherId) {
        subjectService.removeTeacher(id, teacherId);
        return ResponseEntity.noContent().build();
    }
}
