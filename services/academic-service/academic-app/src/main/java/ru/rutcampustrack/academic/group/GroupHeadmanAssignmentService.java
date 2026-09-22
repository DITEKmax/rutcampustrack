package ru.rutcampustrack.academic.group;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentPreviewResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanCandidateResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanRosterResponse;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical group-scoped headman mutation.
 *
 * <p>The lock order is deliberately user rows in ascending id order followed
 * by the group row. Assistant mutations use the group row as their first lock
 * and then re-read durable authority, so this service never acquires a user
 * lock after the group lock.</p>
 */
@Service
public class GroupHeadmanAssignmentService {

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
        this.assistantRepository = Objects.requireNonNull(assistantRepository, "assistantRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public HeadmanRosterResponse roster(Long groupId) {
        Group group = requireActiveGroup(groupId);
        CurrentHeadman current = readCurrentHeadman(group.getId());
        List<HeadmanCandidateResponse> candidates = userRepository
                .findActiveStudentsByGrantGroupId(group.getId())
                .stream()
                .map(user -> new HeadmanCandidateResponse(
                        user.getId(), user.getDisplayName(), Objects.equals(user.getId(), current.id())))
                .toList();
        int activeAssistants = assistantRepository.findByGroupIdAndIsActiveTrue(group.getId()).size();
        return new HeadmanRosterResponse(
                group.getId(), current.id(), current.fio(), candidates, activeAssistants);
    }

    @Transactional(readOnly = true)
    public HeadmanAssignmentPreviewResponse preview(Long groupId, Long studentId) {
        Group group = requireActiveGroup(groupId);
        User candidate = requireCandidate(group.getId(), studentId);
        CurrentHeadman current = readCurrentHeadman(group.getId());
        boolean same = Objects.equals(current.id(), candidate.getId());
        int assistants = same
                ? 0
                : assistantRepository.findByGroupIdAndIsActiveTrue(group.getId()).size();
        return new HeadmanAssignmentPreviewResponse(
                group.getId(), current.id(), current.fio(), candidate.getId(),
                candidate.getDisplayName(), same, current.id() == null, assistants);
    }

    @Transactional
    public HeadmanAssignmentResponse assign(Long groupId, AssignHeadmanRequest request) {
        if (request == null) throw new BadRequestException("Запрос назначения старосты обязателен");
        return mutate(groupId, request.studentId(), request.expectedHeadmanId(), true);
    }

    /**
     * Compatibility bridge for the existing ADMIN users PATCH. It intentionally
     * has no caller-supplied CAS value, but still uses the canonical lock order
     * and all locked rechecks. The public group endpoint always uses CAS.
     */
    @Transactional
    public HeadmanAssignmentResponse assignLegacyHeadman(Long studentId) {
        Long groupId = activeStudentGroup(studentId);
        return mutate(groupId, studentId, null, false);
    }

    /** Compatibility bridge for the existing ADMIN users PATCH isHeadman=false. */
    @Transactional
    public HeadmanAssignmentResponse revokeLegacyHeadman(Long studentId) {
        if (studentId == null || studentId <= 0) {
            throw new BadRequestException("studentId", "ID студента должен быть положительным");
        }
        User candidate = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", studentId));
        Long groupId = activeStudentGroupOrLegacyGroup(candidate);
        if (groupId == null) {
            return new HeadmanAssignmentResponse(null, studentId,
                    candidate.getDisplayName(), false, false, 0);
        }

        // A legacy revoke has one known user. It still takes the group lock
        // after that user lock, matching the canonical order.
        Map<Long, User> locked = lockUsers(List.of(studentId));
        Group group = lockActiveGroup(groupId);
        List<Long> currentIds = readCurrentHeadmanIds(group.getId());
        if (currentIds.size() > 1) throw duplicateHeadmanConflict(group.getId());
        if (!Objects.equals(currentIds.isEmpty() ? null : currentIds.get(0), studentId)) {
            String currentFio = currentIds.isEmpty()
                    ? null
                    : userRepository.findById(currentIds.get(0))
                            .map(User::getDisplayName)
                            .orElse(null);
            return new HeadmanAssignmentResponse(
                    group.getId(), currentIds.isEmpty() ? null : currentIds.get(0),
                    currentFio,
                    false, false, 0);
        }
        User current = locked.get(studentId);
        current.setHeadman(false);
        current.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(current);
        userRepository.flush();
        roleGrantWriter.synchronizeDerivedHeadman(studentId, false, group.getId());
        int revoked = assistantRepository.revokeAllByGroupId(group.getId());
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, group.getId()));
        evictAfterCommit(group.getId(), List.of(studentId));
        return new HeadmanAssignmentResponse(
                group.getId(), null, null, true, false, revoked);
    }

    private HeadmanAssignmentResponse mutate(
            Long groupId,
            Long studentId,
            Long expectedHeadmanId,
            boolean enforceCas) {
        Group initialGroup = requireActiveGroup(groupId);
        User candidate = requireCandidate(initialGroup.getId(), studentId);
        CurrentHeadman initialCurrent = readCurrentHeadman(initialGroup.getId());

        List<Long> idsToLock = new ArrayList<>();
        idsToLock.add(candidate.getId());
        if (initialCurrent.id() != null) idsToLock.add(initialCurrent.id());
        Map<Long, User> lockedUsers = lockUsers(idsToLock);
        Group group = lockActiveGroup(initialGroup.getId());

        List<Long> currentIds = readCurrentHeadmanIds(group.getId());
        if (currentIds.size() > 1) throw duplicateHeadmanConflict(group.getId());
        Long currentId = currentIds.isEmpty() ? null : currentIds.get(0);
        if (currentId != null && !lockedUsers.containsKey(currentId)) {
            throw new ConflictException("expectedHeadmanId", expectedHeadmanId,
                    "Состояние старосты изменилось, обнови реестр");
        }

        User lockedCandidate = lockedUsers.get(studentId);
        validateCandidateAfterLocks(lockedCandidate, studentId, group.getId());
        if (enforceCas && !Objects.equals(expectedHeadmanId, currentId)) {
            throw new ConflictException("expectedHeadmanId", expectedHeadmanId,
                    "Состояние старосты изменилось, обнови реестр");
        }

        if (Objects.equals(currentId, studentId)) {
            return new HeadmanAssignmentResponse(
                    group.getId(), studentId, lockedCandidate.getDisplayName(),
                    false, false, 0);
        }

        User previous = currentId == null ? null : lockedUsers.get(currentId);
        if (previous != null) {
            previous.setHeadman(false);
            previous.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(previous);
        }
        lockedCandidate.setHeadman(true);
        lockedCandidate.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(lockedCandidate);
        userRepository.flush();
        if (previous != null) {
            roleGrantWriter.synchronizeDerivedHeadman(previous.getId(), false, group.getId());
        }
        roleGrantWriter.synchronizeDerivedHeadman(lockedCandidate.getId(), true, group.getId());
        int revoked = assistantRepository.revokeAllByGroupId(group.getId());
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, group.getId()));
        List<Long> affectedUsers = previous == null
                ? List.of(lockedCandidate.getId())
                : List.of(previous.getId(), lockedCandidate.getId());
        evictAfterCommit(group.getId(), affectedUsers);
        return new HeadmanAssignmentResponse(
                group.getId(), lockedCandidate.getId(), lockedCandidate.getDisplayName(),
                true, currentId == null, revoked);
    }

    private Group requireActiveGroup(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BadRequestException("groupId", "ID группы должен быть положительным");
        }
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        if (!group.isActive()) {
            throw new ConflictException("groupId", groupId,
                    "Архивная группа не принимает нового старосту");
        }
        return group;
    }

    private Group lockActiveGroup(Long groupId) {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        if (!group.isActive()) {
            throw new ConflictException("groupId", groupId,
                    "Архивная группа не принимает нового старосту");
        }
        return group;
    }

    private User requireCandidate(Long groupId, Long studentId) {
        if (studentId == null || studentId <= 0) {
            throw new BadRequestException("studentId", "ID студента должен быть положительным");
        }
        User candidate = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", studentId));
        validateCandidateAfterLocks(candidate, studentId, groupId);
        return candidate;
    }

    private void validateCandidateAfterLocks(User candidate, Long studentId, Long groupId) {
        if (candidate == null) {
            throw new ResourceNotFoundException("User", "id", studentId);
        }
        Long membershipGroup = userRepository.findActiveStudentGrantGroupId(candidate.getId()).orElse(null);
        if (!Objects.equals(membershipGroup, groupId)) {
            throw new ConflictException("studentId", candidate.getId(),
                    "Старостой может быть только студент с активной ролью STUDENT в выбранной группе");
        }
    }

    private Long activeStudentGroup(Long studentId) {
        if (studentId == null || studentId <= 0) {
            throw new BadRequestException("studentId", "ID студента должен быть положительным");
        }
        return userRepository.findActiveStudentGrantGroupId(studentId)
                .orElseThrow(() -> new ConflictException("studentId", studentId,
                        "Активная группа студента не найдена"));
    }

    private Long activeStudentGroupOrLegacyGroup(User candidate) {
        return userRepository.findActiveStudentGrantGroupId(candidate.getId())
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
        ids.stream().filter(Objects::nonNull).distinct().sorted().forEach(id ->
                locked.put(id, userRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", id))));
        return locked;
    }

    private ConflictException duplicateHeadmanConflict(Long groupId) {
        return new ConflictException("groupId", groupId,
                "В группе уже сохранено несколько активных старост; требуется исправление данных");
    }

    private void evictAfterCommit(Long groupId, List<Long> userIds) {
        if (cacheManager == null) return;
        List<Long> affected = userIds.stream().filter(Objects::nonNull).distinct().toList();
        Runnable evict = () -> {
            evict(cacheManager.getCache("groups"), groupId);
            evict(cacheManager.getCache("group_members"), groupId);
            Cache users = cacheManager.getCache("users");
            Cache rbac = cacheManager.getCache("rbac");
            for (Long userId : affected) {
                evict(users, userId);
                evict(rbac, userId + ":" + groupId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }

    private static void evict(Cache cache, Object key) {
        if (cache != null) cache.evict(key);
    }

    private record CurrentHeadman(Long id, String fio) {}
}
