package ru.rutcampustrack.attendance.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.schedule.grpc.ActiveLessonRequest;
import ru.rutcampustrack.schedule.grpc.LessonByIdRequest;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsByIdsRequest;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.schedule.grpc.LessonsByGroupRequest;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client wrapper for Schedule Service.
 * All calls use a 3-second deadline to prevent cascading timeouts.
 * StatusRuntimeException is translated to domain exceptions
 * which are then mapped to HTTP status codes by GlobalExceptionHandler.
 */
@Component
public class ScheduleGrpcClient {

    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    public LessonResponse getActiveLesson(Long groupId, String timestamp) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getActiveLesson(ActiveLessonRequest.newBuilder()
                            .setGroupId(groupId)
                            .setTimestamp(timestamp)
                            .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Lesson", "groupId/timestamp", groupId + "/" + timestamp);
            }
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }

    public LessonResponse getLessonById(Long lessonId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getLessonById(LessonByIdRequest.newBuilder()
                            .setLessonId(lessonId)
                            .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Lesson", "id", lessonId);
            }
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }

    public LessonsResponse getLessonsByGroup(Long groupId, Long semesterId, String dateFrom, String dateTo) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getLessonsByGroup(LessonsByGroupRequest.newBuilder()
                            .setGroupId(groupId)
                            .setSemesterId(semesterId)
                            .setDateFrom(dateFrom)
                            .setDateTo(dateTo)
                            .build());
        } catch (StatusRuntimeException e) {
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }

    /**
     * GRPC-04 (D-25): batch-fetch compact lesson info by IDs.
     * Used by ExcuseService to validate that lessonIds belong to the student's group.
     * Empty/null input returns an empty list without making a network call.
     */
    public List<LessonInfo> getLessonsByIds(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return List.of();
        }
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getLessonsByIds(LessonsByIdsRequest.newBuilder()
                            .addAllLessonIds(lessonIds)
                            .build())
                    .getLessonsList();
        } catch (StatusRuntimeException e) {
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }
}
