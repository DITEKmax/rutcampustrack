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

    /**
     * M13 G9 — headman может управлять только assistant'ами своей группы.
     * Защищает от IDOR: headman A не должен править/удалять/смотреть assistant'а группы B.
     */
    private void assertOwnGroup(Long groupId) {
        Long ownGroupId = requestContext.getGroupId();
        if (ownGroupId == null || !ownGroupId.equals(groupId)) {
            throw new AccessDeniedException("Помощник принадлежит другой группе");
        }
    }

    @Transactional
    public HeadmanAssistant assignAssistant(AssignAssistantRequest request) {
        requireHeadman();
        // M13 G9 — request.groupId должен совпадать с группой headman'а
        assertOwnGroup(request.groupId());

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
        // M13 G9 — headman видит assistant'ов только своей группы
        assertOwnGroup(groupId);
        return assistantRepository.findByGroupIdAndIsActiveTrue(groupId);
    }

    @Transactional
    public HeadmanAssistant updatePermissions(Long id, UpdateAssistantPermissionsRequest request) {
        requireHeadman();
        HeadmanAssistant assistant = assistantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HeadmanAssistant", "id", id));
        // M13 G9 — assistant должен принадлежать группе headman'а
        assertOwnGroup(assistant.getGroupId());

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
        // M13 G9 — assistant должен принадлежать группе headman'а
        assertOwnGroup(assistant.getGroupId());
        assistant.setActive(false);
        assistant.setRevokedAt(OffsetDateTime.now());
        assistantRepository.save(assistant);
    }
}
