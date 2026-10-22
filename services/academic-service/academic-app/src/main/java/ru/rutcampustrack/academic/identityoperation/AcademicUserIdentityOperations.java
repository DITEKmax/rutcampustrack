package ru.rutcampustrack.academic.identityoperation;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.admin.AdminAccessService;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.AuthUserArchiveClient;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authauthority.grpc.AuthorityAccountState;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminPermission;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * The existing user create command, implemented with assigned Auth identity and local domain APPLY.
 */
@Service
public class AcademicUserIdentityOperations {
    private final JdbcTemplate jdbc;
    private final AcademicIdentityHttpOperations http;
    private final ObjectMapper canonical;
    private final TransactionTemplate snapshot;
    private final AuthIdentityDataClient identities;
    private final AcademicIdentityReadFence fences;
    private final RequestContext context;
    private final UserArchiveRepository archives;
    private final AuthUserArchiveClient archiveProof;
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    public AcademicUserIdentityOperations(
            JdbcTemplate jdbc,
            AcademicIdentityHttpOperations http,
            ObjectMapper mapper,
            PlatformTransactionManager transactions,
            AuthIdentityDataClient identities,
            AcademicIdentityReadFence fences,
            RequestContext context,
            UserArchiveRepository archives,
            AuthUserArchiveClient archiveProof) {
        this.jdbc = jdbc;
        this.http = http;
        this.identities = identities;
        this.fences = fences;
        this.context = context;
        this.archives = archives;
        this.archiveProof = archiveProof;
        this.canonical =
                mapper.copy()
                        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.snapshot = new TransactionTemplate(transactions);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public AcademicIdentityCoordinator.Operation create(CreateUserRequest request) {
        var intent = intent("CREATE", 0, request);
        return http.execute(
                intent,
                id -> {
                    validate(request);
                    localSnapshot(
                            tx -> {
                                requireEmployeeAvailable(blankToNull(request.employeeNumber()), 0);
                                return null;
                            });
                    var enrollment =
                            request.role() == UserRole.STUDENT
                                    ? localSnapshot(tx -> enrollment(request.groupId()))
                                    : null;
                    var grant =
                            AdmissionGrant.newBuilder()
                                    .setRole(request.role().name())
                                    .setStatus("ACTIVE");
                    if (request.role() == UserRole.STUDENT) grant.setGroupId(request.groupId());
                    var target =
                            IdentityTargetCommand.newBuilder()
                                    .setDesiredAdminProfile(
                                            AdmissionAdminProfile.getDefaultInstance())
                                    .setDesiredAccountState(
                                            IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                    .addDesiredGrants(grant);
                    var access = request.adminAccess();
                    if (access != null)
                        target.setDesiredAdminProfile(
                                AdmissionAdminProfile.newBuilder()
                                        .setAssigned(true)
                                        .setMode(access.mode())
                                        .addAllPermissions(
                                                access.permissions() == null
                                                        ? List.of()
                                                        : access.permissions().stream()
                                                                .distinct()
                                                                .sorted()
                                                                .toList()));
                    var login = AcademicLoginHint.from(request);
                    var auth =
                            PrepareIdentityOperationRequest.newBuilder()
                                    .setOperationId(id.toString())
                                    .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE)
                                    .addTargets(target)
                                    .setCreateIdentity(
                                            CreateIdentityMaterial.newBuilder()
                                                    .setLoginPrefix(login.stem())
                                                    .setLoginDigits(login.digits())
                                                    .setTelegramId(
                                                            request.telegramId() == null
                                                                    ? 0
                                                                    : request.telegramId()));
                    var profile =
                            new AcademicIdentityLocalCommand.Profile(
                                    0,
                                    request.lastName(),
                                    request.firstName(),
                                    request.middleName(),
                                    null,
                                    null,
                                    blankToNull(request.employeeNumber()),
                                    null);
                    var local =
                            new AcademicIdentityLocalCommand(
                                    intent,
                                    profile,
                                    enrollment == null ? null : enrollment.date(),
                                    enrollment == null ? null : enrollment.date(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    enrollment == null ? null : enrollment.semester(),
                                    "initial-enrollment",
                                    enrollment == null ? List.of() : List.of(enrollment.group()));
                    return accepted(auth, local);
                });
    }

    public AcademicIdentityCoordinator.Operation role(
            long user, String roleName, RoleGrantUpdateRequest request) {
        String role = roleName == null ? "" : roleName.trim().toUpperCase(java.util.Locale.ROOT);
        return existing(
                intent("ROLE", user, new RoleIntent(role, request)),
                user,
                () -> validateRole(role, request),
                (id, state) -> {
                    var target = target(state.auth());
                    var grants = new ArrayList<>(target.getDesiredGrantsList());
                    var old = grant(grants, role);
                    boolean fresh = old == null || old.getStatus().equals("ARCHIVED");
                    var changed =
                            old == null
                                    ? AdmissionGrant.newBuilder().setRole(role)
                                    : old.toBuilder();
                    var groups = new ArrayList<AcademicIdentityLocalCommand.GroupRevision>();
                    Enrollment enrollment = null;
                    var profile = state.profile();
                    if (role.equals("STUDENT")) {
                        Long group = old != null && old.hasGroupId() ? old.getGroupId() : null;
                        if (fresh) {
                            Long telegram =
                                    request.telegramId() != null
                                            ? request.telegramId()
                                            : state.auth().hasTelegramId()
                                                    ? state.auth().getTelegramId()
                                                    : null;
                            if (request.groupId() == null || telegram == null)
                                throw new BadRequestException(
                                        "Для добавления STUDENT нужны группа и Telegram ID");
                            enrollment = enrollment(request.groupId());
                            group = request.groupId();
                        } else if (request.groupId() != null
                                && !Objects.equals(group, request.groupId())) {
                            throw new BadRequestException(
                                    "Изменение группы выполняется только через перевод с причиной");
                        }
                        if (group == null)
                            throw new BadRequestException(
                                    "Роль STUDENT требует управляемую группу");
                        groups.add(existingGroup(group));
                        changed.setGroupId(group);
                        if (request.status() == RoleGrantStatus.ACTIVE
                                && request.telegramId() == null
                                && !state.auth().hasTelegramId())
                            throw new BadRequestException(
                                    "Активная STUDENT роль требует Telegram ID");
                        if (request.telegramId() != null)
                            target.setTelegramChange(
                                    TelegramBindingChange.newBuilder()
                                            .setTelegramId(request.telegramId()));
                        if (request.status() != RoleGrantStatus.ACTIVE) suspendHeadman(grants);
                    } else {
                        String employee = blankToNull(request.employeeNumber());
                        if (old == null && employee == null)
                            throw new BadRequestException(
                                    "Для добавления TEACHER нужен табельный номер");
                        if (employee != null) {
                            requireEmployeeAvailable(employee, profile.userId());
                            profile = withEmployee(profile, employee);
                        }
                        if (request.status() == RoleGrantStatus.ACTIVE
                                && blankToNull(profile.employeeNumber()) == null)
                            throw new BadRequestException(
                                    "Активная TEACHER роль требует табельный номер");
                    }
                    if (state.directoryRole().equals(role))
                        profile =
                                withDirectoryStatus(
                                        profile,
                                        List.of("GRADUATED", "DISMISSED")
                                                        .contains(request.status().name())
                                                ? "ACTIVE"
                                                : request.status().name());
                    changed.setStatus(request.status().name());
                    replace(grants, changed.build());
                    target.clearDesiredGrants().addAllDesiredGrants(canonicalGrants(grants));
                    var local =
                            local(
                                    intent("ROLE", user, new RoleIntent(role, request)),
                                    profile,
                                    groups,
                                    enrollment == null ? null : enrollment.semester(),
                                    enrollment == null
                                            ? null
                                            : old == null
                                                    ? enrollment.date()
                                                    : LocalDate.now(MOSCOW),
                                    "role-status");
                    return accepted(
                            command(id, IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE, target),
                            local);
                });
    }

    /** Auth admits FULL/self/last-FULL and exact profile CAS; Academic only owns its revision. */
    public AcademicIdentityCoordinator.Operation adminAccess(
            long user, AdminAccessInput request, Long expectedVersion, boolean revoke) {
        var intent =
                intent(
                        "ADMIN_ACCESS",
                        user,
                        new AdminAccessIntent(request, expectedVersion, revoke));
        return http.execute(
                intent,
                id -> {
                    AcademicIdentityHttpOperations.localPreflight(
                            () -> {
                                if (user <= 0 || expectedVersion == null || expectedVersion < 0)
                                    throw AdminAccessException.invalid();
                                if (!revoke) {
                                    if (request == null) throw AdminAccessException.invalid();
                                    AdminPermission.validate(request.mode(), request.permissions());
                                }
                                return Boolean.TRUE;
                            });
                    identities.checkAdminAccess(List.of(), true, deadline());
                    var identity =
                            identities.readServiceIdentity(List.of(user), deadline()).get(user);
                    var preflight =
                            localSnapshot(
                                    tx -> {
                                        var state = state(identity);
                                        var before = state.auth().getAdminProfile();
                                        if (before.getVersion() != expectedVersion)
                                            return new AdminAccessPreflight(
                                                    null, AdminAccessService.profile(user, before));
                                        var target = target(state.auth());
                                        var grants = new ArrayList<>(target.getDesiredGrantsList());
                                        var old = grant(grants, "ADMIN");
                                        if (old != null || !revoke)
                                            replace(
                                                    grants,
                                                    (old == null
                                                                    ? AdmissionGrant.newBuilder()
                                                                            .setRole("ADMIN")
                                                                    : old.toBuilder())
                                                            .setStatus(
                                                                    revoke ? "ARCHIVED" : "ACTIVE")
                                                            .build());
                                        var profile =
                                                AdmissionAdminProfile.newBuilder()
                                                        .setAssigned(!revoke)
                                                        .setExpectedVersion(expectedVersion);
                                        if (!revoke)
                                            profile.setMode(request.mode())
                                                    .addAllPermissions(
                                                            AdminPermission.validate(
                                                                            request.mode(),
                                                                            request.permissions())
                                                                    .stream()
                                                                    .distinct()
                                                                    .sorted()
                                                                    .toList());
                                        target.clearDesiredGrants()
                                                .addAllDesiredGrants(canonicalGrants(grants))
                                                .setDesiredAdminProfile(profile);
                                        return new AdminAccessPreflight(
                                                accepted(
                                                        command(
                                                                id,
                                                                IdentityOperationKind
                                                                        .IDENTITY_OPERATION_KIND_ADMIN_ACCESS,
                                                                target),
                                                        local(
                                                                intent,
                                                                state.profile(),
                                                                List.of(),
                                                                null,
                                                                null,
                                                                "admin-access")),
                                                null);
                                    });
                    if (preflight.conflictProfile() != null) {
                        // A machine snapshot never admits disclosure. Recheck after the read and
                        // outside
                        // the local transaction before exposing the current profile in the original
                        // 409.
                        identities.checkAdminAccess(List.of(), true, deadline());
                        return AcademicIdentityHttpOperations.localPreflight(
                                () -> {
                                    throw new AdminAccessException(
                                            HttpStatus.CONFLICT,
                                            "ADMIN_ACCESS_CONFLICT",
                                            preflight.conflictProfile());
                                });
                    }
                    return preflight.command();
                });
    }

    private record AdminAccessPreflight(
            AcademicIdentityCoordinator.AcceptedCommand command,
            ru.rutcampustrack.shared.security.admin.AdminAccessProfile conflictProfile) {}

    private record AdminAccessIntent(
            AdminAccessInput input, Long expectedVersion, boolean revoke) {}

    public AcademicIdentityCoordinator.Operation transfer(
            long user, TransferStudentRequest request) {
        var intent = intent("TRANSFER", user, request);
        return existing(
                intent,
                user,
                () -> validateTransfer(request),
                (id, state) -> {
                    var target = target(state.auth());
                    var grants = new ArrayList<>(target.getDesiredGrantsList());
                    var student = grant(grants, "STUDENT");
                    if (student == null
                            || !student.getStatus().equals("ACTIVE")
                            || !student.hasGroupId())
                        throw new BadRequestException(
                                "Перевод доступен только для активного STUDENT");
                    long source = student.getGroupId(), destination = request.newGroupId();
                    if (source == destination)
                        throw new BadRequestException("Новая группа должна отличаться от текущей");
                    var groups =
                            List.of(
                                            group(source, LocalDate.now(MOSCOW)),
                                            group(destination, LocalDate.now(MOSCOW)))
                                    .stream()
                                    .sorted(
                                            java.util.Comparator.comparingLong(
                                                    AcademicIdentityLocalCommand.GroupRevision
                                                            ::groupId))
                                    .toList();
                    replace(grants, student.toBuilder().setGroupId(destination).build());
                    suspendHeadman(grants);
                    target.clearDesiredGrants().addAllDesiredGrants(canonicalGrants(grants));
                    var local =
                            new AcademicIdentityLocalCommand(
                                    intent,
                                    null,
                                    LocalDate.now(MOSCOW),
                                    null,
                                    source,
                                    destination,
                                    null,
                                    null,
                                    null,
                                    null,
                                    request.reason(),
                                    groups);
                    return accepted(
                            command(
                                    id,
                                    IdentityOperationKind.IDENTITY_OPERATION_KIND_TRANSFER,
                                    target),
                            local);
                });
    }

    public void archive(long user, UserArchiveModels.ArchiveRequest request) {
        if (request == null || request.operationId() == null)
            throw new BadRequestException("Требуется UUID операции");
        // Password is intentionally absent from the durable API intent and both command hashes.
        var intent = intent("ARCHIVE", user, new ArchiveIntent(request.previewDigest()));
        http.execute(
                request.operationId(),
                intent,
                id -> {
                    if (request.previewDigest() == null
                            || !request.previewDigest().matches("[0-9a-f]{64}"))
                        throw new BadRequestException(
                                "Требуется действующее подтверждение preview");

                    var identity =
                            identities.readServiceIdentity(List.of(user), deadline()).get(user);
                    var material =
                            localSnapshot(
                                    tx -> {
                                        var state = state(identity);
                                        var impact = archives.observe(user);
                                        archives.checkArchiveSafeguards(context.getUserId(), user);
                                        archives.validatePreview(
                                                context.getUserId(),
                                                user,
                                                request.previewDigest(),
                                                impact);
                                        var groups =
                                                archives.cacheGroups(user).stream()
                                                        .distinct()
                                                        .sorted()
                                                        .map(this::existingGroup)
                                                        .toList();
                                        return new ArchiveMaterial(state, impact, groups);
                                    });
                    String proof =
                            material.impact().requiresPassword()
                                    ? archiveProof.confirm(
                                            user, id, request.previewDigest(), request.password())
                                    : "";
                    var target = target(material.state().auth());
                    var grants =
                            target.getDesiredGrantsList().stream()
                                    .map(g -> g.toBuilder().setStatus("ARCHIVED").build())
                                    .toList();
                    var old = target.getDesiredAdminProfile();
                    target.clearDesiredGrants()
                            .addAllDesiredGrants(canonicalGrants(grants))
                            .setDesiredAccountState(
                                    IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED)
                            .setDesiredAdminProfile(
                                    AdmissionAdminProfile.newBuilder()
                                            .setExpectedVersion(old.getExpectedVersion()));
                    var auth =
                            command(
                                            id,
                                            IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE,
                                            target)
                                    .setPasswordStepUpReceiptId(proof)
                                    .setArchivePreviewDigest(request.previewDigest())
                                    .setArchiveStepUpProtocolVersion(1);
                    var local =
                            new AcademicIdentityLocalCommand(
                                    intent,
                                    null,
                                    LocalDate.now(MOSCOW),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    "archive",
                                    material.groups(),
                                    new AcademicIdentityLocalCommand.ArchiveAdmission(
                                            request.previewDigest(),
                                            material.impact().digest(),
                                            material.impact().requiresPassword()));
                    return accepted(auth, local);
                });
    }

    public void restore(long user, UserArchiveModels.RestoreRequest request) {
        if (request == null || request.operationId() == null)
            throw new BadRequestException("Требуется UUID операции");
        var intent = intent("RESTORE", user, new RestoreIntent(user));
        http.execute(
                request.operationId(),
                intent,
                id -> {
                    var identity =
                            identities.readServiceIdentity(List.of(user), deadline()).get(user);
                    return localSnapshot(
                            tx -> {
                                var state = state(identity, true);
                                var target = target(state.auth());
                                if (target.getDesiredGrantsList().stream()
                                        .anyMatch(g -> !g.getStatus().equals("ARCHIVED")))
                                    throw Status.FAILED_PRECONDITION
                                            .withDescription("archived grants required")
                                            .asRuntimeException();
                                var groups =
                                        archives.cacheGroups(user).stream()
                                                .distinct()
                                                .sorted()
                                                .map(this::existingGroup)
                                                .toList();
                                return accepted(
                                        command(
                                                id,
                                                IdentityOperationKind
                                                        .IDENTITY_OPERATION_KIND_RESTORE,
                                                target),
                                        local(intent, null, groups, null, null, "restore"));
                            });
                });
    }

    private record ArchiveIntent(String previewDigest) {}

    private record RestoreIntent(long userId) {}

    private record ArchiveMaterial(
            State state,
            UserArchiveRepository.Local impact,
            List<AcademicIdentityLocalCommand.GroupRevision> groups) {}

    public void patch(long user, PatchUserRequest request) {
        if (request == null) throw new BadRequestException("Данные пользователя обязательны");
        if (Boolean.TRUE.equals(request.isHeadman())) {
            headman(null, user, null, false, request);
            return;
        }
        var intent = intent("PROFILE", user, request);
        existing(
                intent,
                user,
                (id, state) -> {
                    if (request.status() == AccountStatus.ARCHIVED)
                        throw new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.CONFLICT, "protectedroute_required");

                    var target = target(state.auth());
                    var grants = new ArrayList<>(target.getDesiredGrantsList());
                    var profile = patchProfile(state, request);
                    var student = grant(grants, "STUDENT");
                    Long displayGroup =
                            state.directoryRole().equals("STUDENT")
                                            && student != null
                                            && student.hasGroupId()
                                            && List.of("ACTIVE", "SUSPENDED")
                                                    .contains(student.getStatus())
                                    ? student.getGroupId()
                                    : null;
                    if (request.groupId() != null
                            && !Objects.equals(displayGroup, request.groupId()))
                        throw new BadRequestException(
                                "Изменение группы пользователя выполняется только через перевод");
                    if (Boolean.FALSE.equals(request.isHeadman())
                            && student != null
                            && student.hasGroupId()) {
                        fences.requireGroupReadable(student.getGroupId());
                        if (Objects.equals(currentHeadman(student.getGroupId()), user))
                            throw new ru.rutcampustrack.academic.exception.ConflictException(
                                    "isHeadman",
                                    false,
                                    "Снять действующего старосту можно только заменой на другого"
                                            + " студента");
                    }
                    boolean changed = false;
                    if (request.status() != null && request.status() != AccountStatus.ACTIVE) {
                        var base = grant(grants, state.directoryRole());
                        if (base == null
                                || !List.of("STUDENT", "TEACHER").contains(base.getRole())
                                || base.getRole().equals("TEACHER")
                                        && request.status() == AccountStatus.EXPELLED)
                            throw new BadRequestException(
                                    "Статус не применим к роли " + state.directoryRole());
                        replace(
                                grants,
                                base.toBuilder().setStatus(request.status().name()).build());
                        changed = true;
                        if (base.getRole().equals("STUDENT")) suspendHeadman(grants);
                    }
                    if (request.telegramId() != null) {
                        if (request.telegramId() <= 0)
                            throw new BadRequestException("Telegram ID должен быть положительным");
                        target.setTelegramChange(
                                TelegramBindingChange.newBuilder()
                                        .setTelegramId(request.telegramId()));
                    }
                    target.clearDesiredGrants().addAllDesiredGrants(canonicalGrants(grants));
                    var kind =
                            request.telegramId() != null && !changed
                                    ? IdentityOperationKind.IDENTITY_OPERATION_KIND_TELEGRAM_BINDING
                                    : IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE;
                    var groups =
                            student != null && student.hasGroupId()
                                    ? List.of(existingGroup(student.getGroupId()))
                                    : List.<AcademicIdentityLocalCommand.GroupRevision>of();
                    return accepted(
                            command(id, kind, target),
                            local(intent, profile, groups, null, null, "profile-update"));
                });
    }

    public void replaceProfile(long user, UpdateUserRequest request) {
        if (request == null || request.role() == null)
            throw new BadRequestException("Данные пользователя обязательны");
        var intent = intent("PROFILE", user, request);
        existing(
                intent,
                user,
                (id, state) -> {
                    var target = target(state.auth());
                    if (!state.directoryRole().equals(request.role().name()))
                        throw new BadRequestException(
                                "Изменение роли пользователя требует управляемой истории членства");
                    var student = grant(target.getDesiredGrantsList(), "STUDENT");
                    Long displayGroup =
                            state.directoryRole().equals("STUDENT")
                                            && student != null
                                            && student.hasGroupId()
                                            && List.of("ACTIVE", "SUSPENDED")
                                                    .contains(student.getStatus())
                                    ? student.getGroupId()
                                    : null;
                    if (!Objects.equals(displayGroup, request.groupId()))
                        throw new BadRequestException(
                                "Изменение группы пользователя выполняется только через перевод");
                    var p = state.profile();
                    var profile =
                            new AcademicIdentityLocalCommand.Profile(
                                    user,
                                    request.lastName(),
                                    request.firstName(),
                                    request.middleName(),
                                    p.email(),
                                    p.phone(),
                                    request.employeeNumber(),
                                    p.avatarId(),
                                    p.directoryStatus());
                    if (target.getDesiredGrantsList().stream()
                                    .anyMatch(g -> g.getRole().equals("TEACHER"))
                            && blankToNull(profile.employeeNumber()) == null)
                        throw new BadRequestException(
                                "Для преподавателя табельный номер обязателен");
                    var binding = TelegramBindingChange.newBuilder();
                    if (request.telegramId() != null) {
                        if (request.telegramId() <= 0)
                            throw new BadRequestException("Telegram ID должен быть положительным");
                        binding.setTelegramId(request.telegramId());
                    }
                    target.setTelegramChange(binding);
                    var groups =
                            student != null && student.hasGroupId()
                                    ? List.of(existingGroup(student.getGroupId()))
                                    : List.<AcademicIdentityLocalCommand.GroupRevision>of();
                    return accepted(
                            command(
                                    id,
                                    IdentityOperationKind.IDENTITY_OPERATION_KIND_TELEGRAM_BINDING,
                                    target),
                            local(intent, profile, groups, null, null, "profile-update"));
                });
    }

    public HeadmanAssignmentResponse headman(
            Long requestedGroup,
            long candidate,
            Long expected,
            boolean enforceCas,
            PatchUserRequest legacy) {
        if (candidate <= 0 || requestedGroup != null && requestedGroup <= 0)
            throw new BadRequestException("ID студента/группы должен быть положительным");
        var intent =
                intent(
                                "HEADMAN_REPLACE",
                                candidate,
                                new HeadmanIntent(requestedGroup, expected, enforceCas, legacy))
                        .withAdmissionPermission(legacy == null ? "GROUPS_MANAGE" : "USERS_MANAGE");
        var operation =
                http.execute(
                        intent,
                        id -> {
                            if (legacy != null
                                    && legacy.status() != null
                                    && legacy.status()
                                            != ru.rutcampustrack.academic.contract.enums
                                                    .AccountStatus.ACTIVE)
                                throw new BadRequestException(
                                        "Старостой может быть только активный студент");

                            var candidateIdentity =
                                    identities
                                            .readServiceIdentity(List.of(candidate), deadline())
                                            .get(candidate);
                            var discovery =
                                    localSnapshot(
                                            tx -> {
                                                var state = state(candidateIdentity);
                                                var student =
                                                        grant(
                                                                target(state.auth())
                                                                        .getDesiredGrantsList(),
                                                                "STUDENT");
                                                if (student == null
                                                        || !student.getStatus().equals("ACTIVE")
                                                        || !student.hasGroupId())
                                                    throw new BadRequestException(
                                                            "Старостой может быть только активный"
                                                                    + " студент");
                                                long group = student.getGroupId();
                                                if (requestedGroup != null
                                                        && requestedGroup != group)
                                                    throw new ru.rutcampustrack.academic.exception
                                                            .ConflictException(
                                                            "studentId",
                                                            candidate,
                                                            "Студент должен состоять в выбранной"
                                                                    + " группе");
                                                if (legacy != null
                                                        && legacy.groupId() != null
                                                        && legacy.groupId() != group)
                                                    throw new BadRequestException(
                                                            "Студент должен состоять в выбранной"
                                                                    + " группе");
                                                var revision = activeGroup(group);
                                                Long current = currentHeadman(group);
                                                if (enforceCas
                                                        && !Objects.equals(expected, current))
                                                    throw Status.ABORTED
                                                            .withDescription("headman changed")
                                                            .asRuntimeException();
                                                return new HeadmanDiscovery(
                                                        group, current, revision);
                                            });
                            var ids = new ArrayList<Long>();
                            ids.add(candidate);
                            if (discovery.current() != null && discovery.current() != candidate)
                                ids.add(discovery.current());
                            ids.sort(Long::compareTo);
                            var snapshots = identities.readServiceIdentity(ids, deadline());
                            return localSnapshot(
                                    tx -> {
                                        var group = activeGroup(discovery.group());
                                        if (!group.equals(discovery.revision())
                                                || !Objects.equals(
                                                        currentHeadman(discovery.group()),
                                                        discovery.current()))
                                            throw Status.ABORTED
                                                    .withDescription("headman changed")
                                                    .asRuntimeException();
                                        var auth =
                                                PrepareIdentityOperationRequest.newBuilder()
                                                        .setOperationId(id.toString())
                                                        .setKind(
                                                                IdentityOperationKind
                                                                        .IDENTITY_OPERATION_KIND_HEADMAN_REPLACE);
                                        AcademicIdentityLocalCommand.Profile candidateProfile =
                                                null;
                                        boolean noopCandidate =
                                                Objects.equals(discovery.current(), candidate);
                                        for (long user : ids) {
                                            var state = state(snapshots.get(user));
                                            var target = target(state.auth());
                                            var unchangedGrants =
                                                    List.copyOf(target.getDesiredGrantsList());
                                            var grants =
                                                    new ArrayList<>(target.getDesiredGrantsList());
                                            if (user == candidate) {
                                                var student = grant(grants, "STUDENT");
                                                if (student == null
                                                        || !student.getStatus().equals("ACTIVE")
                                                        || !student.hasGroupId()
                                                        || student.getGroupId()
                                                                != discovery.group())
                                                    throw Status.ABORTED.asRuntimeException();
                                                var old = grant(grants, "HEADMAN");
                                                replace(
                                                        grants,
                                                        (old == null
                                                                        ? AdmissionGrant
                                                                                .newBuilder()
                                                                                .setRole("HEADMAN")
                                                                        : old.toBuilder())
                                                                .setStatus("ACTIVE")
                                                                .setGroupId(discovery.group())
                                                                .build());
                                                candidateProfile =
                                                        legacy == null
                                                                ? state.profile()
                                                                : patchProfile(state, legacy);
                                                if (legacy != null && legacy.telegramId() != null) {
                                                    if (legacy.telegramId() <= 0)
                                                        throw new BadRequestException(
                                                                "Telegram ID должен быть"
                                                                        + " положительным");
                                                    target.setTelegramChange(
                                                            TelegramBindingChange.newBuilder()
                                                                    .setTelegramId(
                                                                            legacy.telegramId()));
                                                }
                                            } else suspendHeadman(grants);
                                            target.clearDesiredGrants()
                                                    .addAllDesiredGrants(canonicalGrants(grants));
                                            if (user == candidate)
                                                noopCandidate &=
                                                        candidateProfile.equals(state.profile())
                                                                && !target.hasTelegramChange()
                                                                && target.getDesiredGrantsList()
                                                                        .equals(unchangedGrants);
                                            auth.addTargets(target);
                                        }
                                        auth.setHeadmanNoopCandidate(noopCandidate);
                                        var local =
                                                new AcademicIdentityLocalCommand(
                                                        intent,
                                                        candidateProfile,
                                                        LocalDate.now(MOSCOW),
                                                        null,
                                                        null,
                                                        null,
                                                        discovery.group(),
                                                        discovery.current(),
                                                        candidate,
                                                        null,
                                                        "headman-replacement",
                                                        List.of(group));
                                        return accepted(auth, local);
                                    });
                        });
        var rows =
                jdbc.query(
                        "SELECT local_payload,headman_assistants_revoked FROM"
                            + " academic_identity_operations WHERE operation_id=? AND actor_id=?"
                            + " AND phase='SUCCEEDED'",
                        (rs, n) ->
                                new HeadmanStored(
                                        rs.getBytes("local_payload"),
                                        rs.getObject("headman_assistants_revoked", Integer.class)),
                        UUID.fromString(operation.id()),
                        context.getUserId());
        if (rows.size() != 1 || rows.getFirst().revoked() == null)
            throw Status.UNAVAILABLE.asRuntimeException();
        AcademicIdentityLocalCommand local;
        try {
            local =
                    canonical.readValue(
                            rows.getFirst().payload(), AcademicIdentityLocalCommand.class);
        } catch (java.io.IOException invalid) {
            throw Status.UNAVAILABLE.asRuntimeException();
        }
        var profile = local.profile();
        String fio =
                java.util.stream.Stream.of(
                                profile.lastName(), profile.firstName(), profile.middleName())
                        .filter(v -> v != null && !v.isBlank())
                        .collect(java.util.stream.Collectors.joining(" "));
        return new HeadmanAssignmentResponse(
                local.headmanGroupId(),
                local.candidateUserId(),
                fio,
                !Objects.equals(local.expectedHeadmanId(), local.candidateUserId()),
                local.expectedHeadmanId() == null,
                rows.getFirst().revoked());
    }

    private Long currentHeadman(long group) {
        var ids =
                jdbc.queryForList(
                        "SELECT user_id FROM academic_role_eligibility WHERE group_id=? AND"
                                + " role='HEADMAN' AND status='ACTIVE' ORDER BY user_id",
                        Long.class,
                        group);
        if (ids.size() > 1) throw Status.FAILED_PRECONDITION.asRuntimeException();
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private AcademicIdentityLocalCommand.GroupRevision activeGroup(long group) {
        var revision = existingGroup(group);
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT is_active FROM groups WHERE id=?", Boolean.class, group)))
            throw Status.FAILED_PRECONDITION
                    .withDescription("active group required")
                    .asRuntimeException();
        return revision;
    }

    private AcademicIdentityLocalCommand.Profile patchProfile(
            State state, PatchUserRequest request) {
        var p = state.profile();
        String employee =
                request.employeeNumber() == null
                        ? p.employeeNumber()
                        : blankToNull(request.employeeNumber().trim());
        boolean teacher =
                state.auth().getGrantsList().stream().anyMatch(g -> g.getRole().equals("TEACHER"));
        if (teacher && employee == null)
            throw new BadRequestException(
                    "employeeNumber", "Для преподавателя табельный номер обязателен");
        if (!Objects.equals(employee, p.employeeNumber()))
            requireEmployeeAvailable(employee, p.userId());
        return new AcademicIdentityLocalCommand.Profile(
                p.userId(),
                request.lastName() == null
                        ? p.lastName()
                        : requiredName("lastName", request.lastName()),
                request.firstName() == null
                        ? p.firstName()
                        : requiredName("firstName", request.firstName()),
                request.middleName() == null
                        ? p.middleName()
                        : blankToNull(request.middleName().trim()),
                p.email(),
                p.phone(),
                employee,
                p.avatarId(),
                request.status() == null ? p.directoryStatus() : request.status().name());
    }

    private void requireEmployeeAvailable(String employee, long userId) {
        if (employee != null
                && Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM academic_profiles WHERE"
                                        + " employee_number=? AND id<>?)",
                                Boolean.class,
                                employee,
                                userId)))
            throw new ru.rutcampustrack.academic.exception.ConflictException(
                    "employeeNumber", employee, "Табельный номер уже используется");
    }

    private static String requiredName(String field, String value) {
        if (value == null || value.isBlank())
            throw new BadRequestException(field, "Обязательное поле не может быть пустым");
        return value.trim();
    }

    private <T> T localSnapshot(
            org.springframework.transaction.support.TransactionCallback<T> work) {
        return AcademicIdentityHttpOperations.localPreflight(() -> snapshot.execute(work));
    }

    private static long deadline() {
        return System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    }

    private record HeadmanIntent(
            Long group, Long expected, boolean enforceCas, PatchUserRequest legacy) {}

    private record HeadmanDiscovery(
            long group, Long current, AcademicIdentityLocalCommand.GroupRevision revision) {}

    private record HeadmanStored(byte[] payload, Integer revoked) {}

    private static void validateRole(String role, RoleGrantUpdateRequest request) {
        if (!List.of("STUDENT", "TEACHER").contains(role))
            throw new BadRequestException("Роль нельзя изменить через этот endpoint");
        if (request == null
                || request.status() == null
                || request.status() == RoleGrantStatus.ARCHIVED
                || !(role.equals("STUDENT")
                                ? List.of("ACTIVE", "SUSPENDED", "EXPELLED", "GRADUATED")
                                : List.of("ACTIVE", "SUSPENDED", "DISMISSED"))
                        .contains(request.status().name()))
            throw new BadRequestException("Статус не применим к роли " + role);
        if (role.equals("TEACHER") && request.groupId() != null)
            throw new BadRequestException("groupId", "Данные не применимы к роли " + role);
        if (request.groupId() != null && request.groupId() <= 0
                || request.telegramId() != null && request.telegramId() <= 0)
            throw new BadRequestException("ID должен быть положительным");
        if (role.equals("STUDENT") && blankToNull(request.employeeNumber()) != null
                || role.equals("TEACHER") && request.telegramId() != null)
            throw new BadRequestException("Данные не применимы к роли " + role);
    }

    private static void validateTransfer(TransferStudentRequest request) {
        if (request == null
                || request.newGroupId() == null
                || request.newGroupId() <= 0
                || request.reason() == null
                || request.reason().isBlank()
                || request.reason().length() > 1000)
            throw new BadRequestException("Требуются новая группа и причина перевода");
    }

    private AcademicIdentityCoordinator.Operation existing(
            AcademicIdentityLocalCommand.ApiIntent intent,
            long user,
            BiFunction<UUID, State, AcademicIdentityCoordinator.AcceptedCommand> build) {
        return existing(intent, user, () -> {}, build);
    }

    private AcademicIdentityCoordinator.Operation existing(
            AcademicIdentityLocalCommand.ApiIntent intent,
            long user,
            Runnable validate,
            BiFunction<UUID, State, AcademicIdentityCoordinator.AcceptedCommand> build) {
        if (user <= 0) throw new BadRequestException("ID пользователя должен быть положительным");
        return http.execute(
                intent,
                id -> {
                    validate.run();
                    var identity =
                            identities
                                    .readServiceIdentity(
                                            List.of(user),
                                            System.nanoTime()
                                                    + java.util.concurrent.TimeUnit.SECONDS.toNanos(
                                                            3))
                                    .get(user);
                    return localSnapshot(tx -> build.apply(id, state(identity)));
                });
    }

    private State state(IdentityAuthoritySnapshot identity) {
        return state(identity, false);
    }

    private State state(IdentityAuthoritySnapshot identity, boolean archived) {
        if (identity == null
                || identity.getAccountState()
                        != (archived
                                ? AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ARCHIVED
                                : AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE))
            throw Status.FAILED_PRECONDITION
                    .withDescription("identity account state changed")
                    .asRuntimeException();
        fences.requireUserReadable(identity.getUserId());
        var profiles =
                jdbc.query(
                        "SELECT"
                            + " directory_role,directory_status,last_name,first_name,middle_name,email,phone,employee_number,avatar_id,academic_revision,applied_auth_roles_version,archived_at"
                            + " FROM academic_profiles WHERE id=?",
                        (rs, n) ->
                                new State(
                                        identity,
                                        new AcademicIdentityLocalCommand.Profile(
                                                identity.getUserId(),
                                                rs.getString("last_name"),
                                                rs.getString("first_name"),
                                                rs.getString("middle_name"),
                                                rs.getString("email"),
                                                rs.getString("phone"),
                                                rs.getString("employee_number"),
                                                rs.getString("avatar_id"),
                                                rs.getString("directory_status")),
                                        rs.getString("directory_role"),
                                        rs.getLong("academic_revision"),
                                        rs.getLong("applied_auth_roles_version"),
                                        rs.getObject("archived_at") != null),
                        identity.getUserId());
        if (profiles.size() != 1) throw Status.NOT_FOUND.asRuntimeException();
        var result = profiles.getFirst();
        if (result.revision() != identity.getAcademicRevision()
                || result.rv() != identity.getRolesVersion()
                || result.archived() != archived)
            throw Status.ABORTED.withDescription("identity revision changed").asRuntimeException();
        return result;
    }

    private IdentityTargetCommand.Builder target(IdentityAuthoritySnapshot state) {
        var profile = state.getAdminProfile();
        return IdentityTargetCommand.newBuilder()
                .setUserId(state.getUserId())
                .setExpectedRolesVersion(state.getRolesVersion())
                .setExpectedCredentialsVersion(state.getCredentialsVersion())
                .setExpectedTelegramBindingVersion(state.getTelegramBindingVersion())
                .setExpectedAcademicRevision(state.getAcademicRevision())
                .setDesiredAccountState(IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                .setDesiredAdminProfile(
                        AdmissionAdminProfile.newBuilder()
                                .setAssigned(profile.getAssigned())
                                .setMode(profile.getMode())
                                .addAllPermissions(
                                        profile.getPermissionsList().stream()
                                                .distinct()
                                                .sorted()
                                                .toList())
                                .setExpectedVersion(profile.getVersion()))
                .addAllDesiredGrants(
                        canonicalGrants(
                                state.getGrantsList().stream()
                                        .map(
                                                g -> {
                                                    var grant =
                                                            AdmissionGrant.newBuilder()
                                                                    .setGrantId(g.getGrantId())
                                                                    .setRole(g.getRole())
                                                                    .setStatus(g.getStatus());
                                                    if (g.hasGroupId())
                                                        grant.setGroupId(g.getGroupId());
                                                    return grant.build();
                                                })
                                        .toList()));
    }

    private PrepareIdentityOperationRequest.Builder command(
            UUID id, IdentityOperationKind kind, IdentityTargetCommand.Builder target) {
        return PrepareIdentityOperationRequest.newBuilder()
                .setOperationId(id.toString())
                .setKind(kind)
                .addTargets(target);
    }

    private AcademicIdentityLocalCommand local(
            AcademicIdentityLocalCommand.ApiIntent intent,
            AcademicIdentityLocalCommand.Profile profile,
            List<AcademicIdentityLocalCommand.GroupRevision> groups,
            Long semester,
            LocalDate initial,
            String reason) {
        return new AcademicIdentityLocalCommand(
                intent,
                profile,
                LocalDate.now(MOSCOW),
                initial,
                null,
                null,
                null,
                null,
                null,
                semester,
                reason,
                groups.stream()
                        .distinct()
                        .sorted(
                                java.util.Comparator.comparingLong(
                                        AcademicIdentityLocalCommand.GroupRevision::groupId))
                        .toList());
    }

    private AcademicIdentityLocalCommand.GroupRevision existingGroup(long id) {
        fences.requireGroupReadable(id);
        var revisions =
                jdbc.queryForList(
                        "SELECT academic_revision FROM groups WHERE id=?", Long.class, id);
        if (revisions.size() != 1)
            throw Status.FAILED_PRECONDITION
                    .withDescription("group not found")
                    .asRuntimeException();
        return new AcademicIdentityLocalCommand.GroupRevision(id, revisions.getFirst());
    }

    private AcademicIdentityLocalCommand.GroupRevision group(long id, LocalDate date) {
        fences.requireGroupReadable(id);
        var rows =
                jdbc.query(
                        "SELECT g.academic_revision,c.coverage_from,c.writer_version FROM groups g"
                            + " JOIN group_history_coverage c ON c.group_id=g.id WHERE g.id=? AND"
                            + " g.is_active",
                        (rs, n) ->
                                new Coverage(
                                        rs.getLong("academic_revision"),
                                        rs.getObject("coverage_from", LocalDate.class),
                                        rs.getString("writer_version")),
                        id);
        if (rows.size() != 1
                || !"managed_v1".equals(rows.getFirst().writer())
                || rows.getFirst().from() == null
                || rows.getFirst().from().isAfter(date))
            throw Status.FAILED_PRECONDITION
                    .withDescription("managed group history required")
                    .asRuntimeException();
        return new AcademicIdentityLocalCommand.GroupRevision(id, rows.getFirst().revision());
    }

    private static List<AdmissionGrant> canonicalGrants(List<AdmissionGrant> grants) {
        return grants.stream()
                .sorted(java.util.Comparator.comparing(AdmissionGrant::getRole))
                .toList();
    }

    private static AdmissionGrant grant(List<AdmissionGrant> grants, String role) {
        return grants.stream().filter(g -> g.getRole().equals(role)).findFirst().orElse(null);
    }

    private static void replace(List<AdmissionGrant> grants, AdmissionGrant value) {
        grants.removeIf(g -> g.getRole().equals(value.getRole()));
        grants.add(value);
    }

    private static void suspendHeadman(List<AdmissionGrant> grants) {
        var old = grant(grants, "HEADMAN");
        if (old != null && old.getStatus().equals("ACTIVE"))
            replace(grants, old.toBuilder().setStatus("SUSPENDED").build());
    }

    private static AcademicIdentityLocalCommand.Profile withEmployee(
            AcademicIdentityLocalCommand.Profile p, String employee) {
        return new AcademicIdentityLocalCommand.Profile(
                p.userId(),
                p.lastName(),
                p.firstName(),
                p.middleName(),
                p.email(),
                p.phone(),
                employee,
                p.avatarId(),
                p.directoryStatus());
    }

    private static AcademicIdentityLocalCommand.Profile withDirectoryStatus(
            AcademicIdentityLocalCommand.Profile p, String status) {
        return new AcademicIdentityLocalCommand.Profile(
                p.userId(),
                p.lastName(),
                p.firstName(),
                p.middleName(),
                p.email(),
                p.phone(),
                p.employeeNumber(),
                p.avatarId(),
                status);
    }

    private record RoleIntent(String role, RoleGrantUpdateRequest request) {}

    private record State(
            IdentityAuthoritySnapshot auth,
            AcademicIdentityLocalCommand.Profile profile,
            String directoryRole,
            long revision,
            long rv,
            boolean archived) {}

    private Enrollment enrollment(long group) {
        fences.requireGroupReadable(group);
        var semesters =
                jdbc.query(
                        "SELECT id,date_from,date_to FROM semesters WHERE is_active AND NOT"
                                + " is_archived AND archive_transition='NONE' AND NOT"
                                + " archive_release_pending ORDER BY id LIMIT 2",
                        (rs, n) ->
                                new Semester(
                                        rs.getLong("id"),
                                        rs.getObject("date_from", LocalDate.class),
                                        rs.getObject("date_to", LocalDate.class)));
        if (semesters.size() != 1
                || semesters.getFirst().from() == null
                || semesters.getFirst().to() == null
                || semesters.getFirst().from().isAfter(semesters.getFirst().to()))
            throw Status.FAILED_PRECONDITION.asRuntimeException();
        var semester = semesters.getFirst();
        var groups =
                jdbc.query(
                        "SELECT g.academic_revision,c.coverage_from,c.writer_version FROM groups g"
                            + " JOIN group_history_coverage c ON c.group_id=g.id WHERE g.id=? AND"
                            + " g.is_active",
                        (rs, n) ->
                                new Coverage(
                                        rs.getLong("academic_revision"),
                                        rs.getObject("coverage_from", LocalDate.class),
                                        rs.getString("writer_version")),
                        group);
        if (groups.size() != 1
                || !"managed_v1".equals(groups.getFirst().writer())
                || groups.getFirst().from() == null
                || groups.getFirst().from().isAfter(semester.from()))
            throw Status.FAILED_PRECONDITION.asRuntimeException();
        return new Enrollment(
                semester.id(),
                semester.from(),
                new AcademicIdentityLocalCommand.GroupRevision(
                        group, groups.getFirst().revision()));
    }

    private AcademicIdentityCoordinator.AcceptedCommand accepted(
            PrepareIdentityOperationRequest.Builder auth, AcademicIdentityLocalCommand local) {
        auth.setCommandHash(hash(auth.clearCommandHash().build().toByteArray()));
        byte[] payload = bytes(local);
        return new AcademicIdentityCoordinator.AcceptedCommand(
                auth.build(), hash(payload), ByteString.copyFrom(payload));
    }

    private AcademicIdentityLocalCommand.ApiIntent intent(
            String action, long target, Object nonsecretRequest) {
        return new AcademicIdentityLocalCommand.ApiIntent(
                action,
                target,
                HexFormat.of().formatHex(hash(bytes(nonsecretRequest)).toByteArray()));
    }

    private byte[] bytes(Object value) {
        try {
            return canonical.writeValueAsBytes(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("canonical nonsecret command required")
                    .asRuntimeException();
        }
    }

    private static ByteString hash(byte[] value) {
        try {
            return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void validate(CreateUserRequest request) {
        if (request == null || request.role() == null)
            throw new BadRequestException("role", "Роль обязательна");
        requiredName("lastName", request.lastName());
        requiredName("firstName", request.firstName());
        boolean employee = blankToNull(request.employeeNumber()) != null;
        if (request.telegramId() != null && request.telegramId() <= 0)
            throw new BadRequestException("telegramId", "Telegram ID должен быть положительным");
        switch (request.role()) {
            case STUDENT -> {
                if (request.groupId() == null || request.groupId() <= 0)
                    throw new BadRequestException("groupId", "Группа обязательна для студента");
                if (request.telegramId() == null)
                    throw new BadRequestException(
                            "telegramId", "Telegram ID обязателен для студента");
                if (employee)
                    throw new BadRequestException(
                            "employeeNumber", "Табельный номер не применим к роли STUDENT");
            }
            case TEACHER -> {
                if (request.groupId() != null)
                    throw new BadRequestException("groupId", "Группа не применима к роли TEACHER");
                if (!employee)
                    throw new BadRequestException(
                            "employeeNumber", "Табельный номер обязателен для преподавателя");
            }
            case ADMIN -> {
                if (request.groupId() != null)
                    throw new BadRequestException("groupId", "Группа не применима к роли ADMIN");
                if (employee)
                    throw new BadRequestException(
                            "employeeNumber", "Табельный номер не применим к роли ADMIN");
                if (request.adminAccess() == null)
                    throw new BadRequestException(
                            "adminAccess", "Для ADMIN нужен явный профиль доступа");
            }
        }
        if (request.role() != UserRole.ADMIN && request.adminAccess() != null)
            throw new BadRequestException("adminAccess", "Профиль доступа применим только к ADMIN");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Semester(long id, LocalDate from, LocalDate to) {}

    private record Coverage(long revision, LocalDate from, String writer) {}

    private record Enrollment(
            long semester, LocalDate date, AcademicIdentityLocalCommand.GroupRevision group) {}
}
