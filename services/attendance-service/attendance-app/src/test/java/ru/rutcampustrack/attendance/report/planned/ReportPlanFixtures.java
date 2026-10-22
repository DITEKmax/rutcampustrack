package ru.rutcampustrack.attendance.report.planned;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.authsecurity.grpc.StoredSessionIdentity;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.teacher.grpc.*;

import java.util.UUID;

public final class ReportPlanFixtures {
    public static final UUID SESSION = UUID.fromString("33333333-3333-4333-8333-333333333333");
    public static final String JOB = "11111111-1111-4111-8111-111111111111";
    public static final String LEASE = "22222222-2222-4222-8222-222222222222";

    private ReportPlanFixtures() {}

    public static InternalJwtClaims actor(boolean teacher, boolean headman) {
        return new InternalJwtClaims(
                teacher ? 71 : 1,
                SESSION,
                2,
                5,
                teacher ? "TEACHER" : headman ? "HEADMAN" : "STUDENT",
                "ACTIVE",
                teacher ? null : 33L,
                headman,
                false);
    }

    public static StoredSessionIdentity identity(InternalJwtClaims actor) {
        var builder =
                StoredSessionIdentity.newBuilder()
                        .setUserId(actor.userId())
                        .setSessionId(actor.sessionId().toString())
                        .setSessionVersion(actor.sessionVersion())
                        .setRolesVersion(actor.rolesVersion())
                        .setRole(actor.role())
                        .setStatus(actor.status())
                        .setIsHeadman(actor.isHeadman())
                        .setReadOnly(actor.readOnly());
        if (actor.groupId() != null) builder.setGroupId(actor.groupId());
        return builder.build();
    }

    public static ReportRenderPlan plan(String kind, String format) {
        boolean teacher = kind.startsWith("TEACHER_");
        boolean composition = kind.equals("HEADMAN_GROUP_COMPOSITION");
        var actor = actor(teacher, composition);
        var base =
                ReportRenderPlan.newBuilder()
                        .setActor(identity(actor))
                        .setJobId(JOB)
                        .setLeaseId(LEASE)
                        .setReportKind(kind)
                        .setGeneratedAt("2026-10-18T09:00:00Z")
                        .setViewStatsAuthorized(!teacher)
                        .setSemester(
                                SemesterResponse.newBuilder()
                                        .setId(9)
                                        .setName("Осень 2026")
                                        .setDateFrom("2026-09-01")
                                        .setDateTo("2026-12-31"))
                        .addGroups(
                                GroupResponse.newBuilder()
                                        .setId(33)
                                        .setName("УИТ-311")
                                        .setIsActive(true))
                        .addSubjects(
                                SubjectInfo.newBuilder()
                                        .setSubjectId(22)
                                        .setGroupId(33)
                                        .setSubjectName("Математика")
                                        .setSubjectType("core")
                                        .addLessonTypes("lecture"))
                        .addRosters(
                                ReportGroupRoster.newBuilder()
                                        .setGroupId(33)
                                        .setRoster(roster(false)))
                        .addRosters(
                                ReportGroupRoster.newBuilder()
                                        .setGroupId(33)
                                        .setRoster(roster(true)))
                        .addLessons(lesson());
        if (teacher) {
            base.setTeacherSubjects(
                    TeacherSubjectsResponse.newBuilder()
                            .addSubjects(
                                    TeacherSubjectInfo.newBuilder()
                                            .setGroupId(33)
                                            .setGroupName("УИТ-311")
                                            .setSubjectId(22)
                                            .setSubjectName("Математика")
                                            .setAssignmentId(12)
                                            .setSemesterId(9)
                                            .setLessonType("lecture")
                                            .setValidFrom("2026-09-01")
                                            .setValidUntilExclusive("2027-01-01")));
            base.setTeacherAssignments(
                    TeacherAssignmentsResponse.newBuilder()
                            .setSemesterDateFrom("2026-09-01")
                            .setSemesterDateTo("2026-12-31")
                            .addAssignments(
                                    TeacherAssignment.newBuilder()
                                            .setAssignmentId(12)
                                            .setTeacherId(71)
                                            .setGroupId(33)
                                            .setGroupName("УИТ-311")
                                            .setSubjectId(22)
                                            .setSubjectName("Математика")
                                            .setSemesterId(9)
                                            .setLessonType("lecture")
                                            .setValidFrom("2026-09-01")
                                            .setValidUntilExclusive("2027-01-01")));
        } else {
            var semester =
                    HeadmanStatsSemester.newBuilder()
                            .setId(9)
                            .setName("Осень 2026")
                            .setDateFrom("2026-09-01")
                            .setDateTo("2026-12-31")
                            .setActive(true);
            base.setHeadmanStatsScope(
                    HeadmanStatsScopeResponse.newBuilder()
                            .setGroupId(33)
                            .setGroupName("УИТ-311")
                            .setActiveSemesterId(9)
                            .setSelectedSemester(semester)
                            .addSemesters(semester)
                            .setMembershipActive(3)
                            .setMembershipTotal(3));
        }
        switch (kind) {
            case "TEACHER_JOURNAL" ->
                    base.setTeacherJournal(
                            TeacherAttendanceExportRequest.newBuilder()
                                    .setSemesterId(9)
                                    .setGroupId(33)
                                    .setSubjectId(22)
                                    .addLessonTypes("lecture")
                                    .setFormat(format)
                                    .setReportKind(
                                            TeacherAttendanceReportKind
                                                    .TEACHER_ATTENDANCE_REPORT_KIND_SUBJECT_JOURNAL));
            case "TEACHER_STATS" ->
                    base.setTeacherStats(
                            TeacherStatsExportRequest.newBuilder()
                                    .setFormat(format)
                                    .setStatsRequest(
                                            TeacherStatsRequest.newBuilder()
                                                    .setSemesterId(9)
                                                    .setGroupId(33)
                                                    .setSubjectId(22)
                                                    .addLessonIds(101)
                                                    .addLessonTypes("lecture")
                                                    .setScope(
                                                            TeacherStatsScope
                                                                    .TEACHER_STATS_STUDENTS)));
            case "HEADMAN_WEEKLY_CURRENT", "HEADMAN_WEEKLY_SELECTED" ->
                    base.setWeekly(
                            ReportWeeklySelector.newBuilder()
                                    .addWeekStarts("2026-10-12")
                                    .setFormat(format));
            case "HEADMAN_STATS" ->
                    base.setHeadmanStats(
                            ReportHeadmanStatsSelector.newBuilder()
                                    .setSemesterId(9)
                                    .setSubjectId(22)
                                    .setFormat(format));
            case "HEADMAN_STATS_TREND" ->
                    base.setTrend(
                            ReportTrendSelector.newBuilder()
                                    .setMode("SEMESTER")
                                    .setBucketUnit("WEEK")
                                    .setSemesterId(9)
                                    .setFormat(format));
            case "HEADMAN_SUBJECT_JOURNAL" ->
                    base.setSubjectJournal(
                            ReportSubjectJournalSelector.newBuilder()
                                    .setSubjectId(22)
                                    .addLessonTypes("lecture")
                                    .addWeekStarts("2026-10-12")
                                    .setFormat(format));
            case "HEADMAN_GROUP_COMPOSITION" -> {
                base.setComposition(ReportCompositionSelector.newBuilder().setFormat(format));
                var members =
                        HeadmanGroupCompositionResponse.newBuilder()
                                .setGroupId(33)
                                .setGroupName("УИТ-311");
                for (int id = 1; id <= 3; id++)
                    members.addMembers(
                            GroupCompositionMember.newBuilder()
                                    .setUserId(id)
                                    .setDisplayName("Студент " + id)
                                    .setLogin("synthetic" + id)
                                    .setGroupRole(id == 1 ? "HEADMAN" : "STUDENT"));
                base.setGroupComposition(members);
            }
            default -> throw new IllegalArgumentException("unknown fixture kind");
        }
        return base.build();
    }

    public static InternalJwtClaims adminActor() {
        return new InternalJwtClaims(7, SESSION, 2, 5, "ADMIN", "ACTIVE", null, false, false);
    }

    public static ReportRenderPlan adminPlan() {
        return plan("HEADMAN_STATS", "html").toBuilder()
                .clearSelector()
                .clearHeadmanStatsScope()
                .clearViewStatsAuthorized()
                .clearRosters()
                .addRosters(ReportGroupRoster.newBuilder().setGroupId(33).setRoster(roster(true)))
                .setActor(identity(adminActor()))
                .setReportKind("ADMIN_GROUP_ATTENDANCE")
                .setAdminGroupStats(
                        ru.rutcampustrack.attendance.grpc.AdminGroupStatsQuery.newBuilder()
                                .setSemesterId(9)
                                .setDateFrom("2026-09-01")
                                .setDateTo("2026-12-31")
                                .addGroupIds(33))
                .build();
    }

    public static LessonResponse lesson() {
        return LessonResponse.newBuilder()
                .setId(101)
                .setGroupId(33)
                .setSubjectId(22)
                .setSemesterId(9)
                .setAssignmentId(12)
                .setAssignedTeacherId(71)
                .setOccurrenceId(101)
                .setScheduleItemId(11)
                .setDate("2026-10-14")
                .setStartTime("09:00:00")
                .setEndTime("10:20:00")
                .setLessonType("lecture")
                .setLessonNumber(1)
                .setStatus("closed")
                .build();
    }

    public static GroupMembersResponse roster(boolean dated) {
        var builder = GroupMembersResponse.newBuilder();
        if (dated) builder.setAsOfDate("2026-10-14").setSemesterId(9);
        for (int id = 1; id <= 3; id++)
            builder.addStudents(
                    StudentInfo.newBuilder().setUserId(id).setDisplayName("Студент " + id));
        return builder.build();
    }
}
