package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.attendance.report.studentprojection.AttendanceMetricCalculator;
import ru.rutcampustrack.attendance.report.studentprojection.StudentAttendanceProjectionService;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

class AttendanceStudentGrpcProjectionTest {

    private static final long SEMESTER_ID = 11L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 9);
    private static final InternalJwtClaims CLAIMS =
            new InternalJwtClaims(
                    100L,
                    UUID.fromString("55555555-5555-4555-8555-555555555555"),
                    1L,
                    1L,
                    "STUDENT",
                    "ACTIVE",
                    10L,
                    false,
                    false);

    @Test
    void semesterOutageAfterProjectionPreservesReadWithoutRequestActions() throws Exception {
        AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub =
                mock(AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub.class);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.getActiveSemester(any()))
                .thenReturn(SemesterResponse.newBuilder().setId(SEMESTER_ID).build())
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());
        AcademicGrpcClient academic = new AcademicGrpcClient();
        Field stubField = AcademicGrpcClient.class.getDeclaredField("stub");
        stubField.setAccessible(true);
        stubField.set(academic, stub);
        SemesterCacheService semesters = new SemesterCacheService(academic);
        StudentRequestService requests = mock(StudentRequestService.class);
        when(requests.options(any()))
                .thenReturn(
                        new StudentRequestModels.RequestOptions(List.of(), null, null, List.of()));
        StudentAttendanceProjectionService projections =
                mock(StudentAttendanceProjectionService.class);
        StudentAttendanceProjectionRequest request =
                StudentAttendanceProjectionRequest.newBuilder()
                        .setSemesterId(SEMESTER_ID)
                        .setRange("days")
                        .build();
        when(projections.project(CLAIMS, SEMESTER_ID, null, "days", List.of()))
                .thenReturn(projection());
        AttendanceStudentGrpcServiceImpl handler =
                new AttendanceStudentGrpcServiceImpl(
                        null, null, null, academic, semesters, requests, projections);

        // An admitted projection with history/metrics is unchanged by optional action availability.
        RecordingObserver control = invoke(handler, request);
        assertThat(control.error).isNull();
        assertThat(control.completions).isEqualTo(1);
        assertThat(control.responses).hasSize(1);
        StudentAttendanceProjectionResponse expected = control.responses.getFirst();
        assertThat(expected.getMetrics().getAbsent().getCount()).isEqualTo(1);
        assertThat(expected.getDays(0).getLessons(0).getStatus()).isEqualTo("ABSENT");
        assertThat(expected.getSubjects(0).getTypeCards(0).getHistory(0).getStatus())
                .isEqualTo("ABSENT");
        assertThat(expected.getDays(0).getLessons(0).getRequestOptionsList()).isEmpty();
        clearInvocations(requests, projections, stub);

        RecordingObserver unavailable = invoke(handler, request);

        assertThat(unavailable.error)
                .as("optional current-semester outage must preserve the admitted read")
                .isNull();
        assertThat(unavailable.completions).isEqualTo(1);
        assertThat(unavailable.responses).containsExactly(expected);
        verify(projections).project(CLAIMS, SEMESTER_ID, null, "days", List.of());
        verify(stub).getActiveSemester(any());
        verifyNoInteractions(requests);
    }

    private static RecordingObserver invoke(
            AttendanceStudentGrpcServiceImpl handler, StudentAttendanceProjectionRequest request) {
        RecordingObserver observer = new RecordingObserver();
        Context.current()
                .withValue(StudentGrpcIdentity.CLAIMS, CLAIMS)
                .run(() -> handler.getStudentAttendanceProjection(request, observer));
        return observer;
    }

    private static StudentAttendanceProjectionService.Projection projection() {
        var metrics =
                new AttendanceMetricCalculator.Metrics(
                        1, 1, 0, 0, 0, 1, AttendanceMetricCalculator.Diagnostics.none());
        var lesson =
                new StudentAttendanceProjectionService.Lesson(
                        77L,
                        78L,
                        10L,
                        DATE,
                        1,
                        20L,
                        "Математика",
                        "LECTURE",
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 30),
                        "101",
                        StudentAttendanceProjectionService.ScheduleState.CLOSED,
                        null);
        var day =
                new StudentAttendanceProjectionService.Day(
                        DATE, "Пятница", "9", "CLOSED", List.of(lesson));
        var card =
                new StudentAttendanceProjectionService.TypeCard(
                        "LECTURE",
                        metrics,
                        List.of(
                                new StudentAttendanceProjectionService.HistorySegment(
                                        "77", "ABSENT")));
        var subject =
                new StudentAttendanceProjectionService.Subject(
                        20L,
                        "Математика",
                        metrics,
                        List.of("LECTURE"),
                        List.of("LECTURE"),
                        metrics,
                        List.of(card),
                        List.of());
        return new StudentAttendanceProjectionService.Projection(
                100L,
                SEMESTER_ID,
                DATE,
                DATE,
                Instant.parse("2026-10-09T09:00:00Z"),
                false,
                metrics,
                List.of(day),
                List.of(subject),
                new StudentAttendanceProjectionService.Graph(List.of(), List.of()),
                new StudentAttendanceProjectionService.Rank(null, 0, false));
    }

    private static final class RecordingObserver
            implements StreamObserver<StudentAttendanceProjectionResponse> {
        private final List<StudentAttendanceProjectionResponse> responses = new ArrayList<>();
        private Throwable error;
        private int completions;

        @Override
        public void onNext(StudentAttendanceProjectionResponse value) {
            responses.add(value);
        }

        @Override
        public void onError(Throwable failure) {
            error = failure;
        }

        @Override
        public void onCompleted() {
            completions++;
        }
    }
}
