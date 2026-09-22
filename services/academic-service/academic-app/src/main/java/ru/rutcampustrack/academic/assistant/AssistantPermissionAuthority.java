package ru.rutcampustrack.academic.assistant;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;
import ru.rutcampustrack.academic.entity.HeadmanAssistant;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Fresh durable authority for assistant actions.
 *
 * <p>The actor is always taken from the validated internal JWT.  The request
 * only supplies the target group and closed permission name; neither a user
 * id nor an isHeadman flag from the caller can elevate the actor.  Repository
 * reads are intentionally uncached so revocation is visible on the next RPC.
 */
@Service
public class AssistantPermissionAuthority {

    private static final Set<String> RPC_PERMISSIONS = Set.of(
            AssistantPermission.MARK_ATTENDANCE.name(),
            AssistantPermission.MANAGE_EXCUSES.name(),
            AssistantPermission.CANCEL_LESSONS.name(),
            AssistantPermission.VIEW_STATS.name());

    private final UserRoleGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final HeadmanAssistantRepository assistantRepository;

    public AssistantPermissionAuthority(UserRoleGrantRepository grantRepository,
                                        UserRepository userRepository,
                                        HeadmanAssistantRepository assistantRepository) {
        this.grantRepository = grantRepository;
        this.userRepository = userRepository;
        this.assistantRepository = assistantRepository;
    }

    public boolean allows(InternalJwtClaims claims, long targetGroupId, String rawPermission) {
        if (claims == null || claims.userId() <= 0 || targetGroupId <= 0
                || claims.groupId() == null || !claims.groupId().equals(targetGroupId)
                || claims.readOnly() || !"ACTIVE".equalsIgnoreCase(claims.status())) {
            return false;
        }
        String permission = rawPermission == null
                ? ""
                : rawPermission.trim().toUpperCase(Locale.ROOT);
        if (!RPC_PERMISSIONS.contains(permission)) {
            return false;
        }

        if ("HEADMAN".equalsIgnoreCase(claims.role())) {
            return grantRepository.findByUserIdAndRoleAndStatus(
                            claims.userId(), "headman", "active")
                    .stream()
                    .anyMatch(grant -> targetGroupId == (grant.getGroupId() == null
                            ? Long.MIN_VALUE : grant.getGroupId()));
        }
        if (!"STUDENT".equalsIgnoreCase(claims.role())) {
            return false;
        }

        Long studentGroupId = userRepository.findActiveStudentGrantGroupId(claims.userId())
                .orElse(null);
        if (!Long.valueOf(targetGroupId).equals(studentGroupId)) {
            return false;
        }
        return assistantRepository.findByGroupIdAndStudentIdAndIsActiveTrue(targetGroupId, claims.userId())
                .map(assistant -> hasPermission(assistant, permission))
                .orElse(false);
    }

    private static boolean hasPermission(HeadmanAssistant assistant, String permission) {
        String expected = permission.toLowerCase(Locale.ROOT);
        return assistant.getPermissions() != null
                && Arrays.stream(assistant.getPermissions())
                .filter(value -> value != null)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(expected::equals);
    }
}
