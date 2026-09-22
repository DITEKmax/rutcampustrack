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
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import org.springframework.hateoas.EntityModel;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.GroupHistoryCoverage;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService;
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
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.academic.security.RequestContext;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final UserRoleGrantReader roleGrantReader;
    private final GroupHeadmanAssignmentService headmanAssignmentService;

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
                       UserRoleGrantWriter roleGrantWriter,
                       UserRoleGrantReader roleGrantReader,
                       GroupHeadmanAssignmentService headmanAssignmentService) {
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
        this.roleGrantReader = roleGrantReader;
        this.headmanAssignmentService = headmanAssignmentService;
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
                null, null, null, null, null, null);
    }

    @Transactional
    public EntityModel<UserCreatedResponse> createUser(CreateUserRequest request) {
        validateCreateRoleData(request);

        // Lock and validate the group/semester before creating a STUDENT.  The
        // returned semester is captured once and becomes the authoritative
        // joined_at value for this transaction. Role-specific validation above
        // guarantees that a STUDENT always has a real group here.
        EnrollmentContext enrollment = request.role() == UserRole.STUDENT
                ? prepareInitialEnrollment(request.groupId())
                : null;

        // One account keeps one login even when later role grants are added.
        String login = generateLogin(request);

        // BUG-006-2 / D-07: Pre-check unique fields before save to surface
        // field-specific 409 Conflict responses. The DataIntegrityViolation
        // handler is kept as a race-condition backstop (T-58-02-02).
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
            evictGroupMembersAfterCommit(enrollment.group().getId());
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
                                String roleFilter,
                                AccountStatus statusFilter,
                                String roleStatusFilter,
                                Pageable pageable) {
        validateRoleFilter(roleFilter, roleStatusFilter);
        Specification<User> spec = Specification
                .where(UserSpecifications.matchesSearch(search))
                .and(UserSpecifications.matchesGrant(roleFilter, roleStatusFilter))
                .and(roleStatusFilter == null ? UserSpecifications.matchesStatus(statusFilter) : null);
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        org.springframework.data.domain.Sort.by("lastName", "firstName", "middleName"));
        return userRepository.findAll(spec, effective);
    }

    /** Legacy overload retained for source-era callers and tests. */
    public Page<User> listUsers(String search,
                                UserRole roleFilter,
                                AccountStatus statusFilter,
                                Pageable pageable) {
        return listUsers(search, roleFilter == null ? null : roleFilter.name(),
                statusFilter, null, pageable);
    }

    /** Backward-compatible overload for internal callers (e.g. gRPC service). */
    public Page<User> listUsers(UserRole roleFilter, Pageable pageable) {
        return listUsers(null, roleFilter, null, pageable);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public User updateRoleGrant(Long id, String roleName, RoleGrantUpdateRequest request) {
        if (roleGrantReader == null || roleGrantWriter == null) {
            throw HistoricalMembershipException.precondition("Role grant writer is unavailable");
        }
        UserRole role = parseManagedRole(roleName);
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        var current = roleGrantReader.findByUserId(id).stream()
                .filter(grant -> role.name().equals(grant.role()))
                .findFirst();
        validateRoleGrantData(role, request, current.isPresent());
        RoleGrantStatus status = request.status();
        validateRoleStatus(role, status);
        String previousStatus = current.map(grant -> grant.status().toUpperCase(java.util.Locale.ROOT))
                .orElse(null);
        if ("ARCHIVED".equals(previousStatus)) {
            throw new BadRequestException("Архивную роль нельзя изменить через этот endpoint");
        }

        if (role == UserRole.ADMIN && current.isPresent() && !"ACTIVE".equals(current.get().status())) {
            throw new BadRequestException("Неактивную роль ADMIN нельзя реактивировать в этом пакете");
        }

        Long grantGroupId = current.map(grant -> grant.groupId()).orElse(null);
        if (role == UserRole.STUDENT && grantGroupId == null && current.isPresent()
                && user.getRole() == UserRole.STUDENT) {
            grantGroupId = user.getGroupId();
        }
        boolean createdStudentHistory = false;
        if (role == UserRole.STUDENT) {
            if (current.isPresent()) {
                if (request.groupId() != null && !java.util.Objects.equals(request.groupId(), grantGroupId)) {
                    throw HistoricalMembershipException.unsupported(
                        "Изменение группы выполняется только через перевод с причиной");
                }
                if (status == RoleGrantStatus.ACTIVE) {
                    if (grantGroupId == null) {
                        throw new BadRequestException("Активная STUDENT роль требует группу; используй перевод с историей");
                    }
                    Long telegramId = request.telegramId() != null ? request.telegramId() : user.getTelegramId();
                    applyStudentTelegram(user, telegramId);
                } else if (request.telegramId() != null) {
                    applyStudentTelegram(user, request.telegramId());
                }
            } else {
                if (request.groupId() == null || request.telegramId() == null) {
                    throw new BadRequestException("Для добавления STUDENT нужны группа и Telegram ID");
                }
                EnrollmentContext enrollment = prepareInitialEnrollment(request.groupId());
                grantGroupId = enrollment.group().getId();
                applyStudentTelegram(user, request.telegramId());
                StudentGroupHistory history = new StudentGroupHistory();
                history.setUserId(user.getId());
                history.setGroupId(grantGroupId);
                history.setJoinedAt(enrollment.semester().getDateFrom());
                history.setCreatedAt(OffsetDateTime.now());
                history.setReason("initial-enrollment");
                studentGroupHistoryRepository.save(history);
                createdStudentHistory = true;
            }
        } else if (role == UserRole.TEACHER) {
            if (current.isEmpty()) {
                if (request.employeeNumber() == null || request.employeeNumber().isBlank()) {
                    throw new BadRequestException("Для добавления TEACHER нужен табельный номер");
                }
                ensureEmployeeAvailable(user, request.employeeNumber());
                if (user.getEmployeeNumber() == null) user.setEmployeeNumber(request.employeeNumber());
            } else if (request.employeeNumber() != null
                    && !request.employeeNumber().isBlank()
                    && !request.employeeNumber().equals(user.getEmployeeNumber())) {
                ensureEmployeeAvailable(user, request.employeeNumber());
                user.setEmployeeNumber(request.employeeNumber());
            }
            if (status == RoleGrantStatus.ACTIVE
                    && (user.getEmployeeNumber() == null || user.getEmployeeNumber().isBlank())) {
                throw new BadRequestException("Активная TEACHER роль требует табельный номер");
            }
        } else {
            if (request.groupId() != null || request.employeeNumber() != null || request.telegramId() != null) {
                throw new BadRequestException("Для ADMIN role-data не поддерживается этим пакетом");
            }
        }

        if (role == UserRole.STUDENT && status != RoleGrantStatus.ACTIVE && user.isHeadman()) {
            if (user.getGroupId() != null) {
                headmanAssistantRepository.revokeAllByGroupId(user.getGroupId());
            }
            user.setHeadman(false);
        }
        // Keep the legacy scalar aligned when this is the user's base role.
        // GRADUATED/DISMISSED have no legacy AccountStatus value; ACTIVE is a
        // deliberate neutral marker so synchronize() preserves the durable
        // role-specific status instead of reactivating or rewriting it.
        if (role == user.getRole()) {
            switch (status) {
                case ACTIVE -> user.setStatus(AccountStatus.ACTIVE);
                case EXPELLED -> user.setStatus(AccountStatus.EXPELLED);
                case SUSPENDED -> user.setStatus(AccountStatus.SUSPENDED);
                case GRADUATED, DISMISSED -> user.setStatus(AccountStatus.ACTIVE);
                case ARCHIVED -> throw new BadRequestException("ARCHIVED не применим к управляемой роли");
            }
        }

        if (role == UserRole.STUDENT) {
            if (createdStudentHistory && isTerminalStudentGrantStatus(status)) {
                closeOpenMembershipOnTerminalStatus(id, LocalDate.now(MOSCOW));
            } else if (current.isPresent()
                    && isTerminalStudentGrantStatus(status)
                    && !isTerminalStudentGrantStatus(previousStatus)) {
                closeOpenMembershipOnTerminalStatus(id, LocalDate.now(MOSCOW));
            } else if (current.isPresent()
                    && status == RoleGrantStatus.ACTIVE
                    && isTerminalStudentGrantStatus(previousStatus)) {
                openMembershipOnStudentReactivation(id, grantGroupId, LocalDate.now(MOSCOW));
            }
        }
        user.setUpdatedAt(OffsetDateTime.now());
        User saved = userRepository.save(user);
        userRepository.flush();
        roleGrantWriter.upsertRole(
                id,
                role.name().toLowerCase(java.util.Locale.ROOT),
                status.name().toLowerCase(java.util.Locale.ROOT),
                grantGroupId);
        if (role == UserRole.STUDENT) {
            roleGrantWriter.synchronizeDerivedHeadman(
                    id,
                    status == RoleGrantStatus.ACTIVE && saved.isHeadman(),
                    grantGroupId);
            evictGroupMembersAfterCommit(grantGroupId);
        }
        return saved;
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

    private void validateRoleFilter(String roleFilter, String roleStatusFilter) {
        if (roleStatusFilter != null && !roleStatusFilter.isBlank()
                && (roleFilter == null || roleFilter.isBlank())) {
            throw new BadRequestException("Фильтр статуса роли требует фильтр роли");
        }
        if (roleFilter == null || roleFilter.isBlank()) return;
        String normalized = roleFilter.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("STUDENT", "TEACHER", "ADMIN", "HEADMAN").contains(normalized)) {
            throw new BadRequestException("Недопустимая роль: " + roleFilter);
        }
        if (roleStatusFilter != null && !roleStatusFilter.isBlank()) {
            RoleGrantStatus status;
            try {
                status = RoleGrantStatus.valueOf(roleStatusFilter.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Недопустимый статус роли: " + roleStatusFilter);
            }
            if (!isApplicableRoleStatus(normalized, status)) {
                throw new BadRequestException("Статус " + status + " не применим к роли " + normalized);
            }
        }
    }

    private static UserRole parseManagedRole(String roleName) {
        if (roleName == null) throw new BadRequestException("Роль обязательна");
        try {
            UserRole role = UserRole.valueOf(roleName.trim().toUpperCase(java.util.Locale.ROOT));
            if (role == UserRole.ADMIN || role == UserRole.STUDENT || role == UserRole.TEACHER) return role;
        } catch (IllegalArgumentException ignored) {
            // Use the same domain error for unknown and derived roles.
        }
        throw new BadRequestException("Роль нельзя изменить через этот endpoint: " + roleName);
    }

    private static void validateRoleStatus(UserRole role, RoleGrantStatus status) {
        if (status == null) throw new BadRequestException("Статус роли обязателен");
        boolean valid = isApplicableRoleStatus(role.name(), status)
                && status != RoleGrantStatus.ARCHIVED;
        if (!valid) throw new BadRequestException("Статус не применим к роли " + role);
    }

    private static boolean isApplicableRoleStatus(String role, RoleGrantStatus status) {
        return switch (role) {
            case "STUDENT" -> status == RoleGrantStatus.ACTIVE
                    || status == RoleGrantStatus.EXPELLED
                    || status == RoleGrantStatus.GRADUATED
                    || status == RoleGrantStatus.SUSPENDED
                    || status == RoleGrantStatus.ARCHIVED;
            case "TEACHER" -> status == RoleGrantStatus.ACTIVE
                    || status == RoleGrantStatus.DISMISSED
                    || status == RoleGrantStatus.SUSPENDED
                    || status == RoleGrantStatus.ARCHIVED;
            case "ADMIN" -> status == RoleGrantStatus.ACTIVE
                    || status == RoleGrantStatus.ARCHIVED;
            case "HEADMAN" -> status == RoleGrantStatus.ACTIVE
                    || status == RoleGrantStatus.SUSPENDED
                    || status == RoleGrantStatus.ARCHIVED;
            default -> false;
        };
    }

    private static void validateRoleGrantData(UserRole role,
                                              RoleGrantUpdateRequest request,
                                              boolean existingGrant) {
        if (request == null) throw new BadRequestException("Данные роли обязательны");
        if (request.groupId() != null && request.groupId() <= 0) {
            throw new BadRequestException("groupId", "ID группы должен быть положительным");
        }
        if (request.telegramId() != null && request.telegramId() <= 0) {
            throw new BadRequestException("telegramId", "Telegram ID должен быть положительным");
        }
        boolean hasEmployeeNumber = request.employeeNumber() != null
                && !request.employeeNumber().isBlank();
        switch (role) {
            case STUDENT -> {
                if (hasEmployeeNumber) {
                    throw new BadRequestException("employeeNumber",
                            "Табельный номер не применим к роли STUDENT");
                }
                if (!existingGrant && (request.groupId() == null || request.telegramId() == null)) {
                    throw new BadRequestException(
                            "Для добавления STUDENT нужны группа и Telegram ID");
                }
            }
            case TEACHER -> {
                if (request.groupId() != null) {
                    throw new BadRequestException("groupId", "Группа не применима к роли TEACHER");
                }
                if (request.telegramId() != null) {
                    throw new BadRequestException("telegramId", "Telegram ID не изменяется через роль TEACHER");
                }
                if (!existingGrant && !hasEmployeeNumber) {
                    throw new BadRequestException("employeeNumber", "Для добавления TEACHER нужен табельный номер");
                }
            }
            case ADMIN -> {
                if (request.groupId() != null) {
                    throw new BadRequestException("groupId", "Группа не применима к роли ADMIN");
                }
                if (hasEmployeeNumber) {
                    throw new BadRequestException("employeeNumber", "Табельный номер не применим к роли ADMIN");
                }
                if (request.telegramId() != null) {
                    throw new BadRequestException("telegramId", "Telegram ID не изменяется через роль ADMIN");
                }
            }
        }
    }

    private void ensureTelegramAvailable(User current, Long telegramId) {
        if (telegramId == null || telegramId <= 0) {
            throw new BadRequestException("Telegram ID должен быть положительным");
        }
        userRepository.findByTelegramId(telegramId).ifPresent(existing -> {
            if (!existing.getId().equals(current.getId())) {
                throw new ConflictException("telegramId", telegramId,
                        "Telegram ID уже привязан к другой учётной записи");
            }
        });
    }

    private void applyStudentTelegram(User user, Long telegramId) {
        ensureTelegramAvailable(user, telegramId);
        if (user.getTelegramId() != null && !user.getTelegramId().equals(telegramId)) {
            throw new BadRequestException("Telegram ID уже привязан к этой учётной записи");
        }
        if (user.getTelegramId() == null) user.setTelegramId(telegramId);
    }

    private void ensureEmployeeAvailable(User current, String employeeNumber) {
        if (employeeNumber == null || employeeNumber.isBlank()) {
            throw new BadRequestException("Табельный номер обязателен");
        }
        userRepository.findByEmployeeNumber(employeeNumber).ifPresent(existing -> {
            if (!existing.getId().equals(current.getId())) {
                throw new ConflictException("employeeNumber", employeeNumber,
                        "Табельный номер уже используется");
            }
        });
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public User patchUser(Long id, PatchUserRequest request) {
        boolean canonicalHeadmanMutation = request != null
                && request.isHeadman() != null
                && headmanAssignmentService != null;
        if (canonicalHeadmanMutation) {
            // The canonical operation owns all user locks before taking the
            // group lock. Combining it with a group move would make the
            // resulting membership ambiguous, so keep the old admin-users
            // action atomic and require two explicit operations.
            if (request.groupId() != null) {
                User membershipSnapshot = userRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
                if (!java.util.Objects.equals(membershipSnapshot.getGroupId(), request.groupId())) {
                    throw new BadRequestException(
                            "groupId", "Назначение старосты нельзя совмещать с переводом группы");
                }
            }
            if (request.isHeadman() && request.status() != null
                    && request.status() != AccountStatus.ACTIVE) {
                throw new BadRequestException(
                        "status", "Старостой может быть только активный студент");
            }
            if (request.isHeadman()) {
                headmanAssignmentService.assignLegacyHeadman(id);
            } else {
                headmanAssignmentService.revokeLegacyHeadman(id);
            }
        }
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
            if (!canonicalHeadmanMutation && user.getRole() != UserRole.STUDENT) {
                throw new BadRequestException("Только студент может быть назначен старостой");
            }
            if (!canonicalHeadmanMutation && targetStatus != AccountStatus.ACTIVE) {
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
        if (!canonicalHeadmanMutation
                && request.isHeadman() != null && !request.isHeadman() && user.isHeadman()) {
            headmanAssistantRepository.revokeAllByGroupId(user.getGroupId());
            user.setHeadman(false);
        }

        // Headman assign (USER-03). Request validation ran before the
        // terminal-history mutation above, so an unsupported transition cannot
        // leave a closed history row behind.
        if (!canonicalHeadmanMutation && request.isHeadman() != null && request.isHeadman()) {
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
        var studentGrant = roleGrantReader == null ? java.util.Optional.<ru.rutcampustrack.academic.contract.dto.user.RoleGrantViewResponse>empty()
                : roleGrantReader.findByUserId(id).stream()
                .filter(grant -> "STUDENT".equals(grant.role()))
                .findFirst();
        boolean legacyStudent = user.getRole() == UserRole.STUDENT;
        if (!legacyStudent && studentGrant.isEmpty()) {
            throw new BadRequestException("Перевод возможен только для студентов");
        }

        Long oldGroupId = studentGrant.map(grant -> grant.groupId()).orElse(user.getGroupId());
        if (studentGrant.isPresent() && !"ACTIVE".equals(studentGrant.get().status())) {
            throw new BadRequestException("Перевод доступен только для активного STUDENT");
        }
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
        if (legacyStudent) {
            user.setGroupId(newGroupId);
        }

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
        if (!legacyStudent && studentGrant.isPresent()) {
            roleGrantWriter.upsertRole(
                    id,
                    "student",
                    studentGrant.get().status().toLowerCase(java.util.Locale.ROOT),
                    newGroupId);
        }
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

    private static boolean isTerminalStudentGrantStatus(RoleGrantStatus status) {
        return status == RoleGrantStatus.EXPELLED || status == RoleGrantStatus.GRADUATED;
    }

    private static boolean isTerminalStudentGrantStatus(String status) {
        return "EXPELLED".equalsIgnoreCase(status) || "GRADUATED".equalsIgnoreCase(status);
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

    /**
     * Reopens a new managed interval after a terminal STUDENT grant is
     * reactivated. The old closed row remains immutable; using today's
     * canonical Moscow date avoids resurrecting the old semester-start row.
     */
    private void openMembershipOnStudentReactivation(Long userId,
                                                     Long groupId,
                                                     LocalDate effectiveDate) {
        if (userId == null || userId <= 0 || groupId == null || groupId <= 0
                || effectiveDate == null) {
            throw HistoricalMembershipException.invalid("Student reactivation membership is invalid");
        }
        if (groupRepository == null || coverageRepository == null
                || studentGroupHistoryRepository == null) {
            throw HistoricalMembershipException.precondition(
                    "Historical membership writer is unavailable");
        }
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> HistoricalMembershipException.notFound(
                        "Group " + groupId + " not found"));
        requireCoveredGroup(group, effectiveDate);
        List<StudentGroupHistory> openHistories = studentGroupHistoryRepository
                .findOpenByUserIdForUpdate(userId);
        if (openHistories == null) {
            throw HistoricalMembershipException.precondition(
                    "Open membership lookup returned no coherent result");
        }
        if (!openHistories.isEmpty()) {
            throw HistoricalMembershipException.precondition(
                    "Student reactivation would overlap an open membership interval");
        }
        StudentGroupHistory history = new StudentGroupHistory();
        history.setUserId(userId);
        history.setGroupId(groupId);
        history.setJoinedAt(effectiveDate);
        history.setCreatedAt(OffsetDateTime.now());
        history.setReason("role-reactivation");
        studentGroupHistoryRepository.save(history);
    }

    private static void validateCreateRoleData(CreateUserRequest request) {
        if (request == null || request.role() == null) {
            throw new BadRequestException("role", "Роль обязательна");
        }
        if (request.groupId() != null && request.groupId() <= 0) {
            throw new BadRequestException("groupId", "ID группы должен быть положительным");
        }
        if (request.telegramId() != null && request.telegramId() <= 0) {
            throw new BadRequestException("telegramId", "Telegram ID должен быть положительным");
        }
        boolean hasEmployeeNumber = request.employeeNumber() != null
                && !request.employeeNumber().isBlank();
        switch (request.role()) {
            case STUDENT -> {
                if (request.groupId() == null) {
                    throw new BadRequestException("groupId", "ID группы обязателен для студента");
                }
                if (hasEmployeeNumber) {
                    throw new BadRequestException("employeeNumber",
                            "Табельный номер не применим к роли STUDENT");
                }
                validateTelegramForRole(request);
            }
            case TEACHER -> {
                if (request.groupId() != null) {
                    throw new BadRequestException("groupId", "Группа не применима к роли TEACHER");
                }
                if (!hasEmployeeNumber) {
                    throw new BadRequestException("employeeNumber",
                            "Табельный номер обязателен для преподавателя");
                }
            }
            case ADMIN -> {
                if (request.groupId() != null) {
                    throw new BadRequestException("groupId", "Группа не применима к роли ADMIN");
                }
                if (hasEmployeeNumber) {
                    throw new BadRequestException("employeeNumber",
                            "Табельный номер не применим к роли ADMIN");
                }
            }
        }
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

    /**
     * Invalidates cached group rosters only after the membership transaction
     * commits. Evicting before commit permits a concurrent reader to repopulate
     * the five-minute cache from the old database snapshot.
     */
    private void evictGroupMembersAfterCommit(Long... groupIds) {
        if (cacheManager == null) return;
        List<Long> affected = Arrays.stream(groupIds)
                .filter(groupId -> groupId != null && groupId > 0)
                .distinct()
                .toList();
        if (affected.isEmpty()) return;
        Runnable evict = () -> {
            Cache groupMembers = cacheManager.getCache("group_members");
            if (groupMembers != null) {
                affected.forEach(groupMembers::evict);
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

    private record EnrollmentContext(Group group, Semester semester, GroupHistoryCoverage coverage) {
    }

    /**
     * BUG-006-3 / D-08..D-11: enforces that {@code telegramId} is present for
     * {@link UserRole#STUDENT} accounts. Teacher/admin accounts keep the field
     * optional. A telegramId of {@code 0} is normalised to "missing" — some
     * clients submit zero instead of null.
     */
    private static void validateTelegramForRole(CreateUserRequest request) {
        if (request.role() == UserRole.STUDENT
                && (request.telegramId() == null || request.telegramId() == 0L)) {
            throw new BadRequestException("telegramId",
                    "Telegram ID обязателен для студента");
        }
    }

    private String generateLogin(CreateUserRequest request) {
        String surname = transliterate(request.lastName());
        String given = transliterate(request.firstName());
        String digits = loginDigits(request);
        String stem = (surname + "-" + given).replaceAll("-+", "-");
        if (stem.equals("-")) stem = "user";
        // Keep the four-digit identity suffix inside the 32-character limit.
        // Collision suffixes are reserved separately below, so a long name
        // cannot make every candidate collapse to the same trimmed login.
        String normalizedStem = trimLogin(stem);
        String digitPart = digits.isBlank() ? "" : "-" + digits;
        int baseStemLength = Math.max(1, 32 - digitPart.length());
        String baseStem = normalizedStem.substring(0, Math.min(normalizedStem.length(), baseStemLength));
        String base = trimLogin(baseStem + digitPart);
        if (!userRepository.existsByLogin(base)) return base;
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String collisionPart = "-" + suffix;
            int candidateStemLength = Math.max(1, 32 - digitPart.length() - collisionPart.length());
            String candidateStem = baseStem.substring(0, Math.min(baseStem.length(), candidateStemLength));
            String candidate = trimLogin(candidateStem + digitPart + collisionPart);
            if (!userRepository.existsByLogin(candidate)) return candidate;
        }
        throw new ConflictException("login", base, "Не удалось подобрать уникальный логин");
    }

    private String loginDigits(CreateUserRequest request) {
        String source = request.telegramId() == null
                ? request.employeeNumber()
                : Long.toString(request.telegramId());
        if (source == null) {
            return String.format("%04d", Math.floorMod(userRepository.nextTeacherLoginSeq(), 10_000));
        }
        String digits = source.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return String.format("%04d", Math.floorMod(userRepository.nextTeacherLoginSeq(), 10_000));
        }
        return digits.length() <= 4 ? String.format("%04d", Long.parseLong(digits))
                : digits.substring(digits.length() - 4);
    }

    private static String trimLogin(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("^-+|-+$", "");
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }

    private static String transliterate(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        String[] source = {"щ", "ш", "ч", "ц", "ю", "я", "ж", "ё", "й", "х", "ъ", "ь"};
        String[] target = {"shch", "sh", "ch", "ts", "yu", "ya", "zh", "yo", "y", "kh", "", ""};
        for (int i = 0; i < lower.length(); i++) {
            String symbol = lower.substring(i, i + 1);
            boolean replaced = false;
            for (int j = 0; j < source.length; j++) {
                if (symbol.equals(source[j])) {
                    result.append(target[j]);
                    replaced = true;
                    break;
                }
            }
            if (replaced) continue;
            char c = lower.charAt(i);
            if (c >= 'а' && c <= 'я') {
                String mapped = switch (c) {
                    case 'а' -> "a"; case 'б' -> "b"; case 'в' -> "v"; case 'г' -> "g";
                    case 'д' -> "d"; case 'е' -> "e"; case 'з' -> "z"; case 'и' -> "i";
                    case 'к' -> "k"; case 'л' -> "l"; case 'м' -> "m"; case 'н' -> "n";
                    case 'о' -> "o"; case 'п' -> "p"; case 'р' -> "r"; case 'с' -> "s";
                    case 'т' -> "t"; case 'у' -> "u"; case 'ф' -> "f"; case 'ы' -> "y";
                    case 'э' -> "e"; default -> "";
                };
                result.append(mapped);
            } else if (Character.isLetterOrDigit(c)) {
                result.append(c);
            }
        }
        return result.toString();
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
