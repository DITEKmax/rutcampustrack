package ru.rutcampustrack.attendance.report.planned;

import static org.assertj.core.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.reportrendering.grpc.*;

import java.util.List;

class AdmittedReportPlanTest {
    @Test
    void canonicalAdminCalculationExcludesCancelledMarksEvenForCapturedClosedLessons() {
        var source = ReportPlanFixtures.adminPlan();
        var admitted = AdmittedReportPlan.admit(source, ReportPlanFixtures.adminActor());
        var marks =
                org.mockito.Mockito.mock(
                        ru.rutcampustrack.attendance.shared.port.AttendanceReadPort.class);
        var academic =
                org.mockito.Mockito.mock(
                        ru.rutcampustrack.attendance.grpc.AcademicGrpcClient.class);
        var schedule =
                org.mockito.Mockito.mock(
                        ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient.class);
        var reports =
                new ru.rutcampustrack.attendance.report.ReportService(
                        marks,
                        academic,
                        schedule,
                        org.mockito.Mockito.mock(
                                ru.rutcampustrack.attendance.semester.SemesterCacheService.class),
                        org.mockito.Mockito.mock(
                                ru.rutcampustrack.attendance.security.RequestContext.class),
                        java.time.Clock.systemUTC(),
                        org.mockito.Mockito.mock(
                                ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort
                                        .class));
        var cancelled =
                new ru.rutcampustrack.attendance.shared.port.AttendanceRecord(
                        101L,
                        1L,
                        33L,
                        22L,
                        java.time.LocalDate.of(2026, 10, 14),
                        1,
                        ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.CANCELLED,
                        ru.rutcampustrack.attendance.contract.enums.AttendanceSource.AUTO_SCHEDULER,
                        null);
        var present =
                new ru.rutcampustrack.attendance.shared.port.AttendanceRecord(
                        101L,
                        2L,
                        33L,
                        22L,
                        java.time.LocalDate.of(2026, 10, 14),
                        1,
                        ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.PRESENT,
                        ru.rutcampustrack.attendance.contract.enums.AttendanceSource.AUTO_SCHEDULER,
                        null);
        org.mockito.Mockito.when(marks.findByLessonIds(List.of(101L)))
                .thenReturn(List.of(cancelled, present));
        var mixed =
                ru.rutcampustrack.attendance.report.AdminGroupStatsReadService.readPlanned(
                                reports, admitted)
                        .getGroups(0);
        assertThat(mixed.getCountedLessons()).isEqualTo(1);
        assertThat(mixed.getPresent()).isEqualTo(1);
        assertThat(mixed.getAbsent()).isEqualTo(1); // Missing historical mark remains ABSENT.
        assertThat(mixed.getExcused()).isZero();
        assertThat(mixed.getOpportunities()).isEqualTo(2);
        assertThat(mixed.getPresentPercent()).isEqualTo(50);
        var allCancelled =
                new java.util.ArrayList<
                        ru.rutcampustrack.attendance.shared.port.AttendanceRecord>();
        for (long user = 1; user <= 3; user++)
            allCancelled.add(
                    new ru.rutcampustrack.attendance.shared.port.AttendanceRecord(
                            101L,
                            user,
                            33L,
                            22L,
                            java.time.LocalDate.of(2026, 10, 14),
                            1,
                            ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.CANCELLED,
                            ru.rutcampustrack.attendance.contract.enums.AttendanceSource
                                    .AUTO_SCHEDULER,
                            null));
        org.mockito.Mockito.when(marks.findByLessonIds(List.of(101L))).thenReturn(allCancelled);
        var empty =
                ru.rutcampustrack.attendance.report.AdminGroupStatsReadService.readPlanned(
                                reports, admitted)
                        .getGroups(0);
        assertThat(empty.getCountedLessons()).isEqualTo(1);
        assertThat(empty.getPresent()).isZero();
        assertThat(empty.getAbsent()).isZero();
        assertThat(empty.getExcused()).isZero();
        assertThat(empty.getOpportunities()).isZero();
        assertThat(empty.hasPresentPercent()).isFalse();
        assertThat(empty.hasPresentOrExcusedPercent()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(academic, schedule);
    }

    @Test
    void adminDataPlanHasExactGroupsSemesterAndDatedRostersWithoutOrdinaryUserAuthority() {
        var source = ReportPlanFixtures.adminPlan();
        var plan = AdmittedReportPlan.admit(source, ReportPlanFixtures.adminActor());
        assertThat(plan.getGroupMembers(33L, java.time.LocalDate.of(2026, 10, 14), 9L))
                .isEqualTo(ReportPlanFixtures.roster(true));
        assertThatThrownBy(() -> plan.getGroup(44L)).isInstanceOf(AccessDeniedException.class);
        for (var changed :
                List.of(
                        source.toBuilder()
                                .setAdminGroupStats(
                                        source.getAdminGroupStats().toBuilder().addGroupIds(44))
                                .build(),
                        source.toBuilder()
                                .setAdminGroupStats(
                                        source.getAdminGroupStats().toBuilder().setSemesterId(10))
                                .build(),
                        source.toBuilder()
                                .setAdminGroupStats(
                                        source.getAdminGroupStats().toBuilder()
                                                .setDateTo("2027-01-01"))
                                .build(),
                        source.toBuilder().setViewStatsAuthorized(true).build(),
                        source.toBuilder().addRosters(source.getRosters(0)).build(),
                        source.toBuilder().clearRosters().build())) {
            assertThatThrownBy(
                            () ->
                                    AdmittedReportPlan.admit(
                                            changed, ReportPlanFixtures.adminActor()))
                    .isInstanceOfAny(AccessDeniedException.class, ReportValidationException.class);
        }
        assertThatThrownBy(
                        () ->
                                AdmittedReportPlan.admit(
                                        source, ReportPlanFixtures.actor(false, false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void helperViewStatsAndDatedReadsStayBoundToImmutableOwnScope() {
        var source = ReportPlanFixtures.plan("HEADMAN_STATS", "html");
        var plan = AdmittedReportPlan.admit(source, ReportPlanFixtures.actor(false, false));
        assertThat(plan.isHeadman()).isFalse();
        assertThat(plan.hasAssistantPermission(33L, "VIEW_STATS")).isTrue();
        assertThat(plan.hasAssistantPermission(99L, "VIEW_STATS")).isFalse();
        assertThat(plan.hasAssistantPermission(33L, "MARK_ATTENDANCE")).isFalse();
        assertThat(plan.getGroupMembers(33L, java.time.LocalDate.of(2026, 10, 14), 9L))
                .isEqualTo(ReportPlanFixtures.roster(true));
        assertThatThrownBy(
                        () -> plan.getGroupMembers(33L, java.time.LocalDate.of(2026, 10, 15), 9L))
                .isInstanceOf(ReportValidationException.class);
        assertThatThrownBy(() -> plan.getGroup(99L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> plan.getHeadmanGroupComposition(33))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void digestBindsSnapshotsActorSelectorAndLeaseNotOnlySelector() {
        var source = ReportPlanFixtures.plan("HEADMAN_STATS", "html");
        String digest = AdmittedReportPlan.digest(source);
        for (var changed :
                List.of(
                        source.toBuilder().setGeneratedAt("2026-10-18T10:00:00Z").build(),
                        source.toBuilder()
                                .setLeaseId("44444444-4444-4444-8444-444444444444")
                                .build(),
                        source.toBuilder()
                                .setGroups(
                                        0, source.getGroups(0).toBuilder().setName("Другой снимок"))
                                .build(),
                        source.toBuilder()
                                .setActor(source.getActor().toBuilder().setSessionVersion(3))
                                .build())) {
            assertThat(AdmittedReportPlan.digest(changed)).isNotEqualTo(digest);
        }
        assertThatThrownBy(
                        () ->
                                AdmittedReportPlan.admit(
                                        source.toBuilder()
                                                .setActor(
                                                        source.getActor().toBuilder()
                                                                .setRolesVersion(6))
                                                .build(),
                                        ReportPlanFixtures.actor(false, false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateForeignMissingAndUnknownMaterialNeverFallsBackToLiveReaders() {
        var source = ReportPlanFixtures.plan("HEADMAN_STATS", "html");
        for (var invalid :
                List.of(
                        source.toBuilder().addLessons(source.getLessons(0)).build(),
                        source.toBuilder().clearRosters().build(),
                        source.toBuilder().setReportKind("ADMIN_USERS").build(),
                        source.toBuilder()
                                .setWeekly(ReportWeeklySelector.newBuilder().setFormat("html"))
                                .build())) {
            assertThatThrownBy(
                            () ->
                                    AdmittedReportPlan.admit(
                                            invalid, ReportPlanFixtures.actor(false, false)))
                    .isInstanceOf(ReportValidationException.class);
        }
        assertThatThrownBy(
                        () ->
                                AdmittedReportPlan.admit(
                                        source.toBuilder()
                                                .setLessons(
                                                        0,
                                                        source.getLessons(0).toBuilder()
                                                                .setGroupId(99))
                                                .build(),
                                        ReportPlanFixtures.actor(false, false)))
                .isInstanceOf(AccessDeniedException.class);
        var unknown =
                source.toBuilder()
                        .setUnknownFields(
                                UnknownFieldSet.newBuilder()
                                        .addField(
                                                999,
                                                UnknownFieldSet.Field.newBuilder()
                                                        .addVarint(1)
                                                        .build())
                                        .build())
                        .build();
        assertThatThrownBy(() -> AdmittedReportPlan.digest(unknown))
                .isInstanceOf(ReportValidationException.class);
    }

    @Test
    void bucketLimitAppliesToActualTrendWorkRatherThanUnrelatedSemesterLength() {
        var original = ReportPlanFixtures.plan("HEADMAN_STATS_TREND", "html");
        var semester =
                original.getSemester().toBuilder()
                        .setDateFrom("2000-01-01")
                        .setDateTo("2026-12-31")
                        .build();
        var selected =
                original.getHeadmanStatsScope().getSelectedSemester().toBuilder()
                        .setDateFrom(semester.getDateFrom())
                        .setDateTo(semester.getDateTo());
        var base =
                original.toBuilder()
                        .setSemester(semester)
                        .setHeadmanStatsScope(
                                original.getHeadmanStatsScope().toBuilder()
                                        .setSelectedSemester(selected));
        var daily =
                base.clone().setTrend(original.getTrend().toBuilder().setBucketUnit("DAY")).build();
        assertThatThrownBy(
                        () ->
                                AdmittedReportPlan.admit(
                                        daily, ReportPlanFixtures.actor(false, false)))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
        assertThatCode(
                        () ->
                                AdmittedReportPlan.admit(
                                        base.clone()
                                                .setTrend(
                                                        original.getTrend().toBuilder()
                                                                .setBucketUnit("WEEK"))
                                                .build(),
                                        ReportPlanFixtures.actor(false, false)))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                AdmittedReportPlan.admit(
                                        base.clone()
                                                .setTrend(
                                                        original.getTrend().toBuilder()
                                                                .setMode("WEEK")
                                                                .setWeekStart("2026-10-12")
                                                                .setBucketUnit("DAY"))
                                                .build(),
                                        ReportPlanFixtures.actor(false, false)))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                AdmittedReportPlan.admit(
                                        base.clone()
                                                .setReportKind("HEADMAN_WEEKLY_SELECTED")
                                                .setWeekly(
                                                        ReportWeeklySelector.newBuilder()
                                                                .addWeekStarts("2026-10-12")
                                                                .setFormat("docx"))
                                                .build(),
                                        ReportPlanFixtures.actor(false, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void countAndByteLimitsRejectBeforeRendering() {
        var source = ReportPlanFixtures.plan("HEADMAN_STATS", "html");
        var oversized = source.toBuilder().clearLessons();
        for (int id = 1; id <= 5001; id++)
            oversized.addLessons(source.getLessons(0).toBuilder().setId(id));
        assertThatThrownBy(
                        () ->
                                AdmittedReportPlan.admit(
                                        oversized.build(), ReportPlanFixtures.actor(false, false)))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
        var weeks = ReportWeeklySelector.newBuilder().setFormat("html");
        for (int week = 0; week < 65; week++) weeks.addWeekStarts("2026-10-12");
        assertThatThrownBy(
                        () ->
                                AdmittedReportPlan.admit(
                                        source.toBuilder()
                                                .setReportKind("HEADMAN_WEEKLY_SELECTED")
                                                .setWeekly(weeks)
                                                .build(),
                                        ReportPlanFixtures.actor(false, false)))
                .isInstanceOf(ReportValidationException.class);
        var bytes =
                source.toBuilder().setGeneratedAt("x".repeat(AdmittedReportPlan.MAX_BYTES)).build();
        assertThatThrownBy(() -> AdmittedReportPlan.digest(bytes))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
    }
}
