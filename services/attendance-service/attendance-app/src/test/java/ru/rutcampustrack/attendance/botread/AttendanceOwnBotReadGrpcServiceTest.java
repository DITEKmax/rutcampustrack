package ru.rutcampustrack.attendance.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.attendance.contract.enums.*;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.attendance.shared.port.*;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.botreads.grpc.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

class AttendanceOwnBotReadGrpcServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-17T21:15:00Z");
    private static final DirectedBotIdentity IDENTITY =
            DirectedBotIdentity.newBuilder()
                    .setTelegramSenderId(777)
                    .setPrivateChatId(777)
                    .setExpectedUserId(71)
                    .setExpectedBindingVersion(3)
                    .setExpectedRolesVersion(5)
                    .build();
    private final AttendanceBotReadDependencies dependencies =
            mock(AttendanceBotReadDependencies.class);
    private final AttendanceReadPort records = mock(AttendanceReadPort.class);
    private final EducationalTime time = new EducationalTime(Clock.fixed(NOW, ZoneOffset.UTC));
    private final ReportService reports =
            new ReportService(records, null, null, null, null, time.clock(), null);
    private final AttendanceOwnBotReadGrpcService producer =
            new AttendanceOwnBotReadGrpcService(dependencies, reports, time);

    @Test
    void actualProducerAndReportReturnCompleteRosterDistinctUnmarkedAndExactStatistics() {
        valid();
        when(records.findByLessonId(101L))
                .thenReturn(
                        List.of(
                                record(1, AttendanceStatus.PRESENT),
                                record(2, AttendanceStatus.ABSENT),
                                record(3, AttendanceStatus.FREE_ATTENDANCE)));
        var result = read(true, 101);
        assertThat(result.error).isNull();
        assertThat(result.values)
                .singleElement()
                .satisfies(
                        response -> {
                            assertThat(response.getRowsList())
                                    .extracting(BotAttendanceRow::getStatus)
                                    .containsExactly("present", "absent", "free_attendance", "");
                            assertThat(response.getRows(3).getRecordPresent()).isFalse();
                            assertThat(response.getStatistics().getRosterCount()).isEqualTo(4);
                            assertThat(response.getStatistics().getMarkedCount()).isEqualTo(3);
                            assertThat(response.getStatistics().getPresentCount()).isEqualTo(1);
                            assertThat(response.getStatistics().getAbsentCount()).isEqualTo(1);
                            assertThat(response.getStatistics().getFreeAttendanceCount())
                                    .isEqualTo(1);
                            assertThat(response.getStatistics().getUnmarkedCount()).isEqualTo(1);
                            assertThat(response.getStatistics().getExcusedCount()).isZero();
                            assertThat(response.getEducationalDate()).isEqualTo("2026-10-18");
                        });
    }

    @Test
    void missingServiceCallerAndInvalidPrivateChatReadNothing() {
        assertStatus(read(false, 101), Status.Code.UNAUTHENTICATED);
        Result result = new Result();
        bff(
                () ->
                        producer.getOwnBotLessonAttendance(
                                BotOwnLessonAttendanceRequest.newBuilder()
                                        .setIdentity(IDENTITY.toBuilder().setPrivateChatId(-100))
                                        .setLessonId(101)
                                        .build(),
                                result));
        assertStatus(result, Status.Code.INVALID_ARGUMENT);
        verifyNoInteractions(dependencies, records);
    }

    @Test
    void foreignHistoricalCancelledOrEndedLessonCannotExposeRoster() {
        valid();
        for (var invalid :
                List.of(
                        lesson().toBuilder().setGroupId(99).build(),
                        lesson().toBuilder().setSemesterId(99).build(),
                        lesson().toBuilder().setDate("2026-10-17").build(),
                        lesson().toBuilder().setStatus("cancelled").build(),
                        lesson().toBuilder().setEndTime("00:15").build(),
                        lesson().toBuilder().setTransferState("PENDING").build())) {
            when(dependencies.lesson(101)).thenReturn(invalid);
            assertStatus(read(true, 101), Status.Code.PERMISSION_DENIED);
        }
        verifyNoInteractions(records);
    }

    @Test
    void revocationLocalChangeAndScheduleChangeDuringReadDiscardAllRows() {
        valid();
        when(records.findByLessonId(101L)).thenReturn(List.of());
        when(dependencies.check(IDENTITY))
                .thenReturn(authority())
                .thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());
        assertStatus(read(true, 101), Status.Code.FAILED_PRECONDITION);
        doReturn(authority()).when(dependencies).check(IDENTITY);
        when(dependencies.scope(IDENTITY))
                .thenReturn(scope(), scope().toBuilder().setEligibilityRevision(6).build());
        assertStatus(read(true, 101), Status.Code.FAILED_PRECONDITION);
        when(dependencies.scope(IDENTITY)).thenReturn(scope());
        when(dependencies.lesson(101))
                .thenReturn(lesson(), lesson().toBuilder().setStatus("cancelled").build());
        assertStatus(read(true, 101), Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void mismatchedDatedRosterOrAuthorityCannotReturnPartialSuccess() {
        valid();
        when(dependencies.scope(IDENTITY))
                .thenReturn(scope().toBuilder().addRoster(scope().getRoster(0)).build());
        assertStatus(read(true, 101), Status.Code.UNAVAILABLE);
        when(dependencies.scope(IDENTITY)).thenThrow(Status.UNAVAILABLE.asRuntimeException());
        assertStatus(read(true, 101), Status.Code.UNAVAILABLE);
    }

    private void valid() {
        when(dependencies.check(IDENTITY)).thenReturn(authority());
        when(dependencies.scope(IDENTITY)).thenReturn(scope());
        when(dependencies.lesson(101)).thenReturn(lesson());
    }

    private static CheckBotOwnAuthorityResponse authority() {
        return CheckBotOwnAuthorityResponse.newBuilder()
                .setUserId(71)
                .setTelegramBindingVersion(3)
                .setRolesVersion(5)
                .setCapability(
                        BotReadCapability.BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ)
                .addAuthorizedGroupIds(33)
                .build();
    }

    private static BotAcademicScopeResponse scope() {
        var scope =
                BotAcademicScopeResponse.newBuilder()
                        .setUserId(71)
                        .setTelegramBindingVersion(3)
                        .setRolesVersion(5)
                        .setAction(BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT)
                        .setEligibilityRevision(5)
                        .setOwnGroupId(33)
                        .setOwnGroupName("УИТ-311")
                        .setSemesterId(9)
                        .setServerNow(NOW.toString())
                        .setEducationalDate("2026-10-18")
                        .addSubjects(
                                BotScopeSubject.newBuilder()
                                        .setSubjectId(22)
                                        .setSubjectName("Математика")
                                        .addLessonTypes("lecture"));
        for (int id = 1; id <= 4; id++)
            scope.addRoster(
                    BotScopeRosterMember.newBuilder()
                            .setStudentId(id)
                            .setDisplayName("Студент " + id));
        return scope.build();
    }

    private static LessonResponse lesson() {
        return LessonResponse.newBuilder()
                .setId(101)
                .setGroupId(33)
                .setSubjectId(22)
                .setSemesterId(9)
                .setDate("2026-10-18")
                .setStartTime("00:00")
                .setEndTime("00:30")
                .setLessonType("lecture")
                .setStatus("active")
                .setLessonNumber(1)
                .build();
    }

    private static AttendanceRecord record(long id, AttendanceStatus status) {
        return new AttendanceRecord(
                101L,
                id,
                33L,
                22L,
                LocalDate.of(2026, 10, 18),
                1,
                status,
                AttendanceSource.HEADMAN,
                "private",
                "private",
                "private",
                "private",
                "private",
                "private",
                1L);
    }

    private Result read(boolean admitted, long lesson) {
        Result result = new Result();
        Runnable operation =
                () ->
                        producer.getOwnBotLessonAttendance(
                                BotOwnLessonAttendanceRequest.newBuilder()
                                        .setIdentity(IDENTITY)
                                        .setLessonId(lesson)
                                        .build(),
                                result);
        if (admitted) bff(operation);
        else operation.run();
        return result;
    }

    private static void bff(Runnable operation) {
        Context.current()
                .withValue(ServicePrincipal.CONTEXT_KEY, new ServicePrincipal("mobile-bff"))
                .run(operation);
    }

    private static void assertStatus(Result result, Status.Code expected) {
        assertThat(result.values).isEmpty();
        assertThat(Status.fromThrowable(result.error).getCode()).isEqualTo(expected);
    }

    private static final class Result implements StreamObserver<BotOwnLessonAttendanceResponse> {
        private final List<BotOwnLessonAttendanceResponse> values = new ArrayList<>();
        private Throwable error;

        @Override
        public void onNext(BotOwnLessonAttendanceResponse value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {}
    }
}
