package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.schedule.grpc.CountSubjectReferencesRequest;
import ru.rutcampustrack.schedule.grpc.CountSubjectReferencesResponse;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.ResolveLessonRequest;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Phase 61 / D-04: gRPC клиент academic→schedule. Используется в {@code HomeworkService}
 * для валидации: пара {@code (groupId, date, lessonNumber)} существует и её subject
 * совпадает с {@code subjectId} ДЗ.
 *
 * <p>3-секундный deadline — mitigation T-61-05 (DoS через hanging gRPC call).
 * NOT_FOUND транслируется в {@link Optional#empty()} — вызывающий сервис сам решает,
 * как его интерпретировать (для D-04 — это 400 «пары нет в расписании»).
 * Любая другая ошибка gRPC → {@link ScheduleServiceUnavailableException} → 503.
 */
@Component
public class ScheduleGrpcClient {

    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    public Optional<LessonResponse> resolveLesson(Long groupId, LocalDate date, int lessonNumber) {
        try {
            LessonResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .resolveLesson(ResolveLessonRequest.newBuilder()
                            .setGroupId(groupId)
                            .setDate(date.toString())
                            .setLessonNumber(lessonNumber)
                            .build());
            return Optional.of(response);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw new ScheduleServiceUnavailableException(
                    "Не удалось связаться с schedule-service: " + e.getStatus(), e);
        }
    }

    /**
     * Pre-check перед удалением Subject: узнаём, сколько объектов расписания
     * ссылаются на него. Если {@code non_planned_lessons_count > 0} — есть
     * посещаемость, SubjectService вернёт 409 без force=true.
     *
     * <p>Любая ошибка gRPC → {@link ScheduleServiceUnavailableException} (503).
     */
    public CountSubjectReferencesResponse countSubjectReferences(Long subjectId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .countSubjectReferences(CountSubjectReferencesRequest.newBuilder()
                            .setSubjectId(subjectId)
                            .build());
        } catch (StatusRuntimeException e) {
            throw new ScheduleServiceUnavailableException(
                    "Не удалось связаться с schedule-service: " + e.getStatus(), e);
        }
    }
}
