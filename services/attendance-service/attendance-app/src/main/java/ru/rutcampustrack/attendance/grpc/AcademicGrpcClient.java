package ru.rutcampustrack.attendance.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckRequest;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SubjectInfo;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsRequest;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsResponse;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsRequest;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;

import java.util.List;
import java.util.Map;
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

    public Map<Long, String> getSubjectsByIds(List<Long> subjectIds) {
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
                            SubjectInfo::getSubjectName,
                            (a, b) -> a));
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }
}
