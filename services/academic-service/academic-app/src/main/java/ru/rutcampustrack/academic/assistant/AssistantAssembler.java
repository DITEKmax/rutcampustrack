package ru.rutcampustrack.academic.assistant;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.assistant.AssistantResponse;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;
import ru.rutcampustrack.academic.entity.HeadmanAssistant;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.util.Arrays;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class AssistantAssembler implements RepresentationModelAssembler<HeadmanAssistant, EntityModel<AssistantResponse>> {

    private final UserRepository userRepository;

    public AssistantAssembler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public EntityModel<AssistantResponse> toModel(HeadmanAssistant assistant) {
        var userOpt = userRepository.findById(assistant.getStudentId());
        String studentName = userOpt.map(u -> u.getDisplayName()).orElse("Unknown");
        String login = userOpt.map(u -> u.getLogin()).orElse(null);

        List<AssistantPermission> permissions = Arrays.stream(assistant.getPermissions())
                .map(s -> AssistantPermission.valueOf(s.toUpperCase()))
                .toList();

        AssistantResponse response = new AssistantResponse();
        response.setId(assistant.getId());
        response.setStudentId(assistant.getStudentId());
        response.setStudentName(studentName);
        response.setFullName(studentName);
        response.setLogin(login);
        response.setGroupId(assistant.getGroupId());
        response.setPermissions(permissions);
        response.setActive(assistant.isActive());
        response.setAssignedAt(assistant.getAssignedAt());
        response.setRevokedAt(assistant.getRevokedAt());

        return EntityModel.of(response,
                linkTo(methodOn(AssistantController.class).revokeAssistant(assistant.getId())).withSelfRel()
        );
    }
}
