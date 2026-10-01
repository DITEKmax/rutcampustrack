package ru.rutcampustrack.academic.grpc;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.Set;
import java.util.stream.Collectors;

/** Uncached, coherent durable authority and current membership snapshot. */
@Service
public class HeadmanGroupCompositionReadService {
    private final GroupRepository groups;
    private final UserRepository users;
    private final HeadmanAssistantRepository assistants;

    public HeadmanGroupCompositionReadService(GroupRepository groups, UserRepository users,
                                              HeadmanAssistantRepository assistants) {
        this.groups = groups;
        this.users = users;
        this.assistants = assistants;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HeadmanGroupCompositionResponse read(InternalJwtClaims actor, long groupId) {
        if (actor == null || actor.userId() <= 0 || groupId <= 0
                || actor.groupId() == null || actor.groupId() != groupId
                || !"HEADMAN".equals(actor.role()) || !actor.isHeadman()
                || actor.readOnly() || !"ACTIVE".equals(actor.status())) {
            throw denied();
        }
        var group = groups.findById(groupId).orElseThrow(HeadmanGroupCompositionReadService::denied);
        if (!group.isActive()) throw denied();
        var headmen = users.findActiveHeadmanIdsByGrantGroupId(groupId);
        if (headmen.size() != 1 || headmen.getFirst() != actor.userId()) throw denied();
        var members = users.findActiveStudentsByGrantGroupId(groupId);
        if (members.stream().noneMatch(user -> user.getId() == actor.userId())) throw denied();
        Set<Long> assistantIds = assistants.findByGroupIdAndIsActiveTrue(groupId).stream()
                .map(assistant -> assistant.getStudentId()).collect(Collectors.toSet());
        var result = HeadmanGroupCompositionResponse.newBuilder()
                .setGroupId(groupId).setGroupName(group.getName());
        // The canonical repository orders surname/name/patronymic with ru_icu.
        members.forEach(user -> result.addMembers(GroupCompositionMember.newBuilder()
                .setUserId(user.getId()).setDisplayName(user.getDisplayName()).setLogin(user.getLogin())
                .setGroupRole(user.getId() == actor.userId() ? "HEADMAN"
                        : assistantIds.contains(user.getId()) ? "ASSISTANT" : "STUDENT")));
        return result.build();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Выгрузка состава доступна только текущему старосте своей группы");
    }
}
