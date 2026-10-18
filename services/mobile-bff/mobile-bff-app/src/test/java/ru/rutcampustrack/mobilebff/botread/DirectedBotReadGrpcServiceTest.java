package ru.rutcampustrack.mobilebff.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.botreads.grpc.*;
import ru.rutcampustrack.mobilebff.teacher.TeacherReadFacade;
import ru.rutcampustrack.schedule.grpc.*;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

class DirectedBotReadGrpcServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-17T21:15:00Z");
    private final BotReadDependencies dependencies = mock(BotReadDependencies.class);
    private final EducationalTime time = new EducationalTime(Clock.fixed(NOW, ZoneOffset.UTC));
    private final DirectedBotReadGrpcService producer =
            new DirectedBotReadGrpcService(
                    dependencies, new TeacherReadFacade(null, null, null, null, time), time);
    private static final DirectedBotIdentity IDENTITY =
            DirectedBotIdentity.newBuilder()
                    .setTelegramSenderId(777)
                    .setPrivateChatId(777)
                    .setExpectedUserId(71)
                    .setExpectedBindingVersion(3)
                    .setExpectedRolesVersion(5)
                    .build();

    @Test
    void actualProducerReturnsOwnTeacherTodayThroughExistingDayComposition() {
        var action = BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY;
        authorize(BotReadCapability.BOT_READ_CAPABILITY_TEACHER_TODAY_READ);
        when(dependencies.scope(IDENTITY, action))
                .thenReturn(
                        scope(action).toBuilder()
                                .addAssignments(
                                        BotTeacherAssignment.newBuilder()
                                                .setAssignmentId(12)
                                                .setGroupId(33)
                                                .setSubjectId(22)
                                                .setSemesterId(9)
                                                .setLessonType("lecture")
                                                .setValidFrom("2026-09-01")
                                                .setValidUntilExclusive("2026-12-01")
                                                .setGroupName("УИТ-311")
                                                .setSubjectName("Математика"))
                                .build());
        when(dependencies.day(33, 9, java.time.LocalDate.of(2026, 10, 18)))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(101))
                                .addLessons(lesson(102).toBuilder().setAssignedTeacherId(72))
                                .addLessons(lesson(103).toBuilder().setGroupId(99))
                                .build());
        var result = teacher(IDENTITY, true);
        assertThat(result.error).isNull();
        assertThat(result.values)
                .singleElement()
                .satisfies(
                        value -> {
                            assertThat(value.getEducationalDate()).isEqualTo("2026-10-18");
                            assertThat(value.getLessonsList())
                                    .extracting(BotLessonSummary::getLessonId)
                                    .containsExactly(101L);
                            assertThat(value.getLessons(0).getGroupName()).isEqualTo("УИТ-311");
                        });
    }

    @Test
    void teacherWithNoDatedAssignmentsGetsEmptyDayWithoutBroadScheduleRead() {
        authorize(BotReadCapability.BOT_READ_CAPABILITY_TEACHER_TODAY_READ);
        var action = BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY;
        when(dependencies.scope(IDENTITY, action)).thenReturn(scope(action));
        var result = teacher(IDENTITY, true);
        assertThat(result.error).isNull();
        assertThat(result.values.getFirst().getLessonsList()).isEmpty();
        verify(dependencies, never()).day(anyLong(), anyLong(), any());
    }

    @Test
    void missingDirectedCallerAndNonPrivateIdentityNeverReadDomainData() {
        assertStatus(teacher(IDENTITY, false), Status.Code.UNAUTHENTICATED);
        assertStatus(
                teacher(IDENTITY.toBuilder().setPrivateChatId(-100).build(), true),
                Status.Code.INVALID_ARGUMENT);
        verifyNoInteractions(dependencies);
    }

    @Test
    void revokedDuringReadDiscardsEntireTeacherResponse() {
        var capability = BotReadCapability.BOT_READ_CAPABILITY_TEACHER_TODAY_READ;
        when(dependencies.check(IDENTITY, capability))
                .thenReturn(authority(capability))
                .thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());
        var action = BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY;
        when(dependencies.scope(IDENTITY, action)).thenReturn(scope(action));
        assertStatus(teacher(IDENTITY, true), Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void currentRoleDeniedOrDependencyUnavailableDoesNotReturnEmptySuccess() {
        when(dependencies.check(IDENTITY, BotReadCapability.BOT_READ_CAPABILITY_TEACHER_TODAY_READ))
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException())
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());
        assertStatus(teacher(IDENTITY, true), Status.Code.PERMISSION_DENIED);
        assertStatus(teacher(IDENTITY, true), Status.Code.UNAVAILABLE);
    }

    @Test
    void headmanHasExplicitNoCurrentAndSafeChoiceOverAllOwnCandidates() {
        authorize(BotReadCapability.BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ);
        var action = BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT;
        when(dependencies.scope(IDENTITY, action)).thenReturn(scope(action));
        when(dependencies.day(33, 9, java.time.LocalDate.of(2026, 10, 18)))
                .thenReturn(LessonsResponse.getDefaultInstance())
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(101))
                                .addLessons(lesson(102))
                                .build());
        var empty = headman(0);
        assertThat(empty.error).isNull();
        assertThat(empty.values.getFirst().getState())
                .isEqualTo(BotCurrentAttendanceState.BOT_CURRENT_ATTENDANCE_STATE_NO_CURRENT);
        var choice = headman(0);
        assertThat(choice.error).isNull();
        assertThat(choice.values.getFirst().getState())
                .isEqualTo(BotCurrentAttendanceState.BOT_CURRENT_ATTENDANCE_STATE_CHOOSE);
        assertThat(choice.values.getFirst().getChoicesList())
                .extracting(BotLessonSummary::getLessonId)
                .containsExactly(101L, 102L);
        verify(dependencies, never()).attendance(any(), anyLong());
    }

    @Test
    void staleForeignHeadmanCallbackAndChangedLocalEligibilityProduceNoRows() {
        authorize(BotReadCapability.BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ);
        var action = BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT;
        when(dependencies.scope(IDENTITY, action)).thenReturn(scope(action));
        when(dependencies.day(33, 9, java.time.LocalDate.of(2026, 10, 18)))
                .thenReturn(LessonsResponse.newBuilder().addLessons(lesson(101)).build());
        assertStatus(headman(999), Status.Code.PERMISSION_DENIED);
        when(dependencies.scope(IDENTITY, action))
                .thenReturn(
                        scope(action), scope(action).toBuilder().setEligibilityRevision(6).build());
        when(dependencies.day(33, 9, java.time.LocalDate.of(2026, 10, 18)))
                .thenReturn(LessonsResponse.getDefaultInstance());
        assertStatus(headman(0), Status.Code.FAILED_PRECONDITION);
    }

    private void authorize(BotReadCapability capability) {
        when(dependencies.check(IDENTITY, capability)).thenReturn(authority(capability));
    }

    private static CheckBotOwnAuthorityResponse authority(BotReadCapability capability) {
        return CheckBotOwnAuthorityResponse.newBuilder()
                .setUserId(71)
                .setTelegramBindingVersion(3)
                .setRolesVersion(5)
                .setCapability(capability)
                .addAuthorizedGroupIds(33)
                .build();
    }

    private static BotAcademicScopeResponse scope(BotAcademicReadAction action) {
        return BotAcademicScopeResponse.newBuilder()
                .setUserId(71)
                .setTelegramBindingVersion(3)
                .setRolesVersion(5)
                .setAction(action)
                .setEligibilityRevision(5)
                .setServerNow(NOW.toString())
                .setEducationalDate("2026-10-18")
                .setSemesterId(9)
                .setSemesterDateFrom("2026-09-01")
                .setSemesterDateTo("2026-12-01")
                .setOwnGroupId(
                        action == BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT
                                ? 33
                                : 0)
                .setOwnGroupName("УИТ-311")
                .addSubjects(
                        BotScopeSubject.newBuilder()
                                .setSubjectId(22)
                                .setSubjectName("Математика")
                                .addLessonTypes("lecture"))
                .build();
    }

    private static LessonResponse lesson(long id) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(33)
                .setSubjectId(22)
                .setSemesterId(9)
                .setAssignmentId(12)
                .setAssignedTeacherId(71)
                .setDate("2026-10-18")
                .setLessonType("lecture")
                .setStartTime("00:00")
                .setEndTime("00:30")
                .setStatus("active")
                .setLessonNumber(1)
                .build();
    }

    private Result<BotTeacherTodayResponse> teacher(
            DirectedBotIdentity identity, boolean admitted) {
        Result<BotTeacherTodayResponse> result = new Result<>();
        if (admitted) bot(() -> producer.getOwnTeacherToday(identity, result));
        else producer.getOwnTeacherToday(identity, result);
        return result;
    }

    private Result<BotHeadmanCurrentAttendanceResponse> headman(long selected) {
        Result<BotHeadmanCurrentAttendanceResponse> result = new Result<>();
        bot(
                () ->
                        producer.getOwnHeadmanCurrentAttendance(
                                BotHeadmanCurrentAttendanceRequest.newBuilder()
                                        .setIdentity(IDENTITY)
                                        .setSelectedLessonId(selected)
                                        .build(),
                                result));
        return result;
    }

    private static void bot(Runnable operation) {
        Context.current()
                .withValue(ServicePrincipal.CONTEXT_KEY, new ServicePrincipal("notification-bot"))
                .run(operation);
    }

    private static void assertStatus(Result<?> result, Status.Code code) {
        assertThat(result.values).isEmpty();
        assertThat(result.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(result.error).getCode()).isEqualTo(code);
    }

    private static final class Result<T> implements StreamObserver<T> {
        private final List<T> values = new ArrayList<>();
        private Throwable error;

        @Override
        public void onNext(T value) {
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
