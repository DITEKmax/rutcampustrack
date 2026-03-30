package ru.rutcampustrack.academic.assistant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.assistant.UpdateAssistantPermissionsRequest;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.HeadmanAssistant;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AssistantService {

    private final HeadmanAssistantRepository assistantRepository;
    private final UserRepository userRepository;
    private final RequestContext requestContext;

    public AssistantService(HeadmanAssistantRepository assistantRepository,
                             UserRepository userRepository,
                             RequestContext requestContext) {
        this.assistantRepository = assistantRepository;
        this.userRepository = userRepository;
        this.requestContext = requestContext;
    }

    private void requireHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может управлять помощниками");
        }
    }

    @Transactional
    public HeadmanAssistant assignAssistant(AssignAssistantRequest request) {
        requireHeadman();

        // Validate student exists
        userRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.studentId()));

        // Check no existing active assistant
        assistantRepository.findByGroupIdAndStudentId(request.groupId(), request.studentId())
                .filter(HeadmanAssistant::isActive)
                .ifPresent(existing -> {
                    throw new ConflictException("Студент ��же является помо��ником старосты в этой группе");
                });

        // Convert permissions to lowercase string array (Pitfall 4)
        String[] permissionsArray = request.permissions().stream()
                .map(p -> p.name().toLowerCase())
                .toArray(String[]::new);

        HeadmanAssistant assistant = new HeadmanAssistant(
                request.groupId(), request.studentId(), permissionsArray, requestContext.getUserId()
        );
        return assistantRepository.save(assistant);
    }

    @Transactional(readOnly = true)
    public List<HeadmanAssistant> listAssistants(Long groupId) {
        return assistantRepository.findByGroupIdAndIsActiveTrue(groupId);
    }

    @Transactional
    public HeadmanAssistant updatePermissions(Long id, UpdateAssistantPermissionsRequest request) {
        requireHeadman();
        HeadmanAssistant assistant = assistantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HeadmanAssistant", "id", id));

        // Convert permissions to lowercase string array (Pitfall 4)
        String[] permissionsArray = request.permissions().stream()
                .map(p -> p.name().toLowerCase())
                .toArray(String[]::new);

        assistant.setPermissions(permissionsArray);
        return assistantRepository.save(assistant);
    }

    @Transactional
    public void revokeAssistant(Long id) {
        requireHeadman();
        HeadmanAssistant assistant = assistantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HeadmanAssistant", "id", id));
        assistant.setActive(false);
        assistant.setRevokedAt(OffsetDateTime.now());
        assistantRepository.save(assistant);
    }
}
