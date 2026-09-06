package ru.rutcampustrack.mobilebff.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.schedule.grpc.LessonsByGroupRequest;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Component
public class MobileScheduleClient {
    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;
    private final MobileGrpcAuth auth;

    public MobileScheduleClient(MobileGrpcAuth auth) { this.auth = auth; }

    public LessonsResponse lessons(long groupId, long semesterId, LocalDate from, LocalDate to) {
        try {
            return auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS).getLessonsByGroup(
                    LessonsByGroupRequest.newBuilder()
                            .setGroupId(groupId).setSemesterId(semesterId)
                            .setDateFrom(from.toString()).setDateTo(to.toString()).build());
        } catch (StatusRuntimeException error) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCode.DEPENDENCY_UNAVAILABLE,
                    "Schedule Service временно недоступен");
        }
    }
}
