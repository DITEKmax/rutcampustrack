package ru.rutcampustrack.academic.assistant;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.AssistantApi;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.assistant.AssistantResponse;
import ru.rutcampustrack.academic.contract.dto.assistant.UpdateAssistantPermissionsRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.HeadmanAssistant;
import ru.rutcampustrack.academic.security.RequireRole;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
public class AssistantController implements AssistantApi {

    private final AssistantService assistantService;
    private final AssistantAssembler assistantAssembler;

    public AssistantController(AssistantService assistantService, AssistantAssembler assistantAssembler) {
        this.assistantService = assistantService;
        this.assistantAssembler = assistantAssembler;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<AssistantResponse>> assignAssistant(AssignAssistantRequest request) {
        HeadmanAssistant assistant = assistantService.assignAssistant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assistantAssembler.toModel(assistant));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<CollectionModel<EntityModel<AssistantResponse>>> listAssistants(Long groupId) {
        List<HeadmanAssistant> assistants = assistantService.listAssistants(groupId);
        CollectionModel<EntityModel<AssistantResponse>> models = assistantAssembler.toCollectionModel(assistants);
        models.add(linkTo(methodOn(AssistantController.class).listAssistants(groupId)).withSelfRel());
        return ResponseEntity.ok(models);
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<AssistantResponse>> updatePermissions(Long id,
                                                                              UpdateAssistantPermissionsRequest request) {
        HeadmanAssistant assistant = assistantService.updatePermissions(id, request);
        return ResponseEntity.ok(assistantAssembler.toModel(assistant));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> revokeAssistant(Long id) {
        assistantService.revokeAssistant(id);
        return ResponseEntity.noContent().build();
    }
}
