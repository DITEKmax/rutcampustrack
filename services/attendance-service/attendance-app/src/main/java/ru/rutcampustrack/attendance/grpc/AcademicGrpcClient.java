package ru.rutcampustrack.attendance.grpc;

import io.grpc.StatusRuntimeException;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckRequest;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SubjectInfo;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsRequest;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsResponse;
import ru.rutcampustrack.academic.grpc.StudentProjectionScopeRequest;
import ru.rutcampustrack.academic.grpc.StudentProjectionScopeResponse;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsRequest;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.academic.grpc.UserRequest;
import ru.rutcampustrack.academic.grpc.UserResponse;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.student.StudentCheckinException;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * gRPC client wrapper for Academic Service.
 * All calls use a 3-second deadline to prevent cascading timeouts.
 * StatusRuntimeException is translated to domain exceptions
 * which are then mapped to HTTP status codes by GlobalExceptionHandler.
 */
@Component
public class AcademicGrpcClient {

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    public GroupResponse getGroup(Long groupId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getGroup(GroupRequest.newBuilder()
                            .setGroupId(groupId)
                            .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Group", "id", groupId);
            }
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    public GroupMembersResponse getGroupMembers(Long groupId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getGroupMembers(GroupMembersRequest.newBuilder()
                            .setGroupId(groupId)
                            .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Group", "id", groupId);
            }
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    /**
     * Reads a complete historical roster.  The paired fields are mandatory
     * here; callers must validate the response echoes before materializing any
     * attendance document.
     */
    public GroupMembersResponse getGroupMembers(Long groupId, LocalDate asOfDate, Long semesterId) {
        if (groupId == null || groupId <= 0 || asOfDate == null || semesterId == null || semesterId <= 0) {
            throw new IllegalArgumentException("groupId, asOfDate and semesterId are required");
        }
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getGroupMembers(GroupMembersRequest.newBuilder()
                            .setGroupId(groupId)
                            .setAsOfDate(asOfDate.toString())
                            .setSemesterId(semesterId)
                            .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Group", "id", groupId);
            }
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    public GeofenceResponse getCampusGeofence() {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getCampusGeofence(Empty.getDefaultInstance());
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    public SemesterResponse getActiveSemester() {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getActiveSemester(Empty.getDefaultInstance());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Semester", "status", "active");
            }
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    public HeadmanCheckResponse isHeadman(Long userId, Long groupId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .isHeadman(HeadmanCheckRequest.newBuilder()
                            .setUserId(userId)
                            .setGroupId(groupId)
                            .build());
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    public TeacherSubjectsResponse getTeacherSubjects(Long teacherId, Long semesterId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getTeacherSubjects(TeacherSubjectsRequest.newBuilder()
                            .setTeacherId(teacherId)
                            .setSemesterId(semesterId)
                            .build());
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    /**
     * D-26: fetch user's display name for excuse-ticket snapshot.
     * Returns "Студент #userId" fallback if the user is not found to avoid blocking
     * ticket creation on transient academic-service outages.
     */
    public String getUserDisplayName(Long userId) {
        try {
            UserResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getUserById(UserRequest.newBuilder()
                            .setUserId(userId)
                            .build());
            String name = response.getDisplayName();
            return (name == null || name.isBlank()) ? ("Студент #" + userId) : name;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("User", "id", userId);
            }
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    public Map<Long, String> getSubjectsByIds(List<Long> subjectIds) {
        return getSubjectDetailsByIds(subjectIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().name(),
                        (a, b) -> a));
    }

    public Map<Long, SubjectDetails> getSubjectDetailsByIds(List<Long> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return Map.of();
        }
        try {
            SubjectsByIdsResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getSubjectsByIds(SubjectsByIdsRequest.newBuilder()
                            .addAllSubjectIds(subjectIds)
                            .build());
            return response.getSubjectsList().stream()
                    .collect(Collectors.toMap(
                            SubjectInfo::getSubjectId,
                            subject -> new SubjectDetails(subject.getSubjectName(), subject.getSubjectType()),
                            (a, b) -> a));
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    /**
     * Resolve the signed student's historical membership and rank cohort.  The
     * original validated JWT is forwarded to Academic so its authority
     * service re-checks the same actor and session; claims are never rebuilt
     * into a weaker synthetic header.
     */
    public StudentProjectionScopeResponse resolveStudentProjectionScope(long semesterId) {
        if (semesterId <= 0) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "semester_id должен быть положительным");
        }
        String token = StudentGrpcIdentity.token();
        if (token == null || token.isBlank()) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_SESSION,
                    "Подписанная student-сессия отсутствует");
        }
        Metadata metadata = new Metadata();
        metadata.put(StudentGrpcIdentityInterceptor.INTERNAL_TOKEN, token);
        try {
            return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .resolveStudentProjectionScope(StudentProjectionScopeRequest.newBuilder()
                            .setSemesterId(semesterId)
                            .build());
        } catch (StatusRuntimeException error) {
            switch (error.getStatus().getCode()) {
                case UNAUTHENTICATED -> throw new StudentCheckinException(
                        StudentCheckinException.Code.INVALID_SESSION,
                        "Academic Service отклонил student-сессию");
                case PERMISSION_DENIED -> throw new StudentCheckinException(
                        StudentCheckinException.Code.OUT_OF_SCOPE,
                        "Семестр не входит в подписанный student scope");
                case INVALID_ARGUMENT -> throw new StudentCheckinException(
                        StudentCheckinException.Code.INVALID_REQUEST,
                        "Academic Service отклонил semester_id");
                default -> throw new AcademicServiceUnavailableException(
                        "Academic Service unavailable: " + error.getStatus());
            }
        }
    }

    public record SubjectDetails(String name, String type) {}
}
