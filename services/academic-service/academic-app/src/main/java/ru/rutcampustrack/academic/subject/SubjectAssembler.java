package ru.rutcampustrack.academic.subject;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.assignment.AssignmentLifecycle;
import ru.rutcampustrack.academic.contract.dto.subject.AssignmentSummaryResponse;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.SubjectLessonType;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;

@Component
public class SubjectAssembler
        implements RepresentationModelAssembler<Subject, EntityModel<SubjectResponse>> {

    private final SubjectLessonTypeRepository lessonTypeRepository;
    private final AssignmentRepository assignmentRepository;

    public SubjectAssembler(
            SubjectLessonTypeRepository lessonTypeRepository,
            AssignmentRepository assignmentRepository) {
        this.lessonTypeRepository = lessonTypeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public EntityModel<SubjectResponse> toModel(Subject subject) {
        return EntityModel.of(
                toResponse(subject),
                linkTo(methodOn(SubjectController.class).getSubject(subject.getId()))
                        .withSelfRel());
    }

    public Page<SubjectResponse> toResponsePage(Page<Subject> page) {
        if (page.isEmpty()) return page.map(subject -> toResponse(subject, List.of(), List.of()));
        List<Long> ids = page.stream().map(Subject::getId).toList();
        Map<Long, List<SubjectLessonType>> types =
                lessonTypeRepository.findBySubjectIdIn(ids).stream()
                        .collect(Collectors.groupingBy(SubjectLessonType::getSubjectId));
        Map<Long, List<Assignment>> assignments =
                assignmentRepository.findPublishedActiveBySubjectIdIn(ids).stream()
                        .collect(Collectors.groupingBy(Assignment::getSubjectId));
        return page.map(
                subject ->
                        toResponse(
                                subject,
                                types.getOrDefault(subject.getId(), List.of()),
                                assignments.getOrDefault(subject.getId(), List.of())));
    }

    public SubjectResponse toResponse(Subject subject) {
        return toResponse(
                subject,
                lessonTypeRepository.findBySubjectId(subject.getId()),
                assignmentRepository.findPublishedActiveBySubjectIdAndGroupId(
                        subject.getId(), subject.getGroupId()));
    }

    private SubjectResponse toResponse(
            Subject subject, List<SubjectLessonType> lessonTypes, List<Assignment> candidates) {
        List<SubjectType> types =
                lessonTypes.stream()
                        .map(SubjectLessonType::getLessonType)
                        .sorted(Comparator.comparing(SubjectType::name))
                        .toList();
        List<Assignment> assignments =
                candidates.stream()
                        .filter(a -> a.getLifecycleState() == AssignmentLifecycle.ACTIVE)
                        .filter(a -> subject.getGroupId().equals(a.getGroupId()))
                        .sorted(
                                Comparator.comparing(Assignment::getSemesterId)
                                        .thenComparing(a -> a.getLessonType().name())
                                        .thenComparing(Assignment::getTeacherId)
                                        .thenComparing(Assignment::getValidFrom)
                                        .thenComparing(Assignment::getId))
                        .toList();
        List<AssignmentSummaryResponse> summaries =
                assignments.stream()
                        .map(
                                a ->
                                        new AssignmentSummaryResponse(
                                                a.getId(),
                                                a.getTeacherId(),
                                                a.getSemesterId(),
                                                a.getLessonType(),
                                                a.getValidFrom(),
                                                a.getValidUntilExclusive()))
                        .toList();
        List<Long> teacherIds =
                assignments.stream().map(Assignment::getTeacherId).distinct().sorted().toList();
        List<Long> assignmentIds = assignments.stream().map(Assignment::getId).toList();
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getType(),
                subject.getGroupId(),
                types,
                teacherIds,
                summaries,
                assignmentIds,
                subject.getCreatedAt());
    }
}
