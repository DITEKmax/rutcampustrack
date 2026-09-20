package ru.rutcampustrack.academic.assignment;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignmentResponse;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

/** Maps the immutable V25 identity and validity fields to REST responses. */
@Component
public class AssignmentAssembler implements RepresentationModelAssembler<Assignment, EntityModel<AssignmentResponse>> {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final GroupRepository groupRepository;

    public AssignmentAssembler(UserRepository userRepository,
                               SubjectRepository subjectRepository,
                               GroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public EntityModel<AssignmentResponse> toModel(Assignment assignment) {
        // No executable close link is exposed while the lifecycle fence is
        // intentionally disabled by L5A.
        return EntityModel.of(toResponse(assignment));
    }

    public AssignmentResponse toResponse(Assignment assignment) {
        String teacherName = userRepository.findById(assignment.getTeacherId())
                .map(u -> u.getDisplayName()).orElse("Unknown");
        String subjectName = subjectRepository.findById(assignment.getSubjectId())
                .map(s -> s.getName()).orElse("Unknown");
        String groupName = groupRepository.findById(assignment.getGroupId())
                .map(g -> g.getName()).orElse("Unknown");
        return new AssignmentResponse(
                assignment.getId(), assignment.getTeacherId(), teacherName,
                assignment.getSubjectId(), subjectName,
                assignment.getGroupId(), groupName, assignment.getSemesterId(),
                assignment.getLessonType(), assignment.getValidFrom(),
                assignment.getValidUntilExclusive());
    }
}
