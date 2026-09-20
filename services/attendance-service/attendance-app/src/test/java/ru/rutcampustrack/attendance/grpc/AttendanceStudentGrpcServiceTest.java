package ru.rutcampustrack.attendance.grpc;

import com.google.rpc.Status;
import io.grpc.Context;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceStudentGrpcServiceTest {

    private static final long STUDENT_ID = 100L;
    private static final long LESSON_ID = 77L;
    private static final String KEY = "headman-blocked-0001";
    private static final InternalJwtClaims CLAIMS =
            new InternalJwtClaims(STUDENT_ID, "STUDENT", 10L, false);

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
                        .setLessonNumber(1)
                        .setDate("2026-09-06")
                        .setStartTime("00:00")
                        .setEndTime("23:59")
                        .setStatus("active")
                        .setIsGeoBlocked(isGeoBlocked)
                        .setIsBlockedByHeadman(isBlockedByHeadman)
                        .build());
        when(academicGrpcClient.getUserDisplayName(STUDENT_ID)).thenReturn("Иван Иванов");

        StudentCheckinService checkinService = new StudentCheckinService(
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
                Clock.fixed(Instant.parse("2026-09-06T07:00:00Z"), ZoneOffset.UTC));
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

        verify(attendanceRepository, never()).save(any());
        verify(pairRepository, never()).save(any());
        verify(receiptRepository, never()).save(any());
        verify(lateCheckinRepository, never()).save(any());
        verify(pairCoordinator, never()).lock(anyLong(), anyLong(), anyLong(), any());
        verify(transactionTemplate, never()).execute(any());
        verify(geofenceService, never()).isWithinCampus(anyDouble(), anyDouble());
        verify(attendanceEvents, never()).publishMarked(any());
        verify(lateCheckinEvents, never()).publishRequested(any(), any(), anyInt(), any(), any());
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
