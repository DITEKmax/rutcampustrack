package ru.rutcampustrack.attendance.grpc;

import com.google.rpc.Status;
import io.grpc.Context;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.geofence.GeofenceService;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.CheckinPairStateRepository;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.student.StudentAttendanceSnapshotService;
import ru.rutcampustrack.attendance.student.StudentCheckinModels;
import ru.rutcampustrack.attendance.student.StudentCheckinReceiptRepository;
import ru.rutcampustrack.attendance.student.StudentCheckinService;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttendanceStudentGrpcServiceTest {

    private static final long STUDENT_ID = 100L;
    private static final long LESSON_ID = 77L;
    private static final String KEY = "headman-blocked-0001";
    private static final UUID SESSION_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final InternalJwtClaims CLAIMS =
            new InternalJwtClaims(STUDENT_ID, SESSION_ID, 1L, 1L,
                    "STUDENT", "ACTIVE", 10L, false, false);

    @ParameterizedTest(name = "blocked lesson rejects {0} (geo={1}, headman={2}) before any write")
    @MethodSource("geoCommands")
    void headmanBlockedLessonRejectsEveryGeoVariantBeforeMutation(
            String geoKind, boolean isGeoBlocked, boolean isBlockedByHeadman,
            StudentCheckinCommand command) throws Exception {
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        CheckinPairStateRepository pairRepository = mock(CheckinPairStateRepository.class);
        StudentCheckinReceiptRepository receiptRepository = mock(StudentCheckinReceiptRepository.class);
        LateCheckinRepository lateCheckinRepository = mock(LateCheckinRepository.class);
        PairWriteCoordinator pairCoordinator = mock(PairWriteCoordinator.class);
        GeofenceService geofenceService = mock(GeofenceService.class);
        AttendanceEventPublisher attendanceEvents = mock(AttendanceEventPublisher.class);
        LateCheckinEventPublisher lateCheckinEvents = mock(LateCheckinEventPublisher.class);
        BusinessMetrics metrics = mock(BusinessMetrics.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ScheduleGrpcClient scheduleGrpcClient = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academicGrpcClient = mock(AcademicGrpcClient.class);

        when(receiptRepository.findByStudentIdAndLessonIdAndIdempotencyKey(
                STUDENT_ID, LESSON_ID, KEY)).thenReturn(Optional.empty());
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(
                ru.rutcampustrack.schedule.grpc.LessonResponse.newBuilder()
                        .setId(LESSON_ID)
                        .setGroupId(10L)
                        .setSubjectId(5L)
                        .setSemesterId(30L)
                        .setLessonNumber(1)
                        .setDate("2026-09-06")
                        .setStartTime("00:00")
                        .setEndTime("23:59")
                        .setLessonType("SEMINAR")
                        .setStatus("active")
                        .setIsGeoBlocked(isGeoBlocked)
                        .setIsBlockedByHeadman(isBlockedByHeadman)
                        .build());
        when(academicGrpcClient.getUserDisplayName(STUDENT_ID)).thenReturn("Иван Иванов");
        when(academicGrpcClient.getSubjectDetailsByIds(List.of(5L))).thenReturn(Map.of(5L,
                new AcademicGrpcClient.SubjectDetails("Алгебра", "SUBJECT_TYPE")));

        StudentCheckinService checkinService = spy(new StudentCheckinService(
                attendanceRepository,
                pairRepository,
                receiptRepository,
                lateCheckinRepository,
                pairCoordinator,
                geofenceService,
                attendanceEvents,
                lateCheckinEvents,
                metrics,
                transactionTemplate,
                Clock.fixed(Instant.parse("2026-09-06T07:00:00Z"), ZoneOffset.UTC)));
        AttendanceStudentGrpcServiceImpl service = new AttendanceStudentGrpcServiceImpl(
                checkinService,
                mock(StudentAttendanceSnapshotService.class),
                scheduleGrpcClient,
                academicGrpcClient,
                mock(SemesterCacheService.class));
        RecordingObserver observer = new RecordingObserver();

        Context.current().withValue(StudentGrpcIdentity.CLAIMS, CLAIMS)
                .run(() -> service.checkin(command, observer));

        Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.FAILED_PRECONDITION.value());
        assertThat(status.getDetails(0).unpack(StudentCheckinErrorDetail.class).getCode())
                .isEqualTo(StudentCheckinErrorCode.STUDENT_CHECKIN_ERROR_CODE_CHECKIN_NOT_ELIGIBLE);
        assertThat(observer.value).as("blocked %s must not return a result", geoKind).isNull();

        ArgumentCaptor<StudentCheckinModels.Lesson> lessonCaptor =
                ArgumentCaptor.forClass(StudentCheckinModels.Lesson.class);
        verify(checkinService).checkin(any(), lessonCaptor.capture(), eq(KEY), any());
        assertThat(lessonCaptor.getValue())
                .extracting(StudentCheckinModels.Lesson::subjectName,
                        StudentCheckinModels.Lesson::subjectType,
                        StudentCheckinModels.Lesson::startsAt,
                        StudentCheckinModels.Lesson::endsAt)
                .containsExactly("Алгебра", "SEMINAR", LocalTime.MIDNIGHT, LocalTime.of(23, 59));

        verify(attendanceRepository, never()).save(any());
        verify(pairRepository, never()).save(any());
        verify(receiptRepository, never()).save(any());
        verify(lateCheckinRepository, never()).save(any());
        verify(pairCoordinator, never()).lock(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(transactionTemplate, never()).execute(any());
        verify(geofenceService, never()).isWithinCampus(anyDouble(), anyDouble());
        verify(attendanceEvents, never()).publishMarked(any());
        verify(lateCheckinEvents, never()).publishRequested(any(), any(), anyInt(), any(), any());
    }

    @ParameterizedTest(name = "read-only check-in is denied before dependencies ({0})")
    @MethodSource("readOnlyGeoCommands")
    void readOnlyIdentityIsRejectedBeforeGeoParsingAndDependencies(
            String geoKind, StudentCheckinCommand command) throws Exception {
        StudentCheckinService checkinService = mock(StudentCheckinService.class);
        StudentAttendanceSnapshotService snapshotService = mock(StudentAttendanceSnapshotService.class);
        ScheduleGrpcClient scheduleGrpcClient = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academicGrpcClient = mock(AcademicGrpcClient.class);
        SemesterCacheService semesterCacheService = mock(SemesterCacheService.class);
        AttendanceStudentGrpcServiceImpl service = new AttendanceStudentGrpcServiceImpl(
                checkinService, snapshotService, scheduleGrpcClient, academicGrpcClient, semesterCacheService);
        InternalJwtClaims readOnlyClaims = new InternalJwtClaims(
                STUDENT_ID, SESSION_ID, 1L, 1L, "STUDENT", "EXPELLED", 10L, false, true);
        RecordingObserver observer = new RecordingObserver();

        Context.current().withValue(StudentGrpcIdentity.CLAIMS, readOnlyClaims)
                .run(() -> service.checkin(StudentCheckinCommand.getDefaultInstance(), observer));

        Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.PERMISSION_DENIED.value());
        assertThat(status.getDetails(0).unpack(StudentCheckinErrorDetail.class).getCode())
                .isEqualTo(StudentCheckinErrorCode.STUDENT_CHECKIN_ERROR_CODE_OUT_OF_SCOPE);
        assertThat(observer.value).as(geoKind).isNull();
        verifyNoInteractions(checkinService, snapshotService, scheduleGrpcClient,
                academicGrpcClient, semesterCacheService);
    }

    private static Stream<Arguments> readOnlyGeoCommands() {
        return Stream.of(
                Arguments.of("coordinates", StudentCheckinCommand.newBuilder()
                        .setLessonId(LESSON_ID)
                        .setIdempotencyKey(KEY)
                        .setCoordinates(Coordinates.newBuilder()
                                .setLatitude(55.75).setLongitude(37.61))
                        .build()),
                Arguments.of("unavailable", StudentCheckinCommand.newBuilder()
                        .setLessonId(LESSON_ID)
                        .setIdempotencyKey(KEY)
                        .setUnavailable(GeoUnavailable.newBuilder()
                                .setReason(GeoUnavailableReason.GEO_UNAVAILABLE_REASON_TIMEOUT))
                        .build())
        );
    }

    private static Stream<Arguments> geoCommands() {
        return Stream.of(
                Arguments.of("coordinates", true, false, StudentCheckinCommand.newBuilder()
                        .setLessonId(LESSON_ID)
                        .setIdempotencyKey(KEY)
                        .setCoordinates(Coordinates.newBuilder()
                                .setLatitude(55.75)
                                .setLongitude(37.61))
                        .build()),
                Arguments.of("unavailable", true, false, StudentCheckinCommand.newBuilder()
                        .setLessonId(LESSON_ID)
                        .setIdempotencyKey(KEY)
                        .setUnavailable(GeoUnavailable.newBuilder()
                                .setReason(GeoUnavailableReason.GEO_UNAVAILABLE_REASON_TIMEOUT))
                        .build()),
                Arguments.of("coordinates", false, true, StudentCheckinCommand.newBuilder()
                        .setLessonId(LESSON_ID)
                        .setIdempotencyKey(KEY)
                        .setCoordinates(Coordinates.newBuilder()
                                .setLatitude(55.75)
                                .setLongitude(37.61))
                        .build()),
                Arguments.of("unavailable", false, true, StudentCheckinCommand.newBuilder()
                        .setLessonId(LESSON_ID)
                        .setIdempotencyKey(KEY)
                        .setUnavailable(GeoUnavailable.newBuilder()
                                .setReason(GeoUnavailableReason.GEO_UNAVAILABLE_REASON_TIMEOUT))
                        .build())
        );
    }

    private static final class RecordingObserver implements StreamObserver<StudentCheckinResult> {
        private StudentCheckinResult value;
        private Throwable error;

        @Override
        public void onNext(StudentCheckinResult value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {
        }
    }
}
