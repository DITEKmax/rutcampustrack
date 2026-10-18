package ru.rutcampustrack.academic.group;

import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentPreviewResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanCandidateResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanRosterResponse;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical group-scoped headman mutation.
 *
 * <p>The lock order is deliberately user rows in ascending id order followed by the group row.
 * Assistant mutations use the group row as their first lock and then re-read durable authority, so
 * this service never acquires a user lock after the group lock.
 */
@Service
public class GroupHeadmanAssignmentService {
    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.notification.AcademicPersonalNotificationPublisher
            personalNotifications;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.admin.AcademicAdminAccess adminAccess;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations
            identityOperations;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence fences;

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final UserRoleGrantWriter roleGrantWriter;
    private final HeadmanAssistantRepository assistantRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    public GroupHeadmanAssignmentService(
            GroupRepository groupRepository,
            UserRepository userRepository,
            UserRoleGrantWriter roleGrantWriter,
            HeadmanAssistantRepository assistantRepository,
            ApplicationEventPublisher eventPublisher,
            @Nullable CacheManager cacheManager) {
        this.groupRepository = Objects.requireNonNull(groupRepository, "groupRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.roleGrantWriter = Objects.requireNonNull(roleGrantWriter, "roleGrantWriter");
        this.assistantRepository =
                Objects.requireNonNull(assistantRepository, "assistantRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.cacheManager = cacheManager;
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public HeadmanRosterResponse roster(Long groupId) {
        Group group = requireActiveGroup(groupId);
        fences.requireGroupReadable(group.getId());
        CurrentHeadman current = readCurrentHeadman(group.getId());
        List<HeadmanCandidateResponse> candidates =
                userRepository.findActiveStudentsByGrantGroupId(group.getId()).stream()
                        .map(
                                user ->
                                        new HeadmanCandidateResponse(
                                                user.getId(),
                                                user.getDisplayName(),
                                                Objects.equals(user.getId(), current.id())))
                        .toList();
        int activeAssistants =
                assistantRepository.findByGroupIdAndIsActiveTrue(group.getId()).size();
        return new HeadmanRosterResponse(
                group.getId(), current.id(), current.fio(), candidates, activeAssistants);
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public HeadmanAssignmentPreviewResponse preview(Long groupId, Long studentId) {
        Group group = requireActiveGroup(groupId);
        fences.requireGroupReadable(group.getId());
        User candidate = requireCandidate(group.getId(), studentId);
        CurrentHeadman current = readCurrentHeadman(group.getId());
        boolean same = Objects.equals(current.id(), candidate.getId());
        int assistants =
                same ? 0 : assistantRepository.findByGroupIdAndIsActiveTrue(group.getId()).size();
        return new HeadmanAssignmentPreviewResponse(
                group.getId(),
                current.id(),
                current.fio(),
                candidate.getId(),
                candidate.getDisplayName(),
                same,
                current.id() == null,
                assistants);
    }

    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public HeadmanAssignmentResponse assign(Long groupId, AssignHeadmanRequest request) {
        adminAccess.require("GROUPS_MANAGE");
        if (request == null || request.studentId() == null)
            throw new BadRequestException("Запрос назначения старосты обязателен");
        return identityOperations.headman(
                groupId, request.studentId(), request.expectedHeadmanId(), true, null);
    }

    /**
     * Compatibility bridge for the existing ADMIN users PATCH. It intentionally has no
     * caller-supplied CAS value, but still uses the canonical lock order and all locked rechecks.
     * The public group endpoint always uses CAS.
     */
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public HeadmanAssignmentResponse assignLegacyHeadman(Long studentId) {
        return assignLegacyHeadman(studentId, null, null);
    }

    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public HeadmanAssignmentResponse assignLegacyHeadman(
            Long studentId, Long telegramId, String status) {
        adminAccess.require("USERS_MANAGE", "GROUPS_MANAGE");
        if (studentId == null) throw new BadRequestException("ID студента обязателен");
        var patch =
                new ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest(
                        null,
                        null,
                        null,
                        true,
                        null,
                        null,
                        telegramId,
                        status == null
                                ? null
                                : ru.rutcampustrack.academic.contract.enums.AccountStatus.valueOf(
                                        status.toUpperCase(java.util.Locale.ROOT)));
        return identityOperations.headman(null, studentId, null, false, patch);
    }

    /** The legacy false flag is a no-op for non-headmen; current headmen require replacement. */
    @Transactional
    public HeadmanAssignmentResponse revokeLegacyHeadman(Long studentId) {
        adminAccess.begin("USERS_MANAGE");
        adminAccess.require("GROUPS_MANAGE");
        if (studentId == null || studentId <= 0) {
            throw new BadRequestException("studentId", "ID студента должен быть положительным");
        }
        adminAccess.target(studentId);
        User candidate =
                userRepository
                        .findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", studentId));
        Long groupId = activeStudentGroupOrLegacyGroup(candidate);
        if (groupId == null) {
            return new HeadmanAssignmentResponse(
                    null, studentId, candidate.getDisplayName(), false, false, 0);
        }

        // A legacy false-flag request has one known user. It still takes the group lock
        // after that user lock, matching the canonical order.
        lockUsers(List.of(studentId));
        Group group = lockActiveGroup(groupId);
        List<Long> currentIds = readCurrentHeadmanIds(group.getId());
        if (currentIds.size() > 1) throw duplicateHeadmanConflict(group.getId());
        if (!Objects.equals(currentIds.isEmpty() ? null : currentIds.get(0), studentId)) {
            String currentFio =
                    currentIds.isEmpty()
                            ? null
                            : userRepository
                                    .findById(currentIds.get(0))
                                    .map(User::getDisplayName)
                                    .orElse(null);
            return new HeadmanAssignmentResponse(
                    group.getId(),
                    currentIds.isEmpty() ? null : currentIds.get(0),
                    currentFio,
                    false,
                    false,
                    0);
        }
        throw new ConflictException(
                "isHeadman",
                false,
                "Снять действующего старосту можно только заменой на другого студента");
    }

    private Group requireActiveGroup(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BadRequestException("groupId", "ID группы должен быть положительным");
        }
        Group group =
                groupRepository
                        .findById(groupId)
                        .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        if (!group.isActive()) {
            throw new ConflictException(
                    "groupId", groupId, "Архивная группа не принимает нового старосту");
        }
        return group;
    }

    private Group lockActiveGroup(Long groupId) {
        Group group =
                groupRepository
                        .findByIdForUpdate(groupId)
                        .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        if (!group.isActive()) {
            throw new ConflictException(
                    "groupId", groupId, "Архивная группа не принимает нового старосту");
        }
        return group;
    }

    private User requireCandidate(Long groupId, Long studentId) {
        if (studentId == null || studentId <= 0) {
            throw new BadRequestException("studentId", "ID студента должен быть положительным");
        }
        User candidate =
                userRepository
                        .findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", studentId));
        validateCandidateAfterLocks(candidate, studentId, groupId);
        return candidate;
    }

    private void validateCandidateAfterLocks(User candidate, Long studentId, Long groupId) {
        if (candidate == null) {
            throw new ResourceNotFoundException("User", "id", studentId);
        }
        Long membershipGroup =
                userRepository.findActiveStudentGrantGroupId(candidate.getId()).orElse(null);
        if (!Objects.equals(membershipGroup, groupId)) {
            throw new ConflictException(
                    "studentId",
                    candidate.getId(),
                    "Старостой может быть только студент с активной ролью STUDENT в выбранной"
                            + " группе");
        }
    }

    private Long activeStudentGroupOrLegacyGroup(User candidate) {
        return userRepository
                .findActiveStudentGrantGroupId(candidate.getId())
                .orElse(candidate.getGroupId());
    }

    private CurrentHeadman readCurrentHeadman(Long groupId) {
        List<User> users = userRepository.findActiveHeadmenByGrantGroupId(groupId);
        if (users.size() > 1) throw duplicateHeadmanConflict(groupId);
        if (users.isEmpty()) return new CurrentHeadman(null, null);
        User user = users.get(0);
        return new CurrentHeadman(user.getId(), user.getDisplayName());
    }

    private List<Long> readCurrentHeadmanIds(Long groupId) {
        return userRepository.findActiveHeadmanIdsByGrantGroupId(groupId);
    }

    private Map<Long, User> lockUsers(List<Long> ids) {
        Map<Long, User> locked = new LinkedHashMap<>();
        List<Long> ordered = new ArrayList<>(ids);
        ordered.add(adminAccess.actorId());
        ordered.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(
                        id ->
                                locked.put(
                                        id,
                                        userRepository
                                                .findByIdForUpdate(id)
                                                .orElseThrow(
                                                        () ->
                                                                new ResourceNotFoundException(
                                                                        "User", "id", id))));
        return locked;
    }

    private ConflictException duplicateHeadmanConflict(Long groupId) {
        return new ConflictException(
                "groupId",
                groupId,
                "В группе уже сохранено несколько активных старост; требуется исправление данных");
    }

    private record CurrentHeadman(Long id, String fio) {}
}
