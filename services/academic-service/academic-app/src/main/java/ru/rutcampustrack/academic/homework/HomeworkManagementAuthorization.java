package ru.rutcampustrack.academic.homework;

import java.util.Arrays;
import java.util.Objects;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;

/** Shared current-authority check for publication and each edit transaction. */
final class HomeworkManagementAuthorization {
    private HomeworkManagementAuthorization() {}

    static String require(
            RequestContext context,
            UserRoleGrantRepository grants,
            HeadmanAssistantRepository assistants) {
        Long actor = context.getUserId(), group = context.getGroupId();
        if (actor == null || actor <= 0 || group == null || group <= 0 || grants == null) {
            throw new AccessDeniedException(
                    "Управлять ДЗ может только староста или помощник с manage_homework");
        }
        if (grants.findByUserIdAndRoleAndStatus(actor, "headman", "active").stream()
                .anyMatch(grant -> group.equals(grant.getGroupId()))) return "HEADMAN";
        if (grants.findByUserIdAndRoleAndStatus(actor, "student", "active").stream()
                .noneMatch(grant -> group.equals(grant.getGroupId())))
            throw new AccessDeniedException("Нет текущего права управления ДЗ");
        var assistant =
                assistants
                        .findByGroupIdAndStudentIdAndIsActiveTrue(group, actor)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Нет текущего права управления ДЗ"));
        if (assistant.getPermissions() == null
                || Arrays.stream(assistant.getPermissions())
                        .filter(Objects::nonNull)
                        .noneMatch(value -> "manage_homework".equalsIgnoreCase(value.trim()))) {
            throw new AccessDeniedException("Отсутствует право MANAGE_HOMEWORK");
        }
        return "ASSISTANT";
    }
}
