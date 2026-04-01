package ru.rutcampustrack.schedule.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckRequest;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client wrapper for Academic Service.
 * All calls use a 3-second deadline to prevent cascading timeouts.
 * gRPC failures (StatusRuntimeException) are translated to domain exceptions
 * which are then mapped to HTTP status codes by GlobalExceptionHandler.
 */
@Component
public class AcademicGrpcClient {

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    /**
     * Validates that a group exists and is active.
     *
     * @param groupId the group ID to validate
     * @return GroupResponse with group details
     * @throws ResourceNotFoundException if the group does not exist or is inactive
     * @throws AcademicServiceUnavailableException if Academic Service is unreachable
     */
    public GroupResponse validateGroup(Long groupId) {
        try {
            GroupResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getGroup(GroupRequest.newBuilder().setGroupId(groupId).build());
            if (!response.getIsActive()) {
                throw new ResourceNotFoundException("Group", "id", groupId);
            }
            return response;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    /**
     * Retrieves the currently active semester.
     *
     * @return SemesterResponse with semester details
     * @throws ResourceNotFoundException if no active semester exists
     * @throws AcademicServiceUnavailableException if Academic Service is unreachable
     */
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

    /**
     * Parses first_week_type from SemesterResponse to schedule-service's WeekType enum.
     * Throws IllegalStateException if the field is blank (Academic Service may need restart after V6 migration).
     *
     * @param response SemesterResponse from Academic Service
     * @return WeekType parsed from first_week_type field
     * @throws IllegalStateException if first_week_type is null or blank
     */
    public WeekType parseSemesterFirstWeekType(SemesterResponse response) {
        String raw = response.getFirstWeekType();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                "SemesterResponse missing first_week_type — Academic Service may need restart after V6 migration");
        }
        return WeekType.valueOf(raw.toUpperCase());
    }

    /**
     * Checks whether a user is the headman of a group.
     *
     * @param userId  the user to check
     * @param groupId the group to check
     * @return true if the user is headman, false otherwise
     * @throws AcademicServiceUnavailableException if Academic Service is unreachable
     */
    public boolean isHeadman(Long userId, Long groupId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .isHeadman(HeadmanCheckRequest.newBuilder()
                            .setUserId(userId)
                            .setGroupId(groupId)
                            .build())
                    .getIsHeadman();
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }
}
