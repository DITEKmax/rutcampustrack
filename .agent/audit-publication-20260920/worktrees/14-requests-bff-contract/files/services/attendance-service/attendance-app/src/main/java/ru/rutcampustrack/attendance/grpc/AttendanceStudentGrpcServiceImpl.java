package ru.rutcampustrack.attendance.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.studentrequest.RequestBucket;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinResolutionReason;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.StudentAttendanceSnapshotService;
import ru.rutcampustrack.attendance.student.StudentCheckinException;
import ru.rutcampustrack.attendance.student.StudentCheckinModels;
import ru.rutcampustrack.attendance.student.StudentCheckinService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@GrpcService
public class AttendanceStudentGrpcServiceImpl
        extends AttendanceStudentGrpcServiceGrpc.AttendanceStudentGrpcServiceImplBase {

    private final StudentCheckinService checkinService;
    private final StudentAttendanceSnapshotService snapshotService;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final SemesterCacheService semesterCacheService;
    private final StudentRequestService requestService;

    public AttendanceStudentGrpcServiceImpl(
            StudentCheckinService checkinService,
            StudentAttendanceSnapshotService snapshotService,
            ScheduleGrpcClient scheduleGrpcClient,
            AcademicGrpcClient academicGrpcClient,
            SemesterCacheService semesterCacheService
    ) {
        this(checkinService, snapshotService, scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, null);
    }

    @Autowired
    public AttendanceStudentGrpcServiceImpl(
            StudentCheckinService checkinService,
            StudentAttendanceSnapshotService snapshotService,
            ScheduleGrpcClient scheduleGrpcClient,
            AcademicGrpcClient academicGrpcClient,
            SemesterCacheService semesterCacheService,
            StudentRequestService requestService
    ) {
        this.checkinService = checkinService;
        this.snapshotService = snapshotService;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.semesterCacheService = semesterCacheService;
        this.requestService = requestService;
    }

    @Override
    public void getStudentAttendanceSnapshot(
            StudentAttendanceSnapshotRequest request,
            StreamObserver<StudentAttendanceSnapshotResponse> observer
    ) {
        try {
            InternalJwtClaims claims = requireClaims();
            var snapshot = snapshotService.getSnapshot(identity(claims, null), request.getLessonIdsList());
            StudentAttendanceSnapshotResponse.Builder response = StudentAttendanceSnapshotResponse.newBuilder()
                    .setServerNow(snapshot.serverNow().toString());
            snapshot.entries().forEach(entry -> response.addEntries(toProto(entry)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(mapError(error));
        }
    }

    @Override
    public void checkin(StudentCheckinCommand command, StreamObserver<StudentCheckinResult> observer) {
        try {
            InternalJwtClaims claims = requireClaims();
            StudentCheckinModels.Geo geo = parseGeo(command);
            StudentCheckinModels.Identity identity = identity(claims, null);
            var replay = checkinService.replay(
                    identity, command.getLessonId(), command.getIdempotencyKey(), geo);
            if (replay != null) {
                observer.onNext(toProto(replay));
                observer.onCompleted();
                return;
            }
            LessonResponse lesson = scheduleGrpcClient.getLessonById(command.getLessonId());
            String displayName = academicGrpcClient.getUserDisplayName(claims.userId());
            StudentCheckinModels.Lesson domainLesson = new StudentCheckinModels.Lesson(
                    lesson.getId(), lesson.getGroupId(), lesson.getSubjectId(),
                    semesterCacheService.getActiveSemesterId(), lesson.getLessonNumber(),
                    LocalDate.parse(lesson.getDate()), LocalTime.parse(lesson.getStartTime()),
                    LocalTime.parse(lesson.getEndTime()), lesson.getStatus(),
                    lesson.getIsGeoBlocked() || lesson.getIsBlockedByHeadman());
            var ack = checkinService.checkin(
                    identity(claims, displayName), domainLesson, command.getIdempotencyKey(), geo);
            observer.onNext(toProto(ack));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(mapError(error));
        }
    }

    @Override
    public void listStudentRequests(StudentRequestListQuery query,
                                    StreamObserver<StudentRequestPage> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            RequestBucket bucket = query.getBucket() == StudentRequestBucket.STUDENT_REQUEST_BUCKET_ARCHIVE
                    ? RequestBucket.ARCHIVE : RequestBucket.OPEN;
            Integer page = query.hasPage() ? query.getPage() : null;
            Integer size = query.hasSize() ? query.getSize() : null;
            observer.onNext(StudentRequestGrpcMapper.page(requireRequestService().list(identity, bucket, page, size)));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void getStudentRequest(StudentRequestId request,
                                  StreamObserver<StudentRequestDetail> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            validateObjectId(request.getRequestId(), "request_id");
            observer.onNext(StudentRequestGrpcMapper.detail(
                    requireRequestService().get(identity, request.getRequestId())));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void getStudentRequestOptions(StudentRequestOptionsQuery query,
                                         StreamObserver<StudentRequestOptions> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            observer.onNext(StudentRequestGrpcMapper.options(requireRequestService().options(identity)));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void submitStudentExcuse(SubmitStudentExcuseCommand command,
                                    StreamObserver<StudentRequestDetail> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            if (command.getIdempotencyKey().isBlank()) {
                throw new ru.rutcampustrack.attendance.exception.InvalidIdempotencyKeyException();
            }
            List<StudentRequestModels.AttachmentInput> attachments = command.getAttachmentsList().stream()
                    .map(file -> new StudentRequestModels.AttachmentInput(
                            file.getName(), file.getDeclaredContentType(), file.getData().toByteArray()))
                    .toList();
            StudentRequestModels.ExcuseSubmission submission = new StudentRequestModels.ExcuseSubmission(
                    command.getLessonIdsList(), parseReason(command.getReason()),
                    command.hasComment() ? command.getComment() : null,
                    attachments, command.getIdempotencyKey());
            observer.onNext(StudentRequestGrpcMapper.detail(
                    requireRequestService().submitExcuse(identity, submission)));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void submitStudentLateCheckin(SubmitStudentLateCheckinCommand command,
                                         StreamObserver<StudentRequestDetail> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            if (command.getIdempotencyKey().isBlank()) {
                throw new ru.rutcampustrack.attendance.exception.InvalidIdempotencyKeyException();
            }
            observer.onNext(StudentRequestGrpcMapper.detail(requireRequestService().submitLateCheckin(
                    identity, new StudentRequestModels.LateCheckinSubmission(
                            command.getLessonId(), command.getIdempotencyKey()))));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void cancelStudentRequest(StudentRequestId request,
                                     StreamObserver<StudentRequestDetail> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            validateObjectId(request.getRequestId(), "request_id");
            observer.onNext(StudentRequestGrpcMapper.detail(
                    requireRequestService().cancel(identity, request.getRequestId())));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void downloadStudentRequestAttachment(StudentRequestAttachmentId request,
                                                  StreamObserver<StudentRequestAttachmentDownload> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            validateObjectId(request.getRequestId(), "request_id");
            validateObjectId(request.getAttachmentId(), "attachment_id");
            observer.onNext(StudentRequestGrpcMapper.download(requireRequestService().download(
                    identity, request.getRequestId(), request.getAttachmentId())));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    private StudentRequestService requireRequestService() {
        if (requestService == null) {
            throw new ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException(
                    "Student request transport is not configured");
        }
        return requestService;
    }

    private static StudentRequestModels.Identity requestIdentity(InternalJwtClaims claims) {
        if (claims == null || claims.userId() == null || claims.userId() <= 0
                || claims.groupId() == null || claims.groupId() <= 0) {
            throw new StudentRequestTransportException(
                    StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_OUT_OF_SCOPE,
                    "Authenticated student scope is missing");
        }
        if (claims.role() == null || !"STUDENT".equalsIgnoreCase(claims.role())) {
            throw new StudentRequestTransportException(
                    StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_WRONG_ROLE,
                    "Student request API requires STUDENT role");
        }
        return new StudentRequestModels.Identity(claims.userId(), UserRole.STUDENT,
                claims.groupId(), claims.isHeadman());
    }

    private static ExcuseType parseReason(StudentExcuseReason reason) {
        if (reason == null || reason == StudentExcuseReason.STUDENT_EXCUSE_REASON_UNSPECIFIED
                || reason == StudentExcuseReason.UNRECOGNIZED) {
            throw new BadRequestException("Excuse reason is required");
        }
        try {
            return ExcuseType.valueOf(reason.name().replace("STUDENT_EXCUSE_REASON_", ""));
        } catch (IllegalArgumentException error) {
            throw new BadRequestException("Excuse reason is not supported");
        }
    }

    private static void validateObjectId(String value, String field) {
        if (value == null || !value.matches("[0-9a-fA-F]{24}")) {
            throw new BadRequestException(field + " must be a 24-character hexadecimal id");
        }
    }

    private static InternalJwtClaims requireClaims() {
        InternalJwtClaims claims = StudentGrpcIdentity.CLAIMS.get();
        if (claims == null) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_SESSION,
                    "Internal identity is missing");
        }
        return claims;
    }

    private static StudentCheckinModels.Identity identity(InternalJwtClaims claims, String displayName) {
        return new StudentCheckinModels.Identity(
                claims.userId() == null ? 0 : claims.userId(),
                claims.role(), claims.groupId(), claims.isHeadman(), displayName);
    }

    private static StudentCheckinModels.Geo parseGeo(StudentCheckinCommand command) {
        if (command.getLessonId() <= 0) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "lesson_id должен быть положительным");
        }
        return switch (command.getGeoCase()) {
            case COORDINATES -> {
                Coordinates coordinates = command.getCoordinates();
                if (!coordinates.hasLatitude() || !coordinates.hasLongitude()) {
                    throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                            "Обе координаты обязательны");
                }
                yield new StudentCheckinModels.Coordinates(
                        coordinates.getLatitude(), coordinates.getLongitude());
            }
            case UNAVAILABLE -> {
                GeoUnavailableReason reason = command.getUnavailable().getReason();
                if (reason == GeoUnavailableReason.GEO_UNAVAILABLE_REASON_UNSPECIFIED
                        || reason == GeoUnavailableReason.UNRECOGNIZED) {
                    throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                            "Причина недоступной геолокации обязательна");
                }
                yield new StudentCheckinModels.Unavailable(reason.name());
            }
            case GEO_NOT_SET -> throw new StudentCheckinException(
                    StudentCheckinException.Code.INVALID_REQUEST, "geo обязателен");
        };
    }

    private static StudentAttendanceEntry toProto(StudentAttendanceSnapshotService.Entry entry) {
        StudentAttendanceEntry.Builder result = StudentAttendanceEntry.newBuilder()
                .setLessonId(entry.lessonId())
                .setStatus(toProtoStatus(entry.attendanceStatus()))
                .setSource(toProtoSource(entry.attendanceSource()))
                .setEligibility(toProto(entry.eligibility()));
        if (entry.markedAt() != null) result.setMarkedAt(entry.markedAt().toString());
        if (entry.request() != null) result.setRequest(toProto(entry.request()));
        if (entry.retryAt() != null) result.setRetryAt(entry.retryAt().toString());
        return result.build();
    }

    private static StudentCheckinResult toProto(StudentCheckinModels.Ack ack) {
        StudentCheckinResult.Builder result = StudentCheckinResult.newBuilder()
                .setOutcome(ack.outcome() == StudentCheckinModels.Outcome.PRESENT
                        ? CheckinOutcome.CHECKIN_OUTCOME_PRESENT
                        : CheckinOutcome.CHECKIN_OUTCOME_PENDING_CONFIRMATION)
                .setLessonId(ack.lessonId())
                .setServerNow(ack.serverNow().toString());
        if (ack.attendance() != null) {
            result.setAttendance(StudentAttendanceEntry.newBuilder()
                    .setLessonId(ack.lessonId())
                    .setStatus(toProtoStatus(ack.attendance().status()))
                    .setSource(toProtoSource(ack.attendance().source()))
                    .setMarkedAt(ack.attendance().markedAt().toString())
                    .build());
        }
        if (ack.request() != null) {
            result.setRequest(AutomaticCheckinRequest.newBuilder()
                    .setId(ack.request().id())
                    .setStatus(toProtoRequestStatus(ack.request().status()))
                    .setOrigin(AutomaticCheckinRequestOrigin.AUTOMATIC_CHECKIN_REQUEST_ORIGIN_AUTO_GEO_FAILURE)
                    .build());
        }
        if (ack.retryAt() != null) result.setRetryAt(ack.retryAt().toString());
        return result.build();
    }

    private static AutomaticCheckinRequest toProto(
            ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest request) {
        AutomaticCheckinRequest.Builder result = AutomaticCheckinRequest.newBuilder()
                .setId(request.getId())
                .setStatus(toProtoRequestStatus(request.getStatus()))
                .setOrigin(AutomaticCheckinRequestOrigin.AUTOMATIC_CHECKIN_REQUEST_ORIGIN_AUTO_GEO_FAILURE);
        if (request.getResolutionReason() != null) {
            result.setResolutionReason(switch (request.getResolutionReason()) {
                case GEO_CONFIRMED -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_GEO_CONFIRMED;
                case HEADMAN_APPROVED -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_HEADMAN_APPROVED;
                case HEADMAN_REJECTED -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_HEADMAN_REJECTED;
                case CANCELLED_BY_STUDENT -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_STUDENT_CANCELLED;
                case PRESENT_PRIORITY -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_PRESENT_PRIORITY;
            });
        }
        return result.build();
    }

    private static AutomaticCheckinRequestStatus toProtoRequestStatus(
            ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus status) {
        return switch (status) {
            case PENDING -> AutomaticCheckinRequestStatus.AUTOMATIC_CHECKIN_REQUEST_STATUS_PENDING;
            case APPROVED -> AutomaticCheckinRequestStatus.AUTOMATIC_CHECKIN_REQUEST_STATUS_APPROVED;
            case REJECTED -> AutomaticCheckinRequestStatus.AUTOMATIC_CHECKIN_REQUEST_STATUS_REJECTED;
            case CANCELLED -> AutomaticCheckinRequestStatus.AUTOMATIC_CHECKIN_REQUEST_STATUS_CANCELLED;
        };
    }

    private static StudentCheckinEligibility toProto(StudentAttendanceSnapshotService.Eligibility eligibility) {
        StudentCheckinEligibility.Builder result = StudentCheckinEligibility.newBuilder()
                .setAllowed(eligibility.allowed())
                .setReason(StudentCheckinEligibilityReason.valueOf(
                        "STUDENT_CHECKIN_ELIGIBILITY_REASON_" + eligibility.reason().name()));
        if (eligibility.retryAt() != null) result.setRetryAt(eligibility.retryAt().toString());
        return result.build();
    }

    private static ru.rutcampustrack.attendance.grpc.AttendanceStatus toProtoStatus(AttendanceStatus status) {
        if (status == null) return ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_UNSPECIFIED;
        return switch (status) {
            case PRESENT -> ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_PRESENT;
            case ABSENT -> ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_ABSENT;
            case EXCUSED, FREE_ATTENDANCE -> ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_EXCUSED;
            case CANCELLED -> ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_UNSPECIFIED;
        };
    }

    private static ru.rutcampustrack.attendance.grpc.AttendanceSource toProtoSource(AttendanceSource source) {
        if (source == null) return ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_UNSPECIFIED;
        return switch (source) {
            case STUDENT_GEO -> ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_STUDENT_GEO;
            case LATE_CHECKIN -> ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_LATE_CHECKIN;
            case HEADMAN, HEADMAN_EXCUSE -> ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_HEADMAN;
            case AUTO_SCHEDULER -> ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_SYSTEM;
        };
    }

    private static RuntimeException mapError(RuntimeException error) {
        if (error instanceof StudentCheckinException checkin) {
            return StudentCheckinGrpcErrors.toStatus(checkin);
        }
        if (error instanceof ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException) {
            return StudentCheckinGrpcErrors.toStatus(new StudentCheckinException(
                    StudentCheckinException.Code.LESSON_NOT_FOUND, "Пара не найдена"));
        }
        if (error instanceof ScheduleServiceUnavailableException
                || error instanceof AcademicServiceUnavailableException) {
            return StudentCheckinGrpcErrors.toStatus(new StudentCheckinException(
                    StudentCheckinException.Code.DEPENDENCY_UNAVAILABLE,
                    "Обязательный сервис временно недоступен"));
        }
        return io.grpc.Status.INTERNAL.withDescription("Student attendance command failed")
                .withCause(error).asRuntimeException();
    }
}
