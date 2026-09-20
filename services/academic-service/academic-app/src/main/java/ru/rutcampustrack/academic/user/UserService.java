package ru.rutcampustrack.academic.user;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import org.springframework.hateoas.EntityModel;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.GroupHistoryCoverage;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;
import ru.rutcampustrack.academic.security.RequestContext;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Business logic for User domain: CRUD, login generation, BCrypt password,
 * headman cascade revoke, student group transfer with history.
 */
@Service
public class UserService {

    private static final String CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final HeadmanAssistantRepository headmanAssistantRepository;
    private final StudentGroupHistoryRepository studentGroupHistoryRepository;
    private final RequestContext requestContext;
    private final UserAssembler userAssembler;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final GroupHistoryCoverageRepository coverageRepository;
    private final UserRoleGrantWriter roleGrantWriter;

    @Autowired
    public UserService(UserRepository userRepository,
                       HeadmanAssistantRepository headmanAssistantRepository,
                       StudentGroupHistoryRepository studentGroupHistoryRepository,
                       RequestContext requestContext,
                       UserAssembler userAssembler,
                       @Nullable CacheManager cacheManager,
                       ApplicationEventPublisher eventPublisher,
                       GroupRepository groupRepository,
                       SemesterRepository semesterRepository,
                       GroupHistoryCoverageRepository coverageRepository,
                       UserRoleGrantWriter roleGrantWriter) {
        this.userRepository = userRepository;
        this.headmanAssistantRepository = headmanAssistantRepository;
        this.studentGroupHistoryRepository = studentGroupHistoryRepository;
        this.requestContext = requestContext;
        this.userAssembler = userAssembler;
        this.cacheManager = cacheManager;
        this.eventPublisher = eventPublisher;
        this.groupRepository = groupRepository;
        this.semesterRepository = semesterRepository;
        this.coverageRepository = coverageRepository;
        this.roleGrantWriter = roleGrantWriter;
    }

    /** Compatibility constructor for source-era unit tests that do not mutate membership. */
    public UserService(UserRepository userRepository,
                       HeadmanAssistantRepository headmanAssistantRepository,
                       StudentGroupHistoryRepository studentGroupHistoryRepository,
                       RequestContext requestContext,
                       UserAssembler userAssembler,
                       @Nullable CacheManager cacheManager,
                       ApplicationEventPublisher eventPublisher) {
        this(userRepository, headmanAssistantRepository, studentGroupHistoryRepository,
                requestContext, userAssembler, cacheManager, eventPublisher,
                null, null, null, null);
    }

    @Transactional
    public EntityModel<UserCreatedResponse> createUser(CreateUserRequest request) {
        // BUG-006-3 / D-08..D-11: STUDENT role requires telegramId (staroste/boto
        // notifications won't work without it). TEACHER/ADMIN keep it optional.
        // Guard runs before any repo access so it's cheap and deterministic.
        validateTelegramForRole(request);

        // Lock and validate the group/semester before creating a STUDENT.  The
        // returned semester is captured once and becomes the authoritative
        // joined_at value for this transaction.
        EnrollmentContext enrollment = request.role() == UserRole.STUDENT && request.groupId() != null
                ? prepareInitialEnrollment(request.groupId())
                : null;

        // Generate login based on role
        String login = generateLogin(request.role());

        // BUG-006-2 / D-07: Pre-check unique fields before save to surface
        // field-specific 409 Conflict responses. The DataIntegrityViolation
        // handler is kept as a race-condition backstop (T-58-02-02).
        if (userRepository.existsByLogin(login)) {
            throw new ConflictException("login", login,
                    "Логин уже используется. Выберите другой");
        }
        if (request.telegramId() != null
                && userRepository.existsByTelegramId(request.telegramId())) {
            throw new ConflictException("telegramId", request.telegramId(),
                    "Telegram ID уже привязан к другой учётной записи");
        }
        if (request.employeeNumber() != null
                && !request.employeeNumber().isBlank()
                && userRepository.existsByEmployeeNumber(request.employeeNumber())) {
            throw new ConflictException("employeeNumber", request.employeeNumber(),
                    "Табельный номер уже используется");
        }

        // Generate random plain-text password
        String plainPassword = generatePassword();

        // Hash password with BCrypt
        String passwordHash = passwordEncoder.encode(plainPassword);

        // Build user entity
        User user = new User();
        user.setLogin(login);
        user.setLastName(request.lastName());
        user.setFirstName(request.firstName());
        user.setMiddleName(request.middleName());
        user.setPasswordHash(passwordHash);
        user.setRole(request.role());
        user.setStatus(AccountStatus.ACTIVE);
        user.setGroupId(request.groupId());
        user.setEmployeeNumber(request.employeeNumber());
        user.setTelegramId(request.telegramId());
        user.setHeadman(false);
        user.setPasswordChanged(false);
        // BUG-006: храним plaintext до первой смены пароля чтобы админ мог
        // переиспользовать его (показать пользователю), пока тот его не сменил.
        user.setInitialPassword(plainPassword);
        OffsetDateTime now = OffsetDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        user = userRepository.save(user);
        userRepository.flush();
        synchronizeRoleGrants(user);
        if (enrollment != null) {
            StudentGroupHistory history = new StudentGroupHistory();
            history.setUserId(user.getId());
            history.setGroupId(enrollment.group().getId());
            history.setJoinedAt(enrollment.semester().getDateFrom());
            history.setCreatedAt(now);
            history.setReason("initial-enrollment");
            studentGroupHistoryRepository.save(history);
        }
        return userAssembler.toCreatedModel(user, plainPassword);
    }

    public User findUserById(Long id) {
        return userRepository.findByIdIncludingArchived(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * Unified list: case-insensitive search по login/ФИО/telegramId (BUG-006-1)
     * + опциональные фильтры role/status через JPA Specification API (D-02).
     * Пустые параметры → без ограничений (backward compatible).
     */
    public Page<User> listUsers(String search,
                                UserRole roleFilter,
                                AccountStatus statusFilter,
                                Pageable pageable) {
        Specification<User> spec = Specification
                .where(UserSpecifications.matchesSearch(search))
                .and(UserSpecifications.matchesRole(roleFilter))
                .and(UserSpecifications.matchesStatus(statusFilter));
        // Default sort by surname when caller did not specify one. JPA Specification
        // can't apply COLLATE, so this is ASCII-order — Ё ends up at the end. Acceptable
        // for the admin-only screen; group/journal lists use ICU via native ORDER BY.
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        org.springframework.data.domain.Sort.by("lastName", "firstName", "middleName"));
        return userRepository.findAll(spec, effective);
    }

    /** Backward-compatible overload for internal callers (e.g. gRPC service). */
    public Page<User> listUsers(UserRole roleFilter, Pageable pageable) {
        return listUsers(null, roleFilter, null, pageable);
    }

    public List<User> listTeachers() {
        return userRepository.findByRole("teacher", Pageable.ofSize(500)).getContent();
    }

    /**
     * Batch-резолв display-имён по списку ID (для STUDENT/TEACHER аудитных мест).
     * Cap на size — 100 ids, защита от abuse через query string. Несуществующие
     * ID молча пропускаются (downstream caller сам решит fallback по отсутствующему).
     *
     * @param ids список ID, 1..100 элементов
     * @return найденные пользователи (порядок не гарантируется — callee должен мапить по id)
     * @throws BadRequestException если ids пуст или > 100
     */
    public List<User> findUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("ids must not be empty");
        }
        if (ids.size() > 100) {
            throw new BadRequestException("ids size must not exceed 100, got " + ids.size());
        }
        return userRepository.findAllById(ids);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public User updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        ensureMembershipMutationSupported(
                user,
                request.role(),
                request.groupId());
        user.setLastName(request.lastName());
        user.setFirstName(request.firstName());
        user.setMiddleName(request.middleName());
        user.setRole(request.role());
        user.setGroupId(request.groupId());
        user.setEmployeeNumber(request.employeeNumber());
        user.setTelegramId(request.telegramId());
        user.setUpdatedAt(OffsetDateTime.now());
        User saved = userRepository.save(user);
        userRepository.flush();
        synchronizeRoleGrants(saved);
        return saved;
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public User patchUser(Long id, PatchUserRequest request) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        Long oldGroupId = user.getGroupId();
        boolean oldHeadman = user.isHeadman();
        ensureMembershipMutationSupported(
                user,
                null,
                request.groupId());

        AccountStatus targetStatus = request.status() != null
                ? request.status()
                : user.getStatus();
        if (request.isHeadman() != null && request.isHeadman()) {
            if (user.getRole() != UserRole.STUDENT) {
                throw new BadRequestException("Только студент может быть назначен старостой");
            }
            if (targetStatus != AccountStatus.ACTIVE) {
                throw new BadRequestException("Только активный студент может быть назначен старостой");
            }
        }
        if (isTerminalMembershipStatus(targetStatus)) {
            closeOpenMembershipOnTerminalStatus(id, LocalDate.now(MOSCOW));
        }

        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.middleName() != null) {
            user.setMiddleName(request.middleName());
        }
        if (request.groupId() != null) {
            user.setGroupId(request.groupId());
        }
        if (request.employeeNumber() != null) {
            user.setEmployeeNumber(request.employeeNumber());
        }
        if (request.telegramId() != null) {
            user.setTelegramId(request.telegramId());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        if (isTerminalMembershipStatus(targetStatus) && user.isHeadman()) {
            headmanAssistantRepository.revokeAllByGroupId(oldGroupId);
            user.setHeadman(false);
        }

        // Headman revoke cascade (D-13)
        if (request.isHeadman() != null && !request.isHeadman() && user.isHeadman()) {
            headmanAssistantRepository.revokeAllByGroupId(user.getGroupId());
            user.setHeadman(false);
        }

        // Headman assign (USER-03). Request validation ran before the
        // terminal-history mutation above, so an unsupported transition cannot
        // leave a closed history row behind.
        if (request.isHeadman() != null && request.isHeadman()) {
            user.setHeadman(true);
        }

        if (isTerminalMembershipStatus(targetStatus)) {
            // The historical row is the source of dated membership. Clear the
            // live assignment as well so the completeness guard does not treat
            // an archived/expelled account as a current student with no open
            // interval. Past rows remain available through their own timeline.
            user.setGroupId(null);
        }

        boolean groupChanged = !java.util.Objects.equals(oldGroupId, user.getGroupId());

        // After headman flag change — evict groups and group_members caches for this user's group (per D-10)
        if ((request.isHeadman() != null || groupChanged) && oldGroupId != null && cacheManager != null) {
            Cache groupsCache = cacheManager.getCache("groups");
            if (groupsCache != null) {
                groupsCache.evict(oldGroupId);
            }
            Cache groupMembersCache = cacheManager.getCache("group_members");
            if (groupMembersCache != null) {
                groupMembersCache.evict(oldGroupId);
            }
        }

        // M05 audit fix (bug-hunter 1.1): rbac evict должен идти AFTER COMMIT,
        // иначе concurrent isHeadmanOf читает pre-commit snapshot и кешит
        // старое значение — ex-headman сохраняет privileges до истечения TTL.
        boolean headmanChanged = oldHeadman != user.isHeadman();
        if (headmanChanged || groupChanged) {
            Long newGroupId = user.getGroupId();
            evictRbacAfterCommit(id, oldGroupId, newGroupId);
        }

        user.setUpdatedAt(OffsetDateTime.now());
        User saved = userRepository.save(user);
        userRepository.flush();
        if (saved.getStatus() == AccountStatus.ARCHIVED) {
            archiveRoleGrants(saved);
        } else {
            synchronizeRoleGrants(saved);
        }
        return saved;
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public void archiveUser(Long id) {
        User user = userRepository.findByIdIncludingArchivedForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        Long oldGroupId = user.getGroupId();
        boolean wasHeadman = user.isHeadman();
        closeOpenMembershipOnTerminalStatus(id, LocalDate.now(MOSCOW));
        if (wasHeadman) {
            headmanAssistantRepository.revokeAllByGroupId(oldGroupId);
            user.setHeadman(false);
        }
        user.setStatus(AccountStatus.ARCHIVED);
        user.setGroupId(null);
        user.setUpdatedAt(OffsetDateTime.now());
        User saved = userRepository.save(user);
        userRepository.flush();
        archiveRoleGrants(saved);
        if (wasHeadman) {
            evictRbacAfterCommit(id, oldGroupId, null);
        }
    }

    @CacheEvict(value = "group_members", key = "#request.newGroupId()")
    @Transactional
    public User transferStudent(Long id, TransferStudentRequest request) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (user.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Перевод возможен только для студентов");
        }

        Long oldGroupId = user.getGroupId();
        Long newGroupId = request.newGroupId();
        if (newGroupId == null || newGroupId <= 0 || oldGroupId == null) {
            throw HistoricalMembershipException.precondition(
                    "Перевод требует текущую и новую управляемые группы");
        }
        if (oldGroupId.equals(newGroupId)) {
            throw HistoricalMembershipException.invalid("Новая группа должна отличаться от текущей");
        }
        if (groupRepository == null || coverageRepository == null
                || studentGroupHistoryRepository == null) {
            throw HistoricalMembershipException.precondition(
                    "Historical membership writer is unavailable");
        }

        // All transfers lock the affected group rows in ascending order after
        // the user row is locked.  Re-reading the user under the lock makes the
        // source group authoritative even if the initial request raced.
        List<Long> groupIds = new ArrayList<>(List.of(oldGroupId, newGroupId));
        groupIds.sort(Comparator.naturalOrder());
        for (Long groupId : groupIds) {
            groupRepository.findByIdForUpdate(groupId)
                    .orElseThrow(() -> HistoricalMembershipException.notFound(
                            "Group " + groupId + " not found"));
        }
        Group oldGroup = groupRepository.findById(oldGroupId)
                .orElseThrow(() -> HistoricalMembershipException.notFound("Source group not found"));
        Group newGroup = groupRepository.findById(newGroupId)
                .orElseThrow(() -> HistoricalMembershipException.notFound("Destination group not found"));
        LocalDate transferDate = LocalDate.now(MOSCOW);
        requireCoveredGroup(oldGroup, transferDate);
        requireCoveredGroup(newGroup, transferDate);

        List<StudentGroupHistory> openHistories = studentGroupHistoryRepository
                .findOpenByUserIdForUpdate(id);
        if (openHistories.size() != 1 || !oldGroupId.equals(openHistories.get(0).getGroupId())) {
            throw HistoricalMembershipException.precondition(
                    "Student must have exactly one open source membership history");
        }
        StudentGroupHistory oldHistory = openHistories.get(0);
        if (oldHistory.getJoinedAt() == null || oldHistory.getJoinedAt().isAfter(transferDate)) {
            throw HistoricalMembershipException.precondition("Source membership history is invalid");
        }

        // Close current group history entry
        oldHistory.setLeftAt(transferDate);
        oldHistory.setReason(request.reason());
        studentGroupHistoryRepository.save(oldHistory);

        // Create new group history entry
        StudentGroupHistory newHistory = new StudentGroupHistory();
        newHistory.setUserId(id);
        newHistory.setGroupId(newGroupId);
        newHistory.setJoinedAt(transferDate);
        newHistory.setReason(request.reason());
        newHistory.setCreatedAt(OffsetDateTime.now());
        studentGroupHistoryRepository.save(newHistory);

        // Update user's current group
        user.setGroupId(newGroupId);

        // If user was headman in old group — cascade revoke (same as D-13)
        if (user.isHeadman() && oldGroupId != null) {
            headmanAssistantRepository.revokeAllByGroupId(oldGroupId);
            user.setHeadman(false);
        }

        user.setUpdatedAt(OffsetDateTime.now());

        // Evict caches for old group (ID only known at runtime) and transferred user
        if (cacheManager != null) {
            Cache groupMembersCache = cacheManager.getCache("group_members");
            if (groupMembersCache != null && oldGroupId != null) {
                groupMembersCache.evict(oldGroupId);
            }
            Cache usersCache = cacheManager.getCache("users");
            if (usersCache != null) {
                usersCache.evict(id);
            }
        }
        // M05 audit fix (bug-hunter 1.1): rbac evict переносится в afterCommit —
        // concurrent isHeadmanOf иначе закеширует стейл-значение из pre-commit
        // snapshot'а и дальнейшие RBAC-проверки пройдут по старой группе.
        evictRbacAfterCommit(id, oldGroupId, newGroupId);

        User saved = userRepository.save(user);
        userRepository.flush();
        synchronizeRoleGrants(saved);
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, oldGroupId));
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, newGroupId));
        return saved;
    }

    public User getMe() {
        Long userId = requestContext.getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Transactional
    public User updateMyAvatar(String avatarId) {
        Long userId = requestContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        // Empty/blank string == clear (render initials).
        user.setAvatarId(avatarId == null || avatarId.isBlank() ? null : avatarId);
        user.setUpdatedAt(OffsetDateTime.now());
        User saved = userRepository.save(user);
        if (cacheManager != null) {
            Cache usersCache = cacheManager.getCache("users");
            if (usersCache != null) {
                usersCache.evict(userId);
            }
        }
        return saved;
    }

    // --- Private helpers ---

    private EnrollmentContext prepareInitialEnrollment(Long groupId) {
        if (groupRepository == null || semesterRepository == null || coverageRepository == null) {
            throw HistoricalMembershipException.precondition(
                    "Historical membership writer is unavailable");
        }
        if (groupId == null || groupId <= 0) {
            throw HistoricalMembershipException.invalid("group_id must be positive");
        }
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        Semester semester = resolveActiveSemester();
        GroupHistoryCoverage coverage = coverageRepository.findById(groupId)
                .orElseThrow(() -> HistoricalMembershipException.precondition(
                        "Student enrollment requires managed group history coverage"));
        requireCoveredGroup(group, semester.getDateFrom());
        if (coverage.getCoverageFrom().isAfter(semester.getDateFrom())) {
            throw HistoricalMembershipException.precondition(
                    "Group history coverage does not support the active semester");
        }
        return new EnrollmentContext(group, semester, coverage);
    }

    private Semester resolveActiveSemester() {
        final List<Semester> active;
        try {
            active = semesterRepository.findAllByIsActiveTrueOrderByIdAsc();
        } catch (RuntimeException error) {
            throw HistoricalMembershipException.precondition("Active semester could not be resolved");
        }
        if (active == null || active.size() != 1) {
            throw HistoricalMembershipException.precondition(
                    "Exactly one active semester is required");
        }
        Semester semester = active.get(0);
        if (semester.getDateFrom() == null || semester.getDateTo() == null
                || semester.getDateFrom().isAfter(semester.getDateTo())) {
            throw HistoricalMembershipException.precondition("Active semester has invalid dates");
        }
        return semester;
    }

    private void requireCoveredGroup(Group group, LocalDate effectiveDate) {
        if (group == null || group.getId() == null || effectiveDate == null || !group.isActive()) {
            throw HistoricalMembershipException.precondition("Membership requires an active group");
        }
        if (coverageRepository == null) {
            throw HistoricalMembershipException.precondition(
                    "Historical membership writer is unavailable");
        }
        GroupHistoryCoverage coverage = coverageRepository.findById(group.getId())
                .orElseThrow(() -> HistoricalMembershipException.precondition(
                        "Group history coverage is not established"));
        if (!HistoricalMembershipService.WRITER_VERSION.equals(coverage.getWriterVersion())
                || coverage.getCoverageFrom() == null
                || coverage.getCoverageFrom().isAfter(effectiveDate)) {
            throw HistoricalMembershipException.precondition(
                    "Group history coverage does not support this date");
        }
    }

    private void ensureMembershipMutationSupported(User user,
                                                   UserRole requestedRole,
                                                   Long requestedGroupId) {
        if (requestedRole != null && requestedRole != user.getRole()) {
            throw HistoricalMembershipException.unsupported(
                    "Изменение роли пользователя требует управляемой истории членства");
        }
        if (requestedGroupId != null
                ? !java.util.Objects.equals(requestedGroupId, user.getGroupId())
                : requestedRole != null && user.getGroupId() != null) {
            if (requestedGroupId != null || requestedRole != null) {
                throw HistoricalMembershipException.unsupported(
                        "Изменение группы пользователя выполняется только через перевод");
            }
        }
    }

    private static boolean isTerminalMembershipStatus(AccountStatus status) {
        return status == AccountStatus.EXPELLED || status == AccountStatus.ARCHIVED;
    }

    /**
     * Closes the one managed open interval at the terminal transition date.
     * The half-open interval remains valid for same-day and future-dated
     * legacy rows by using max(joined_at, transitionDate); an uncovered user
     * receives no synthetic history row.
     */
    private void closeOpenMembershipOnTerminalStatus(Long userId, LocalDate transitionDate) {
        if (userId == null || userId <= 0 || transitionDate == null) {
            throw HistoricalMembershipException.invalid("Terminal membership transition is invalid");
        }
        if (studentGroupHistoryRepository == null) {
            throw HistoricalMembershipException.precondition(
                    "Historical membership writer is unavailable");
        }
        List<StudentGroupHistory> openHistories = studentGroupHistoryRepository
                .findOpenByUserIdForUpdate(userId);
        if (openHistories == null) {
            throw HistoricalMembershipException.precondition(
                    "Open membership lookup returned no coherent result");
        }
        if (openHistories.size() > 1) {
            throw HistoricalMembershipException.precondition(
                    "Student has multiple open membership intervals");
        }
        if (openHistories.isEmpty()) {
            return;
        }

        StudentGroupHistory openHistory = openHistories.get(0);
        if (openHistory == null || openHistory.getJoinedAt() == null) {
            throw HistoricalMembershipException.precondition(
                    "Open membership interval is invalid");
        }
        LocalDate leftAt = transitionDate.isBefore(openHistory.getJoinedAt())
                ? openHistory.getJoinedAt()
                : transitionDate;
        openHistory.setLeftAt(leftAt);
        studentGroupHistoryRepository.save(openHistory);
    }

    private void synchronizeRoleGrants(User user) {
        // The compatibility constructor is retained for source-era unit tests;
        // the Spring application constructor always supplies the V24 writer.
        if (roleGrantWriter != null) {
            roleGrantWriter.synchronize(user);
        }
    }

    private void archiveRoleGrants(User user) {
        // Archive keeps durable grant rows for session/audit foreign keys while
        // removing every selectable privilege from this account.
        if (roleGrantWriter != null) {
            roleGrantWriter.archive(user);
        }
    }

    private record EnrollmentContext(Group group, Semester semester, GroupHistoryCoverage coverage) {
    }

    /**
     * BUG-006-3 / D-08..D-11: enforces that {@code telegramId} is present for
     * {@link UserRole#STUDENT} accounts. Teacher/admin accounts keep the field
     * optional. A telegramId of {@code 0} is normalised to "missing" — some
     * clients submit zero instead of null.
     */
    private void validateTelegramForRole(CreateUserRequest request) {
        if (request.role() == UserRole.STUDENT
                && (request.telegramId() == null || request.telegramId() == 0L)) {
            throw new BadRequestException("telegramId",
                    "Telegram ID обязателен для студента");
        }
    }

    private String generateLogin(UserRole role) {
        if (role == UserRole.STUDENT) {
            long seq = userRepository.nextStudentLoginSeq();
            return "student" + seq;
        } else if (role == UserRole.TEACHER) {
            long seq = userRepository.nextTeacherLoginSeq();
            return "teacher" + seq;
        } else {
            // ADMIN — use teacher sequence with "admin" prefix
            long seq = userRepository.nextTeacherLoginSeq();
            return "admin" + seq;
        }
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(CHARSET.charAt(secureRandom.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    /**
     * M05 audit fix (bug-hunter 1.1/1.2): evict rbac-cache ключи
     * {@code <userId>:<groupId>} только ПОСЛЕ commit'а транзакции.
     *
     * <p>Если evict'ить внутри @Transactional — concurrent isHeadmanOf читает
     * pre-commit snapshot из БД и перезаписывает cache старым значением до
     * истечения TTL (60s), что позволяет ex-headman'у сохранять privileges.
     *
     * <p>Fallback вне активной транзакции — немедленный evict (сценарий
     * unit-тестов и прямых вызовов из non-@Transactional-context).
     */
    private void evictRbacAfterCommit(Long userId, Long oldGroupId, Long newGroupId) {
        if (cacheManager == null) {
            return;
        }
        Runnable evict = () -> {
            Cache rbac = cacheManager.getCache("rbac");
            if (rbac == null) {
                return;
            }
            if (oldGroupId != null) {
                rbac.evict(userId + ":" + oldGroupId);
            }
            if (newGroupId != null && !java.util.Objects.equals(oldGroupId, newGroupId)) {
                rbac.evict(userId + ":" + newGroupId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
