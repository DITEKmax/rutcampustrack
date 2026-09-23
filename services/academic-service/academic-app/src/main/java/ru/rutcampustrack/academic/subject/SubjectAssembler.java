package ru.rutcampustrack.academic.subject;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.subject.AssignmentSummaryResponse;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.SubjectLessonType;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;

import java.util.Comparator;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class SubjectAssembler implements RepresentationModelAssembler<Subject, EntityModel<SubjectResponse>> {

    private final SubjectLessonTypeRepository lessonTypeRepository;
    private final AssignmentRepository assignmentRepository;

    public SubjectAssembler(SubjectLessonTypeRepository lessonTypeRepository,
                            AssignmentRepository assignmentRepository) {
        this.lessonTypeRepository = lessonTypeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public EntityModel<SubjectResponse> toModel(Subject subject) {
        return EntityModel.of(toResponse(subject),
                linkTo(methodOn(SubjectController.class).getSubject(subject.getId())).withSelfRel());
    }

    public SubjectResponse toResponse(Subject subject) {
        List<SubjectType> types = lessonTypeRepository.findBySubjectId(subject.getId()).stream()
                .map(SubjectLessonType::getLessonType)
                .sorted(Comparator.comparing(SubjectType::name))
                .toList();
        List<Assignment> assignments = assignmentRepository.findBySubjectId(subject.getId()).stream()
                .filter(a -> "ACTIVE".equals(a.getLifecycleState()))
                .sorted(Comparator.comparing(Assignment::getSemesterId)
                        .thenComparing(a -> a.getLessonType().name())
                        .thenComparing(Assignment::getTeacherId)
                        .thenComparing(Assignment::getValidFrom)
                        .thenComparing(Assignment::getId))
                .toList();
        List<AssignmentSummaryResponse> summaries = assignments.stream()
                .map(a -> new AssignmentSummaryResponse(a.getId(), a.getTeacherId(), a.getSemesterId(),
                        a.getLessonType(), a.getValidFrom(), a.getValidUntilExclusive()))
                .toList();
        List<Long> teacherIds = assignments.stream()
                .map(Assignment::getTeacherId)
                .distinct()
                .sorted()
                .toList();
        List<Long> assignmentIds = assignments.stream().map(Assignment::getId).toList();
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getType(), subject.getGroupId(),
                types, teacherIds, summaries, assignmentIds, subject.getCreatedAt());
    }
}
