package ru.rutcampustrack.academic.exportjobs;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.academic.adminattendance.AdminAttendanceReadRepository;
import ru.rutcampustrack.attendance.grpc.AdminGroupStatsQuery;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/** Serial bounded transport; no report-wide roster or list of plans is retained. */
@Component
public final class AdminAttendanceReportBatcher {
    static final int MAX_ITEMS = 5000;
    static final int MAX_BYTES = 20 * 1024 * 1024;
    private final AcademicReportPlanBuilder plans;
    private final ReportPlanSnapshotReader snapshots;

    public AdminAttendanceReportBatcher(
            AcademicReportPlanBuilder plans, ReportPlanSnapshotReader snapshots) {
        this.plans = plans;
        this.snapshots = snapshots;
    }

    public void read(
            ExportJobService.GroupAttendanceReadScope scope,
            AdminAttendanceReadRepository.Selection selected,
            Consumer<Batch> consumer) {
        scope.check();
        var job = scope.job();
        var lessons = plans.adminLessons(job, selected);
        scope.check();
        Instant generatedAt = Instant.now(); // Rendering clock, not a global DB snapshot claim.
        var byGroup = new TreeMap<Long, TreeMap<LocalDate, List<LessonResponse>>>();
        for (var lesson : lessons) {
            if (!lesson.getStatus().equalsIgnoreCase("closed")) continue;
            byGroup.computeIfAbsent(lesson.getGroupId(), ignored -> new TreeMap<>())
                    .computeIfAbsent(
                            LocalDate.parse(lesson.getDate()), ignored -> new ArrayList<>())
                    .add(lesson);
        }
        var packer = new Packer(consumer);
        for (var group : byGroup.entrySet()) {
            for (var date : group.getValue().entrySet()) {
                scope.check();
                // Existing ordinary one-group/date materialization is retained, then released.
                var unit =
                        snapshots.adminUnit(
                                scope.job(),
                                selected,
                                group.getKey(),
                                date.getKey(),
                                date.getValue(),
                                generatedAt);
                scope.check();
                packer.accept(unit);
            }
        }
        packer.flush();
        scope.check();
    }

    /** False only for later student fragments of the exact same lesson partition. */
    public record Batch(ReportRenderPlan plan, boolean countLessons) {}

    static final class Packer {
        private final Consumer<Batch> consumer;
        private ReportRenderPlan pending;

        Packer(Consumer<Batch> consumer) {
            this.consumer = Objects.requireNonNull(consumer);
        }

        void accept(ReportPlanSnapshotReader.AdminUnit unit) {
            var roster = unit.roster().getRoster();
            long previous = 0;
            for (var student : roster.getStudentsList()) {
                if (student.getUserId() <= previous) throw unavailable();
                previous = student.getUserId();
            }
            var whole = withRoster(unit.plan(), unit.roster());
            if (!fits(whole)) {
                flush();
                fragmented(unit.plan(), unit.roster());
                return;
            }
            if (pending == null) {
                pending = whole;
                return;
            }
            var combined = combine(pending, whole);
            if (fits(combined)) pending = combined;
            else {
                flush();
                pending = whole;
            }
        }

        void flush() {
            if (pending == null) return;
            var plan = pending;
            pending = null;
            consumer.accept(new Batch(plan, true));
        }

        private void fragmented(ReportRenderPlan template, ReportGroupRoster roster) {
            var members = roster.getRoster().getStudentsList();
            var empty =
                    roster.toBuilder()
                            .setRoster(roster.getRoster().toBuilder().clearStudents())
                            .build();
            // If even one member cannot fit, split disjoint lessons before slicing students.
            var minimum =
                    members.isEmpty()
                            ? empty
                            : empty.toBuilder()
                                    .setRoster(
                                            empty.getRoster().toBuilder()
                                                    .addStudents(members.getFirst()))
                                    .build();
            if (!fits(withRoster(template, minimum))) {
                if (template.getLessonsCount() <= 1) throw tooLarge();
                int middle = template.getLessonsCount() / 2;
                fragmented(lessonPartition(template, 0, middle), roster);
                fragmented(lessonPartition(template, middle, template.getLessonsCount()), roster);
                return;
            }
            if (members.isEmpty()) {
                consumer.accept(new Batch(withRoster(template, empty), true));
                return;
            }
            int from = 0;
            boolean first = true;
            while (from < members.size()) {
                int to = Math.min(members.size(), from + MAX_ITEMS);
                ReportRenderPlan candidate;
                for (; ; ) {
                    var part =
                            empty.toBuilder()
                                    .setRoster(
                                            empty.getRoster().toBuilder()
                                                    .addAllStudents(members.subList(from, to)))
                                    .build();
                    candidate = withRoster(template, part);
                    if (fits(candidate)) break;
                    if (to - from <= 1) throw tooLarge();
                    to = from + (to - from) / 2;
                }
                consumer.accept(new Batch(candidate, first));
                first = false;
                from = to; // Exact disjoint coverage of this one captured historical roster.
            }
        }

        private static ReportRenderPlan lessonPartition(ReportRenderPlan source, int from, int to) {
            var selected = source.getLessonsList().subList(from, to);
            var subjects =
                    selected.stream()
                            .map(LessonResponse::getSubjectId)
                            .collect(java.util.stream.Collectors.toSet());
            return source.toBuilder()
                    .clearLessons()
                    .addAllLessons(selected)
                    .clearSubjects()
                    .addAllSubjects(
                            source.getSubjectsList().stream()
                                    .filter(s -> subjects.contains(s.getSubjectId()))
                                    .toList())
                    .build();
        }

        private static ReportRenderPlan withRoster(
                ReportRenderPlan source, ReportGroupRoster roster) {
            return select(source.toBuilder().clearRosters().addRosters(roster)).build();
        }

        private static ReportRenderPlan combine(ReportRenderPlan left, ReportRenderPlan right) {
            if (!left.getActor().equals(right.getActor())
                    || !left.getJobId().equals(right.getJobId())
                    || !left.getLeaseId().equals(right.getLeaseId())
                    || !left.getReportKind().equals(right.getReportKind())
                    || !left.getSemester().equals(right.getSemester())
                    || !left.getGeneratedAt().equals(right.getGeneratedAt())) throw unavailable();
            var builder =
                    left.toBuilder()
                            .addAllLessons(right.getLessonsList())
                            .addAllRosters(right.getRostersList());
            var groups = new HashSet<Long>();
            for (var group : left.getGroupsList()) groups.add(group.getId());
            for (var group : right.getGroupsList())
                if (groups.add(group.getId())) builder.addGroups(group);
            var subjects = new HashSet<Long>();
            for (var subject : left.getSubjectsList()) subjects.add(subject.getSubjectId());
            for (var subject : right.getSubjectsList())
                if (subjects.add(subject.getSubjectId())) builder.addSubjects(subject);
            return select(builder).build();
        }

        private static ReportRenderPlan.Builder select(ReportRenderPlan.Builder plan) {
            return plan.setAdminGroupStats(
                    AdminGroupStatsQuery.newBuilder()
                            .setSemesterId(plan.getSemester().getId())
                            .setDateFrom(plan.getSemester().getDateFrom())
                            .setDateTo(plan.getSemester().getDateTo())
                            .addAllGroupIds(
                                    plan.getGroupsList().stream()
                                            .map(g -> g.getId())
                                            .sorted()
                                            .toList()));
        }

        private static boolean fits(ReportRenderPlan plan) {
            if (plan.getSerializedSize() > MAX_BYTES
                    || plan.getLessonsCount() > MAX_ITEMS
                    || plan.getGroupsCount() > MAX_ITEMS
                    || plan.getRostersCount() > MAX_ITEMS
                    || plan.getSubjectsCount() > MAX_ITEMS) return false;
            var students = new HashSet<Long>();
            for (var roster : plan.getRostersList())
                for (var student : roster.getRoster().getStudentsList()) {
                    students.add(student.getUserId());
                    if (students.size() > MAX_ITEMS) return false;
                }
            return true;
        }
    }

    private static RuntimeException unavailable() {
        return ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static RuntimeException tooLarge() {
        return ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
