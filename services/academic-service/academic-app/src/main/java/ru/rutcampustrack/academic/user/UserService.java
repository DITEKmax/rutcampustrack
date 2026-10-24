package ru.rutcampustrack.academic.user;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.hateoas.EntityModel;
import org.springframework.lang.Nullable;
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
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.GroupHistoryCoverage;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;
import ru.rutcampustrack.academic.security.RequestContext;

/**
 * Academic profile, eligibility and membership operations; Auth allocates identity and credentials.
 */
@Service
public class UserService {

    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private ru.rutcampustrack.academic.notification.AcademicPersonalNotificationPublisher
            personalNotifications;

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
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

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.admin.AcademicAdminAccess adminAccess;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.admin.AdminAccessService adminAccessService;

    private final GroupHeadmanAssignmentService headmanAssignmentService;

    @Autowired
    public UserService(
            UserRepository userRepository,
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
            GroupHeadmanAssignmentService headmanAssignmentService,
            PasswordEncoder passwordEncoder) {
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
    public UserService(
            UserRepository userRepository,
            HeadmanAssistantRepository headmanAssistantRepository,
            StudentGroupHistoryRepository studentGroupHistoryRepository,
            RequestContext requestContext,
            UserAssembler userAssembler,
            @Nullable CacheManager cacheManager,
            ApplicationEventPublisher eventPublisher,
            PasswordEncoder passwordEncoder) {
        this(
                userRepository,
                headmanAssistantRepository,
                studentGroupHistoryRepository,
                requestContext,
                userAssembler,
                cacheManager,
                eventPublisher,
                null,
                null,
                null,
                null,
                null,
                null,
                passwordEncoder);
    }

    @Autowired
    private ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations
            identityOperations;

    @Autowired
    private ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient identityData;

    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public EntityModel<UserCreatedResponse> createUser(CreateUserRequest request) {
        var operation = identityOperations.create(request);
        if (operation.prepared() == null || operation.prepared().getTargetsCount() != 1)
            throw io.grpc.Status.UNAVAILABLE.asRuntimeException();
        long id = operation.prepared().getTargets(0).getUserId();
        User user = findUserById(id);
        String initial = null;
        try {
            var credentials =
                    identityData.initialCredentials(
                            List.of(id),
                            System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3));
            if (credentials.getCredentialsCount() != 1
                    || credentials.getCredentials(0).getUserId() != id)
                throw io.grpc.Status.UNAVAILABLE.asRuntimeException();
            initial = credentials.getCredentials(0).getInitialPassword();
        } catch (io.grpc.StatusRuntimeException denied) {
            if (denied.getStatus().getCode() != io.grpc.Status.Code.PERMISSION_DENIED) throw denied;
        }
        return userAssembler.toCreatedModel(user, initial);
    }

    public User findUserById(Long id) {
        return userRepository
                .findByIdIncludingArchived(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * Unified list: case-insensitive search по login/ФИО/telegramId (BUG-006-1) + опциональные
     * фильтры role/status через JPA Specification API (D-02). Пустые параметры → без ограничений
     * (backward compatible).
     */
    @Autowired
    private ru.rutcampustrack.academic.identitycatalog.IdentityRegistryQueryBarrier registryBarrier;

    public ru.rutcampustrack.academic.identitycatalog.IdentityRegistryQueryBarrier.Result
            listUserRegistry(
                    String search,
                    String roleFilter,
                    AccountStatus statusFilter,
                    String roleStatusFilter,
                    Pageable pageable) {
        return listUserRegistry(search, roleFilter, statusFilter, roleStatusFilter, null, pageable);
    }

    public ru.rutcampustrack.academic.identitycatalog.IdentityRegistryQueryBarrier.Result
            listUserRegistry(
                    String search,
                    String roleFilter,
                    AccountStatus statusFilter,
                    String roleStatusFilter,
                    Long groupId,
                    Pageable pageable) {
        return registryBarrier.read(
                () ->
                        listUsersLocal(
                                search,
                                roleFilter,
                                statusFilter,
                                roleStatusFilter,
                                groupId,
                                pageable));
    }

    public ru.rutcampustrack.academic.identitycatalog.IdentityRegistryQueryBarrier.Result
            listUserRegistryForReport(
                    ru.rutcampustrack.academic.contract.dto.export.RegistryExportModels.Users query,
                    Pageable pageable,
                    ru.rutcampustrack.academic.exportjobs.ExportJobService.UserRegistryReadScope
                            reportScope) {
        java.util.Objects.requireNonNull(reportScope).requireSelector(query);
        return registryBarrier.readForReport(
                () ->
                        listUsersLocal(
                                query.search(),
                                query.role(),
                                query.status(),
                                query.roleStatus(),
                                query.groupId(),
                                pageable),
                reportScope);
    }

    public ru.rutcampustrack.academic.identitycatalog.IdentityRegistryQueryBarrier.Result
            findUserDetail(long userId) {
        return registryBarrier.read(
                () ->
                        new org.springframework.data.domain.PageImpl<>(
                                List.of(findUserById(userId))));
    }

    public Page<User> listUsers(
            String search,
            String roleFilter,
            AccountStatus statusFilter,
            String roleStatusFilter,
            Pageable pageable) {
        return listUserRegistry(search, roleFilter, statusFilter, roleStatusFilter, pageable)
                .page();
    }

    private Page<User> listUsersLocal(
            String search,
            String roleFilter,
            AccountStatus statusFilter,
            String roleStatusFilter,
            Long groupId,
            Pageable pageable) {
        if (groupId != null && groupId <= 0)
            throw new BadRequestException(
                    "groupId", "Идентификатор группы должен быть положительным");
        validateRoleFilter(roleFilter, roleStatusFilter);
        Specification<User> spec =
                Specification.where(UserSpecifications.matchesSearch(search))
                        .and(UserSpecifications.matchesGrant(roleFilter, roleStatusFilter, groupId))
                        .and(
                                roleStatusFilter == null
                                        ? UserSpecifications.matchesStatus(statusFilter)
                                        : null);
        Sort sort =
                pageable.getSort().isSorted()
                        ? pageable.getSort()
                        : Sort.by("lastName", "firstName", "middleName");
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by("id"));
        Pageable effective =
                pageable.isPaged()
                        ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                        : Pageable.unpaged(sort);
        boolean archivedRole =
                roleStatusFilter != null && "ARCHIVED".equalsIgnoreCase(roleStatusFilter.trim());
        if (archivedRole || (roleStatusFilter == null && statusFilter == AccountStatus.ARCHIVED)) {
            // A restored account can still have archived grants. Do not force account ARCHIVED
            // when the administrator explicitly selects archived role history.
            Map<String, String> columns =
                    Map.ofEntries(
                            Map.entry("id", "id"),
                            Map.entry("login", "login"),
                            Map.entry("lastName", "last_name"),
                            Map.entry("firstName", "first_name"),
                            Map.entry("middleName", "middle_name"),
                            Map.entry("role", "role"),
                            Map.entry("status", "status"),
                            Map.entry("employeeNumber", "employee_number"),
                            Map.entry("createdAt", "created_at"));
            List<Sort.Order> orders = new ArrayList<>();
            for (Sort.Order order : effective.getSort()) {
                String column = columns.get(order.getProperty());
                if (column == null)
                    throw new BadRequestException("sort", "Недопустимое поле сортировки");
                orders.add(order.withProperty(column));
            }
            if (orders.stream().noneMatch(order -> order.getProperty().equals("id")))
                orders.add(Sort.Order.asc("id"));
            Pageable archivedPage =
                    effective.isPaged()
                            ? PageRequest.of(
                                    effective.getPageNumber(),
                                    effective.getPageSize(),
                                    Sort.by(orders))
                            : Pageable.unpaged(Sort.by(orders));
            String pattern =
                    search == null || search.isBlank()
                            ? null
                            : "%"
                                    + UserSpecifications.escapeLike(
                                            search.trim().toLowerCase(Locale.ROOT))
                                    + "%";
            return userRepository.findAdminRegistryIncludingArchived(
                    pattern,
                    roleFilter == null || roleFilter.isBlank()
                            ? null
                            : roleFilter.trim().toLowerCase(Locale.ROOT),
                    roleStatusFilter == null
                            ? null
                            : roleStatusFilter.trim().toLowerCase(Locale.ROOT),
                    roleStatusFilter == null && statusFilter != null
                            ? statusFilter.name().toLowerCase(Locale.ROOT)
                            : null,
                    groupId,
                    archivedPage);
        }
        return userRepository.findAll(spec, effective);
    }

    /** Legacy overload retained for source-era callers and tests. */
    public Page<User> listUsers(
            String search, UserRole roleFilter, AccountStatus statusFilter, Pageable pageable) {
        return listUsers(
                search,
                roleFilter == null ? null : roleFilter.name(),
                statusFilter,
                null,
                pageable);
    }

    /** Backward-compatible overload for internal callers (e.g. gRPC service). */
    public Page<User> listUsers(UserRole roleFilter, Pageable pageable) {
        return listUsers(null, roleFilter, null, pageable);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public User updateRoleGrant(Long id, String roleName, RoleGrantUpdateRequest request) {
        identityOperations.role(id, roleName, request);
        return findUserById(id);
    }

    public List<User> listTeachers() {
        return userRepository.findByRole("teacher", Pageable.ofSize(500)).getContent();
    }

    /**
     * Bounded teacher lookup for headman subject assignment. The durable grant is authoritative: a
     * mixed-role account with an active TEACHER grant is a candidate even when its legacy scalar
     * role belongs to another role.
     */
    public Page<User> searchActiveTeachers(String search, Pageable pageable) {
        int pageSize = Math.max(1, Math.min(pageable.getPageSize(), 20));
        Pageable bounded =
                org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageSize,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.asc("lastName"),
                                org.springframework.data.domain.Sort.Order.asc("firstName"),
                                org.springframework.data.domain.Sort.Order.asc("middleName"),
                                org.springframework.data.domain.Sort.Order.asc("id")));
        Specification<User> specification =
                Specification.where(UserSpecifications.matchesTeacherLookup(search))
                        .and(UserSpecifications.matchesGrant("TEACHER", "ACTIVE"));
        return userRepository.findAll(specification, bounded);
    }

    /**
     * Batch-резолв display-имён по списку ID (для STUDENT/TEACHER аудитных мест). Cap на size — 100
     * ids, защита от abuse через query string. Несуществующие ID молча пропускаются (downstream
     * caller сам решит fallback по отсутствующему).
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
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public User updateUser(Long id, UpdateUserRequest request) {
        identityOperations.replaceProfile(id, request);
        return findUserById(id);
    }

    private void validateRoleFilter(String roleFilter, String roleStatusFilter) {
        if (roleStatusFilter != null
                && !roleStatusFilter.isBlank()
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
                status =
                        RoleGrantStatus.valueOf(
                                roleStatusFilter.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Недопустимый статус роли: " + roleStatusFilter);
            }
            if (!isApplicableRoleStatus(normalized, status)) {
                throw new BadRequestException(
                        "Статус " + status + " не применим к роли " + normalized);
            }
        }
    }

    private static UserRole parseManagedRole(String roleName) {
        if (roleName == null) throw new BadRequestException("Роль обязательна");
        try {
            UserRole role = UserRole.valueOf(roleName.trim().toUpperCase(java.util.Locale.ROOT));
            if (role == UserRole.ADMIN || role == UserRole.STUDENT || role == UserRole.TEACHER)
                return role;
        } catch (IllegalArgumentException ignored) {
            // Use the same domain error for unknown and derived roles.
        }
        throw new BadRequestException("Роль нельзя изменить через этот endpoint: " + roleName);
    }

    private static void validateRoleStatus(UserRole role, RoleGrantStatus status) {
        if (status == null) throw new BadRequestException("Статус роли обязателен");
        boolean valid =
                isApplicableRoleStatus(role.name(), status) && status != RoleGrantStatus.ARCHIVED;
        if (!valid) throw new BadRequestException("Статус не применим к роли " + role);
    }

    private static boolean isApplicableRoleStatus(String role, RoleGrantStatus status) {
        return switch (role) {
            case "STUDENT" ->
                    status == RoleGrantStatus.ACTIVE
                            || status == RoleGrantStatus.EXPELLED
                            || status == RoleGrantStatus.GRADUATED
                            || status == RoleGrantStatus.SUSPENDED
                            || status == RoleGrantStatus.ARCHIVED;
            case "TEACHER" ->
                    status == RoleGrantStatus.ACTIVE
                            || status == RoleGrantStatus.DISMISSED
                            || status == RoleGrantStatus.SUSPENDED
                            || status == RoleGrantStatus.ARCHIVED;
            case "ADMIN" -> status == RoleGrantStatus.ACTIVE || status == RoleGrantStatus.ARCHIVED;
            case "HEADMAN" ->
                    status == RoleGrantStatus.ACTIVE
                            || status == RoleGrantStatus.SUSPENDED
                            || status == RoleGrantStatus.ARCHIVED;
            default -> false;
        };
    }

    private static void validateRoleGrantData(
            UserRole role, RoleGrantUpdateRequest request, boolean existingGrant) {
        if (request == null) throw new BadRequestException("Данные роли обязательны");
        if (request.groupId() != null && request.groupId() <= 0) {
            throw new BadRequestException("groupId", "ID группы должен быть положительным");
        }
        if (request.telegramId() != null && request.telegramId() <= 0) {
            throw new BadRequestException("telegramId", "Telegram ID должен быть положительным");
        }
        boolean hasEmployeeNumber =
                request.employeeNumber() != null && !request.employeeNumber().isBlank();
        switch (role) {
            case STUDENT -> {
                if (hasEmployeeNumber) {
                    throw new BadRequestException(
                            "employeeNumber", "Табельный номер не применим к роли STUDENT");
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
                    throw new BadRequestException(
                            "telegramId", "Telegram ID не изменяется через роль TEACHER");
                }
                if (!existingGrant && !hasEmployeeNumber) {
                    throw new BadRequestException(
                            "employeeNumber", "Для добавления TEACHER нужен табельный номер");
                }
            }
            case ADMIN -> {
                if (request.groupId() != null) {
                    throw new BadRequestException("groupId", "Группа не применима к роли ADMIN");
                }
                if (hasEmployeeNumber) {
                    throw new BadRequestException(
                            "employeeNumber", "Табельный номер не применим к роли ADMIN");
                }
                if (request.telegramId() != null) {
                    throw new BadRequestException(
                            "telegramId", "Telegram ID не изменяется через роль ADMIN");
                }
            }
        }
    }

    private void ensureTelegramAvailable(User current, Long telegramId) {
        if (telegramId == null || telegramId <= 0) {
            throw new BadRequestException("Telegram ID должен быть положительным");
        }
        userRepository
                .findByTelegramId(telegramId)
                .ifPresent(
                        existing -> {
                            if (!existing.getId().equals(current.getId())) {
                                throw new ConflictException(
                                        "telegramId",
                                        telegramId,
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
        userRepository
                .findByEmployeeNumber(employeeNumber)
                .ifPresent(
                        existing -> {
                            if (!existing.getId().equals(current.getId())) {
                                throw new ConflictException(
                                        "employeeNumber",
                                        employeeNumber,
                                        "Табельный номер уже используется");
                            }
                        });
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public User patchUser(Long id, PatchUserRequest request) {
        if (request != null && request.isHeadman() != null) adminAccess.require("GROUPS_MANAGE");
        identityOperations.patch(id, request);
        return findUserById(id);
    }

    private static String normalizeRequiredProfileField(String value, String field, String label) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new BadRequestException(field, label + " не может быть пустой");
        }
        if (normalized.length() > 128) {
            throw new BadRequestException(field, label + " не может быть длиннее 128 символов");
        }
        return normalized;
    }

    private static String normalizeOptionalProfileField(
            String value, int maxLength, String field, String label) {
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BadRequestException(
                    field, label + " не может быть длиннее " + maxLength + " символов");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    @CacheEvict(value = "group_members", key = "#request.newGroupId()")
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public User transferStudent(Long id, TransferStudentRequest request) {
        identityOperations.transfer(id, request);
        return findUserById(id);
    }

    public User getMe() {
        Long userId = requestContext.getUserId();
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Transactional
    public User updateMyAvatar(String avatarId) {
        Long userId = requestContext.getUserId();
        if (userId == null || userId <= 0)
            throw io.grpc.Status.UNAUTHENTICATED.asRuntimeException();
        String avatar = avatarId == null || avatarId.isBlank() ? null : avatarId;
        if (avatar != null && avatar.length() > 32)
            throw new BadRequestException("avatarId", "Недопустимый аватар");
        // Avatar is presentation-only and independently merged; coordinator APPLY never
        // overwrites it from an earlier accepted profile snapshot.
        if (jdbc.update(
                        "UPDATE academic_profiles p SET avatar_id=?,updated_at=now() WHERE p.id=?"
                                + " AND p.archived_at IS NULL AND NOT EXISTS(SELECT 1 FROM"
                                + " academic_identity_operation_targets t JOIN"
                                + " academic_identity_operations o ON o.operation_id=t.operation_id"
                                + " WHERE t.user_id=p.id AND t.fenced AND o.phase<>'SUCCEEDED')",
                        avatar,
                        userId)
                != 1)
            throw io.grpc.Status.UNAVAILABLE
                    .withDescription("profile mutation pending")
                    .asRuntimeException();
        if (entityManager != null) entityManager.clear();
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("users");
            if (cache != null) cache.evict(userId);
        }
        return findUserById(userId);
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
        Group group =
                groupRepository
                        .findByIdForUpdate(groupId)
                        .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        Semester semester = resolveActiveSemester();
        GroupHistoryCoverage coverage =
                coverageRepository
                        .findById(groupId)
                        .orElseThrow(
                                () ->
                                        HistoricalMembershipException.precondition(
                                                "Student enrollment requires managed group history"
                                                        + " coverage"));
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
            throw HistoricalMembershipException.precondition(
                    "Active semester could not be resolved");
        }
        if (active == null || active.size() != 1) {
            throw HistoricalMembershipException.precondition(
                    "Exactly one active semester is required");
        }
        Semester semester = active.get(0);
        if (semester.getDateFrom() == null
                || semester.getDateTo() == null
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
        GroupHistoryCoverage coverage =
                coverageRepository
                        .findById(group.getId())
                        .orElseThrow(
                                () ->
                                        HistoricalMembershipException.precondition(
                                                "Group history coverage is not established"));
        if (!HistoricalMembershipService.WRITER_VERSION.equals(coverage.getWriterVersion())
                || coverage.getCoverageFrom() == null
                || coverage.getCoverageFrom().isAfter(effectiveDate)) {
            throw HistoricalMembershipException.precondition(
                    "Group history coverage does not support this date");
        }
    }

    private void ensureMembershipMutationSupported(
            User user, UserRole requestedRole, Long requestedGroupId) {
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
     * Reopens a new managed interval after a terminal STUDENT grant is reactivated. The old closed
     * row remains immutable; using today's canonical Moscow date avoids resurrecting the old
     * semester-start row.
     */
    private void openMembershipOnStudentReactivation(
            Long userId, Long groupId, LocalDate effectiveDate) {
        if (userId == null
                || userId <= 0
                || groupId == null
                || groupId <= 0
                || effectiveDate == null) {
            throw HistoricalMembershipException.invalid(
                    "Student reactivation membership is invalid");
        }
        if (groupRepository == null
                || coverageRepository == null
                || studentGroupHistoryRepository == null) {
            throw HistoricalMembershipException.precondition(
                    "Historical membership writer is unavailable");
        }
        Group group =
                groupRepository
                        .findByIdForUpdate(groupId)
                        .orElseThrow(
                                () ->
                                        HistoricalMembershipException.notFound(
                                                "Group " + groupId + " not found"));
        requireCoveredGroup(group, effectiveDate);
        List<StudentGroupHistory> openHistories =
                studentGroupHistoryRepository.findOpenByUserIdForUpdate(userId);
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
        boolean hasEmployeeNumber =
                request.employeeNumber() != null && !request.employeeNumber().isBlank();
        switch (request.role()) {
            case STUDENT -> {
                if (request.groupId() == null) {
                    throw new BadRequestException("groupId", "ID группы обязателен для студента");
                }
                if (hasEmployeeNumber) {
                    throw new BadRequestException(
                            "employeeNumber", "Табельный номер не применим к роли STUDENT");
                }
                validateTelegramForRole(request);
            }
            case TEACHER -> {
                if (request.groupId() != null) {
                    throw new BadRequestException("groupId", "Группа не применима к роли TEACHER");
                }
                if (!hasEmployeeNumber) {
                    throw new BadRequestException(
                            "employeeNumber", "Табельный номер обязателен для преподавателя");
                }
            }
            case ADMIN -> {
                if (request.groupId() != null) {
                    throw new BadRequestException("groupId", "Группа не применима к роли ADMIN");
                }
                if (hasEmployeeNumber) {
                    throw new BadRequestException(
                            "employeeNumber", "Табельный номер не применим к роли ADMIN");
                }
            }
        }
    }

    /**
     * Invalidates cached group rosters only after the membership transaction commits. Evicting
     * before commit permits a concurrent reader to repopulate the five-minute cache from the old
     * database snapshot.
     */
    private void evictGroupMembersAfterCommit(Long... groupIds) {
        if (cacheManager == null) return;
        List<Long> affected =
                Arrays.stream(groupIds)
                        .filter(groupId -> groupId != null && groupId > 0)
                        .distinct()
                        .toList();
        if (affected.isEmpty()) return;
        Runnable evict =
                () -> {
                    Cache groupMembers = cacheManager.getCache("group_members");
                    if (groupMembers != null) {
                        affected.forEach(groupMembers::evict);
                    }
                };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            evict.run();
                        }
                    });
        } else {
            evict.run();
        }
    }

    private record EnrollmentContext(
            Group group, Semester semester, GroupHistoryCoverage coverage) {}

    /**
     * BUG-006-3 / D-08..D-11: enforces that {@code telegramId} is present for {@link
     * UserRole#STUDENT} accounts. Teacher/admin accounts keep the field optional. A telegramId of
     * {@code 0} is normalised to "missing" — some clients submit zero instead of null.
     */
    private static void validateTelegramForRole(CreateUserRequest request) {
        if (request.role() == UserRole.STUDENT
                && (request.telegramId() == null || request.telegramId() == 0L)) {
            throw new BadRequestException("telegramId", "Telegram ID обязателен для студента");
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
        String baseStem =
                normalizedStem.substring(0, Math.min(normalizedStem.length(), baseStemLength));
        String base = trimLogin(baseStem + digitPart);
        if (!userRepository.existsByLogin(base)) return base;
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String collisionPart = "-" + suffix;
            int candidateStemLength = Math.max(1, 32 - digitPart.length() - collisionPart.length());
            String candidateStem =
                    baseStem.substring(0, Math.min(baseStem.length(), candidateStemLength));
            String candidate = trimLogin(candidateStem + digitPart + collisionPart);
            if (!userRepository.existsByLogin(candidate)) return candidate;
        }
        throw new ConflictException("login", base, "Не удалось подобрать уникальный логин");
    }

    private String loginDigits(CreateUserRequest request) {
        String source =
                request.telegramId() == null
                        ? request.employeeNumber()
                        : Long.toString(request.telegramId());
        if (source == null) {
            return String.format(
                    "%04d", Math.floorMod(userRepository.nextTeacherLoginSeq(), 10_000));
        }
        String digits = source.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return String.format(
                    "%04d", Math.floorMod(userRepository.nextTeacherLoginSeq(), 10_000));
        }
        return digits.length() <= 4
                ? String.format("%04d", Long.parseLong(digits))
                : digits.substring(digits.length() - 4);
    }

    private static String trimLogin(String value) {
        String normalized =
                value.toLowerCase(java.util.Locale.ROOT)
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
                String mapped =
                        switch (c) {
                            case 'а' -> "a";
                            case 'б' -> "b";
                            case 'в' -> "v";
                            case 'г' -> "g";
                            case 'д' -> "d";
                            case 'е' -> "e";
                            case 'з' -> "z";
                            case 'и' -> "i";
                            case 'к' -> "k";
                            case 'л' -> "l";
                            case 'м' -> "m";
                            case 'н' -> "n";
                            case 'о' -> "o";
                            case 'п' -> "p";
                            case 'р' -> "r";
                            case 'с' -> "s";
                            case 'т' -> "t";
                            case 'у' -> "u";
                            case 'ф' -> "f";
                            case 'ы' -> "y";
                            case 'э' -> "e";
                            default -> "";
                        };
                result.append(mapped);
            } else if (Character.isLetterOrDigit(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }
}
