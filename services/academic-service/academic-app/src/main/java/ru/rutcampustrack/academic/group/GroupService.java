package ru.rutcampustrack.academic.group;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.CreateAdminGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.GroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.GroupHistoryCoverage;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.GroupRenamedEvent;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Business logic for Group domain: CRUD and member listing.
 */
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

    @Autowired
    public GroupService(GroupRepository groupRepository,
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
    public GroupService(GroupRepository groupRepository,
                        UserRepository userRepository,
                        RequestContext requestContext,
                        ApplicationEventPublisher eventPublisher,
                        GroupNameParser nameParser) {
        this(groupRepository, userRepository, requestContext, eventPublisher, nameParser,
                null, null, null, null);
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
            return createGroupCanonical(
                    GroupCodeRules.fromName(request.name(), null), "name");
        } catch (UnknownProgramTypeException e) {
            throw new BadRequestException(
                    "name",
                    "Неизвестный тип программы (цифра " + e.getDigit() + ")"
            );
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
                    "numericCode",
                    "Неизвестный тип программы (цифра " + e.getDigit() + ")"
            );
        } catch (GroupCodeRules.InvalidCodeException e) {
            throw new BadRequestException(e.field(), e.getMessage());
        }
    }

    private Group createGroupCanonical(GroupCodeRules.CanonicalCode code,
                                       String duplicateField) {
        Semester activeSemester = requireSingleActiveSemester();
        if (groupRepository.existsByAlphabeticCodeAndNumericCodeAndIsActiveTrue(
                code.alphabeticCode(), code.numericCode())
                || groupRepository.existsByName(code.name())) {
            Object value = "name".equals(duplicateField)
                    ? code.name() : code.numericCode();
            throw new ConflictException(duplicateField, value,
                    "Группа с таким кодом уже существует");
        }
        Group group = new Group();
        GroupCodeRules.apply(group, code);
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        Group saved = groupRepository.save(group);
        // The marker is written in the same transaction as the genuinely new
        // group.  Existing/legacy groups are never certified by this path.
        coverageRepository.save(new GroupHistoryCoverage(
                saved.getId(), activeSemester.getDateFrom(),
                HistoricalMembershipService.WRITER_VERSION, OffsetDateTime.now()));
        return saved;
    }

    public Group findGroupById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
    }

    public Page<Group> listGroups(Boolean active, Pageable pageable) {
        if (active != null) {
            return groupRepository.findByIsActive(active, pageable);
        }
        return groupRepository.findAll(pageable);
    }

    /**
     * 58-06 / BUG-006-6: фильтр по статусу жизненного цикла + ILIKE по name.
     */
    public Page<Group> listGroups(GroupStatus status, String search, Pageable pageable) {
        return groupRepository.findAll(
                GroupSpecifications.statusAndSearch(status, search),
                pageable
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GroupRegistryPage listAdminGroups(AdminGroupStatus status, String search, Pageable pageable) {
        if (registryReadRepository == null) {
            throw HistoricalMembershipException.precondition("Group registry read model is unavailable");
        }
        Page<GroupRegistryReadRepository.GroupRegistryRow> page =
                registryReadRepository.find(status, search, pageable);
        GroupRegistryReadRepository.GroupRegistryCounts counts =
                registryReadRepository.countAll(search);
        return new GroupRegistryPage(page, counts);
    }

    public record GroupRegistryPage(
            Page<GroupRegistryReadRepository.GroupRegistryRow> page,
            GroupRegistryReadRepository.GroupRegistryCounts counts) {}

    @Caching(evict = {
        @CacheEvict(value = "groups", key = "#id"),
        @CacheEvict(value = "group_members", key = "#id")
    })
    @Transactional
    public Group updateGroup(Long id, UpdateGroupRequest request) {
        Group group = findGroupById(id);
        // 58-06 / BUG-006-6: архивные группы неизменяемы. Deactivation of an
        // active group goes through the existing archival writer below so the
        // suffix, archived_at and archive event stay canonical.
        if (!group.isActive()) {
            throw new ConflictException(
                    "archived",
                    group.getId(),
                    "Нельзя редактировать архивную группу"
            );
        }
        final GroupCodeRules.CanonicalCode code;
        try {
            code = GroupCodeRules.fromName(request.name(), group.getTrainingDurationYears());
        } catch (UnknownProgramTypeException e) {
            throw new BadRequestException(
                    "name",
                    "Неизвестный тип программы (цифра " + e.getDigit() + ")"
            );
        } catch (GroupCodeRules.InvalidCodeException e) {
            throw new BadRequestException("name", e.getMessage());
        }
        // 58-04: при переименовании проверяем конфликт имени (кроме самой себя).
        boolean nameChanged = !code.name().equals(group.getName());
        if (nameChanged && groupRepository.existsByName(code.name())) {
            throw new ConflictException("name", code.name(),
                    "Группа с таким названием уже существует");
        }
        GroupCodeRules.apply(group, code);
        if (!request.active()) {
            if (archivalService == null) {
                throw HistoricalMembershipException.precondition(
                        "Group archival writer is unavailable");
            }
            archivalService.archive(group);
        } else {
            group.setActive(true);
        }
        Group saved = groupRepository.save(group);
        // 58-07 / BUG-006-6: отдельное событие о переименовании (push/telegram для студентов).
        // GroupUpdatedEvent продолжает публиковаться для инвалидации кэшей и STOMP-рассылок.
        if (nameChanged) {
            eventPublisher.publishEvent(new GroupRenamedEvent(this, saved.getId(), saved.getName()));
        }
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, saved.getId()));
        return saved;
    }

    @Caching(evict = {
        @CacheEvict(value = "groups", key = "#id"),
        @CacheEvict(value = "group_members", key = "#id")
    })
    @Transactional
    public void deleteGroup(Long id) {
        Group group = findGroupById(id);
        if (coverageRepository != null && coverageRepository.existsById(id)) {
            throw HistoricalMembershipException.unsupported(
                    "Нельзя удалить группу с управляемой историей посещаемости");
        }
        groupRepository.delete(group);
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, id));
    }

    public Page<User> getMyGroupMembers(Pageable pageable) {
        Long groupId = requestContext.getGroupId();
        return userRepository.findActiveStudentsByGrantGroupId(groupId, pageable);
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
        if (semester.getDateFrom() == null || semester.getDateTo() == null
                || semester.getDateFrom().isAfter(semester.getDateTo())) {
            throw HistoricalMembershipException.precondition("Active semester has invalid dates");
        }
        return semester;
    }
}
