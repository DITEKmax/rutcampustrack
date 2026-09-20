package ru.rutcampustrack.attendance.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
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

    public AttendanceStudentGrpcServiceImpl(
            StudentCheckinService checkinService,
            StudentAttendanceSnapshotService snapshotService,
            ScheduleGrpcClient scheduleGrpcClient,
            AcademicGrpcClient academicGrpcClient,
            SemesterCacheService semesterCacheService
    ) {
        this.checkinService = checkinService;
        this.snapshotService = snapshotService;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.semesterCacheService = semesterCacheService;
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
                case PRESENT_PRIORITY -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_UNSPECIFIED;
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
