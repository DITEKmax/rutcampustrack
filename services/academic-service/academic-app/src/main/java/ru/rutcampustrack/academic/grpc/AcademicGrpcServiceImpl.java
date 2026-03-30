package ru.rutcampustrack.academic.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.CampusSettingRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * gRPC service implementation for Academic Service.
 * Queries repositories directly — does NOT use REST service layer to avoid
 * RequestContext scope issues in gRPC context.
 */
@GrpcService
public class AcademicGrpcServiceImpl extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectGroupRepository assignmentRepository;
    private final CampusSettingRepository campusSettingRepository;

    public AcademicGrpcServiceImpl(
            GroupRepository groupRepository,
            UserRepository userRepository,
            SemesterRepository semesterRepository,
            SubjectRepository subjectRepository,
            TeacherSubjectGroupRepository assignmentRepository,
            CampusSettingRepository campusSettingRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.semesterRepository = semesterRepository;
        this.subjectRepository = subjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.campusSettingRepository = campusSettingRepository;
    }

    /**
     * GRPC-01: Get group by ID.
     */
    @Override
    public void getGroup(GroupRequest request, StreamObserver<GroupResponse> responseObserver) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", request.getGroupId()));

        GroupResponse response = GroupResponse.newBuilder()
                .setId(group.getId())
                .setName(group.getName())
                .setCode(group.getCode() != null ? group.getCode() : "")
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
        List<User> users = userRepository.findByGroupId(request.getGroupId());

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
     */
    @Override
    public void isHeadman(HeadmanCheckRequest request, StreamObserver<HeadmanCheckResponse> responseObserver) {
        Optional<User> userOpt = userRepository.findById(request.getUserId());

        boolean isHeadman = userOpt.map(user ->
                user.isHeadman()
                        && user.getGroupId() != null
                        && user.getGroupId().equals(request.getGroupId())
        ).orElse(false);

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
        Semester semester = semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "isActive", true));

        SemesterResponse response = SemesterResponse.newBuilder()
                .setId(semester.getId())
                .setName(semester.getName())
                .setDateFrom(semester.getDateFrom().toString())
                .setDateTo(semester.getDateTo().toString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * GRPC-06: Get campus geofence configuration (always uses ID=1).
     */
    @Override
    public void getCampusGeofence(Empty request, StreamObserver<GeofenceResponse> responseObserver) {
        ru.rutcampustrack.academic.entity.CampusSetting setting = campusSettingRepository.findById(1L)
                .orElseThrow(() -> new ResourceNotFoundException("CampusSetting", "id", 1L));

        GeofenceResponse response = GeofenceResponse.newBuilder()
                .setLat(setting.getLat())
                .setLng(setting.getLng())
                .setRadiusM(setting.getRadiusM())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * GRPC-07: Get user by ID including archived users.
     * Uses native query to bypass @SQLRestriction so downstream services
     * can access historical data for archived users.
     */
    @Override
    public void getUserById(UserRequest request, StreamObserver<UserResponse> responseObserver) {
        User user = userRepository.findByIdIncludingArchived(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        UserResponse response = UserResponse.newBuilder()
                .setId(user.getId())
                .setLogin(user.getLogin())
                .setDisplayName(user.getDisplayName())
                .setRole(user.getRole().name().toLowerCase())
                .setStatus(user.getStatus().name().toLowerCase())
                .setGroupId(user.getGroupId() != null ? user.getGroupId() : 0L)
                .setIsHeadman(user.isHeadman())
                .setTelegramId(user.getTelegramId() != null ? user.getTelegramId() : 0L)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
