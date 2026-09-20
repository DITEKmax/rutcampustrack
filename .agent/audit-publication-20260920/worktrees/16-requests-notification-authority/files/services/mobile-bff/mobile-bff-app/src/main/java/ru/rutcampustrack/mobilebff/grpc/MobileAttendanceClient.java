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
import java.util.concurrent.Callable;

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
            throw translate(error, ProblemCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    public StudentRequestPage listRequests(StudentRequestBucket bucket, Integer page, Integer size) {
        StudentRequestListQuery.Builder query = StudentRequestListQuery.newBuilder()
                .setBucket(bucket == null ? StudentRequestBucket.STUDENT_REQUEST_BUCKET_OPEN : bucket);
        if (page != null) query.setPage(page);
        if (size != null) query.setSize(size);
        return call(() -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .listStudentRequests(query.build()), ProblemCode.REQUEST_NOT_FOUND);
    }

    public StudentRequestOptions requestOptions() {
        return call(() -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .getStudentRequestOptions(StudentRequestOptionsQuery.getDefaultInstance()),
                ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentRequestDetail getRequest(String requestId) {
        return call(() -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .getStudentRequest(StudentRequestId.newBuilder().setRequestId(requestId).build()),
                ProblemCode.REQUEST_NOT_FOUND);
    }

    public StudentRequestDetail submitExcuse(SubmitStudentExcuseCommand command) {
        return call(() -> auth.attach(stub).withDeadlineAfter(10, TimeUnit.SECONDS)
                .submitStudentExcuse(command), ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentRequestDetail submitLateCheckin(long lessonId, String idempotencyKey) {
        return call(() -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .submitStudentLateCheckin(SubmitStudentLateCheckinCommand.newBuilder()
                        .setLessonId(lessonId).setIdempotencyKey(idempotencyKey == null ? "" : idempotencyKey)
                        .build()), ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentRequestDetail cancelRequest(String requestId) {
        return call(() -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .cancelStudentRequest(StudentRequestId.newBuilder().setRequestId(requestId).build()),
                ProblemCode.REQUEST_NOT_FOUND);
    }

    public StudentRequestAttachmentDownload downloadAttachment(String requestId, String attachmentId) {
        return call(() -> auth.attach(stub).withDeadlineAfter(10, TimeUnit.SECONDS)
                .downloadStudentRequestAttachment(StudentRequestAttachmentId.newBuilder()
                        .setRequestId(requestId).setAttachmentId(attachmentId).build()),
                ProblemCode.ATTACHMENT_NOT_FOUND);
    }

    private static <T> T call(Callable<T> action, ProblemCode notFoundCode) {
        try {
            return action.call();
        } catch (StatusRuntimeException error) {
            throw translate(error, notFoundCode);
        } catch (MobileBffException error) {
            throw error;
        } catch (Exception error) {
            throw new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCode.DEPENDENCY_UNAVAILABLE, "Attendance Service временно недоступен");
        }
    }

    static MobileBffException translate(StatusRuntimeException error) {
        return translate(error, ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    static MobileBffException translate(StatusRuntimeException error, ProblemCode notFoundCode) {
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
                if (detail.is(StudentRequestErrorDetail.class)) {
                    try {
                        StudentRequestErrorDetail typed = detail.unpack(StudentRequestErrorDetail.class);
                        ProblemCode code = ProblemCode.valueOf(typed.getCode().name()
                                .replace("STUDENT_REQUEST_ERROR_CODE_", ""));
                        return new MobileBffException(httpStatus(code), code,
                                requestMessage(code));
                    } catch (Exception malformed) {
                        break;
                    }
                }
            }
        }
        ProblemCode code = switch (error.getStatus().getCode()) {
            case UNAUTHENTICATED -> ProblemCode.INVALID_SESSION;
            case PERMISSION_DENIED -> ProblemCode.OUT_OF_SCOPE;
            case INVALID_ARGUMENT -> ProblemCode.INVALID_REQUEST;
            case NOT_FOUND -> notFoundCode;
            case ABORTED -> ProblemCode.REQUEST_CONFLICT;
            case FAILED_PRECONDITION -> ProblemCode.ATTACHMENT_EXPIRED;
            case RESOURCE_EXHAUSTED -> ProblemCode.PAYLOAD_TOO_LARGE;
            default -> ProblemCode.DEPENDENCY_UNAVAILABLE;
        };
        return new MobileBffException(httpStatus(code), code,
                code == ProblemCode.INVALID_SESSION ? "Сессия недействительна" : "Attendance Service временно недоступен");
    }

    private static String requestMessage(ProblemCode code) {
        return switch (code) {
            case INVALID_REQUEST -> "Запрос не прошёл проверку";
            case INVALID_IDEMPOTENCY_KEY -> "Idempotency-Key не прошёл проверку";
            case INVALID_SESSION -> "Сессия недействительна";
            case WRONG_ROLE -> "Операция доступна только студенту";
            case OUT_OF_SCOPE -> "Запрошенные данные недоступны в текущем scope";
            case REQUEST_NOT_FOUND -> "Заявка не найдена";
            case ATTACHMENT_NOT_FOUND -> "Вложение не найдено";
            case REQUEST_CONFLICT -> "Операция конфликтует с текущим состоянием заявки";
            case ATTACHMENT_EXPIRED -> "Срок хранения вложения истёк";
            case PAYLOAD_TOO_LARGE -> "Размер запроса превышает допустимый предел";
            case DEPENDENCY_UNAVAILABLE -> "Attendance Service временно недоступен";
            default -> "Операция запроса отклонена";
        };
    }

    private static HttpStatus httpStatus(ProblemCode code) {
        return switch (code) {
            case INVALID_REQUEST, INVALID_IDEMPOTENCY_KEY -> HttpStatus.BAD_REQUEST;
            case INVALID_SESSION -> HttpStatus.UNAUTHORIZED;
            case WRONG_ROLE, OUT_OF_SCOPE -> HttpStatus.FORBIDDEN;
            case LESSON_NOT_FOUND, HOMEWORK_NOT_FOUND, REQUEST_NOT_FOUND, ATTACHMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ATTACHMENT_EXPIRED -> HttpStatus.GONE;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case CHECKIN_COOLDOWN -> HttpStatus.TOO_MANY_REQUESTS;
            case MANUAL_ABSENCE_REQUIRES_APPEAL, IDEMPOTENCY_PAYLOAD_MISMATCH, CHECKIN_NOT_ELIGIBLE,
                 REQUEST_CONFLICT -> HttpStatus.CONFLICT;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
