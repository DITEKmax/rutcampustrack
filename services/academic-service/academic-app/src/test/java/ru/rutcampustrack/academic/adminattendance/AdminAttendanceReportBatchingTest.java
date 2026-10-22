package ru.rutcampustrack.academic.adminattendance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels.Semester;
import ru.rutcampustrack.academic.exportjobs.*;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.authsecurity.grpc.StoredSessionIdentity;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.LocalDate;
import java.util.*;

class AdminAttendanceReportBatchingTest {
    private final AcademicReportPlanBuilder plans = mock(AcademicReportPlanBuilder.class);
    private final ReportPlanSnapshotReader snapshots = mock(ReportPlanSnapshotReader.class);
    private final ExportJobService.GroupAttendanceReadScope scope =
            mock(ExportJobService.GroupAttendanceReadScope.class);
    private final AdminAttendanceReportBatcher batcher =
            new AdminAttendanceReportBatcher(plans, snapshots);
    private final Semester semester =
            new Semester(
                    "9",
                    "Семестр",
                    LocalDate.parse("2026-09-01"),
                    LocalDate.parse("2026-12-31"),
                    true,
                    false);

    @Test
    void sixThousandStudentsAcrossOneHundredGroupsStayCompleteWithinTwoBoundedPlans() {
        var units = new ArrayList<ReportPlanSnapshotReader.AdminUnit>();
        for (int group = 1; group <= 100; group++)
            units.add(unit(group, "2026-10-14", (group - 1) * 60 + 1, 60, 1));
        var batches = run(units);
        assertThat(batches).hasSize(2);
        assertThat(
                        batches.stream()
                                .flatMap(b -> b.plan().getGroupsList().stream())
                                .map(GroupResponse::getId))
                .containsExactlyElementsOf(
                        java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList());
        assertThat(
                        batches.stream()
                                .flatMap(b -> b.plan().getRostersList().stream())
                                .mapToInt(r -> r.getRoster().getStudentsCount())
                                .sum())
                .isEqualTo(6000);
        var totals = counters(units);
        for (var batch : batches) {
            assertThat(batch.countLessons()).isTrue();
            assertBounded(batch.plan());
            AttendanceAdminGroupStatsClient.accumulate(batch, response(batch.plan()), totals);
        }
        assertThat(AttendanceAdminGroupStatsClient.finish(totals))
                .hasSize(100)
                .allSatisfy(
                        row -> {
                            assertThat(row.getCountedLessons()).isEqualTo(1);
                            assertThat(row.getOpportunities()).isEqualTo(60);
                            assertThat(row.getPresent()).isEqualTo(20);
                            assertThat(row.getExcused()).isEqualTo(20);
                            assertThat(row.getAbsent()).isEqualTo(20);
                        });
    }

    @Test
    void oversizedOneDateRosterIsDisjointAndCountsEachLessonOnceWhileSummingRawCounters() {
        var source = unit(33, "2026-10-14", 1, 6001, 2);
        var batches = run(List.of(source));
        assertThat(batches).hasSize(2);
        assertThat(batches)
                .extracting(AdminAttendanceReportBatcher.Batch::countLessons)
                .containsExactly(true, false);
        assertThat(
                        batches.stream()
                                .flatMap(
                                        b ->
                                                b
                                                        .plan()
                                                        .getRosters(0)
                                                        .getRoster()
                                                        .getStudentsList()
                                                        .stream())
                                .map(StudentInfo::getUserId))
                .containsExactlyElementsOf(
                        java.util.stream.LongStream.rangeClosed(1, 6001).boxed().toList());
        var totals = counters(List.of(source));
        for (var batch : batches) {
            assertBounded(batch.plan());
            AttendanceAdminGroupStatsClient.accumulate(batch, response(batch.plan()), totals);
        }
        var result = AttendanceAdminGroupStatsClient.finish(totals).getFirst();
        assertThat(result.getCountedLessons()).isEqualTo(2);
        assertThat(result.getPresent()).isEqualTo(4000);
        assertThat(result.getExcused()).isEqualTo(4002);
        assertThat(result.getAbsent()).isEqualTo(4000);
        assertThat(result.getOpportunities()).isEqualTo(12002);
        assertThat(result.getPresentPercent()).isEqualTo(4000 * 100.0 / 12002);
        assertThat(result.getPresentOrExcusedPercent()).isEqualTo(8002 * 100.0 / 12002);
        var later = batches.get(1);
        var wrong = response(later.plan()).toBuilder();
        wrong.setGroups(0, wrong.getGroups(0).toBuilder().setCountedLessons(1));
        assertThatThrownBy(
                        () ->
                                AttendanceAdminGroupStatsClient.accumulate(
                                        later, wrong.build(), counters(List.of(source))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void canonicalCancelledCellsMayReduceDenominatorIncludingAnEntireFirstFragment() {
        var source = unit(33, "2026-10-14", 1, 6001, 2);
        var batches = run(List.of(source));
        var totals = counters(List.of(source));
        var cancelled = response(batches.getFirst().plan()).toBuilder();
        cancelled.setGroups(
                0,
                cancelled.getGroups(0).toBuilder()
                        .setPresent(0)
                        .setAbsent(0)
                        .setExcused(0)
                        .setOpportunities(0)
                        .clearPresentPercent()
                        .clearPresentOrExcusedPercent());
        AttendanceAdminGroupStatsClient.accumulate(batches.getFirst(), cancelled.build(), totals);
        var noData = AttendanceAdminGroupStatsClient.finish(totals).getFirst();
        assertThat(noData.getCountedLessons()).isEqualTo(2);
        assertThat(noData.getOpportunities()).isZero();
        assertThat(noData.hasPresentPercent()).isFalse();
        assertThat(noData.hasPresentOrExcusedPercent()).isFalse();
        AttendanceAdminGroupStatsClient.accumulate(
                batches.get(1), response(batches.get(1).plan()), totals);
        var result = AttendanceAdminGroupStatsClient.finish(totals).getFirst();
        assertThat(result.getCountedLessons()).isEqualTo(2);
        assertThat(result.getPresent()).isEqualTo(668);
        assertThat(result.getAbsent()).isEqualTo(666);
        assertThat(result.getExcused()).isEqualTo(668);
        assertThat(result.getOpportunities()).isEqualTo(2002);
        assertThat(result.getPresentPercent()).isEqualTo(668 * 100.0 / 2002);

        var one = unit(44, "2026-10-15", 1, 3, 1);
        var batch = run(List.of(one)).getFirst();
        var mixed = response(batch.plan()).toBuilder();
        mixed.setGroups(
                0,
                mixed.getGroups(0).toBuilder()
                        .setPresent(1)
                        .setExcused(0)
                        .setAbsent(1)
                        .setOpportunities(2)
                        .setPresentPercent(50)
                        .setPresentOrExcusedPercent(50));
        var mixedTotals = counters(List.of(one));
        AttendanceAdminGroupStatsClient.accumulate(batch, mixed.build(), mixedTotals);
        assertThat(
                        AttendanceAdminGroupStatsClient.finish(mixedTotals)
                                .getFirst()
                                .getOpportunities())
                .isEqualTo(2);
        var excess = mixed.build().toBuilder();
        excess.setGroups(
                0,
                excess.getGroups(0).toBuilder()
                        .setAbsent(3)
                        .setOpportunities(4)
                        .setPresentPercent(25)
                        .setPresentOrExcusedPercent(25));
        assertThatThrownBy(
                        () ->
                                AttendanceAdminGroupStatsClient.accumulate(
                                        batch, excess.build(), counters(List.of(one))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void emptyHistoricalRosterCountsClosedLessonAndTransferDatesNeverDeduplicateStudents() {
        var units =
                List.of(
                        unit(33, "2026-10-14", 1, 1, 1),
                        unit(44, "2026-10-15", 1, 1, 1),
                        unit(55, "2026-10-16", 1, 0, 1));
        var totals = counters(units);
        for (var batch : run(units))
            AttendanceAdminGroupStatsClient.accumulate(batch, response(batch.plan()), totals);
        var rows = AttendanceAdminGroupStatsClient.finish(totals);
        assertThat(rows)
                .extracting(AdminGroupAttendanceTotals::getCountedLessons)
                .containsExactly(1L, 1L, 1L);
        assertThat(rows)
                .extracting(AdminGroupAttendanceTotals::getOpportunities)
                .containsExactly(1L, 1L, 0L);
        assertThat(rows.get(2).hasPresentPercent()).isFalse();
        assertThat(rows.get(2).hasPresentOrExcusedPercent()).isFalse();
    }

    @Test
    void groupsWithoutClosedLessonsHaveZeroRowsWithoutInventingHistoricalRosterReads() {
        var selected = new AdminAttendanceReadRepository.Selection(semester, Map.of(33L, "Группа"));
        when(scope.job()).thenReturn(mock(ExportJobRepository.Job.class));
        when(plans.adminLessons(any(), eq(selected))).thenReturn(List.of());
        var batches = new ArrayList<AdminAttendanceReportBatcher.Batch>();
        batcher.read(scope, selected, batches::add);
        assertThat(batches).isEmpty();
        verifyNoInteractions(snapshots);
        var result =
                AttendanceAdminGroupStatsClient.finish(new TreeMap<>(Map.of(33L, new long[5])))
                        .getFirst();
        assertThat(result.getCountedLessons()).isZero();
        assertThat(result.getOpportunities()).isZero();
        assertThat(result.hasPresentPercent()).isFalse();
    }

    private List<AdminAttendanceReportBatcher.Batch> run(
            List<ReportPlanSnapshotReader.AdminUnit> units) {
        var groups = new TreeMap<Long, String>();
        var lessons = new ArrayList<LessonResponse>();
        for (var unit : units) {
            groups.put(unit.plan().getGroups(0).getId(), unit.plan().getGroups(0).getName());
            lessons.addAll(unit.plan().getLessonsList());
        }
        var selected = new AdminAttendanceReadRepository.Selection(semester, groups);
        when(scope.job()).thenReturn(mock(ExportJobRepository.Job.class));
        when(plans.adminLessons(any(), eq(selected))).thenReturn(lessons);
        when(snapshots.adminUnit(any(), eq(selected), anyLong(), any(), anyList(), any()))
                .thenAnswer(
                        invocation ->
                                units.stream()
                                        .filter(
                                                unit ->
                                                        unit.roster().getGroupId()
                                                                        == invocation
                                                                                .<Long>getArgument(
                                                                                        2)
                                                                && unit.roster()
                                                                        .getRoster()
                                                                        .getAsOfDate()
                                                                        .equals(
                                                                                invocation
                                                                                        .<LocalDate>
                                                                                                getArgument(
                                                                                                        3)
                                                                                        .toString()))
                                        .findFirst()
                                        .orElseThrow());
        var result = new ArrayList<AdminAttendanceReportBatcher.Batch>();
        batcher.read(scope, selected, result::add);
        return result;
    }

    private static Map<Long, long[]> counters(List<ReportPlanSnapshotReader.AdminUnit> units) {
        var result = new TreeMap<Long, long[]>();
        for (var unit : units) result.put(unit.roster().getGroupId(), new long[5]);
        return result;
    }

    private static void assertBounded(ReportRenderPlan plan) {
        assertThat(plan.getSerializedSize()).isLessThanOrEqualTo(20 * 1024 * 1024);
        assertThat(plan.getLessonsCount()).isLessThanOrEqualTo(5000);
        assertThat(
                        plan.getRostersList().stream()
                                .flatMap(r -> r.getRoster().getStudentsList().stream())
                                .map(StudentInfo::getUserId)
                                .distinct()
                                .count())
                .isLessThanOrEqualTo(5000);
    }

    private static AdminGroupStatsSnapshot response(ReportRenderPlan plan) {
        var query = plan.getAdminGroupStats();
        var response =
                AdminGroupStatsSnapshot.newBuilder()
                        .setSemesterId(query.getSemesterId())
                        .setDateFrom(query.getDateFrom())
                        .setDateTo(query.getDateTo());
        for (long group : query.getGroupIdsList()) {
            long present = 0, absent = 0, excused = 0, lessons = 0;
            for (var lesson : plan.getLessonsList())
                if (lesson.getGroupId() == group) {
                    lessons++;
                    var roster =
                            plan.getRostersList().stream()
                                    .filter(
                                            r ->
                                                    r.getGroupId() == group
                                                            && r.getRoster()
                                                                    .getAsOfDate()
                                                                    .equals(lesson.getDate()))
                                    .findFirst()
                                    .orElseThrow();
                    for (var member : roster.getRoster().getStudentsList()) {
                        if (member.getUserId() % 3 == 0) present++;
                        else if (member.getUserId() % 3 == 1) excused++;
                        else absent++;
                    }
                }
            long opportunities = present + absent + excused;
            var row =
                    AdminGroupAttendanceTotals.newBuilder()
                            .setGroupId(group)
                            .setCountedLessons(lessons)
                            .setPresent(present)
                            .setAbsent(absent)
                            .setExcused(excused)
                            .setOpportunities(opportunities);
            if (opportunities > 0)
                row.setPresentPercent(present * 100.0 / opportunities)
                        .setPresentOrExcusedPercent((present + excused) * 100.0 / opportunities);
            response.addGroups(row);
        }
        return response.build();
    }

    private static ReportPlanSnapshotReader.AdminUnit unit(
            long group, String date, int firstStudent, int students, int lessons) {
        var plan =
                ReportRenderPlan.newBuilder()
                        .setActor(
                                StoredSessionIdentity.newBuilder()
                                        .setUserId(7)
                                        .setSessionId("33333333-3333-4333-8333-333333333333")
                                        .setSessionVersion(1)
                                        .setRolesVersion(2)
                                        .setRole("ADMIN")
                                        .setStatus("ACTIVE"))
                        .setJobId("11111111-1111-4111-8111-111111111111")
                        .setLeaseId("22222222-2222-4222-8222-222222222222")
                        .setReportKind("ADMIN_GROUP_ATTENDANCE")
                        .setGeneratedAt("2026-10-18T09:00:00Z")
                        .setSemester(
                                SemesterResponse.newBuilder()
                                        .setId(9)
                                        .setName("Семестр")
                                        .setDateFrom("2026-09-01")
                                        .setDateTo("2026-12-31"))
                        .addGroups(
                                GroupResponse.newBuilder().setId(group).setName("Группа " + group))
                        .addSubjects(
                                SubjectInfo.newBuilder()
                                        .setSubjectId(group)
                                        .setGroupId(group)
                                        .setSubjectName("Предмет"));
        for (int index = 0; index < lessons; index++)
            plan.addLessons(
                    LessonResponse.newBuilder()
                            .setId(group * 100 + index)
                            .setGroupId(group)
                            .setSubjectId(group)
                            .setSemesterId(9)
                            .setDate(date)
                            .setStatus("closed")
                            .setLessonType("lecture"));
        var roster = GroupMembersResponse.newBuilder().setAsOfDate(date).setSemesterId(9);
        for (int index = 0; index < students; index++)
            roster.addStudents(
                    StudentInfo.newBuilder()
                            .setUserId(firstStudent + index)
                            .setDisplayName("Студент"));
        return new ReportPlanSnapshotReader.AdminUnit(
                plan.build(),
                ReportGroupRoster.newBuilder().setGroupId(group).setRoster(roster).build());
    }
}
