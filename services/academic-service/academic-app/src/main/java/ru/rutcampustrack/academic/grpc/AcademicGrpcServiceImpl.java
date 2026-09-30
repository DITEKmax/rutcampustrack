package ru.rutcampustrack.academic.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.homework.HomeworkStudentService;
import ru.rutcampustrack.academic.map.CampusMapReadException;
import ru.rutcampustrack.academic.map.CampusMapReadModels;
import ru.rutcampustrack.academic.map.CampusMapReadService;
import ru.rutcampustrack.academic.map.CampusMapUsageService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.AssignmentReplacementOperationRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionGrpcErrors;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionScope;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionScopeService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/**
 * gRPC service implementation for Academic Service.
 * Delegates cached read operations to AcademicReadService to ensure
 * Spring AOP @Cacheable proxy is not bypassed via self-invocation (per D-01).
 * Non-cached methods (getTeacherSubjects, isHeadman) query repositories directly.
 */
@GrpcService
public class AcademicGrpcServiceImpl extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    /**
     * M16 G7: rate-limit на isHeadman по userId, теперь через
     * {@link HeadmanRateLimiter} (инжектированный bean).
     *
     * <p>До M16 G7 был in-memory ConcurrentHashMap (M06 G8b). При horizontal
     * scale-out каждый pod видел свои bucket'ы → headman c N pods мог
     * делать N × limit calls/min. Сейчас limiter — Redis-backed (default)
     * либо in-memory fallback для тестов.
     *
     * <p>Limit вынесен в {@link HeadmanRateLimitProperties} — owner поднял
     * до 300/min на M16 (было 120/min, M15 staging show'ал что недостаточно
     * для bulk-mark группы из 30 студентов).
     *
     * <p>Per-user (не per-pair), чтобы attacker не мог обойти, подбирая
     * новые groupId для того же userId.
     */
    private final AcademicReadService academicReadService;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectGroupRepository legacyAssignmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SemesterRepository semesterRepository;
    private final UserRoleGrantRepository grantRepository;
    private final HomeworkRepository homeworkRepository;
    private final HomeworkCompletionRepository completionRepository;
    private final HeadmanRateLimiter headmanRateLimiter;
    private final HomeworkStudentService homeworkStudentService;
    private final CampusMapReadService campusMapReadService;
    private final CampusMapUsageService campusMapUsageService;
    private final StudentProjectionScopeService studentProjectionScopeService;
    private final AssistantPermissionAuthority assistantPermissionAuthority;
    private final AssignmentReplacementOperationRepository replacementOperationRepository;

    @Autowired
    public AcademicGrpcServiceImpl(
            AcademicReadService academicReadService,
            GroupRepository groupRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            AssignmentRepository assignmentRepository,
            SemesterRepository semesterRepository,
            UserRoleGrantRepository grantRepository,
            HomeworkRepository homeworkRepository,
            HomeworkCompletionRepository completionRepository,
            HeadmanRateLimiter headmanRateLimiter,
            HomeworkStudentService homeworkStudentService,
            StudentProjectionScopeService studentProjectionScopeService,
            CampusMapReadService campusMapReadService,
            CampusMapUsageService campusMapUsageService,
            AssistantPermissionAuthority assistantPermissionAuthority,
            AssignmentReplacementOperationRepository replacementOperationRepository) {
        this.academicReadService = academicReadService;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.legacyAssignmentRepository = null;
        this.assignmentRepository = assignmentRepository;
        this.semesterRepository = semesterRepository;
        this.grantRepository = grantRepository;
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.headmanRateLimiter = headmanRateLimiter;
        this.homeworkStudentService = homeworkStudentService;
        this.studentProjectionScopeService = studentProjectionScopeService;
        this.campusMapReadService = campusMapReadService;
        this.campusMapUsageService = campusMapUsageService;
        this.assistantPermissionAuthority = assistantPermissionAuthority;
        this.replacementOperationRepository = replacementOperationRepository;
    }

    /** Source-compatible constructor for focused tests predating assistant RPC. */
    public AcademicGrpcServiceImpl(
            AcademicReadService academicReadService,
            GroupRepository groupRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            AssignmentRepository assignmentRepository,
            SemesterRepository semesterRepository,
            UserRoleGrantRepository grantRepository,
            HomeworkRepository homeworkRepository,
            HomeworkCompletionRepository completionRepository,
            HeadmanRateLimiter headmanRateLimiter,
            HomeworkStudentService homeworkStudentService,
            StudentProjectionScopeService studentProjectionScopeService,
            CampusMapReadService campusMapReadService,
            CampusMapUsageService campusMapUsageService) {
        this(academicReadService, groupRepository, userRepository, subjectRepository,
                assignmentRepository, semesterRepository, grantRepository,
                homeworkRepository, completionRepository, headmanRateLimiter,
                homeworkStudentService, studentProjectionScopeService,
                campusMapReadService, campusMapUsageService, null, null);
    }

    /** Compatibility constructor retained for source-era assignment tests. */
    public AcademicGrpcServiceImpl(
            AcademicReadService academicReadService,
            GroupRepository groupRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            AssignmentRepository assignmentRepository,
            SemesterRepository semesterRepository,
            UserRoleGrantRepository grantRepository,
            HomeworkRepository homeworkRepository,
            HomeworkCompletionRepository completionRepository,
            HeadmanRateLimiter headmanRateLimiter,
            HomeworkStudentService homeworkStudentService) {
        this(academicReadService, groupRepository, userRepository, subjectRepository,
                assignmentRepository, semesterRepository, grantRepository,
                homeworkRepository, completionRepository, headmanRateLimiter,
                homeworkStudentService, null, null, null, null, null);
    }

    /** Compatibility constructor retained for existing map/projection and identity tests. */
    public AcademicGrpcServiceImpl(
            AcademicReadService academicReadService,
            GroupRepository groupRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            TeacherSubjectGroupRepository assignmentRepository,
            HomeworkRepository homeworkRepository,
            HomeworkCompletionRepository completionRepository,
            HeadmanRateLimiter headmanRateLimiter,
            HomeworkStudentService homeworkStudentService,
            StudentProjectionScopeService studentProjectionScopeService,
            CampusMapReadService campusMapReadService) {
        this.academicReadService = academicReadService;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.legacyAssignmentRepository = assignmentRepository;
        this.assignmentRepository = null;
        this.semesterRepository = null;
        this.grantRepository = null;
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.headmanRateLimiter = headmanRateLimiter;
        this.homeworkStudentService = homeworkStudentService;
        this.studentProjectionScopeService = studentProjectionScopeService;
        this.campusMapReadService = campusMapReadService;
        this.campusMapUsageService = null;
        this.assistantPermissionAuthority = null;
        this.replacementOperationRepository = null;
    }

    /** Compatibility constructor retained for source-era identity tests. */
    public AcademicGrpcServiceImpl(
            AcademicReadService academicReadService,
            GroupRepository groupRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            TeacherSubjectGroupRepository assignmentRepository,
            HomeworkRepository homeworkRepository,
            HomeworkCompletionRepository completionRepository,
            HeadmanRateLimiter headmanRateLimiter,
            HomeworkStudentService homeworkStudentService) {
        this.academicReadService = academicReadService;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.legacyAssignmentRepository = assignmentRepository;
        this.assignmentRepository = null;
        this.semesterRepository = null;
        this.grantRepository = null;
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.headmanRateLimiter = headmanRateLimiter;
        this.homeworkStudentService = homeworkStudentService;
        this.studentProjectionScopeService = null;
        this.campusMapReadService = null;
        this.campusMapUsageService = null;
        this.assistantPermissionAuthority = null;
        this.replacementOperationRepository = null;
    }

    /**
     * GRPC-01: Get group by ID.
     */
    @Override
    public void getGroup(GroupRequest request, StreamObserver<GroupResponse> responseObserver) {
        Group group = academicReadService.fetchGroup(request.getGroupId());

        GroupResponse response = GroupResponse.newBuilder()
                .setId(group.getId())
                .setName(group.getName())
                .setIsActive(group.isActive())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * GRPC-02: Get group members (active students only, filtered by @SQLRestriction).
     */
    @Override
    public void getGroupMembers(GroupMembersRequest request, StreamObserver<GroupMembersResponse> responseObserver) {
        boolean hasDate = request.hasAsOfDate();
        boolean hasSemester = request.hasSemesterId();
        if (hasDate != hasSemester) {
            throw HistoricalMembershipException.invalid(
                    "as_of_date and semester_id must be supplied together");
        }
        if (!hasDate) {
            List<User> users = academicReadService.fetchGroupMembers(request.getGroupId());
            List<StudentInfo> studentInfos = users.stream()
                    .map(AcademicGrpcServiceImpl::toStudentInfo)
                    .toList();
            responseObserver.onNext(GroupMembersResponse.newBuilder()
                    .addAllStudents(studentInfos)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (request.getGroupId() <= 0 || request.getSemesterId() <= 0
                || request.getAsOfDate().isBlank()) {
            throw HistoricalMembershipException.invalid(
                    "group_id, as_of_date and semester_id must be valid");
        }
        final LocalDate asOfDate;
        try {
            asOfDate = LocalDate.parse(request.getAsOfDate());
        } catch (RuntimeException error) {
            throw HistoricalMembershipException.invalid("as_of_date must be an ISO date");
        }

        HistoricalMembershipService.RosterSnapshot snapshot = academicReadService
                .fetchHistoricalGroupMembers(request.getGroupId(), asOfDate, request.getSemesterId());
        List<StudentInfo> studentInfos = snapshot.students().stream()
                .map(AcademicGrpcServiceImpl::toStudentInfo)
                .toList();

        GroupMembersResponse response = GroupMembersResponse.newBuilder()
                .addAllStudents(studentInfos)
                .setAsOfDate(snapshot.asOfDate().toString())
                .setSemesterId(snapshot.semesterId())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /** Resolves the managed group audience for an event date without semester coupling. */
    @Override
    public void getGroupMemberIdsAsOf(GroupMemberIdsAsOfRequest request,
                                     StreamObserver<GroupMemberIdsAsOfResponse> responseObserver) {
        if (request.getGroupId() <= 0 || request.getAsOfDate().isBlank()) {
            throw HistoricalMembershipException.invalid("group_id and as_of_date must be valid");
        }
        final LocalDate asOfDate;
        try {
            asOfDate = LocalDate.parse(request.getAsOfDate());
        } catch (RuntimeException error) {
            throw HistoricalMembershipException.invalid("as_of_date must be an ISO date");
        }

        List<Long> userIds = academicReadService.fetchHistoricalGroupMemberIds(
                request.getGroupId(), asOfDate);
        responseObserver.onNext(GroupMemberIdsAsOfResponse.newBuilder()
                .setGroupId(request.getGroupId())
                .setAsOfDate(asOfDate.toString())
                .addAllUserIds(userIds)
                .build());
        responseObserver.onCompleted();
    }

    private static StudentInfo toStudentInfo(User user) {
        return StudentInfo.newBuilder()
                .setUserId(user.getId())
                .setDisplayName(user.getDisplayName())
                .setIsHeadman(user.isHeadman())
                .setTelegramId(user.getTelegramId() != null ? user.getTelegramId() : 0L)
                .build();
    }

    /**
     * GRPC-03: Get subjects taught by a teacher in a given semester.
     * Not cached (per D-02).
     */
    @Override
    public void getTeacherSubjects(TeacherSubjectsRequest request, StreamObserver<TeacherSubjectsResponse> responseObserver) {
        if (request.getTeacherId() <= 0 || request.getSemesterId() <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("teacher_id and semester_id must be positive")
                    .asRuntimeException());
            return;
        }
        if (assignmentRepository == null || semesterRepository == null || grantRepository == null) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("V25 assignment repository is unavailable")
                    .asRuntimeException());
            return;
        }
        if (grantRepository.findByUserIdAndRoleAndStatus(
                request.getTeacherId(), "teacher", "active").isEmpty()) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("teacher has no active TEACHER grant")
                    .asRuntimeException());
            return;
        }
        Optional<ru.rutcampustrack.academic.entity.Semester> semester = semesterRepository
                .findById(request.getSemesterId());
        if (semester.isEmpty() || !semester.get().isActive()) {
            responseObserver.onNext(TeacherSubjectsResponse.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }
        ru.rutcampustrack.academic.entity.Semester activeSemester = semester.get();
        LocalDate today = LocalDate.now(MOSCOW);
        if (today.isBefore(activeSemester.getDateFrom()) || today.isAfter(activeSemester.getDateTo())) {
            responseObserver.onNext(TeacherSubjectsResponse.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        List<TeacherSubjectInfo> subjectInfos = assignmentRepository
                .findByTeacherIdAndSemesterId(request.getTeacherId(), request.getSemesterId()).stream()
                .filter(a -> "ACTIVE".equals(a.getLifecycleState()))
                .filter(a -> !a.getValidFrom().isAfter(today))
                .filter(a -> today.isBefore(a.getValidUntilExclusive() != null
                        ? a.getValidUntilExclusive() : activeSemester.getDateTo().plusDays(1)))
                .sorted(Comparator.comparing(Assignment::getSemesterId)
                        .thenComparing(a -> a.getLessonType().name())
                        .thenComparing(Assignment::getTeacherId)
                        .thenComparing(Assignment::getValidFrom)
                        .thenComparing(Assignment::getId))
                .map(a -> {
                    Optional<Subject> subjectOpt = subjectRepository.findById(a.getSubjectId());
                    Optional<Group> groupOpt = groupRepository.findById(a.getGroupId());
                    if (subjectOpt.isEmpty() || groupOpt.isEmpty()) {
                        return null;
                    }
                    Subject subject = subjectOpt.get();
                    Group group = groupOpt.get();
                    String effectiveEnd = (a.getValidUntilExclusive() != null
                            ? a.getValidUntilExclusive() : activeSemester.getDateTo().plusDays(1)).toString();
                    return TeacherSubjectInfo.newBuilder()
                            .setSubjectId(a.getSubjectId())
                            .setSubjectName(subject.getName())
                            .setSubjectType(a.getLessonType().name().toLowerCase(Locale.ROOT))
                            .setGroupId(a.getGroupId())
                            .setGroupName(group.getName())
                            .setAssignmentId(a.getId())
                            .setSemesterId(a.getSemesterId())
                            .setLessonType(a.getLessonType().name().toLowerCase(Locale.ROOT))
                            .setValidFrom(a.getValidFrom().toString())
                            .setValidUntilExclusive(effectiveEnd)
                            .build();
                })
                .filter(info -> info != null)
                .toList();

        TeacherSubjectsResponse response = TeacherSubjectsResponse.newBuilder()
                .addAllSubjects(subjectInfos)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /** Returns complete immutable assignment identities for internal callers. */
    @Override
    public void getAssignmentsByIds(AssignmentsByIdsRequest request,
                                    StreamObserver<AssignmentsByIdsResponse> responseObserver) {
        if (assignmentRepository == null || semesterRepository == null) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("V25 assignment repository is unavailable")
                    .asRuntimeException());
            return;
        }
        List<Long> ids = request.getAssignmentIdsList();
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("assignment_ids must be positive")
                    .asRuntimeException());
            return;
        }
        Map<Long, Assignment> byId = assignmentRepository.findAllById(ids).stream()
                .filter(a -> "ACTIVE".equals(a.getLifecycleState()))
                .collect(Collectors.toMap(Assignment::getId, a -> a, (left, right) -> left,
                        LinkedHashMap::new));
        List<Long> missing = ids.stream().distinct().filter(id -> !byId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("assignment not found: " + missing.get(0))
                    .asRuntimeException());
            return;
        }
        Map<Long, ru.rutcampustrack.academic.entity.Semester> semesters = new LinkedHashMap<>();
        for (Assignment assignment : byId.values()) {
            Optional<ru.rutcampustrack.academic.entity.Semester> semester = semesterRepository
                    .findById(assignment.getSemesterId());
            if (semester.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("semester not found: " + assignment.getSemesterId())
                        .asRuntimeException());
                return;
            }
            semesters.put(assignment.getSemesterId(), semester.get());
        }
        List<AssignmentInfo> infos = byId.values().stream()
                .sorted(Comparator.comparing(Assignment::getId))
                .map(a -> AssignmentInfo.newBuilder()
                        .setId(a.getId())
                        .setTeacherId(a.getTeacherId())
                        .setSubjectId(a.getSubjectId())
                        .setGroupId(a.getGroupId())
                        .setSemesterId(a.getSemesterId())
                        .setLessonType(a.getLessonType().name().toLowerCase(Locale.ROOT))
                        .setValidFrom(a.getValidFrom().toString())
                        .setValidUntilExclusive((a.getValidUntilExclusive() != null
                                ? a.getValidUntilExclusive()
                                : semesters.get(a.getSemesterId()).getDateTo().plusDays(1)).toString())
                        .build())
                .toList();
        responseObserver.onNext(AssignmentsByIdsResponse.newBuilder().addAllAssignments(infos).build());
        responseObserver.onCompleted();
    }

    /**
     * Directed read used by Schedule during the replacement prepare/apply
     * protocol.  Only the durable operation tuple is exposed; ordinary
     * assignment reads never reveal PREPARED rows.
     */
    @Override
    public void getPreparedAssignmentCloseOperation(
            PreparedAssignmentCloseRequest request,
            StreamObserver<PreparedAssignmentCloseResponse> responseObserver) {
        if (replacementOperationRepository == null) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("assignment replacement store is unavailable")
                    .asRuntimeException());
            return;
        }
        final UUID operationId;
        try {
            operationId = UUID.fromString(request.getOperationId());
        } catch (IllegalArgumentException error) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("operation_id must be a UUID")
                    .asRuntimeException());
            return;
        }
        var operation = replacementOperationRepository.findById(operationId).orElse(null);
        if (operation == null || (!"PREPARED".equals(operation.getState())
                && !"APPLIED".equals(operation.getState())
                && !"COMMITTED".equals(operation.getState()))) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("assignment replacement operation not found")
                    .asRuntimeException());
            return;
        }
        if (request.getSourceAssignmentId() != operation.getSourceAssignmentId()) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("source assignment does not match operation")
                    .asRuntimeException());
            return;
        }
        Assignment source = assignmentRepository.findById(operation.getSourceAssignmentId()).orElse(null);
        Assignment target = assignmentRepository.findById(operation.getTargetAssignmentId()).orElse(null);
        if (source == null || target == null) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("assignment replacement tuple is incomplete")
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(PreparedAssignmentCloseResponse.newBuilder()
                .setOperationId(operation.getOperationId().toString())
                .setSourceAssignmentId(source.getId())
                .setTargetAssignmentId(target.getId())
                .setSourceTeacherId(source.getTeacherId())
                .setTargetTeacherId(target.getTeacherId())
                .setSubjectId(source.getSubjectId())
                .setGroupId(source.getGroupId())
                .setSemesterId(source.getSemesterId())
                .setLessonType(source.getLessonType().name().toLowerCase(Locale.ROOT))
                .setEffectiveFrom(operation.getEffectiveFrom().toString())
                .setSourceValidUntilExclusive("PREPARED".equals(operation.getState())
                        ? operation.getTargetValidUntil().toString()
                        : source.getValidUntilExclusive() == null
                                ? "" : source.getValidUntilExclusive().toString())
                .setTargetValidUntilExclusive(operation.getTargetValidUntil() == null
                        ? "" : operation.getTargetValidUntil().toString())
                .setSourceValidFrom(source.getValidFrom().toString())
                .setTargetLifecycleState(target.getLifecycleState())
                .setState(operation.getState())
                .setPayloadHash(ByteString.copyFrom(operation.getPayloadHash()))
                .build());
        responseObserver.onCompleted();
    }

    /**
     * GRPC-04: Check if a user is headman of a given group.
     *
     * <p>Cached via {@code rbac} namespace (M05 D6 — Redis TTL 1 минута).
     * Ранее метод был некешируемым (phase-60 D-02 scope); M05 Группа 3
     * вводит rbac-кеш для hot-path RBAC-проверок. Invalidation —
     * программатическая в {@link ru.rutcampustrack.academic.user.UserService}
     * при смене {@code is_headman}/{@code group_id}.
     */
    @Override
    public void isHeadman(HeadmanCheckRequest request, StreamObserver<HeadmanCheckResponse> responseObserver) {
        long userId = request.getUserId();
        if (!headmanRateLimiter.tryConsume(userId)) {
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("isHeadman rate limit exceeded for user " + userId)
                    .asRuntimeException());
            return;
        }

        boolean isHeadman = academicReadService.isHeadmanOf(
                userId, request.getGroupId());

        HeadmanCheckResponse response = HeadmanCheckResponse.newBuilder()
                .setIsHeadman(isHeadman)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Checks one assistant capability against the current durable grants.
     * The actor is bound by {@link StudentHomeworkGrpcIdentityInterceptor};
     * request fields cannot select another user or group.
     */
    @Override
    public void checkAssistantPermission(AssistantPermissionCheckRequest request,
                                         StreamObserver<AssistantPermissionCheckResponse> responseObserver) {
        final InternalJwtClaims claims;
        try {
            claims = StudentHomeworkGrpcIdentity.requireClaims();
        } catch (IllegalStateException error) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("signed internal identity is required")
                    .asRuntimeException());
            return;
        }

        try {
            boolean allowed = assistantPermissionAuthority != null
                    && assistantPermissionAuthority.allows(
                    claims, request.getGroupId(), request.getPermission());
            responseObserver.onNext(AssistantPermissionCheckResponse.newBuilder()
                    .setAllowed(allowed)
                    .build());
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            // Repository/transport failure must remain distinguishable from a
            // legitimate deny; consumers map UNAVAILABLE to HTTP 503.
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("assistant permission authority unavailable")
                    .withCause(error)
                    .asRuntimeException());
        }
    }

    /**
     * GRPC-05: Get the currently active semester.
     */
    @Override
    public void getActiveSemester(Empty request, StreamObserver<SemesterResponse> responseObserver) {
        ru.rutcampustrack.academic.entity.Semester semester = academicReadService.fetchActiveSemester();

        SemesterResponse response = SemesterResponse.newBuilder()
                .setId(semester.getId())
                .setName(semester.getName())
                .setDateFrom(semester.getDateFrom().toString())
                .setDateTo(semester.getDateTo().toString())
                .setFirstWeekType(semester.getFirstWeekType() != null
                        ? semester.getFirstWeekType()
                        : "odd")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /** Uncached authoritative semester state used by server-side write guards. */
    @Override
    public void getSemesterState(SemesterStateRequest request,
                                 StreamObserver<SemesterStateResponse> responseObserver) {
        long semesterId = request.getSemesterId();
        if (semesterId <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("semester_id must be positive")
                    .asRuntimeException());
            return;
        }

        ru.rutcampustrack.academic.entity.Semester semester = semesterRepository.findByIdUncached(semesterId)
                .orElse(null);
        if (semester == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Semester not found")
                    .asRuntimeException());
            return;
        }

        SemesterStateResponse response = SemesterStateResponse.newBuilder()
                .setId(semester.getId())
                .setActive(semester.isActive())
                .setArchived(semester.isArchived())
                .setStateVersion(semester.getStateVersion())
                .setTransition(ru.rutcampustrack.academic.grpc.SemesterTransition.valueOf(
                        semester.getArchiveTransition().name()))
                .setWriteBlocked(semester.isArchived()
                        || semester.isReleasePending()
                        || semester.getArchiveTransition()
                        != ru.rutcampustrack.academic.contract.enums.SemesterTransition.NONE)
                .setReleasePending(semester.isReleasePending())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * GRPC-06: Get campus geofence configuration (always uses ID=1).
     */
    @Override
    public void getCampusGeofence(Empty request, StreamObserver<GeofenceResponse> responseObserver) {
        ru.rutcampustrack.academic.entity.CampusSetting setting = academicReadService.fetchCampusGeofence();

        GeofenceResponse response = GeofenceResponse.newBuilder()
                .setLat(setting.getLat())
                .setLng(setting.getLng())
                .setRadiusM(setting.getRadiusM())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /** Reads the current coherent campus-map catalog manifest. */
    @Override
    public void getCampusMapManifest(CampusMapManifestRequest request,
                                     StreamObserver<CampusMapManifestResponse> responseObserver) {
        InternalJwtClaims claims;
        try {
            claims = StudentHomeworkGrpcIdentity.requireClaims();
        } catch (IllegalStateException error) {
            onCampusMapError(responseObserver, CampusMapReadException.of(
                    CampusMapReadException.Code.PERMISSION_DENIED,
                    "signed student identity is required"));
            return;
        }
        try {
            CampusMapReadModels.ManifestResult result = campusMapReadService.readManifest(
                    request.getKnownRevision(), claims);
            CampusMapManifestResponse.Builder response = CampusMapManifestResponse.newBuilder();
            if (result.unchanged()) {
                response.setUnchanged(CampusMapManifestUnchanged.newBuilder()
                        .setRevision(result.revision())
                        .build());
            } else {
                response.setManifest(toProtoManifest(result.manifest()));
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (CampusMapReadException error) {
            onCampusMapError(responseObserver, error);
        } catch (RuntimeException error) {
            onCampusMapError(responseObserver, CampusMapReadException.of(
                    CampusMapReadException.Code.INTERNAL,
                    "campus map read failed"));
        }
    }

    /** Reads one current published plan, including both format slots. */
    @Override
    public void getCampusFloorPlan(CampusMapFloorRequest request,
                                   StreamObserver<CampusMapPlanResponse> responseObserver) {
        InternalJwtClaims claims;
        try {
            claims = StudentHomeworkGrpcIdentity.requireClaims();
        } catch (IllegalStateException error) {
            onCampusMapError(responseObserver, CampusMapReadException.of(
                    CampusMapReadException.Code.PERMISSION_DENIED,
                    "signed student identity is required"));
            return;
        }
        try {
            CampusMapReadModels.PlanResult result = campusMapReadService.readFloorPlan(
                    request.getBuildingId(), request.getFloorId(),
                    claims);
            CampusMapPlanResponse.Builder response = CampusMapPlanResponse.newBuilder();
            if (result.hasPlan()) {
                response.setPlan(toProtoPlan(result.plan()));
            } else {
                response.setNoPlan(Empty.newBuilder().build());
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (CampusMapReadException error) {
            onCampusMapError(responseObserver, error);
        } catch (RuntimeException error) {
            onCampusMapError(responseObserver, CampusMapReadException.of(
                    CampusMapReadException.Code.INTERNAL,
                    "campus map read failed"));
        }
    }

    /** Streams one validated immutable published asset in bounded chunks. */
    @Override
    public void readCampusMapAsset(CampusMapAssetRequest request,
                                   StreamObserver<CampusMapAssetChunk> responseObserver) {
        BooleanSupplier cancelled = () -> responseObserver instanceof ServerCallStreamObserver<?> observer
                && observer.isCancelled();
        try {
            if (cancelled.getAsBoolean()) {
                return;
            }
            ru.rutcampustrack.academic.map.CampusMapFormat format = toMapFormat(request.getFormat());
            InternalJwtClaims claims;
            try {
                claims = StudentHomeworkGrpcIdentity.requireClaims();
            } catch (IllegalStateException error) {
                if (!cancelled.getAsBoolean()) {
                    onCampusMapError(responseObserver, CampusMapReadException.of(
                            CampusMapReadException.Code.PERMISSION_DENIED,
                            "signed student identity is required"));
                }
                return;
            }
            java.util.Optional<CampusMapReadModels.Asset> asset = campusMapReadService.readAsset(
                    request.getBuildingId(),
                    request.getFloorId(),
                    request.getVersion(),
                    format,
                    request.getAssetId(),
                    claims,
                    cancelled);
            if (asset.isEmpty() || cancelled.getAsBoolean()) {
                return;
            }
            byte[] content = asset.get().content();
            if (content == null || content.length == 0) {
                throw CampusMapReadException.of(
                        CampusMapReadException.Code.DATA_LOSS,
                        "campus map asset content is invalid");
            }
            for (int offset = 0; offset < content.length; ) {
                if (cancelled.getAsBoolean()) {
                    return;
                }
                int length = Math.min(65_536, content.length - offset);
                responseObserver.onNext(CampusMapAssetChunk.newBuilder()
                        .setData(ByteString.copyFrom(content, offset, length))
                        .setOffset(offset)
                        .build());
                offset += length;
            }
            if (!cancelled.getAsBoolean()) {
                responseObserver.onCompleted();
            }
        } catch (CampusMapReadException error) {
            if (!cancelled.getAsBoolean()) {
                onCampusMapError(responseObserver, error);
            }
        } catch (RuntimeException error) {
            if (!cancelled.getAsBoolean()) {
                onCampusMapError(responseObserver, CampusMapReadException.of(
                        CampusMapReadException.Code.INTERNAL,
                        "campus map read failed"));
            }
        }
    }

    /** Records one successful user-facing floor opening with intent idempotency. */
    @Override
    public void recordCampusFloorOpen(CampusMapOpenRequest request,
                                      StreamObserver<CampusMapOpenAck> responseObserver) {
        try {
            if (campusMapUsageService == null) {
                throw CampusMapReadException.of(
                        CampusMapReadException.Code.UNAVAILABLE,
                        "campus map usage is unavailable");
            }
            InternalJwtClaims claims = StudentHomeworkGrpcIdentity.requireClaims();
            final java.util.UUID intentId;
            try {
                intentId = java.util.UUID.fromString(request.getIntentId());
            } catch (RuntimeException error) {
                throw CampusMapReadException.of(
                        CampusMapReadException.Code.INVALID_ARGUMENT,
                        "map open intent is invalid");
            }
            campusMapUsageService.recordFloorOpen(
                    request.getBuildingId(), request.getFloorId(), intentId, claims);
            responseObserver.onNext(CampusMapOpenAck.newBuilder().setAccepted(true).build());
            responseObserver.onCompleted();
        } catch (IllegalStateException error) {
            onCampusMapError(responseObserver, CampusMapReadException.of(
                    CampusMapReadException.Code.PERMISSION_DENIED,
                    "signed student identity is required"));
        } catch (CampusMapReadException error) {
            onCampusMapError(responseObserver, error);
        } catch (RuntimeException error) {
            onCampusMapError(responseObserver, CampusMapReadException.of(
                    CampusMapReadException.Code.INTERNAL,
                    "campus map usage failed"));
        }
    }

    private static CampusMapManifest toProtoManifest(CampusMapReadModels.Manifest source) {
        CampusMapManifest.Builder manifest = CampusMapManifest.newBuilder()
                .setSchemaVersion(source.schemaVersion())
                .setValidationPolicyVersion(source.validationPolicyVersion())
                .setRevision(source.revision());
        for (CampusMapReadModels.ManifestBuilding sourceBuilding : source.buildings()) {
            CampusMapBuilding.Builder building = CampusMapBuilding.newBuilder()
                    .setId(Long.toString(sourceBuilding.id()))
                    .setLabel(sourceBuilding.label());
            for (CampusMapReadModels.ManifestFloor sourceFloor : sourceBuilding.floors()) {
                CampusMapFloor.Builder floor = CampusMapFloor.newBuilder()
                        .setId(Long.toString(sourceFloor.id()))
                        .setLabel(sourceFloor.label());
                if (sourceFloor.plan() != null) {
                    floor.setPlan(toProtoPlan(sourceFloor.plan()));
                }
                building.addFloors(floor.build());
            }
            manifest.addBuildings(building.build());
        }
        return manifest.build();
    }

    private static CampusMapPlan toProtoPlan(CampusMapReadModels.ManifestPlan source) {
        return CampusMapPlan.newBuilder()
                .setBuildingId(Long.toString(source.buildingId()))
                .setFloorId(Long.toString(source.floorId()))
                .setVersion(source.version())
                .setLabel(source.label())
                .setPng(toProtoFormatSlot(source.png()))
                .setSvg(toProtoFormatSlot(source.svg()))
                .build();
    }

    private static CampusMapFormatSlot toProtoFormatSlot(CampusMapReadModels.FormatSlot source) {
        CampusMapFormatSlot.Builder slot = CampusMapFormatSlot.newBuilder()
                .setFormat(toProtoFormat(source.format()))
                .setState(toProtoFormatState(source.state()))
                .setContentType(source.contentType())
                .setBytes(source.bytes());
        if (source.assetId() != null) {
            slot.setAssetId(Long.toString(source.assetId()));
        }
        if (source.sha256() != null) {
            slot.setSha256(HexFormat.of().formatHex(source.sha256()));
        }
        if (source.width() != null) {
            slot.setWidth(source.width());
        }
        if (source.height() != null) {
            slot.setHeight(source.height());
        }
        if (source.viewBox() != null) {
            slot.addAllViewBox(source.viewBox());
        }
        return slot.build();
    }

    private static CampusMapFormat toProtoFormat(ru.rutcampustrack.academic.map.CampusMapFormat source) {
        return switch (source) {
            case PNG -> CampusMapFormat.CAMPUS_MAP_FORMAT_PNG;
            case SVG -> CampusMapFormat.CAMPUS_MAP_FORMAT_SVG;
        };
    }

    private static CampusMapFormatState toProtoFormatState(
            ru.rutcampustrack.academic.map.CampusMapFormatState source) {
        return switch (source) {
            case ABSENT -> CampusMapFormatState.CAMPUS_MAP_FORMAT_STATE_ABSENT;
            case PROCESSING -> CampusMapFormatState.CAMPUS_MAP_FORMAT_STATE_PROCESSING;
            case READY -> CampusMapFormatState.CAMPUS_MAP_FORMAT_STATE_READY;
            case FAILED -> CampusMapFormatState.CAMPUS_MAP_FORMAT_STATE_FAILED;
        };
    }

    private static ru.rutcampustrack.academic.map.CampusMapFormat toMapFormat(CampusMapFormat source) {
        if (source == null) {
            throw CampusMapReadException.of(
                    CampusMapReadException.Code.INVALID_ARGUMENT,
                    "map format is invalid");
        }
        return switch (source) {
            case CAMPUS_MAP_FORMAT_PNG -> ru.rutcampustrack.academic.map.CampusMapFormat.PNG;
            case CAMPUS_MAP_FORMAT_SVG -> ru.rutcampustrack.academic.map.CampusMapFormat.SVG;
            case CAMPUS_MAP_FORMAT_UNSPECIFIED -> throw CampusMapReadException.of(
                    CampusMapReadException.Code.INVALID_ARGUMENT,
                    "map format is invalid");
            default -> throw CampusMapReadException.of(
                    CampusMapReadException.Code.INVALID_ARGUMENT,
                    "map format is invalid");
        };
    }

    private static void onCampusMapError(StreamObserver<?> observer,
                                         CampusMapReadException error) {
        Status.Code statusCode = switch (error.code()) {
            case INVALID_ARGUMENT -> Status.Code.INVALID_ARGUMENT;
            case PERMISSION_DENIED -> Status.Code.PERMISSION_DENIED;
            case NOT_FOUND -> Status.Code.NOT_FOUND;
            case UNAVAILABLE -> Status.Code.UNAVAILABLE;
            case FAILED_PRECONDITION -> Status.Code.FAILED_PRECONDITION;
            case DATA_LOSS -> Status.Code.DATA_LOSS;
            case RESOURCE_EXHAUSTED -> Status.Code.RESOURCE_EXHAUSTED;
            case INTERNAL -> Status.Code.INTERNAL;
        };
        String description = switch (error.code()) {
            case INVALID_ARGUMENT -> "invalid campus map request";
            case PERMISSION_DENIED -> "campus map access denied";
            case NOT_FOUND -> "campus map resource was not found";
            case UNAVAILABLE -> "campus map catalog is unavailable";
            case FAILED_PRECONDITION -> "campus map resource is not ready";
            case DATA_LOSS -> "campus map data is inconsistent";
            case RESOURCE_EXHAUSTED -> "campus map asset is too large";
            case INTERNAL -> "campus map read failed";
        };
        observer.onError(Status.fromCode(statusCode).withDescription(description).asRuntimeException());
    }

    /**
     * GRPC-08: Get subjects by a list of IDs.
     * Used by Attendance Service to resolve subject names for stats reports.
     * Not cached — infrequent batch lookup.
     */
    @Override
    public void getSubjectsByIds(SubjectsByIdsRequest request, StreamObserver<SubjectsByIdsResponse> responseObserver) {
        List<Long> ids = request.getSubjectIdsList();
        List<Subject> subjects = subjectRepository.findAllById(ids);

        List<SubjectInfo> subjectInfos = subjects.stream()
                .map(s -> SubjectInfo.newBuilder()
                        .setSubjectId(s.getId())
                        .setSubjectName(s.getName())
                        .setSubjectType(s.getType() != null ? s.getType().name().toLowerCase(Locale.ROOT) : "")
                        .build())
                .toList();

        SubjectsByIdsResponse response = SubjectsByIdsResponse.newBuilder()
                .addAllSubjects(subjectInfos)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Resolves the signed student's own semester membership and independent
     * rank cohort.  Identity is supplied by the interceptor, never by the
     * request message.
     */
    @Override
    public void resolveStudentProjectionScope(
            StudentProjectionScopeRequest request,
            StreamObserver<StudentProjectionScopeResponse> responseObserver) {
        try {
            if (studentProjectionScopeService == null) {
                throw new IllegalStateException("Student projection resolver is unavailable");
            }
            StudentProjectionScope scope = studentProjectionScopeService.resolve(
                    request.getSemesterId(), StudentHomeworkGrpcIdentity.CLAIMS.get());
            StudentProjectionScopeResponse.Builder response = StudentProjectionScopeResponse.newBuilder()
                    .setStudentId(scope.studentId())
                    .setSemesterId(scope.semesterId())
                    .setDateFrom(scope.dateFrom().toString())
                    .setDateTo(scope.dateTo().toString())
                    .setTerminalReadOnly(scope.terminalReadOnly())
                    .addAllActiveRosterUserIds(scope.activeRosterUserIds())
                    .addAllSubjects(scope.subjects().stream()
                            .map(AcademicGrpcServiceImpl::toProtoSubject)
                            .toList())
                    .addAllRankSubjects(scope.rankSubjects().stream()
                            .map(AcademicGrpcServiceImpl::toProtoSubject)
                            .toList())
                    .addAllOwnMembershipSegments(scope.ownMembershipSegments().stream()
                            .map(AcademicGrpcServiceImpl::toProtoMembershipSegment)
                            .toList())
                    .setRankVisibility(toProtoRankVisibility(scope.rankVisibility()))
                    .setRankEligible(scope.rankEligible())
                    .setServerDate(scope.serverDate().toString());
            if (scope.rankGroupId() != null) {
                response.setRankGroupId(scope.rankGroupId());
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(StudentProjectionGrpcErrors.toStatus(error));
        }
    }

    private static AcademicSubjectInfo toProtoSubject(StudentProjectionScope.Subject subject) {
        return AcademicSubjectInfo.newBuilder()
                .setSubjectId(subject.subjectId())
                .setSubjectName(subject.name())
                .setSubjectType(subject.type())
                .setGroupId(subject.groupId())
                .addAllLessonTypes(subject.lessonTypes())
                .build();
    }

    private static StudentProjectionMembershipSegment toProtoMembershipSegment(
            StudentProjectionScope.MembershipSegment segment) {
        return StudentProjectionMembershipSegment.newBuilder()
                .setGroupId(segment.groupId())
                .setDateFrom(segment.dateFrom().toString())
                .setDateUntilExclusive(segment.dateUntilExclusive().toString())
                .addAllSubjectIds(segment.subjectIds())
                .build();
    }

    private static StudentProjectionRankVisibility toProtoRankVisibility(
            StudentProjectionScope.RankVisibility visibility) {
        return visibility == StudentProjectionScope.RankVisibility.HIDDEN
                ? StudentProjectionRankVisibility.STUDENT_PROJECTION_RANK_VISIBILITY_HIDDEN
                : StudentProjectionRankVisibility.STUDENT_PROJECTION_RANK_VISIBILITY_VISIBLE;
    }

    /**
     * GRPC-09: Get user by Telegram ID (for bot /start command).
     * Returns found=false when no user has the given telegram_id.
     * Not cached — fresh data needed for bot lookups (D-01 context).
     */
    @Override
    public void getUserByTelegramId(UserByTelegramIdRequest request,
            StreamObserver<UserByTelegramIdResponse> responseObserver) {
        Optional<User> userOpt = academicReadService.fetchUserByTelegramId(request.getTelegramId());

        if (userOpt.isEmpty()) {
            UserByTelegramIdResponse response = UserByTelegramIdResponse.newBuilder()
                    .setFound(false)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        User user = userOpt.get();
        String groupName = "";
        if (user.getGroupId() != null) {
            groupName = groupRepository.findById(user.getGroupId())
                    .map(Group::getName)
                    .orElse("");
        }

        UserByTelegramIdResponse response = UserByTelegramIdResponse.newBuilder()
                .setFound(true)
                .setUserId(user.getId())
                .setLogin(user.getLogin())
                .setDisplayName(user.getDisplayName())
                .setRole(user.getRole().name().toLowerCase())
                .setGroupId(user.getGroupId() != null ? user.getGroupId() : 0L)
                .setGroupName(groupName)
                .setIsHeadman(user.isHeadman())
                .setTelegramId(user.getTelegramId() != null ? user.getTelegramId() : 0L)
                // BUG: initial_password нужен боту для одноразовой выдачи в /start.
                // Возвращаем только пока пароль не сменён — после смены поле NULL в БД.
                // Дополнительной утечки нет: тот же пароль уже виден ADMIN-у через REST
                // /users (BUG-006), а gRPC канал защищён shared secret (IMP-09).
                .setInitialPassword(
                        !user.isPasswordChanged() && user.getInitialPassword() != null
                                ? user.getInitialPassword()
                                : "")
                .setPasswordChanged(user.isPasswordChanged())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Read a student's group homework for a date range.
     *
     * <p>Used by notification-bot reply keyboard. The bot authenticates to gRPC
     * with the shared secret, and this method still verifies that the requested
     * student belongs to the requested group before returning group homework.
     */
    @Override
    public void getHomeworksForWeek(HomeworksForWeekRequest request,
            StreamObserver<HomeworksForWeekResponse> responseObserver) {
        InternalJwtClaims claims = StudentHomeworkGrpcIdentity.CLAIMS.get();
        if (claims == null) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("signed student identity is required")
                    .asRuntimeException());
            return;
        }
        if (!isOwnHomeworkRequest(request, claims)) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("student homework request is outside the signed scope")
                    .asRuntimeException());
            return;
        }
        userRepository.findByIdIncludingArchived(claims.userId())
                .orElseThrow(() -> new ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException(
                        "User", "id", claims.userId()));
        // The validated signed grant is authoritative for role, account status,
        // read-only mode and group scope. The archived-aware lookup proves that
        // the signed own user still exists; legacy row fields may be stale.
        if (!canReadHomework(claims)) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("student is not allowed to read homework")
                    .asRuntimeException());
            return;
        }

        LocalDate from = parseIsoDate(request.getDateFrom(), "date_from");
        LocalDate to = parseIsoDate(request.getDateTo(), "date_to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("date_to must be greater than or equal to date_from");
        }

        CompletionWindow completedToday = request.getIncludeCompletedToday()
                ? parseCompletionWindow(request, from, to)
                : null;

        Map<Long, Homework> homeworksById = new LinkedHashMap<>();
        homeworkRepository
                .findByGroupIdAndSemesterIdAndPublicationStateAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
                        request.getGroupId(), request.getSemesterId(), HomeworkPublicationState.ACTIVE, from, to)
                .forEach(homework -> homeworksById.put(homework.getId(), homework));

        if (completedToday != null) {
            List<Long> completedTodayHomeworkIds = completionRepository
                    .findByStudentIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                            claims.userId(), completedToday.from(), completedToday.to())
                    .stream()
                    .map(HomeworkCompletion::getHomeworkId)
                    .distinct()
                    .toList();
            if (!completedTodayHomeworkIds.isEmpty()) {
                homeworkRepository.findAllById(completedTodayHomeworkIds).stream()
                        .filter(homework -> request.getGroupId() == homework.getGroupId())
                        .filter(homework -> request.getSemesterId() == homework.getSemesterId())
                        .filter(homework -> homework.getPublicationState() == HomeworkPublicationState.ACTIVE)
                        .forEach(homework -> homeworksById.put(homework.getId(), homework));
            }
        }

        List<Homework> homeworks = new ArrayList<>(homeworksById.values());
        homeworks.sort(java.util.Comparator
                .comparing(Homework::getLessonDate)
                .thenComparing(Homework::getLessonNumber)
                .thenComparing(Homework::getId));
        List<Long> homeworkIds = homeworks.stream()
                .map(Homework::getId)
                .toList();
        Map<Long, OffsetDateTime> completedAtByHomeworkId = homeworkIds.isEmpty()
                ? Map.of()
                : completionRepository.findByHomeworkIdInAndStudentId(homeworkIds, claims.userId()).stream()
                        .collect(Collectors.toMap(HomeworkCompletion::getHomeworkId,
                                HomeworkCompletion::getCompletedAt, (left, right) -> left));

        Set<Long> subjectIds = homeworks.stream()
                .map(Homework::getSubjectId)
                .collect(Collectors.toSet());
        Map<Long, String> subjectNames = subjectIds.isEmpty()
                ? Map.of()
                : subjectRepository.findAllById(subjectIds).stream()
                        .collect(Collectors.toMap(Subject::getId, Subject::getName, (left, right) -> left));

        HomeworksForWeekResponse response = HomeworksForWeekResponse.newBuilder()
                .addAllHomeworks(homeworks.stream()
                        .map(homework -> HomeworkInfo.newBuilder()
                                .setHomeworkId(homework.getId())
                                .setSubjectId(homework.getSubjectId())
                                .setSubjectName(subjectNames.getOrDefault(homework.getSubjectId(), ""))
                                .setTitle(emptyIfNull(homework.getTitle()))
                                .setDescription(emptyIfNull(homework.getDescription()))
                                .setLink(emptyIfNull(homework.getLink()))
                                .setLessonDate(homework.getLessonDate().toString())
                                .setLessonNumber(homework.getLessonNumber())
                                .setCompleted(completedAtByHomeworkId.containsKey(homework.getId()))
                                .build())
                        .map(info -> {
                            OffsetDateTime completedAt = completedAtByHomeworkId.get(info.getHomeworkId());
                            if (completedAt == null) {
                                return info;
                            }
                            return info.toBuilder()
                                    .setCompletedAt(completedAt.toInstant().toString())
                                    .build();
                        })
                        .toList())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Applies a student's desired completion state.  The actor is bound to the
     * signed JWT by {@link StudentHomeworkGrpcIdentityInterceptor}; request
     * fields contain no user or group identity that can override it.
     */
    @Override
    public void setHomeworkCompletion(SetHomeworkCompletionRequest request,
            StreamObserver<SetHomeworkCompletionResponse> responseObserver) {
        try {
            InternalJwtClaims claims = StudentHomeworkGrpcIdentity.requireClaims();
            HomeworkStudentService.CompletionState state = homeworkStudentService.setCompletion(
                    request.getHomeworkId(), request.getSemesterId(), claims, request.getCompleted());
            SetHomeworkCompletionResponse.Builder response = SetHomeworkCompletionResponse.newBuilder()
                    .setHomeworkId(request.getHomeworkId())
                    .setCompleted(state.completed());
            if (state.completedAt() != null) {
                response.setCompletedAt(state.completedAt().toInstant().toString());
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (AccessDeniedException error) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription(error.getMessage()).asRuntimeException());
        } catch (ResourceNotFoundException error) {
            Status status = "Semester".equals(error.getResourceName())
                    ? Status.UNAVAILABLE : Status.NOT_FOUND;
            responseObserver.onError(status.withDescription(error.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException error) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(error.getMessage()).asRuntimeException());
        } catch (RuntimeException error) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Homework completion failed").withCause(error).asRuntimeException());
        }
    }

    /**
     * GRPC-07: Get user by ID including archived users.
     * Uses cached lookup that bypasses @SQLRestriction so downstream services
     * can access historical data for archived users.
     */
    @Override
    public void getUserById(UserRequest request, StreamObserver<UserResponse> responseObserver) {
        User user = academicReadService.fetchUserById(request.getUserId());

        UserResponse response = UserResponse.newBuilder()
                .setId(user.getId())
                .setLogin(user.getLogin())
                .setDisplayName(user.getDisplayName())
                .setRole(user.getRole().name().toLowerCase())
                .setStatus(user.getStatus().name().toLowerCase())
                .setGroupId(user.getGroupId() != null ? user.getGroupId() : 0L)
                .setIsHeadman(user.isHeadman())
                .setTelegramId(user.getTelegramId() != null ? user.getTelegramId() : 0L)
                .setAvatarId(user.getAvatarId() != null ? user.getAvatarId() : "")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static LocalDate parseIsoDate(String raw, String field) {
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(field + " must be an ISO date", e);
        }
    }

    private static CompletionWindow parseCompletionWindow(HomeworksForWeekRequest request,
                                                          LocalDate lessonFrom,
                                                          LocalDate lessonTo) {
        if (request.getCompletedTodayFrom().isBlank() || request.getCompletedTodayTo().isBlank()) {
            throw new IllegalArgumentException("completed_today_from/to are required for the union");
        }
        final OffsetDateTime from;
        final OffsetDateTime to;
        try {
            from = OffsetDateTime.parse(request.getCompletedTodayFrom());
            to = OffsetDateTime.parse(request.getCompletedTodayTo());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("completed_today_from/to must be ISO-8601 instants", error);
        }
        ZonedDateTime moscowFrom = from.toInstant().atZone(MOSCOW);
        ZonedDateTime moscowTo = to.toInstant().atZone(MOSCOW);
        if (!moscowFrom.toLocalTime().equals(LocalTime.MIDNIGHT)
                || !moscowTo.toLocalTime().equals(LocalTime.MIDNIGHT)
                || !moscowTo.toLocalDate().equals(moscowFrom.toLocalDate().plusDays(1))
                || moscowFrom.toLocalDate().isBefore(lessonFrom)
                || moscowFrom.toLocalDate().isAfter(lessonTo)) {
            throw new IllegalArgumentException("completion window must be one Moscow day within the lesson range");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("completed_today_to must be after completed_today_from");
        }
        return new CompletionWindow(from, to);
    }

    private static boolean isOwnHomeworkRequest(HomeworksForWeekRequest request, InternalJwtClaims claims) {
        return "STUDENT".equalsIgnoreCase(claims.role())
                && claims.userId() > 0
                && claims.groupId() != null
                && claims.groupId() > 0
                && request.getStudentId() == claims.userId()
                && request.getGroupId() == claims.groupId();
    }

    private static boolean canReadHomework(InternalJwtClaims claims) {
        if (!"STUDENT".equalsIgnoreCase(claims.role())
                || claims.userId() <= 0
                || claims.groupId() == null
                || claims.groupId() <= 0) {
            return false;
        }
        if (!claims.readOnly()) {
            return "ACTIVE".equalsIgnoreCase(claims.status());
        }
        return isTerminalStatus(claims.status());
    }

    private static boolean isTerminalStatus(String status) {
        return "EXPELLED".equalsIgnoreCase(status)
                || "GRADUATED".equalsIgnoreCase(status)
                || "ARCHIVED".equalsIgnoreCase(status);
    }

    private record CompletionWindow(OffsetDateTime from, OffsetDateTime to) {
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}
