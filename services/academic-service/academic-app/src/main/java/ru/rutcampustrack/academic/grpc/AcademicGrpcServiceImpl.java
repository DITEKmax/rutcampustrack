package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * gRPC service implementation for Academic Service.
 * Delegates cached read operations to AcademicReadService to ensure
 * Spring AOP @Cacheable proxy is not bypassed via self-invocation (per D-01).
 * Non-cached methods (getTeacherSubjects, isHeadman) query repositories directly.
 */
@GrpcService
public class AcademicGrpcServiceImpl extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {

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
    private final TeacherSubjectGroupRepository assignmentRepository;
    private final HomeworkRepository homeworkRepository;
    private final HomeworkCompletionRepository completionRepository;
    private final HeadmanRateLimiter headmanRateLimiter;

    public AcademicGrpcServiceImpl(
            AcademicReadService academicReadService,
            GroupRepository groupRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            TeacherSubjectGroupRepository assignmentRepository,
            HomeworkRepository homeworkRepository,
            HomeworkCompletionRepository completionRepository,
            HeadmanRateLimiter headmanRateLimiter) {
        this.academicReadService = academicReadService;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.headmanRateLimiter = headmanRateLimiter;
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
        List<User> users = academicReadService.fetchGroupMembers(request.getGroupId());

        List<StudentInfo> studentInfos = users.stream()
                .map(user -> StudentInfo.newBuilder()
                        .setUserId(user.getId())
                        .setDisplayName(user.getDisplayName())
                        .setIsHeadman(user.isHeadman())
                        .setTelegramId(user.getTelegramId() != null ? user.getTelegramId() : 0L)
                        .build())
                .toList();

        GroupMembersResponse response = GroupMembersResponse.newBuilder()
                .addAllStudents(studentInfos)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * GRPC-03: Get subjects taught by a teacher in a given semester.
     * Not cached (per D-02).
     */
    @Override
    public void getTeacherSubjects(TeacherSubjectsRequest request, StreamObserver<TeacherSubjectsResponse> responseObserver) {
        List<TeacherSubjectGroup> assignments = assignmentRepository
                .findByTeacherIdAndSemesterId(request.getTeacherId(), request.getSemesterId());

        List<TeacherSubjectInfo> subjectInfos = assignments.stream()
                .map(a -> {
                    Optional<Subject> subjectOpt = subjectRepository.findById(a.getSubjectId());
                    Optional<Group> groupOpt = groupRepository.findById(a.getGroupId());
                    if (subjectOpt.isEmpty() || groupOpt.isEmpty()) {
                        return null;
                    }
                    Subject subject = subjectOpt.get();
                    Group group = groupOpt.get();
                    return TeacherSubjectInfo.newBuilder()
                            .setSubjectId(a.getSubjectId())
                            .setSubjectName(subject.getName())
                            .setSubjectType(subject.getType().name().toLowerCase())
                            .setGroupId(a.getGroupId())
                            .setGroupName(group.getName())
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
        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException(
                        "User", "id", request.getStudentId()));
        if (student.getGroupId() == null || !student.getGroupId().equals(request.getGroupId())) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("student does not belong to requested group")
                    .asRuntimeException());
            return;
        }

        LocalDate from = parseIsoDate(request.getDateFrom(), "date_from");
        LocalDate to = parseIsoDate(request.getDateTo(), "date_to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("date_to must be greater than or equal to date_from");
        }

        List<Homework> homeworks = homeworkRepository
                .findByGroupIdAndSemesterIdAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
                        request.getGroupId(), request.getSemesterId(), from, to);
        List<Long> homeworkIds = homeworks.stream()
                .map(Homework::getId)
                .toList();
        Set<Long> completedHomeworkIds = homeworkIds.isEmpty()
                ? Set.of()
                : completionRepository.findByHomeworkIdInAndStudentId(homeworkIds, student.getId()).stream()
                        .map(HomeworkCompletion::getHomeworkId)
                        .collect(Collectors.toSet());

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
                                .setCompleted(completedHomeworkIds.contains(homework.getId()))
                                .build())
                        .toList())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
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

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}
