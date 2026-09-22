package ru.rutcampustrack.academic.assistant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.assistant.AssistantPermissionOption;
import ru.rutcampustrack.academic.contract.dto.assistant.UpdateAssistantPermissionsRequest;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.HeadmanAssistant;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class AssistantService {

    private final HeadmanAssistantRepository assistantRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final UserRoleGrantRepository grantRepository;
    private final RequestContext requestContext;

    public AssistantService(HeadmanAssistantRepository assistantRepository,
                             UserRepository userRepository,
                             GroupRepository groupRepository,
                             UserRoleGrantRepository grantRepository,
                             RequestContext requestContext) {
        this.assistantRepository = assistantRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.grantRepository = grantRepository;
        this.requestContext = requestContext;
    }

    /**
     * M13 G9 — headman может управлять только assistant'ами своей группы.
     * Защищает от IDOR: headman A не должен править/удалять/смотреть assistant'а группы B.
     */
    private Long requireOwnGroup(Long groupId) {
        Long ownGroupId = requestContext.getGroupId();
        if (ownGroupId == null || !ownGroupId.equals(groupId)) {
            throw new AccessDeniedException("Помощник принадлежит другой группе");
        }
        return ownGroupId;
    }

    private Long requireCurrentHeadmanRead(Long requestedGroupId) {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может управлять помощниками");
        }
        Long groupId = requireOwnGroup(requestedGroupId);
        Long actorId = requestContext.getUserId();
        boolean currentHeadman = actorId != null
                && grantRepository.findByUserIdAndRoleAndStatus(actorId, "headman", "active").stream()
                .anyMatch(grant -> groupId.equals(grant.getGroupId()));
        if (!currentHeadman) {
            throw new AccessDeniedException("Текущая сессия больше не является старостой этой группы");
        }
        return groupId;
    }

    /**
     * Serializes assistant writes with headman transfer/revocation.  The group
     * row is locked before any student lookup, then the durable headman grant
     * is re-read; the request-context flag is only a legacy admission hint.
     */
    private Long lockAndRequireCurrentHeadman(Long requestedGroupId) {
        Long groupId = requireCurrentHeadmanRead(requestedGroupId);
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new AccessDeniedException("Учебная группа не найдена"));
        Long actorId = requestContext.getUserId();
        boolean currentHeadman = actorId != null
                && grantRepository.findByUserIdAndRoleAndStatus(actorId, "headman", "active").stream()
                .anyMatch(grant -> groupId.equals(grant.getGroupId()));
        if (!group.isActive() || !currentHeadman) {
            throw new AccessDeniedException("Текущая сессия больше не является старостой этой группы");
        }
        return group.getId();
    }

    private void requireActiveStudentGrant(Long studentId, Long groupId) {
        Long grantedGroupId = userRepository.findActiveStudentGrantGroupId(studentId)
                .orElse(null);
        if (grantedGroupId == null || !grantedGroupId.equals(groupId)) {
            throw new AccessDeniedException("Студент не имеет активного права в этой группе");
        }
    }

    @Transactional
    public HeadmanAssistant assignAssistant(AssignAssistantRequest request) {
        // M13 G9 — request.groupId должен совпадать с группой headman'а
        Long groupId = lockAndRequireCurrentHeadman(request.groupId());

        // The durable active STUDENT grant is the membership authority. The
        // legacy users.group_id field and the client groupId are not enough.
        userRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.studentId()));
        requireActiveStudentGrant(request.studentId(), groupId);

        // Check no existing active assistant
        assistantRepository.findByGroupIdAndStudentIdAndIsActiveTrue(groupId, request.studentId())
                .ifPresent(existing -> {
                    throw new ConflictException("Студент ��же является помо��ником старосты в этой группе");
                });

        // Convert permissions to lowercase string array (Pitfall 4)
        String[] permissionsArray = request.permissions().stream()
                .map(p -> p.name().toLowerCase())
                .toArray(String[]::new);

        HeadmanAssistant assistant = new HeadmanAssistant(
                groupId, request.studentId(), permissionsArray, requestContext.getUserId()
        );
        return assistantRepository.save(assistant);
    }

    @Transactional(readOnly = true)
    public List<HeadmanAssistant> listAssistants(Long groupId) {
        // M13 G9 — headman видит assistant'ов только своей группы
        return assistantRepository.findByGroupIdAndIsActiveTrue(requireCurrentHeadmanRead(groupId));
    }

    @Transactional(readOnly = true)
    public List<AssistantPermissionOption> listPermissionCatalog() {
        requireCurrentHeadmanRead(requestContext.getGroupId());
        return Arrays.stream(AssistantPermission.values())
                .map(permission -> new AssistantPermissionOption(permission, permissionLabel(permission)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssistantPermissionOption> listMyPermissionCatalog() {
        Long userId = requestContext.getUserId();
        Long groupId = requestContext.getGroupId();
        if (userId == null || userId <= 0 || groupId == null || groupId <= 0) {
            throw new AccessDeniedException("Не хватает authenticated user/group scope");
        }
        if (requestContext.isHeadman()) {
            return listPermissionCatalog();
        }
        Long grantedGroupId = userRepository.findActiveStudentGrantGroupId(userId).orElse(null);
        if (!groupId.equals(grantedGroupId)) {
            return List.of();
        }
        return assistantRepository.findByGroupIdAndStudentIdAndIsActiveTrue(groupId, userId)
                .map(assistant -> Arrays.stream(AssistantPermission.values())
                        .filter(permission -> assistant.getPermissions() != null
                                && Arrays.stream(assistant.getPermissions())
                                .filter(value -> value != null)
                                .anyMatch(value -> permission.name().equalsIgnoreCase(value)))
                        .map(permission -> new AssistantPermissionOption(permission, permissionLabel(permission)))
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional
    public HeadmanAssistant updatePermissions(Long id, UpdateAssistantPermissionsRequest request) {
        // M13 G9 — assistant должен принадлежать группе headman'а
        Long groupId = assistantRepository.findGroupIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HeadmanAssistant", "id", id));
        lockAndRequireCurrentHeadman(groupId);
        HeadmanAssistant assistant = assistantRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("HeadmanAssistant", "id", id));
        if (!groupId.equals(assistant.getGroupId())) {
            throw new AccessDeniedException("Помощник принадлежит другой группе");
        }
        if (!assistant.isActive()) {
            throw new ConflictException("Помощник уже отозван");
        }

        // Convert permissions to lowercase string array (Pitfall 4)
        String[] permissionsArray = request.permissions().stream()
                .map(p -> p.name().toLowerCase())
                .toArray(String[]::new);

        assistant.setPermissions(permissionsArray);
        return assistantRepository.save(assistant);
    }

    @Transactional
    public void revokeAssistant(Long id) {
        // M13 G9 — assistant должен принадлежать группе headman'а
        Long groupId = assistantRepository.findGroupIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HeadmanAssistant", "id", id));
        lockAndRequireCurrentHeadman(groupId);
        HeadmanAssistant assistant = assistantRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("HeadmanAssistant", "id", id));
        if (!groupId.equals(assistant.getGroupId())) {
            throw new AccessDeniedException("Помощник принадлежит другой группе");
        }
        if (!assistant.isActive()) {
            return;
        }
        assistant.setActive(false);
        assistant.setRevokedAt(OffsetDateTime.now());
        assistantRepository.save(assistant);
    }

    private static String permissionLabel(AssistantPermission permission) {
        return switch (permission) {
            case MARK_ATTENDANCE -> "Отмечать посещаемость";
            case MANAGE_EXCUSES -> "Обрабатывать уважительные причины";
            case MANAGE_HOMEWORK -> "Управлять домашними заданиями";
            case CANCEL_LESSONS -> "Отменять занятия";
            case VIEW_STATS -> "Просматривать статистику";
        };
    }
}
