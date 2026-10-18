package ru.rutcampustrack.academic.exportjobs;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.assignment.AssignmentLifecycle;
import ru.rutcampustrack.academic.entity.*;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.academic.repository.*;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.authsecurity.grpc.StoredSessionIdentity;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.teacher.grpc.*;

import java.time.*;
import java.util.*;

/**
 * Coherent Academic-only snapshots. No remote operation executes within these read transactions.
 */
@Component
public class ReportPlanSnapshotReader {
    private final LocalReportAuthority local;
    private final SemesterRepository semesters;
    private final GroupRepository groups;
    private final SubjectRepository subjects;
    private final AssignmentRepository assignments;
    private final AcademicReadService reads;
    private final JdbcTemplate jdbc;
    private final HeadmanGroupCompositionReadService composition;

    public ReportPlanSnapshotReader(
            LocalReportAuthority local,
            SemesterRepository semesters,
            GroupRepository groups,
            SubjectRepository subjects,
            AssignmentRepository assignments,
            AcademicReadService reads,
            JdbcTemplate jdbc,
            HeadmanGroupCompositionReadService composition) {
        this.local = local;
        this.semesters = semesters;
        this.groups = groups;
        this.subjects = subjects;
        this.assignments = assignments;
        this.reads = reads;
        this.jdbc = jdbc;
        this.composition = composition;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Discovery discover(ExportJobRepository.Job job) {
        local.requireWithinSnapshot(job.identity(), false);
        var p = job.identity().report();
        List<Long> groupIds;
        if (p.kind() == ReportDownloadKind.TEACHER_STATS && LocalReportAuthority.unscoped(p))
            groupIds =
                    local.teacherScope(job.identity().actor().userId()).tuples().stream()
                            .map(t -> t.groupId())
                            .distinct()
                            .sorted()
                            .toList();
        else if (p.kind() == ReportDownloadKind.TEACHER_STATS)
            groupIds = List.of(p.teacherStats().groupId());
        else if (p.kind() == ReportDownloadKind.TEACHER_JOURNAL)
            groupIds = List.of(p.teacherJournal().groupId());
        else groupIds = List.of(job.identity().actor().groupId());
        Long semesterId = job.identity().semesterId();
        if (semesterId == null) return new Discovery(null, null, null, groupIds);
        var range = local.semesterRange(semesterId);
        return new Discovery(semesterId, range.from(), range.to(), groupIds);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReportRenderPlan snapshot(
            ExportJobRepository.Job job,
            Discovery discovery,
            List<LessonResponse> lessons,
            Instant generatedAt,
            HeadmanScope admittedScope) {
        local.requireWithinSnapshot(job.identity(), false);
        if (!Objects.equals(discovery.semesterId(), job.identity().semesterId()))
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        var scope = requireHeadmanScope(job, discovery, admittedScope);
        if (lessons.size() > 5000) throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
        var p = job.identity().report();
        var actor = job.identity().actor();
        var identity =
                StoredSessionIdentity.newBuilder()
                        .setUserId(actor.userId())
                        .setSessionId(actor.sessionId().toString())
                        .setSessionVersion(actor.sessionVersion())
                        .setRolesVersion(actor.rolesVersion())
                        .setRole(actor.role())
                        .setStatus(actor.status())
                        .setIsHeadman(actor.isHeadman())
                        .setReadOnly(actor.readOnly());
        if (actor.groupId() != null) identity.setGroupId(actor.groupId());
        var plan =
                ReportRenderPlan.newBuilder()
                        .setActor(identity)
                        .setJobId(job.id().toString())
                        .setLeaseId(job.leaseId().toString())
                        .setReportKind(p.kind().code())
                        .setGeneratedAt(generatedAt.toString());
        var groupMap = new TreeMap<Long, Group>();
        for (Long groupId : discovery.groupIds()) {
            local.requireGroupReadable(groupId);
            var group =
                    groups.findById(groupId)
                            .orElseThrow(() -> ExportJobRepository.error(HttpStatus.FORBIDDEN));
            groupMap.put(groupId, group);
            plan.addGroups(
                    GroupResponse.newBuilder()
                            .setId(groupId)
                            .setName(group.getName())
                            .setIsActive(group.isActive()));
        }
        if (scope != null
                && !groupMap.get(scope.getGroupId()).getName().equals(scope.getGroupName()))
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        if (p.kind() == ReportDownloadKind.HEADMAN_GROUP_COMPOSITION) {
            plan.setGroupComposition(composition.read(actor, actor.groupId()));
            if (plan.getGroupComposition().getMembersCount() > 5000
                    || plan.build().getSerializedSize() > 20 * 1024 * 1024)
                throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
            return plan.build();
        }
        if (discovery.semesterId() == null) {
            if (p.kind() != ReportDownloadKind.HEADMAN_STATS
                    && p.kind() != ReportDownloadKind.HEADMAN_STATS_TREND)
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            plan.setViewStatsAuthorized(true);
            plan.setHeadmanStatsScope(scope);
            var students = new HashSet<Long>();
            for (Long groupId : discovery.groupIds()) {
                var roster = GroupMembersResponse.newBuilder();
                for (User user : reads.fetchGroupMembers(groupId)) {
                    students.add(user.getId());
                    roster.addStudents(student(user));
                }
                plan.addRosters(
                        ReportGroupRoster.newBuilder().setGroupId(groupId).setRoster(roster));
            }
            if (students.size() > 5000 || plan.build().getSerializedSize() > 20 * 1024 * 1024)
                throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
            return plan.build();
        }
        var semester =
                semesters
                        .findByIdUncached(discovery.semesterId())
                        .orElseThrow(() -> ExportJobRepository.error(HttpStatus.FORBIDDEN));
        if (scope != null) {
            var selected = scope.getSelectedSemester();
            if (!selected.getName().equals(semester.getName())
                    || !selected.getDateFrom().equals(semester.getDateFrom().toString())
                    || !selected.getDateTo().equals(semester.getDateTo().toString())
                    || !semester.getDateFrom().equals(discovery.from())
                    || !semester.getDateTo().equals(discovery.to())
                    || selected.getActive() != semester.isActive()
                    || selected.getArchived() != semester.isArchived())
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        }
        plan.setSemester(semester(semester));
        plan.addSemesterStates(state(semester));
        var seenLessons = new HashSet<Long>();
        var subjectIds = new TreeSet<Long>();
        for (var lesson : lessons) {
            LocalDate date = LocalDate.parse(lesson.getDate());
            if (lesson.getId() <= 0
                    || !seenLessons.add(lesson.getId())
                    || !groupMap.containsKey(lesson.getGroupId())
                    || lesson.getSemesterId() != semester.getId()
                    || date.isBefore(discovery.from())
                    || date.isAfter(discovery.to())
                    || lesson.getSubjectId() <= 0)
                throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
            subjectIds.add(lesson.getSubjectId());
        }
        if (p.kind() == ReportDownloadKind.TEACHER_JOURNAL)
            subjectIds.add(p.teacherJournal().subjectId());
        if (p.kind() == ReportDownloadKind.TEACHER_STATS && p.teacherStats().subjectId() != null)
            subjectIds.add(p.teacherStats().subjectId());
        if (p.kind() == ReportDownloadKind.HEADMAN_SUBJECT_JOURNAL)
            subjectIds.add(p.headmanSubjectJournal().subjectId());
        if (p.kind() == ReportDownloadKind.HEADMAN_STATS && p.headmanStats().subjectId() != null)
            subjectIds.add(p.headmanStats().subjectId());
        if (p.kind() == ReportDownloadKind.HEADMAN_STATS_TREND
                && p.headmanStatsTrend().subjectId() != null)
            subjectIds.add(p.headmanStatsTrend().subjectId());
        var types = new HashMap<Long, List<String>>();
        if (!subjectIds.isEmpty())
            for (var row : subjects.findLessonTypeCodes(List.copyOf(subjectIds)))
                types.computeIfAbsent(((Number) row[0]).longValue(), ignored -> new ArrayList<>())
                        .add(row[1].toString().toLowerCase(Locale.ROOT));
        var subjectMap = new HashMap<Long, Subject>();
        for (var subject : subjects.findAllById(subjectIds)) {
            if (!groupMap.containsKey(subject.getGroupId()))
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            subjectMap.put(subject.getId(), subject);
        }
        if (subjectMap.size() != subjectIds.size())
            throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        for (Long id : subjectIds) {
            var subject = subjectMap.get(id);
            plan.addSubjects(
                    SubjectInfo.newBuilder()
                            .setSubjectId(id)
                            .setSubjectName(subject.getName())
                            .setSubjectType(subject.getType().name().toLowerCase(Locale.ROOT))
                            .setGroupId(subject.getGroupId())
                            .addAllLessonTypes(types.getOrDefault(id, List.of())));
        }
        plan.addAllLessons(
                lessons.stream()
                        .sorted(
                                Comparator.comparing(LessonResponse::getDate)
                                        .thenComparingInt(LessonResponse::getLessonNumber)
                                        .thenComparingLong(LessonResponse::getId))
                        .toList());
        var uniqueStudents = new HashSet<Long>();
        for (Long groupId : discovery.groupIds()) {
            var current = GroupMembersResponse.newBuilder();
            for (User user : reads.fetchGroupMembers(groupId)) {
                uniqueStudents.add(user.getId());
                current.addStudents(student(user));
            }
            plan.addRosters(ReportGroupRoster.newBuilder().setGroupId(groupId).setRoster(current));
            var dates =
                    lessons.stream()
                            .filter(l -> l.getGroupId() == groupId)
                            .map(l -> LocalDate.parse(l.getDate()))
                            .distinct()
                            .sorted()
                            .toList();
            for (var date : dates) {
                var roster = reads.fetchHistoricalGroupMembers(groupId, date, semester.getId());
                var response =
                        GroupMembersResponse.newBuilder()
                                .setAsOfDate(date.toString())
                                .setSemesterId(semester.getId());
                for (User user : roster.students()) {
                    uniqueStudents.add(user.getId());
                    response.addStudents(student(user));
                }
                plan.addRosters(
                        ReportGroupRoster.newBuilder().setGroupId(groupId).setRoster(response));
                if (uniqueStudents.size() > 5000)
                    throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
            }
        }
        if (actor.role().equals("TEACHER")) {
            var active =
                    semesters
                            .findByIsActiveTrue()
                            .orElseThrow(() -> ExportJobRepository.error(HttpStatus.FORBIDDEN));
            var current = TeacherSubjectsResponse.newBuilder();
            LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
            for (var assignment :
                    assignments
                            .findByTeacherIdAndSemesterId(actor.userId(), active.getId())
                            .stream()
                            .sorted(Comparator.comparing(Assignment::getId))
                            .toList()) {
                if (assignment.getLifecycleState() != AssignmentLifecycle.ACTIVE
                        || assignment.getValidFrom().isAfter(today)
                        || !today.isBefore(
                                assignment.getValidUntilExclusive() == null
                                        ? active.getDateTo().plusDays(1)
                                        : assignment.getValidUntilExclusive())) continue;
                var subject = subjects.findById(assignment.getSubjectId()).orElseThrow();
                var group = groups.findById(assignment.getGroupId()).orElseThrow();
                current.addSubjects(
                        TeacherSubjectInfo.newBuilder()
                                .setSubjectId(subject.getId())
                                .setSubjectName(subject.getName())
                                .setSubjectType(
                                        assignment.getLessonType().name().toLowerCase(Locale.ROOT))
                                .setGroupId(group.getId())
                                .setGroupName(group.getName())
                                .setAssignmentId(assignment.getId())
                                .setSemesterId(active.getId())
                                .setLessonType(
                                        assignment.getLessonType().name().toLowerCase(Locale.ROOT))
                                .setValidFrom(assignment.getValidFrom().toString())
                                .setValidUntilExclusive(
                                        (assignment.getValidUntilExclusive() == null
                                                        ? active.getDateTo().plusDays(1)
                                                        : assignment.getValidUntilExclusive())
                                                .toString()));
            }
            plan.setTeacherSubjects(current);
            var allowedPairs =
                    current.getSubjectsList().stream()
                            .map(
                                    value ->
                                            new ru.rutcampustrack.shared.security.teacher
                                                    .TeacherReportScope.Tuple(
                                                    value.getGroupId(), value.getSubjectId()))
                            .collect(java.util.stream.Collectors.toSet());
            var dated =
                    TeacherAssignmentsResponse.newBuilder()
                            .setSemesterDateFrom(semester.getDateFrom().toString())
                            .setSemesterDateTo(semester.getDateTo().toString())
                            .setServerNow(generatedAt.toString());
            for (var a :
                    assignments
                            .findByTeacherIdAndSemesterId(actor.userId(), semester.getId())
                            .stream()
                            .sorted(Comparator.comparing(Assignment::getId))
                            .toList()) {
                if (!allowedPairs.contains(
                        new ru.rutcampustrack.shared.security.teacher.TeacherReportScope.Tuple(
                                a.getGroupId(), a.getSubjectId()))) continue;
                LocalDate end =
                        a.getValidUntilExclusive() == null
                                ? semester.getDateTo().plusDays(1)
                                : a.getValidUntilExclusive();
                if (a.getValidFrom().isAfter(semester.getDateTo())
                        || !end.isAfter(semester.getDateFrom())) continue;
                var subject = subjects.findById(a.getSubjectId()).orElseThrow();
                var group = groups.findById(a.getGroupId()).orElseThrow();
                dated.addAssignments(
                        TeacherAssignment.newBuilder()
                                .setAssignmentId(a.getId())
                                .setTeacherId(actor.userId())
                                .setGroupId(group.getId())
                                .setGroupName(group.getName())
                                .setSubjectId(subject.getId())
                                .setSubjectName(subject.getName())
                                .setSemesterId(semester.getId())
                                .setLessonType(a.getLessonType().name().toLowerCase(Locale.ROOT))
                                .setValidFrom(a.getValidFrom().toString())
                                .setValidUntilExclusive(end.toString()));
            }
            plan.setTeacherAssignments(dated);
        } else {
            plan.setViewStatsAuthorized(true);
            plan.setHeadmanStatsScope(scope);
        }
        if (uniqueStudents.size() > 5000 || plan.build().getSerializedSize() > 20 * 1024 * 1024)
            throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);
        return plan.build();
    }

    static boolean requiresHeadmanScope(ExportJobRepository.Job job) {
        return switch (job.identity().report().kind()) {
            case HEADMAN_WEEKLY_CURRENT,
                    HEADMAN_WEEKLY_SELECTED,
                    HEADMAN_STATS,
                    HEADMAN_STATS_TREND,
                    HEADMAN_SUBJECT_JOURNAL ->
                    true;
            default -> false;
        };
    }

    private HeadmanStatsScopeResponse requireHeadmanScope(
            ExportJobRepository.Job job, Discovery discovery, HeadmanScope admitted) {
        if (!requiresHeadmanScope(job)) {
            if (admitted != null) throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            return null;
        }
        if (admitted == null || !job.equals(admitted.job()) || admitted.response() == null)
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        var scope = admitted.response();
        if (!Objects.equals(job.identity().actor().groupId(), scope.getGroupId())
                || !discovery.groupIds().equals(List.of(scope.getGroupId()))
                || scope.getGroupName().isBlank()
                || scope.hasSelectedSemester() != (discovery.semesterId() != null)
                || scope.hasSelectedSemester()
                        && scope.getSelectedSemester().getId() != discovery.semesterId()
                || discovery.semesterId() == null && scope.hasActiveSemesterId())
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        // Scope membership is current non-archived STUDENT eligibility, not a dated roster.
        // Bind its summary to the same RR snapshot as all current and historical plan rosters.
        var membership =
                jdbc.queryForObject(
                        """
SELECT count(*) FILTER (WHERE e.status='ACTIVE') AS active, count(*) AS total
FROM academic_role_eligibility e JOIN academic_profiles p ON p.id=e.user_id
WHERE e.role='STUDENT' AND e.group_id=? AND e.status<>'ARCHIVED'
""",
                        (rs, row) -> new long[] {rs.getLong("active"), rs.getLong("total")},
                        scope.getGroupId());
        if (membership == null
                || membership[0] != scope.getMembershipActive()
                || membership[1] != scope.getMembershipTotal())
            throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        return scope;
    }

    /** A current admitted response bound to the exact owner job, actor and frozen selector. */
    public record HeadmanScope(ExportJobRepository.Job job, HeadmanStatsScopeResponse response) {}

    private static StudentInfo student(User user) {
        return StudentInfo.newBuilder()
                .setUserId(user.getId())
                .setDisplayName(user.getDisplayName())
                .setIsHeadman(user.isHeadman())
                .build();
    }

    private static SemesterResponse semester(Semester value) {
        return SemesterResponse.newBuilder()
                .setId(value.getId())
                .setName(value.getName())
                .setDateFrom(value.getDateFrom().toString())
                .setDateTo(value.getDateTo().toString())
                .setFirstWeekType(value.getFirstWeekType())
                .build();
    }

    private static SemesterStateResponse state(Semester value) {
        return SemesterStateResponse.newBuilder()
                .setId(value.getId())
                .setActive(value.isActive())
                .setArchived(value.isArchived())
                .setStateVersion(value.getStateVersion())
                .setTransition(
                        ru.rutcampustrack.academic.grpc.SemesterTransition.valueOf(
                                value.getArchiveTransition().name()))
                .setWriteBlocked(
                        value.isArchived()
                                || value.isReleasePending()
                                || value.getArchiveTransition()
                                        != ru.rutcampustrack.academic.contract.enums
                                                .SemesterTransition.NONE)
                .setReleasePending(value.isReleasePending())
                .setDeletionPhase(
                        value.getDeletionPhase() == null ? "" : value.getDeletionPhase().name())
                .setTransitionOperationId(
                        value.getTransitionOperationId() == null
                                ? ""
                                : value.getTransitionOperationId().toString())
                .build();
    }

    public record Discovery(Long semesterId, LocalDate from, LocalDate to, List<Long> groupIds) {}
}
