package ru.rutcampustrack.mobilebff.grpc;

import com.google.protobuf.Any;
import com.google.rpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.teacher.grpc.TeacherAttachmentDownload;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReadServiceGrpc;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceExportRequest;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceExportResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsExportRequest;
import ru.rutcampustrack.teacher.grpc.TeacherStatsExportResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReportKind;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseAttachmentRequest;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseRequest;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalRequest;
import ru.rutcampustrack.teacher.grpc.TeacherJournalResponse;
import ru.rutcampustrack.teacher.grpc.TeacherLessonRequest;
import ru.rutcampustrack.teacher.grpc.TeacherLessonResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsFilter;
import ru.rutcampustrack.teacher.grpc.TeacherStatsRequest;
import ru.rutcampustrack.teacher.grpc.TeacherStatsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsScope;
import ru.rutcampustrack.teacher.grpc.TeacherStatsSort;

import java.time.Instant;
import java.util.List;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Component
public class MobileAttendanceClient {
    private static final Logger log = LoggerFactory.getLogger(MobileAttendanceClient.class);
    private static final String INTERNAL_ERROR_MESSAGE = "Внутренняя ошибка сервера";
    private static final String DEPENDENCY_UNAVAILABLE_MESSAGE = "Attendance Service временно недоступен";

    @GrpcClient("attendance-service")
    private AttendanceStudentGrpcServiceGrpc.AttendanceStudentGrpcServiceBlockingStub stub;
    @GrpcClient("attendance-service")
    private TeacherAttendanceReadServiceGrpc.TeacherAttendanceReadServiceBlockingStub teacherStub;
    private final MobileGrpcAuth auth;

    public MobileAttendanceClient(MobileGrpcAuth auth) { this.auth = auth; }

    public StudentAttendanceSnapshotResponse snapshot(List<Long> lessonIds) {
        return call("snapshot", () -> auth.attach(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
                .getStudentAttendanceSnapshot(StudentAttendanceSnapshotRequest.newBuilder()
                .addAllLessonIds(lessonIds).build()), ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentAttendanceProjectionResponse projection(
            long semesterId,
            Long subjectId,
            String range,
            List<String> lessonTypes) {
        StudentAttendanceProjectionRequest.Builder request = StudentAttendanceProjectionRequest.newBuilder()
                .setSemesterId(semesterId)
                .setRange(range == null ? "" : range);
        if (subjectId != null) request.setSubjectId(subjectId);
        if (lessonTypes != null) request.addAllLessonTypes(lessonTypes);
        return call("projection", () -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .getStudentAttendanceProjection(request.build()), ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentAttendanceRankingResponse ranking(long semesterId, Integer page, int size) {
        StudentAttendanceRankingRequest.Builder request = StudentAttendanceRankingRequest.newBuilder()
                .setSemesterId(semesterId)
                .setSize(size);
        if (page != null) request.setPage(page);
        return call("ranking", () -> auth.attach(stub).withDeadlineAfter(10, TimeUnit.SECONDS)
                .getStudentAttendanceRanking(request.build()), ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public TeacherLessonResponse teacherLesson(long lessonId) {
        return call("teacherLesson", () -> auth.attach(teacherStub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .getTeacherLesson(TeacherLessonRequest.newBuilder().setLessonId(lessonId).build()),
                ProblemCode.LESSON_NOT_FOUND);
    }

    public TeacherJournalResponse teacherJournal(List<Long> lessonIds) {
        return call("teacherJournal", () -> auth.attach(teacherStub).withDeadlineAfter(10, TimeUnit.SECONDS)
                .getTeacherJournal(TeacherJournalRequest.newBuilder().addAllLessonIds(lessonIds).build()),
                ProblemCode.LESSON_NOT_FOUND);
    }

    public TeacherAttendanceExportResponse exportTeacherAttendance(long semesterId,
                                                                   long groupId,
                                                                   long subjectId,
                                                                   List<String> lessonTypes,
                                                                   LocalDate dateFrom,
                                                                   LocalDate dateTo,
                                                                   String format) {
        TeacherAttendanceExportRequest request = TeacherAttendanceExportRequest.newBuilder()
                .setSemesterId(semesterId)
                .setGroupId(groupId)
                .setSubjectId(subjectId)
                .addAllLessonTypes(lessonTypes)
                .setFormat(format)
                .setReportKind(TeacherAttendanceReportKind.TEACHER_ATTENDANCE_REPORT_KIND_SUBJECT_JOURNAL)
                .setDateFrom(dateFrom.toString())
                .setDateTo(dateTo.toString())
                .build();
        try {
            return auth.attach(teacherStub).withDeadlineAfter(60, TimeUnit.SECONDS)
                    .exportTeacherAttendance(request);
        } catch (StatusRuntimeException error) {
            log.warn("Attendance gRPC request failed: operation={}, code={}", "exportTeacherAttendance", error.getStatus().getCode());
            if (error.getStatus().getCode() == Code.RESOURCE_EXHAUSTED) {
                throw new MobileBffException(HttpStatus.PAYLOAD_TOO_LARGE, ProblemCode.PAYLOAD_TOO_LARGE,
                        "Экспорт превысил лимит передачи (до 20 МиБ; для PDF/PNG действует предел 4 МиБ сервиса преобразования). Попробуй DOCX, HTML или XLSX.");
            }
            throw translate(error, ProblemCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    public TeacherStatsResponse teacherStats(long semesterId,
                                             List<Long> lessonIds,
                                             TeacherStatsScope scope,
                                             long groupId,
                                             long subjectId,
                                             List<String> lessonTypes,
                                             List<TeacherStatsSort> sorts,
                                             List<TeacherStatsFilter> filters) {
        TeacherStatsRequest.Builder request = TeacherStatsRequest.newBuilder()
                .setSemesterId(semesterId)
                .setScope(scope)
                .addAllLessonIds(lessonIds == null ? List.of() : lessonIds)
                .addAllLessonTypes(lessonTypes == null ? List.of() : lessonTypes)
                .addAllSorts(sorts == null ? List.of() : sorts)
                .addAllFilters(filters == null ? List.of() : filters);
        if (groupId > 0) request.setGroupId(groupId);
        if (subjectId > 0) request.setSubjectId(subjectId);
        return teacherStats(request.build());
    }

    public TeacherStatsResponse teacherStats(TeacherStatsRequest request) {
        return call("teacherStats", () -> auth.attach(teacherStub).withDeadlineAfter(15, TimeUnit.SECONDS)
                .getTeacherStats(request), ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public TeacherStatsExportResponse exportTeacherStats(TeacherStatsRequest statsRequest, String format) {
        TeacherStatsExportRequest request = TeacherStatsExportRequest.newBuilder()
                .setStatsRequest(statsRequest)
                .setFormat(format)
                .build();
        try {
            return auth.attach(teacherStub).withDeadlineAfter(60, TimeUnit.SECONDS)
                    .exportTeacherStats(request);
        } catch (StatusRuntimeException error) {
            log.warn("Attendance gRPC request failed: operation={}, code={}", "exportTeacherStats", error.getStatus().getCode());
            if (error.getStatus().getCode() == Code.RESOURCE_EXHAUSTED) {
                throw new MobileBffException(HttpStatus.PAYLOAD_TOO_LARGE, ProblemCode.PAYLOAD_TOO_LARGE,
                        "Экспорт превысил лимит передачи (до 20 МиБ; для PDF/PNG действует предел 4 МиБ сервиса преобразования). Попробуй DOCX, HTML или XLSX.");
            }
            throw translate(error, ProblemCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    public TeacherExcuseResponse teacherExcuse(String requestId) {
        return call("teacherExcuse", () -> auth.attach(teacherStub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .getTeacherExcuse(TeacherExcuseRequest.newBuilder().setRequestId(requestId).build()),
                ProblemCode.REQUEST_NOT_FOUND);
    }

    public TeacherAttachmentDownload teacherExcuseAttachment(String requestId, String attachmentId) {
        return call("teacherExcuseAttachment", () -> auth.attach(teacherStub).withDeadlineAfter(10, TimeUnit.SECONDS)
                .downloadTeacherExcuseAttachment(TeacherExcuseAttachmentRequest.newBuilder()
                        .setRequestId(requestId).setAttachmentId(attachmentId).build()),
                ProblemCode.ATTACHMENT_NOT_FOUND);
    }

    public StudentCheckinResult checkin(StudentCheckinCommand command) {
        return call("checkin", () -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS).checkin(command),
                ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentRequestPage listRequests(StudentRequestBucket bucket, Integer page, Integer size) {
        StudentRequestListQuery.Builder query = StudentRequestListQuery.newBuilder()
                .setBucket(bucket == null ? StudentRequestBucket.STUDENT_REQUEST_BUCKET_OPEN : bucket);
        if (page != null) query.setPage(page);
        if (size != null) query.setSize(size);
        return call("listRequests", () -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .listStudentRequests(query.build()), ProblemCode.REQUEST_NOT_FOUND);
    }

    public StudentRequestOptions requestOptions() {
        return call("requestOptions", () -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .getStudentRequestOptions(StudentRequestOptionsQuery.getDefaultInstance()),
                ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentRequestDetail getRequest(String requestId) {
        return call("getRequest", () -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .getStudentRequest(StudentRequestId.newBuilder().setRequestId(requestId).build()),
                ProblemCode.REQUEST_NOT_FOUND);
    }

    public StudentRequestDetail submitExcuse(SubmitStudentExcuseCommand command) {
        return call("submitExcuse", () -> auth.attach(stub).withDeadlineAfter(10, TimeUnit.SECONDS)
                .submitStudentExcuse(command), ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentRequestDetail submitLateCheckin(long lessonId, String idempotencyKey) {
        return call("submitLateCheckin", () -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .submitStudentLateCheckin(SubmitStudentLateCheckinCommand.newBuilder()
                        .setLessonId(lessonId).setIdempotencyKey(idempotencyKey == null ? "" : idempotencyKey)
                        .build()), ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    public StudentRequestDetail cancelRequest(String requestId) {
        return call("cancelRequest", () -> auth.attach(stub).withDeadlineAfter(5, TimeUnit.SECONDS)
                .cancelStudentRequest(StudentRequestId.newBuilder().setRequestId(requestId).build()),
                ProblemCode.REQUEST_NOT_FOUND);
    }

    public StudentRequestAttachmentDownload downloadAttachment(String requestId, String attachmentId) {
        return call("downloadAttachment", () -> auth.attach(stub).withDeadlineAfter(10, TimeUnit.SECONDS)
                .downloadStudentRequestAttachment(StudentRequestAttachmentId.newBuilder()
                        .setRequestId(requestId).setAttachmentId(attachmentId).build()),
                ProblemCode.ATTACHMENT_NOT_FOUND);
    }

    private static <T> T call(String operation, Callable<T> action, ProblemCode notFoundCode) {
        try {
            return action.call();
        } catch (StatusRuntimeException error) {
            log.warn("Attendance gRPC request failed: operation={}, code={}", operation, error.getStatus().getCode());
            throw translate(error, notFoundCode);
        } catch (MobileBffException error) {
            throw error;
        } catch (Exception error) {
            log.warn("Attendance request failed: operation={}, failure=NON_GRPC", operation);
            throw internalError();
        }
    }

    static MobileBffException translate(StatusRuntimeException error) {
        return translate(error, ProblemCode.DEPENDENCY_UNAVAILABLE);
    }

    static MobileBffException translate(StatusRuntimeException error, ProblemCode notFoundCode) {
        Code rawCode = error.getStatus().getCode();
        ProblemCode rawProblemCode = rawProblemCode(rawCode);
        if (rawProblemCode != null) {
            return new MobileBffException(httpStatus(rawProblemCode), rawProblemCode,
                    message(rawProblemCode));
        }
        Status status;
        try {
            status = StatusProto.fromThrowable(error);
        } catch (RuntimeException malformedStatus) {
            return internalError();
        }
        if (status != null) {
            for (Any detail : status.getDetailsList()) {
                if (detail.is(StudentCheckinErrorDetail.class)) {
                    try {
                        StudentCheckinErrorDetail typed = detail.unpack(StudentCheckinErrorDetail.class);
                        ProblemCode code = problemCode(typed.getCode().name(), "STUDENT_CHECKIN_ERROR_CODE_");
                        if (code == null || code == ProblemCode.INTERNAL_ERROR) {
                            return internalError();
                        }
                        Instant retryAt = typed.hasRetryAt() ? Instant.parse(typed.getRetryAt()) : null;
                        return new MobileBffException(httpStatus(code), code, status.getMessage(), retryAt);
                    } catch (Exception malformed) {
                        return internalError();
                    }
                }
                if (detail.is(StudentRequestErrorDetail.class)) {
                    try {
                        StudentRequestErrorDetail typed = detail.unpack(StudentRequestErrorDetail.class);
                        ProblemCode code = problemCode(typed.getCode().name(), "STUDENT_REQUEST_ERROR_CODE_");
                        if (code == null || code == ProblemCode.INTERNAL_ERROR) {
                            return internalError();
                        }
                        return new MobileBffException(httpStatus(code), code,
                                requestMessage(code));
                    } catch (Exception malformed) {
                        return internalError();
                    }
                }
            }
        }
        ProblemCode code = switch (rawCode) {
            case UNAUTHENTICATED -> ProblemCode.INVALID_SESSION;
            case PERMISSION_DENIED -> ProblemCode.OUT_OF_SCOPE;
            case INVALID_ARGUMENT -> ProblemCode.INVALID_REQUEST;
            case NOT_FOUND -> notFoundCode;
            case ABORTED -> ProblemCode.REQUEST_CONFLICT;
            case FAILED_PRECONDITION -> ProblemCode.ATTACHMENT_EXPIRED;
            case RESOURCE_EXHAUSTED -> ProblemCode.PAYLOAD_TOO_LARGE;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> ProblemCode.DEPENDENCY_UNAVAILABLE;
            default -> ProblemCode.INTERNAL_ERROR;
        };
        return new MobileBffException(httpStatus(code), code,
                message(code));
    }

    private static ProblemCode problemCode(String value, String prefix) {
        if (!value.startsWith(prefix)) {
            return null;
        }
        try {
            return ProblemCode.valueOf(value.substring(prefix.length()));
        } catch (IllegalArgumentException unknownCode) {
            return null;
        }
    }

    private static ProblemCode rawProblemCode(Code code) {
        return switch (code) {
            case UNAUTHENTICATED -> ProblemCode.INVALID_SESSION;
            case PERMISSION_DENIED -> ProblemCode.OUT_OF_SCOPE;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> ProblemCode.DEPENDENCY_UNAVAILABLE;
            case INTERNAL, UNKNOWN, DATA_LOSS -> ProblemCode.INTERNAL_ERROR;
            default -> null;
        };
    }

    private static MobileBffException internalError() {
        return new MobileBffException(HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE);
    }

    private static String message(ProblemCode code) {
        return switch (code) {
            case INTERNAL_ERROR -> INTERNAL_ERROR_MESSAGE;
            case INVALID_SESSION -> "Сессия недействительна";
            case DEPENDENCY_UNAVAILABLE -> DEPENDENCY_UNAVAILABLE_MESSAGE;
            case LESSON_NOT_FOUND -> "Пара не найдена";
            case HOMEWORK_NOT_FOUND -> "Домашнее задание не найдено";
            default -> requestMessage(code);
        };
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
            case INTERNAL_ERROR -> INTERNAL_ERROR_MESSAGE;
            default -> "Операция запроса отклонена";
        };
    }

    private static HttpStatus httpStatus(ProblemCode code) {
        return switch (code) {
            case INVALID_REQUEST, INVALID_IDEMPOTENCY_KEY -> HttpStatus.BAD_REQUEST;
            case INVALID_SESSION -> HttpStatus.UNAUTHORIZED;
            case WRONG_ROLE, OUT_OF_SCOPE, ROLE_READ_ONLY -> HttpStatus.FORBIDDEN;
            case LESSON_NOT_FOUND, HOMEWORK_NOT_FOUND, REQUEST_NOT_FOUND, ATTACHMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ATTACHMENT_EXPIRED -> HttpStatus.GONE;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case CHECKIN_COOLDOWN -> HttpStatus.TOO_MANY_REQUESTS;
            case MANUAL_ABSENCE_REQUIRES_APPEAL, IDEMPOTENCY_PAYLOAD_MISMATCH, CHECKIN_NOT_ELIGIBLE,
                 REQUEST_CONFLICT -> HttpStatus.CONFLICT;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
