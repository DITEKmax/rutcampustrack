package ru.rutcampustrack.mobilebff.grpc;

import com.google.protobuf.Any;
import com.google.rpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.error.MobileBffException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class MobileAttendanceClient {
    @GrpcClient("attendance-service")
    private AttendanceStudentGrpcServiceGrpc.AttendanceStudentGrpcServiceBlockingStub stub;
    private final MobileGrpcAuth auth;

    public MobileAttendanceClient(MobileGrpcAuth auth) { this.auth = auth; }

    public StudentAttendanceSnapshotResponse snapshot(List<Long> lessonIds) {
        try {
            return auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getStudentAttendanceSnapshot(StudentAttendanceSnapshotRequest.newBuilder()
                            .addAllLessonIds(lessonIds).build());
        } catch (StatusRuntimeException error) {
            throw translate(error);
        }
    }

    public StudentCheckinResult checkin(StudentCheckinCommand command) {
        try {
            return auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS).checkin(command);
        } catch (StatusRuntimeException error) {
            throw translate(error);
        }
    }

    static MobileBffException translate(StatusRuntimeException error) {
        Status status = StatusProto.fromThrowable(error);
        if (status != null) {
            for (Any detail : status.getDetailsList()) {
                if (detail.is(StudentCheckinErrorDetail.class)) {
                    try {
                        StudentCheckinErrorDetail typed = detail.unpack(StudentCheckinErrorDetail.class);
                        ProblemCode code = ProblemCode.valueOf(typed.getCode().name()
                                .replace("STUDENT_CHECKIN_ERROR_CODE_", ""));
                        Instant retryAt = typed.hasRetryAt() ? Instant.parse(typed.getRetryAt()) : null;
                        return new MobileBffException(httpStatus(code), code, status.getMessage(), retryAt);
                    } catch (Exception malformed) {
                        break;
                    }
                }
            }
        }
        ProblemCode code = error.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED
                ? ProblemCode.INVALID_SESSION : ProblemCode.DEPENDENCY_UNAVAILABLE;
        return new MobileBffException(httpStatus(code), code,
                code == ProblemCode.INVALID_SESSION ? "Сессия недействительна" : "Attendance Service временно недоступен");
    }

    private static HttpStatus httpStatus(ProblemCode code) {
        return switch (code) {
            case INVALID_REQUEST, INVALID_IDEMPOTENCY_KEY -> HttpStatus.BAD_REQUEST;
            case INVALID_SESSION -> HttpStatus.UNAUTHORIZED;
            case WRONG_ROLE, OUT_OF_SCOPE, ROLE_READ_ONLY -> HttpStatus.FORBIDDEN;
            case LESSON_NOT_FOUND, HOMEWORK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CHECKIN_COOLDOWN -> HttpStatus.TOO_MANY_REQUESTS;
            case MANUAL_ABSENCE_REQUIRES_APPEAL, IDEMPOTENCY_PAYLOAD_MISMATCH, CHECKIN_NOT_ELIGIBLE -> HttpStatus.CONFLICT;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
