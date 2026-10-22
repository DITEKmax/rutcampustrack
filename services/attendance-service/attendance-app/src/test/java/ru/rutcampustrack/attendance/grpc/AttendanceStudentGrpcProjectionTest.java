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

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.attendance.report.studentprojection.AttendanceMetricCalculator;
import ru.rutcampustrack.attendance.report.studentprojection.StudentAttendanceProjectionService;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    void explanationWireKeepsFullCommentAndOptionalPresenceForDayAndHistory() throws Exception {
        var explanation =
                new StudentAttendanceProjectionService.Explanation(
                        "OTHER", "Другое", "Текст ".repeat(40), null);
        var history =
                new StudentAttendanceProjectionService.HistorySegment(
                        "canonical:78", "EXCUSED", explanation);
        var base = projection();
        var lesson = base.days().getFirst().lessons().getFirst();
        var cancelled =
                new StudentAttendanceProjectionService.Lesson(
                        lesson.lessonId(),
                        lesson.occurrenceId(),
                        lesson.groupId(),
                        lesson.date(),
                        lesson.lessonNumber(),
                        lesson.subjectId(),
                        lesson.subjectName(),
                        lesson.lessonType(),
                        lesson.startsAt(),
                        lesson.endsAt(),
                        lesson.room(),
                        StudentAttendanceProjectionService.ScheduleState.CANCELLED,
                        null,
                        "Причина отмены");
        var excusedMark =
                new ru.rutcampustrack.attendance.shared.port.AttendanceRecord(
                        79L,
                        100L,
                        10L,
                        20L,
                        DATE,
                        2,
                        ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.EXCUSED,
                        ru.rutcampustrack.attendance.contract.enums.AttendanceSource.HEADMAN_EXCUSE,
                        "Другое",
                        "OTHER",
                        explanation.comment(),
                        null,
                        null,
                        null,
                        null);
        var excused =
                new StudentAttendanceProjectionService.Lesson(
                        79L,
                        79L,
                        10L,
                        DATE,
                        2,
                        20L,
                        lesson.subjectName(),
                        lesson.lessonType(),
                        lesson.startsAt(),
                        lesson.endsAt(),
                        lesson.room(),
                        StudentAttendanceProjectionService.ScheduleState.CLOSED,
                        excusedMark);
        var subject = base.subjects().getFirst();
        var projected =
                new StudentAttendanceProjectionService.Projection(
                        base.studentId(),
                        base.semesterId(),
                        base.dateFrom(),
                        base.dateTo(),
                        base.serverNow(),
                        false,
                        base.metrics(),
                        List.of(
                                new StudentAttendanceProjectionService.Day(
                                        DATE,
                                        "Пятница",
                                        "9",
                                        "CANCELLED",
                                        List.of(cancelled, excused))),
                        List.of(
                                new StudentAttendanceProjectionService.Subject(
                                        subject.subjectId(),
                                        subject.name(),
                                        subject.metrics(),
                                        subject.availableTypes(),
                                        subject.selectedTypes(),
                                        subject.selectedAggregate(),
                                        List.of(
                                                new StudentAttendanceProjectionService.TypeCard(
                                                        "LECTURE",
                                                        base.metrics(),
                                                        List.of(
                                                                history,
                                                                new StudentAttendanceProjectionService
                                                                        .HistorySegment(
                                                                        "old", "EXCUSED")))),
                                        List.of())),
                        base.graph(),
                        new StudentAttendanceProjectionService.Rank(null, 7, false));
        var projections = mock(StudentAttendanceProjectionService.class);
        when(projections.project(CLAIMS, SEMESTER_ID, null, "days", List.of()))
                .thenReturn(projected);
        var handler =
                new AttendanceStudentGrpcServiceImpl(
                        null, null, null, null, null, null, projections);
        var observed =
                invoke(
                        handler,
                        StudentAttendanceProjectionRequest.newBuilder()
                                .setSemesterId(SEMESTER_ID)
                                .setRange("days")
                                .build());
        assertThat(observed.error).isNull();
        assertThat(observed.completions).isEqualTo(1);
        var wire =
                StudentAttendanceProjectionResponse.parseFrom(
                        observed.responses.getFirst().toByteArray());
        var result = wire.getSubjects(0).getTypeCards(0).getHistory(0);
        assertThat(result.getId()).isEqualTo("canonical:78");
        assertThat(result.hasExplanation()).isTrue();
        assertThat(result.getExplanation().hasComment()).isTrue();
        assertThat(result.getExplanation().getComment()).isEqualTo(explanation.comment());
        assertThat(result.getExplanation().hasCancellationReason()).isFalse();
        assertThat(wire.getSubjects(0).getTypeCards(0).getHistory(1).hasExplanation()).isFalse();
        assertThat(wire.getDays(0).getLessons(0).getExplanation().getCancellationReason())
                .isEqualTo("Причина отмены");
        assertThat(wire.getDays(0).getLessons(0).getExplanation().hasComment()).isFalse();
        assertThat(wire.getDays(0).getLessons(1).getExplanation().hasComment()).isTrue();
        assertThat(wire.getDays(0).getLessons(1).getExplanation().getComment())
                .isEqualTo(explanation.comment());
        assertThat(wire.getDays(0).getLessons(1).getExplanation().hasCancellationReason())
                .isFalse();
        assertThat(wire.getOwnRank().getParticipantCount()).isEqualTo(7);
        assertThat(wire.getOwnRank().hasPosition()).isFalse();
        assertThat(wire.getOwnRank().getAvailable()).isFalse();
        assertThat(wire.toString())
                .doesNotContain(
                        "peer-id-9381",
                        "peer-name-9381",
                        "peer-reason-9381",
                        "peer-attachment-9381");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void ownRankWireStaysPrivateWhileExplicitRankingCanExposeCohort(boolean available)
            throws Exception {
        var base = projection();
        String peer = "peer-name-9381 peer-reason-9381 peer-attachment-9381";
        var service = mock(StudentAttendanceProjectionService.class);
        var own =
                new StudentAttendanceProjectionService.Projection(
                        base.studentId(),
                        base.semesterId(),
                        base.dateFrom(),
                        base.dateTo(),
                        base.serverNow(),
                        false,
                        base.metrics(),
                        base.days(),
                        base.subjects(),
                        base.graph(),
                        new StudentAttendanceProjectionService.Rank(
                                available ? 2 : null, 7, available));
        when(service.project(CLAIMS, SEMESTER_ID, null, "days", List.of())).thenReturn(own);
        when(service.ranking(CLAIMS, SEMESTER_ID, 1, 10))
                .thenReturn(
                        new StudentAttendanceProjectionService.RankingPage(
                                true,
                                1,
                                10,
                                7,
                                2,
                                List.of(
                                        new StudentAttendanceProjectionService.RankingRow(
                                                9381L,
                                                peer,
                                                1,
                                                new java.math.BigDecimal("95"),
                                                false))));
        var handler =
                new AttendanceStudentGrpcServiceImpl(null, null, null, null, null, null, service);
        var ownObserver =
                invoke(
                        handler,
                        StudentAttendanceProjectionRequest.newBuilder()
                                .setSemesterId(SEMESTER_ID)
                                .setRange("days")
                                .build());
        assertThat(ownObserver.error).isNull();
        var wire =
                StudentAttendanceProjectionResponse.parseFrom(
                        ownObserver.responses.getFirst().toByteArray());
        assertThat(wire.getOwnRank().getParticipantCount()).isEqualTo(7);
        assertThat(wire.getOwnRank().getAvailable()).isEqualTo(available);
        assertThat(wire.getOwnRank().hasPosition()).isEqualTo(available);
        if (available) assertThat(wire.getOwnRank().getPosition()).isEqualTo(2);
        assertThat(wire.toString())
                .doesNotContain("9381", "peer-name", "peer-reason", "peer-attachment");
        var rankingResponses = new ArrayList<StudentAttendanceRankingResponse>();
        Context.current()
                .withValue(StudentGrpcIdentity.CLAIMS, CLAIMS)
                .run(
                        () ->
                                handler.getStudentAttendanceRanking(
                                        StudentAttendanceRankingRequest.newBuilder()
                                                .setSemesterId(SEMESTER_ID)
                                                .setPage(1)
                                                .setSize(10)
                                                .build(),
                                        new StreamObserver<>() {
                                            @Override
                                            public void onNext(
                                                    StudentAttendanceRankingResponse value) {
                                                rankingResponses.add(value);
                                            }

                                            @Override
                                            public void onError(Throwable failure) {
                                                throw new AssertionError(failure);
                                            }

                                            @Override
                                            public void onCompleted() {}
                                        }));
        var ranking =
                StudentAttendanceRankingResponse.parseFrom(
                        rankingResponses.getFirst().toByteArray());
        assertThat(ranking.getRows(0).getStudentId()).isEqualTo(9381L);
        assertThat(ranking.getRows(0).getDisplayName()).isEqualTo(peer);
    }

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
        assertThat(expected.getGraph().getWeeks(0).getGroupPresent().getNumerator()).isEqualTo(2);
        assertThat(expected.getGraph().getWeeks(0).getGroupPresent().getDenominator()).isEqualTo(3);
        assertThat(expected.getGraph().getWeeks(0).getGroupPresent().getPercent()).isEqualTo(66.67);
        assertThat(expected.getGraph().getWeeks(1).hasGroupPresent()).isTrue();
        assertThat(expected.getGraph().getWeeks(1).getGroupPresent().hasPercent()).isFalse();
        assertThat(expected.getGraph().getWeeks(2).hasGroupPresent()).isFalse();
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
                new StudentAttendanceProjectionService.Graph(
                        List.of(),
                        List.of(
                                new StudentAttendanceProjectionService.SeriesPoint(
                                        "week:2026-10-05",
                                        "week",
                                        DATE,
                                        DATE,
                                        "DATA",
                                        metrics,
                                        new StudentAttendanceProjectionService.GroupPresent(
                                                2, 3, new java.math.BigDecimal("66.67"))),
                                new StudentAttendanceProjectionService.SeriesPoint(
                                        "empty",
                                        "empty",
                                        DATE,
                                        DATE,
                                        "NO_DATA",
                                        metrics,
                                        new StudentAttendanceProjectionService.GroupPresent(
                                                0, 0, null)),
                                new StudentAttendanceProjectionService.SeriesPoint(
                                        "legacy", "legacy", DATE, DATE, "DATA", metrics))),
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
