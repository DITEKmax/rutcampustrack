package ru.rutcampustrack.mobilebff.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.UserResponse;
import ru.rutcampustrack.attendance.grpc.StudentAttendanceSnapshotResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.Capability;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.SessionResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.TodayResponse;
import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

@ExtendWith(MockitoExtension.class)
class StudentSessionProjectionTest {

    private static final long STUDENT_ID = 42L;
    private static final long GROUP_ID = 7L;
    private static final long SEMESTER_ID = 9L;
    private static final UUID SESSION_ID = UUID.fromString("abcdefab-cdef-4abc-8def-abcdefabcdef");
    private static final Instant SERVER_NOW = Instant.parse("2026-09-07T10:00:00Z");

    @Mock private MobileRequestContext requestContext;
    @Mock private MobileAcademicClient academic;
    @Mock private MobileScheduleClient schedule;
    @Mock private MobileAttendanceClient attendance;

    private StudentQueryService service;

    @BeforeEach
    void setUp() {
        service =
                new StudentQueryService(
                        requestContext,
                        academic,
                        schedule,
                        attendance,
                        Clock.fixed(SERVER_NOW, ZoneOffset.UTC));
    }

    @Test
    void attendanceExplanationMapsNestedOptionalDataAndFullCommentWithoutJoiningIds()
            throws Exception {
        var explanation =
                ru.rutcampustrack.attendance.grpc.StudentAttendanceExplanation.newBuilder()
                        .setReasonCode("OTHER")
                        .setReasonText("Другое")
                        .setComment("Полный комментарий ".repeat(20))
                        .build();
        var history =
                ru.rutcampustrack.attendance.grpc.StudentAttendanceHistorySegment.newBuilder()
                        .setId("occurrence:78")
                        .setStatus("EXCUSED")
                        .setExplanation(explanation)
                        .build();
        var mapper = StudentQueryService.class.getDeclaredMethod("history", history.getClass());
        mapper.setAccessible(true);
        var result =
                (ru.rutcampustrack.mobilebff.contract.model.StudentApiModels
                                .StudentAttendanceHistorySegment)
                        mapper.invoke(null, history);
        assertThat(result.id()).isEqualTo("occurrence:78");
        assertThat(result.explanation().comment()).isEqualTo(explanation.getComment());
        assertThat(result.explanation().reasonText()).isEqualTo("Другое");
        assertThat(result.explanation().cancellationReason()).isNull();
        var json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules()
                        .valueToTree(result);
        assertThat(json.path("explanation").path("reasonCode").asText()).isEqualTo("OTHER");
        var legacy =
                (ru.rutcampustrack.mobilebff.contract.model.StudentApiModels
                                .StudentAttendanceHistorySegment)
                        mapper.invoke(null, history.toBuilder().clearExplanation().build());
        assertThat(legacy.explanation()).isNull();
        var lesson =
                ru.rutcampustrack.attendance.grpc.StudentAttendanceLesson.newBuilder()
                        .setLessonId(77)
                        .setDate("2026-09-07")
                        .setLessonNumber(1)
                        .setSubjectId(5)
                        .setSubjectName("Предмет")
                        .setLessonType("LECTURE")
                        .setStartsAt("09:00")
                        .setEndsAt("10:30")
                        .setStatus("CANCELLED")
                        .setExplanation(
                                ru.rutcampustrack.attendance.grpc.StudentAttendanceExplanation
                                        .newBuilder()
                                        .setCancellationReason("Причина отмены"))
                        .build();
        var dayMapper = StudentQueryService.class.getDeclaredMethod("lesson", lesson.getClass());
        dayMapper.setAccessible(true);
        var day =
                (ru.rutcampustrack.mobilebff.contract.model.StudentApiModels
                                .StudentAttendanceLesson)
                        dayMapper.invoke(null, lesson);
        assertThat(day.id()).isEqualTo("77");
        assertThat(day.explanation().cancellationReason()).isEqualTo("Причина отмены");
        assertThat(day.explanation().comment()).isNull();
    }

    @Test
    void statisticsMapsKnownEmptyAndLegacyGroupComparisonWithoutChangingWeeklyKeys() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                STUDENT_ID,
                                SESSION_ID,
                                1L,
                                1L,
                                "STUDENT",
                                "ACTIVE",
                                GROUP_ID,
                                false,
                                false));
        when(academic.activeSemester())
                .thenReturn(
                        SemesterResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .setName("Осень")
                                .setDateFrom("2026-09-01")
                                .setDateTo("2026-12-31")
                                .build());
        var point =
                ru.rutcampustrack.attendance.grpc.StudentAttendanceSeriesPoint.newBuilder()
                        .setId("week:2026-09-07")
                        .setLabel("Неделя")
                        .setDateFrom("2026-09-07")
                        .setDateTo("2026-09-13")
                        .setState("DATA");
        var response =
                ru.rutcampustrack.attendance.grpc.StudentAttendanceProjectionResponse.newBuilder()
                        .setGraph(
                                ru.rutcampustrack.attendance.grpc.StudentAttendanceGraph
                                        .newBuilder()
                                        .addWeeks(
                                                point.clone()
                                                        .setGroupPresent(
                                                                ru.rutcampustrack.attendance.grpc
                                                                        .StudentAttendanceGroupPresent
                                                                        .newBuilder()
                                                                        .setNumerator(2)
                                                                        .setDenominator(3)
                                                                        .setPercent(66.67)))
                                        .addWeeks(
                                                point.clone()
                                                        .setId("empty")
                                                        .setGroupPresent(
                                                                ru.rutcampustrack.attendance.grpc
                                                                        .StudentAttendanceGroupPresent
                                                                        .newBuilder()))
                                        .addWeeks(point.clone().setId("legacy")))
                        .build();
        when(attendance.projection(SEMESTER_ID, null, "weeks", java.util.List.of()))
                .thenReturn(response);
        var result = service.statistics(SEMESTER_ID).semesterSeries();
        assertThat(result)
                .extracting(
                        ru.rutcampustrack.mobilebff.contract.model.StudentApiModels
                                        .StudentAttendanceSeriesPoint
                                ::id)
                .containsExactly("week:2026-09-07", "empty", "legacy");
        assertThat(result.getFirst().dateFrom()).isEqualTo(java.time.LocalDate.parse("2026-09-07"));
        assertThat(result.getFirst().dateTo()).isEqualTo(java.time.LocalDate.parse("2026-09-13"));
        assertThat(result.getFirst().groupPresent().numerator()).isEqualTo(2);
        assertThat(result.getFirst().groupPresent().denominator()).isEqualTo(3);
        assertThat(result.getFirst().groupPresent().percent()).isEqualTo(66.67);
        assertThat(result.get(1).groupPresent().denominator()).isZero();
        assertThat(result.get(1).groupPresent().percent()).isNull();
        assertThat(result.getLast().groupPresent()).isNull();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertThat(json.valueToTree(result.getLast()).has("groupPresent")).isFalse();
        assertThat(json.valueToTree(result.get(1)).path("groupPresent").path("percent").isNull())
                .isTrue();
    }

    @Test
    void sessionProjectsCompleteValidatedIdentityAndPreservesExistingFields() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                STUDENT_ID,
                                SESSION_ID,
                                9007199254740993L,
                                9007199254740995L,
                                "STUDENT",
                                "ACTIVE",
                                GROUP_ID,
                                false,
                                false));
        when(academic.user(STUDENT_ID))
                .thenReturn(
                        UserResponse.newBuilder()
                                .setId(STUDENT_ID)
                                .setDisplayName("Иван Иванов")
                                .build());
        when(academic.group(GROUP_ID))
                .thenReturn(GroupResponse.newBuilder().setId(GROUP_ID).setName("ИС-101").build());
        when(academic.activeSemester())
                .thenReturn(
                        SemesterResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .setName("Осень 2026")
                                .setDateFrom("2026-09-01")
                                .setDateTo("2026-12-31")
                                .build());

        SessionResponse result = service.session();

        assertThat(result.sessionId()).isEqualTo("abcdefab-cdef-4abc-8def-abcdefabcdef");
        assertThat(result.sessionVersion()).isEqualTo("9007199254740993");
        assertThat(result.rolesVersion()).isEqualTo("9007199254740995");
        assertThat(result.readOnly()).isFalse();
        assertThat(result.user().id()).isEqualTo(Long.toString(STUDENT_ID));
        assertThat(result.user().displayName()).isEqualTo("Иван Иванов");
        assertThat(result.activeRole())
                .isEqualTo(
                        ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ActiveRole
                                .STUDENT);
        assertThat(result.group().id()).isEqualTo(Long.toString(GROUP_ID));
        assertThat(result.semester().id()).isEqualTo(Long.toString(SEMESTER_ID));
        assertThat(result.capabilities())
                .containsExactly(
                        Capability.TODAY,
                        Capability.GEO_CHECKIN,
                        Capability.OFFLINE_SEMESTER_SCHEDULE);
        assertThat(result.serverNow()).isEqualTo(SERVER_NOW);
        assertThat(result.links()).containsKeys("today", "schedule");
        assertThat(result.links().get("today").href().toString())
                .isEqualTo("/api/v1/student/today");
        assertThat(result.links().get("schedule").href().toString())
                .isEqualTo("/api/v1/student/schedule");

        verify(academic).user(STUDENT_ID);
        verify(academic).group(GROUP_ID);
        verify(academic).activeSemester();
    }

    @Test
    void readOnlySessionUsesTheSignedFlagWithoutChangingProjectionShape() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                STUDENT_ID,
                                SESSION_ID,
                                1L,
                                2L,
                                "STUDENT",
                                "EXPELLED",
                                GROUP_ID,
                                false,
                                true));
        when(academic.user(STUDENT_ID))
                .thenReturn(
                        UserResponse.newBuilder()
                                .setId(STUDENT_ID)
                                .setDisplayName("Архивный студент")
                                .build());
        when(academic.group(GROUP_ID))
                .thenReturn(GroupResponse.newBuilder().setId(GROUP_ID).setName("ИС-101").build());
        when(academic.activeSemester())
                .thenReturn(
                        SemesterResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .setName("Осень 2026")
                                .setDateFrom("2026-09-01")
                                .setDateTo("2026-12-31")
                                .build());

        SessionResponse result = service.session();

        assertThat(result.readOnly()).isTrue();
        assertThat(result.sessionId()).isEqualTo(SESSION_ID.toString());
        assertThat(result.user().displayName()).isEqualTo("Архивный студент");
        assertThat(result.capabilities()).contains(Capability.TODAY);
    }

    @Test
    void readOnlyTodayRemainsReadableThroughTheAttendanceSnapshot() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                STUDENT_ID,
                                SESSION_ID,
                                1L,
                                2L,
                                "STUDENT",
                                "EXPELLED",
                                GROUP_ID,
                                false,
                                true));
        when(academic.activeSemester())
                .thenReturn(
                        SemesterResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .setName("Осень 2026")
                                .setDateFrom("2026-09-01")
                                .setDateTo("2026-12-31")
                                .build());
        when(schedule.lessons(
                        GROUP_ID,
                        SEMESTER_ID,
                        java.time.LocalDate.of(2026, 9, 7),
                        java.time.LocalDate.of(2026, 9, 7)))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendance.snapshot(anyList()))
                .thenReturn(
                        StudentAttendanceSnapshotResponse.newBuilder()
                                .setServerNow(SERVER_NOW.toString())
                                .build());

        TodayResponse result = service.today();

        assertThat(result.date()).isEqualTo(java.time.LocalDate.of(2026, 9, 7));
        assertThat(result.lessons()).isEmpty();
        assertThat(result.serverNow()).isEqualTo(SERVER_NOW);
        verify(attendance).snapshot(anyList());
    }
}
