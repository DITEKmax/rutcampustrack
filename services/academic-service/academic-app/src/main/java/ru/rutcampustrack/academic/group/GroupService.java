package ru.rutcampustrack.academic.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.CreateAdminGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.EducationLevel;
import ru.rutcampustrack.academic.contract.dto.group.GroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.StudyMode;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.GroupHistoryCoverage;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.event.DashboardInvalidatedEvent;
import ru.rutcampustrack.academic.event.GroupRenamedEvent;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.OffsetDateTime;
import java.util.List;

/** Business logic for Group domain: CRUD and member listing. */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final RequestContext requestContext;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupNameParser nameParser;
    private final GroupArchivalService archivalService;
    private final SemesterRepository semesterRepository;
    private final GroupHistoryCoverageRepository coverageRepository;
    private final GroupRegistryReadRepository registryReadRepository;

    @Autowired private ru.rutcampustrack.academic.user.UserAssembler memberAssembler;

    @Autowired
    public GroupService(
            GroupRepository groupRepository,
            UserRepository userRepository,
            RequestContext requestContext,
            ApplicationEventPublisher eventPublisher,
            GroupNameParser nameParser,
            GroupArchivalService archivalService,
            SemesterRepository semesterRepository,
            GroupHistoryCoverageRepository coverageRepository,
            GroupRegistryReadRepository registryReadRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.requestContext = requestContext;
        this.eventPublisher = eventPublisher;
        this.nameParser = nameParser;
        this.archivalService = archivalService;
        this.semesterRepository = semesterRepository;
        this.coverageRepository = coverageRepository;
        this.registryReadRepository = registryReadRepository;
    }

    /** Compatibility constructor for source-era unit tests. */
    public GroupService(
            GroupRepository groupRepository,
            UserRepository userRepository,
            RequestContext requestContext,
            ApplicationEventPublisher eventPublisher,
            GroupNameParser nameParser) {
        this(
                groupRepository,
                userRepository,
                requestContext,
                eventPublisher,
                nameParser,
                null,
                null,
                null,
                null);
    }

    @Transactional
    public Group createGroup(CreateGroupRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("name", "Название группы обязательно");
        }
        // Preserve the legacy endpoint's field-specific conflict contract while
        // still sending every successful write through the canonical splitter.
        if (groupRepository.existsByName(request.name())) {
            throw new ConflictException(
                    "name", request.name(), "Группа с таким названием уже существует");
        }
        try {
            // Compatibility callers still converge on the split canonical storage.
            return createGroupCanonical(GroupCodeRules.fromName(request.name(), null), "name");
        } catch (UnknownProgramTypeException e) {
            throw new BadRequestException(
                    "name", "Неизвестный тип программы (цифра " + e.getDigit() + ")");
        } catch (GroupCodeRules.InvalidCodeException e) {
            throw new BadRequestException("name", e.getMessage());
        }
    }

    /** Canonical registry writer used by the new ADMIN route. */
    @Transactional
    public Group createAdminGroup(CreateAdminGroupRequest request) {
        try {
            return createGroupCanonical(
                    GroupCodeRules.fromParts(
                            request == null ? null : request.alphabeticCode(),
                            request == null ? null : request.numericCode(),
                            request == null ? null : request.trainingDurationYears()),
                    "numericCode");
        } catch (UnknownProgramTypeException e) {
            throw new BadRequestException(
                    "numericCode", "Неизвестный тип программы (цифра " + e.getDigit() + ")");
        } catch (GroupCodeRules.InvalidCodeException e) {
            throw new BadRequestException(e.field(), e.getMessage());
        }
    }

    private Group createGroupCanonical(GroupCodeRules.CanonicalCode code, String duplicateField) {
        Semester activeSemester = requireSingleActiveSemester();
        if (groupRepository.existsByAlphabeticCodeAndNumericCodeAndIsActiveTrue(
                        code.alphabeticCode(), code.numericCode())
                || groupRepository.existsByName(code.name())) {
            Object value = "name".equals(duplicateField) ? code.name() : code.numericCode();
            throw new ConflictException(
                    duplicateField, value, "Группа с таким кодом уже существует");
        }
        Group group = new Group();
        GroupCodeRules.apply(group, code);
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        Group saved = groupRepository.save(group);
        // The marker is written in the same transaction as the genuinely new
        // group.  Existing/legacy groups are never certified by this path.
        coverageRepository.save(
                new GroupHistoryCoverage(
                        saved.getId(),
                        activeSemester.getDateFrom(),
                        HistoricalMembershipService.WRITER_VERSION,
                        OffsetDateTime.now()));
        eventPublisher.publishEvent(new DashboardInvalidatedEvent(this));
        return saved;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Group findGroupById(Long id) {
        readFences().requireGroupReadable(id);
        return groupRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Group> listGroups(Boolean active, Pageable pageable) {
        readFences().requireRegistryReadable();
        if (active != null) {
            return groupRepository.findByIsActive(active, pageable);
        }
        return groupRepository.findAll(pageable);
    }

    /** 58-06 / BUG-006-6: фильтр по статусу жизненного цикла + ILIKE по name. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Group> listGroups(GroupStatus status, String search, Pageable pageable) {
        readFences().requireRegistryReadable();
        return groupRepository.findAll(
                GroupSpecifications.statusAndSearch(status, search), pageable);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GroupRegistryPage listAdminGroups(
            AdminGroupStatus status, String search, Pageable pageable) {
        return listAdminGroups(status, search, null, null, null, pageable);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GroupRegistryPage listAdminGroups(
            AdminGroupStatus status,
            String search,
            Integer course,
            StudyMode studyMode,
            EducationLevel educationLevel,
            Pageable pageable) {
        if (course != null && (course < 1 || course > GroupCodeRules.MAX_COURSE)) {
            throw new BadRequestException("course", "Курс должен быть от 1 до 5");
        }
        if (registryReadRepository == null) {
            throw HistoricalMembershipException.precondition(
                    "Group registry read model is unavailable");
        }
        readFences().requireRegistryReadable();
        Page<GroupRegistryReadRepository.GroupRegistryRow> page =
                registryReadRepository.find(
                        status, search, course, studyMode, educationLevel, pageable);
        GroupRegistryReadRepository.GroupRegistryCounts counts =
                registryReadRepository.countAll(search, course, studyMode, educationLevel);
        return new GroupRegistryPage(page, counts);
    }

    public record GroupRegistryPage(
            Page<GroupRegistryReadRepository.GroupRegistryRow> page,
            GroupRegistryReadRepository.GroupRegistryCounts counts) {}

    public Group updateGroup(Long id, UpdateGroupRequest request) {
        return availability().updateGroup(id, request);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Group applyOrdinaryUpdate(Long id, UpdateGroupRequest request) {
        if (request == null || !request.active())
            throw io.grpc.Status.INVALID_ARGUMENT.asRuntimeException();
        groupRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
        requireOrdinaryGroupWritable(id);
        Group result = updateGroupLocal(id, request, null, requestContext.getUserId());
        groupRepository.flush();
        availabilityJdbc.update(
                "UPDATE groups SET academic_revision=academic_revision+1 WHERE id=?", id);
        return result;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Group applyAvailabilityUpdate(
            Long id,
            UpdateGroupRequest request,
            Integer graduationYear,
            Long acceptedActor,
            java.util.UUID operation) {
        requireAvailabilityGuard(id, operation);
        Group result = updateGroupLocal(id, request, graduationYear, acceptedActor);
        groupRepository.flush();
        return result;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void validateAvailabilityUpdate(Long id, UpdateGroupRequest request) {
        var group =
                groupRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
        requireOrdinaryGroupWritable(id);
        validateUpdate(group, request);
    }

    private record UpdateValidation(GroupCodeRules.CanonicalCode code, boolean nameChanged) {}

    private UpdateValidation validateUpdate(Group group, UpdateGroupRequest request) {
        // 58-06 / BUG-006-6: архивные группы неизменяемы. Deactivation of an
        // active group goes through the existing archival writer below so the
        // suffix, archived_at and archive event stay canonical.
        if (!group.isActive()) {
            throw new ConflictException(
                    "archived", group.getId(), "Нельзя редактировать архивную группу");
        }
        if (request.trainingDurationYears() != null && !request.active()) {
            throw new BadRequestException(
                    "trainingDurationYears",
                    "Исправление срока обучения не может одновременно архивировать группу");
        }
        Integer duration =
                request.trainingDurationYears() == null
                        ? group.getTrainingDurationYears()
                        : request.trainingDurationYears();
        final GroupCodeRules.CanonicalCode code;
        try {
            code = GroupCodeRules.fromName(request.name(), duration);
        } catch (UnknownProgramTypeException e) {
            throw new BadRequestException(
                    "name", "Неизвестный тип программы (цифра " + e.getDigit() + ")");
        } catch (GroupCodeRules.InvalidCodeException e) {
            throw new BadRequestException(
                    request.trainingDurationYears() != null
                                    && "trainingDurationYears".equals(e.field())
                            ? e.field()
                            : "name",
                    e.getMessage());
        }
        // 58-04: при переименовании проверяем конфликт имени (кроме самой себя).
        boolean nameChanged = !code.name().equals(group.getName());
        if (nameChanged && groupRepository.existsByName(code.name())) {
            throw new ConflictException(
                    "name", code.name(), "Группа с таким названием уже существует");
        }
        return new UpdateValidation(code, nameChanged);
    }

    private Group updateGroupLocal(
            Long id, UpdateGroupRequest request, Integer graduationYear, Long acceptedActor) {
        Group group =
                groupRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
        var validation = validateUpdate(group, request);
        var code = validation.code();
        boolean nameChanged = validation.nameChanged();
        GroupCodeRules.apply(group, code);
        if (!request.active()) {
            if (archivalService == null) {
                throw HistoricalMembershipException.precondition(
                        "Group archival writer is unavailable");
            }
            archivalService.archiveByActor(group, graduationYear, acceptedActor);
        } else {
            group.setActive(true);
        }
        Group saved = groupRepository.save(group);
        // 58-07 / BUG-006-6: отдельное событие о переименовании (push/telegram для студентов).
        // GroupUpdatedEvent продолжает публиковаться для инвалидации кэшей и STOMP-рассылок.
        if (nameChanged) {
            eventPublisher.publishEvent(
                    new GroupRenamedEvent(this, saved.getId(), saved.getName(), acceptedActor));
        }
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, saved.getId()));
        eventPublisher.publishEvent(new DashboardInvalidatedEvent(this));
        return saved;
    }

    public void restoreGroup(Long id) {
        availability().restoreGroup(id);
    }

    private void restoreGroupLocal(Long id) {
        Group group =
                groupRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
        // A retry after a committed restore is a successful no-op.  In
        // particular it must not emit a second group.updated event.
        if (group.isActive()) {
            return;
        }
        if (archivalService == null) {
            throw HistoricalMembershipException.precondition(
                    "Group archival writer is unavailable");
        }

        GroupArchivalService.RestorableName restoredName = validateRestore(group, id);

        if (groupRepository.restoreArchivedGroup(id, restoredName.name()) != 1) {
            throw new ConflictException(
                    "archived", id, "Группа изменилась во время восстановления");
        }
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, id));
        eventPublisher.publishEvent(new DashboardInvalidatedEvent(this));
    }

    private GroupArchivalService.RestorableName validateRestore(Group group, Long id) {
        GroupArchivalService.RestorableName restoredName = archivalService.restorableName(group);
        try {
            GroupCodeRules.fromName(restoredName.name(), group.getTrainingDurationYears());
        } catch (GroupCodeRules.InvalidCodeException error) {
            throw new ConflictException(error.field(), id, error.getMessage());
        } catch (UnknownProgramTypeException error) {
            throw new ConflictException("name", id, "Неизвестный тип программы архивной группы");
        }
        if (groupRepository.existsByName(restoredName.name())) {
            throw new ConflictException(
                    "name", restoredName.name(), "Группа с таким названием уже существует");
        }
        if (restoredName.alphabeticCode() != null
                && groupRepository.existsByAlphabeticCodeAndNumericCodeAndIsActiveTrue(
                        restoredName.alphabeticCode(), restoredName.numericCode())) {
            throw new ConflictException(
                    "numericCode",
                    restoredName.numericCode(),
                    "Группа с таким кодом уже существует");
        }

        return restoredName;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void applyAvailabilityRestore(Long id, java.util.UUID operation) {
        requireAvailabilityGuard(id, operation);
        restoreGroupLocal(id);
        groupRepository.flush();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean validateAvailabilityRestore(Long id) {
        var group =
                groupRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
        requireOrdinaryGroupWritable(id);
        if (group.isActive()) return false;
        validateRestore(group, id);
        return true;
    }

    private org.springframework.beans.factory.ObjectProvider<
                    ru.rutcampustrack.academic.groupavailability.GroupAvailabilityService>
            availabilityProvider;

    @Autowired
    public void setAvailability(
            org.springframework.beans.factory.ObjectProvider<
                            ru.rutcampustrack.academic.groupavailability.GroupAvailabilityService>
                    provider) {
        this.availabilityProvider = provider;
    }

    private ru.rutcampustrack.academic.groupavailability.GroupAvailabilityService availability() {
        if (availabilityProvider == null || availabilityProvider.getIfAvailable() == null)
            throw io.grpc.Status.UNAVAILABLE.asRuntimeException();
        return availabilityProvider.getObject();
    }

    private void requireOrdinaryGroupWritable(Long id) {
        if (availabilityJdbc == null
                || !Boolean.FALSE.equals(
                        availabilityJdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM academic_group_availability_guards"
                                        + " WHERE group_id=?) OR EXISTS(SELECT 1 FROM"
                                        + " academic_identity_operation_groups g JOIN"
                                        + " academic_identity_operations o ON"
                                        + " o.operation_id=g.operation_id WHERE g.group_id=? AND"
                                        + " o.phase NOT IN ('SUCCEEDED','REJECTED'))",
                                Boolean.class,
                                id,
                                id)))
            throw io.grpc.Status.UNAVAILABLE
                    .withDescription("group lifecycle operation is pending")
                    .asRuntimeException();
    }

    private org.springframework.jdbc.core.JdbcTemplate availabilityJdbc;
    private ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence readFences;

    private ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence readFences() {
        if (readFences == null) throw io.grpc.Status.UNAVAILABLE.asRuntimeException();
        return readFences;
    }

    @Autowired
    public void setAvailabilityJdbc(
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence fences) {
        this.availabilityJdbc = jdbc;
        this.readFences = fences;
    }

    private void requireAvailabilityGuard(Long group, java.util.UUID operation) {
        if (availabilityJdbc == null
                || !Boolean.TRUE.equals(
                        availabilityJdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM academic_group_availability_guards g"
                                        + " JOIN academic_group_availability_operations o ON"
                                        + " o.operation_id=g.operation_id WHERE g.group_id=? AND"
                                        + " g.operation_id=? AND o.phase='AUTH_INVALIDATED')",
                                Boolean.class,
                                group,
                                operation)))
            throw io.grpc.Status.ABORTED
                    .withDescription("accepted group lifecycle guard required")
                    .asRuntimeException();
    }

    /** Final deletion requires the exact preview and current ADMIN password protocol. */
    public void deleteGroup(Long id) {
        throw new ConflictException(
                "deletion", id, "Используй подтверждённое окончательное удаление группы");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<UserResponse> getMyGroupMembers(Pageable pageable) {
        Long groupId = requestContext.getGroupId();
        readFences().requireUserReadable(requestContext.getUserId());
        readFences().requireGroupReadable(groupId);
        // Own User is a scalar directory projection. Resolve its local role/group display here,
        // in the same snapshot as the scope guard and roster; no Auth/credential read is requested.
        return userRepository
                .findActiveStudentsByGrantGroupId(groupId, pageable)
                .map(memberAssembler::toResponse);
    }

    private Semester requireSingleActiveSemester() {
        if (semesterRepository == null || coverageRepository == null) {
            throw HistoricalMembershipException.precondition(
                    "Historical membership writer is unavailable");
        }
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
}
