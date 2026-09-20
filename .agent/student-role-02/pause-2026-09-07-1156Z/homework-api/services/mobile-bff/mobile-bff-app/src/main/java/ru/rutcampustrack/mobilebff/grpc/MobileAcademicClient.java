package ru.rutcampustrack.mobilebff.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.error.MobileBffException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class MobileAcademicClient {
    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;
    private final MobileGrpcAuth auth;

    public MobileAcademicClient(MobileGrpcAuth auth) { this.auth = auth; }

    public UserResponse user(long id) {
        return call(() -> auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                .getUserById(UserRequest.newBuilder().setUserId(id).build()));
    }

    public GroupResponse group(long id) {
        return call(() -> auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                .getGroup(GroupRequest.newBuilder().setGroupId(id).build()));
    }

    public SemesterResponse activeSemester() {
        return call(() -> auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                .getActiveSemester(Empty.getDefaultInstance()));
    }

    public SemesterResponse activeSemesterForHomework() {
        return call(() -> auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                .getActiveSemester(Empty.getDefaultInstance()),
                HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public Map<Long, SubjectInfo> subjects(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        SubjectsByIdsResponse response = call(() -> auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                .getSubjectsByIds(SubjectsByIdsRequest.newBuilder().addAllSubjectIds(ids).build()));
        return response.getSubjectsList().stream().collect(Collectors.toMap(
                SubjectInfo::getSubjectId, value -> value, (left, right) -> left));
    }

    public HomeworksForWeekResponse homeworks(long groupId, long semesterId, long studentId,
                                               String dateFrom, String dateTo) {
        return homeworks(groupId, semesterId, studentId, dateFrom, dateTo,
                false, null, null);
    }

    public HomeworksForWeekResponse homeworks(long groupId, long semesterId, long studentId,
                                               String dateFrom, String dateTo,
                                               boolean includeCompletedToday,
                                               String completedTodayFrom,
                                               String completedTodayTo) {
        HomeworksForWeekRequest.Builder request = HomeworksForWeekRequest.newBuilder()
                .setGroupId(groupId)
                .setSemesterId(semesterId)
                .setStudentId(studentId)
                .setDateFrom(dateFrom)
                .setDateTo(dateTo)
                .setIncludeCompletedToday(includeCompletedToday);
        if (includeCompletedToday) {
            request.setCompletedTodayFrom(completedTodayFrom)
                    .setCompletedTodayTo(completedTodayTo);
        }
        return call(() -> auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                .getHomeworksForWeek(request.build()), HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE);
    }

    public SetHomeworkCompletionResponse setHomeworkCompletion(long homeworkId, long semesterId,
                                                                boolean completed) {
        return call(() -> auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                .setHomeworkCompletion(SetHomeworkCompletionRequest.newBuilder()
                        .setHomeworkId(homeworkId)
                        .setSemesterId(semesterId)
                        .setCompleted(completed)
                        .build()), HttpStatus.NOT_FOUND, ProblemCode.HOMEWORK_NOT_FOUND);
    }

    private static <T> T call(java.util.concurrent.Callable<T> action) {
        return call(action, HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE);
    }

    private static <T> T call(java.util.concurrent.Callable<T> action,
                              HttpStatus notFoundStatus,
                              ProblemCode notFoundCode) {
        try {
            return action.call();
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new MobileBffException(notFoundStatus, notFoundCode,
                        "Запрошенные данные недоступны в текущем scope");
            }
            if (error.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED) {
                throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE,
                        "Запрошенные данные недоступны в текущем scope");
            }
            if (error.getStatus().getCode() == io.grpc.Status.Code.INVALID_ARGUMENT) {
                throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                        "Academic Service отклонил запрос");
            }
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE,
                    "Academic Service временно недоступен");
        } catch (MobileBffException error) {
            throw error;
        } catch (Exception error) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE,
                    "Academic Service временно недоступен");
        }
    }
}
