package ru.rutcampustrack.attendance.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.ConflictException;
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
            LessonResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getActiveLesson(ActiveLessonRequest.newBuilder()
                            .setGroupId(groupId)
                            .setTimestamp(timestamp)
                            .build());
            requireTransferReadable(response);
            return response;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Lesson", "groupId/timestamp", groupId + "/" + timestamp);
            }
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }

    public LessonResponse getLessonById(Long lessonId) {
        try {
            LessonResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getLessonById(LessonByIdRequest.newBuilder()
                            .setLessonId(lessonId)
                            .build());
            requireTransferReadable(response);
            return response;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("Lesson", "id", lessonId);
            }
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }

    public LessonsResponse getLessonsByGroup(Long groupId, Long semesterId, String dateFrom, String dateTo) {
        try {
            LessonsResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getLessonsByGroup(LessonsByGroupRequest.newBuilder()
                            .setGroupId(groupId)
                            .setSemesterId(semesterId)
                            .setDateFrom(dateFrom)
                            .setDateTo(dateTo)
                            .build());
            response.getLessonsList().forEach(this::requireTransferReadable);
            return response;
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
            List<LessonInfo> lessons = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getLessonsByIds(LessonsByIdsRequest.newBuilder()
                            .addAllLessonIds(lessonIds)
                            .build())
                    .getLessonsList();
            // LessonInfo predates the transfer-state fields. Resolve each compact
            // entry through LessonResponse so reads and write validations cannot
            // expose a partially applied transfer as final data.
            lessons.forEach(lesson -> getLessonById(lesson.getLessonId()));
            return lessons;
        } catch (StatusRuntimeException e) {
            throw new ScheduleServiceUnavailableException("Schedule Service unavailable: " + e.getStatus());
        }
    }

    /** Revalidates mutable Attendance writes after their Mongo lesson fence is held. */
    public LessonResponse requireAttendanceMutationReady(long lessonId, long groupId) {
        LessonResponse lesson = getLessonById(lessonId);
        if (lesson.getId() != lessonId || lesson.getGroupId() != groupId) {
            throw new ConflictException("Урок изменился; обнови данные и повтори действие");
        }
        if ("transferred".equalsIgnoreCase(lesson.getStatus())) {
            throw new ConflictException("Урок перенесён; обнови данные и повтори действие");
        }
        return lesson;
    }

    private void requireTransferReadable(LessonResponse response) {
        if (response == null) {
            throw new ScheduleServiceUnavailableException("Schedule returned no lesson snapshot");
        }
        String state = response.getTransferState();
        if (state == null || state.isBlank() || "COMPLETED".equals(state)) {
            return;
        }
        String operationId = response.getTransferOperationId();
        throw new ScheduleServiceUnavailableException(
                "Lesson transfer is " + state + (operationId == null || operationId.isBlank()
                        ? "" : " (operation " + operationId + ")") + "; retry this request");
    }
}
