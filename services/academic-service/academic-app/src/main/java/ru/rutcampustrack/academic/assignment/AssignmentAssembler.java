package ru.rutcampustrack.academic.assignment;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignmentResponse;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class AssignmentAssembler implements RepresentationModelAssembler<TeacherSubjectGroup, EntityModel<AssignmentResponse>> {

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
    public EntityModel<AssignmentResponse> toModel(TeacherSubjectGroup tsg) {
        AssignmentResponse response = toResponse(tsg);
        return EntityModel.of(response,
                linkTo(methodOn(AssignmentController.class).removeAssignment(tsg.getId())).withSelfRel()
        );
    }

    public AssignmentResponse toResponse(TeacherSubjectGroup tsg) {
        String teacherName = userRepository.findById(tsg.getTeacherId())
                .map(u -> u.getDisplayName()).orElse("Unknown");
        String subjectName = subjectRepository.findById(tsg.getSubjectId())
                .map(s -> s.getName()).orElse("Unknown");
        String groupName = groupRepository.findById(tsg.getGroupId())
                .map(g -> g.getName()).orElse("Unknown");

        return new AssignmentResponse(
                tsg.getId(),
                tsg.getTeacherId(), teacherName,
                tsg.getSubjectId(), subjectName,
                tsg.getGroupId(), groupName,
                tsg.getSemesterId()
        );
    }
}
