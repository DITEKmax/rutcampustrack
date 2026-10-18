package ru.rutcampustrack.attendance.report.planned;

import com.google.protobuf.Message;

import io.grpc.Status;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.journal.JournalLessonPolicy;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.schedule.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Immutable, admitted report material. Every read is closed over these snapshots, never an RPC. */
public final class AdmittedReportPlan
        implements ReportAcademicReadPort,
                ReportScheduleReadPort,
                TeacherAssignmentReadPort,
                ReportActor {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ITEMS = 5_000;
    private final ReportRenderPlan source;
    private final InternalJwtClaims actor;
    private final Clock clock;
    private final Map<Long, GroupResponse> groups;
    private final Map<RosterKey, GroupMembersResponse> rosters;
    private final Map<Long, SubjectInfo> subjects;
    private final Map<Long, LessonResponse> lessons;
    private final Map<Long, SemesterStateResponse> semesterStates;
    private final Set<Long> allowedGroups;

    private record RosterKey(long groupId, String date, long semesterId) {}

    private record GroupSubject(long groupId, long subjectId) {}

    public static String digest(ReportRenderPlan plan) {
        if (plan.getSerializedSize() > MAX_BYTES) throw tooLarge();
        rejectUnknown(plan);
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(plan.toByteArray()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static AdmittedReportPlan admit(ReportRenderPlan plan, InternalJwtClaims verifiedActor) {
        return new AdmittedReportPlan(plan, verifiedActor);
    }

    private AdmittedReportPlan(ReportRenderPlan plan, InternalJwtClaims verifiedActor) {
        source = Objects.requireNonNull(plan);
        actor = Objects.requireNonNull(verifiedActor);
        digest(plan);
        requireUuid(plan.getJobId());
        requireUuid(plan.getLeaseId());
        var identity = plan.getActor();
        if (!plan.hasActor()
                || actor.userId() <= 0
                || actor.sessionId() == null
                || actor.sessionVersion() <= 0
                || actor.rolesVersion() <= 0
                || identity.getUserId() != actor.userId()
                || !identity.getSessionId().equals(actor.sessionId().toString())
                || identity.getSessionVersion() != actor.sessionVersion()
                || identity.getRolesVersion() != actor.rolesVersion()
                || !identity.getRole().equals(actor.role())
                || !identity.getStatus().equals(actor.status())
                || identity.hasGroupId() != (actor.groupId() != null)
                || identity.hasGroupId() && !Objects.equals(identity.getGroupId(), actor.groupId())
                || identity.getIsHeadman() != actor.isHeadman()
                || identity.getReadOnly() != actor.readOnly()
                || !"ACTIVE".equals(actor.status())) throw denied();
        validateKind();
        Instant generated = Instant.parse(plan.getGeneratedAt());
        clock = Clock.fixed(generated, JournalLessonPolicy.MOSCOW);
        checkCount(plan.getGroupsCount());
        checkCount(plan.getRostersCount());
        checkCount(plan.getSubjectsCount());
        checkCount(plan.getLessonsCount());
        checkCount(plan.getSemesterStatesCount());
        checkCount(plan.getTeacherSubjects().getSubjectsCount());
        checkCount(plan.getTeacherAssignments().getAssignmentsCount());
        boolean teacher = "TEACHER".equals(actor.domainRole());
        Set<GroupSubject> teacherPairs = new HashSet<>();
        Set<Long> scopeGroups = new HashSet<>();
        if (teacher) {
            if (!plan.hasTeacherSubjects() || !plan.hasTeacherAssignments()) throw malformed();
            for (var subject : plan.getTeacherSubjects().getSubjectsList()) {
                if (subject.getGroupId() <= 0 || subject.getSubjectId() <= 0) throw malformed();
                teacherPairs.add(new GroupSubject(subject.getGroupId(), subject.getSubjectId()));
                scopeGroups.add(subject.getGroupId());
            }
        } else {
            if (actor.groupId() == null || actor.groupId() <= 0) throw denied();
            scopeGroups.add(actor.groupId());
        }
        allowedGroups = Set.copyOf(scopeGroups);
        groups = index(plan.getGroupsList(), GroupResponse::getId);
        for (var group : groups.values()) {
            if (!allowedGroups.contains(group.getId()) || group.getName().isBlank()) throw denied();
        }
        subjects = index(plan.getSubjectsList(), SubjectInfo::getSubjectId);
        for (var subject : subjects.values()) {
            if (!allowedGroups.contains(subject.getGroupId())
                    || subject.getSubjectName().isBlank()
                    || teacher
                            && !teacherPairs.contains(
                                    new GroupSubject(subject.getGroupId(), subject.getSubjectId())))
                throw denied();
        }
        long semesterId = plan.hasSemester() ? plan.getSemester().getId() : 0;
        LocalDate from = null, to = null;
        if (semesterId > 0) {
            from = LocalDate.parse(plan.getSemester().getDateFrom());
            to = LocalDate.parse(plan.getSemester().getDateTo());
            if (to.isBefore(from) || plan.getSemester().getName().isBlank()) throw malformed();
        } else if (!Set.of("HEADMAN_GROUP_COMPOSITION", "HEADMAN_STATS", "HEADMAN_STATS_TREND")
                .contains(plan.getReportKind())) throw malformed();
        Map<RosterKey, GroupMembersResponse> dated = new HashMap<>();
        Set<Long> students = new HashSet<>();
        for (var value : plan.getRostersList()) {
            requireGroup(value.getGroupId());
            var roster = value.getRoster();
            if (!value.hasRoster() || roster.hasAsOfDate() != roster.hasSemesterId())
                throw malformed();
            String date = roster.hasAsOfDate() ? roster.getAsOfDate() : "";
            long rosterSemester = roster.hasSemesterId() ? roster.getSemesterId() : 0;
            if (roster.hasSemesterId()
                    && (rosterSemester != semesterId
                            || from == null
                            || LocalDate.parse(date).isBefore(from)
                            || LocalDate.parse(date).isAfter(to))) throw denied();
            if (dated.put(new RosterKey(value.getGroupId(), date, rosterSemester), roster) != null)
                throw malformed();
            Set<Long> localIds = new HashSet<>();
            for (var student : roster.getStudentsList()) {
                if (student.getUserId() <= 0
                        || student.getDisplayName().isBlank()
                        || !localIds.add(student.getUserId())) throw malformed();
                students.add(student.getUserId());
                checkCount(students.size());
            }
        }
        rosters = Map.copyOf(dated);
        lessons = index(plan.getLessonsList(), LessonResponse::getId);
        for (var lesson : lessons.values()) {
            requireGroup(lesson.getGroupId());
            if (lesson.getSemesterId() != semesterId
                    || lesson.getSubjectId() <= 0
                    || !subjects.containsKey(lesson.getSubjectId())
                    || lesson.getLessonType().isBlank()
                    || teacher
                            && !teacherPairs.contains(
                                    new GroupSubject(lesson.getGroupId(), lesson.getSubjectId())))
                throw denied();
            LocalDate date = LocalDate.parse(lesson.getDate());
            if (from == null
                    || date.isBefore(from)
                    || date.isAfter(to)
                    || !List.of("", "COMPLETED").contains(lesson.getTransferState()))
                throw malformed();
            if (!rosters.containsKey(
                    new RosterKey(lesson.getGroupId(), lesson.getDate(), semesterId)))
                throw malformed();
        }
        semesterStates = index(plan.getSemesterStatesList(), SemesterStateResponse::getId);
        if (semesterStates.keySet().stream().anyMatch(id -> id != semesterId)) throw denied();
        if (teacher) {
            var assignments = plan.getTeacherAssignments();
            if (!assignments.getSemesterDateFrom().equals(plan.getSemester().getDateFrom())
                    || !assignments.getSemesterDateTo().equals(plan.getSemester().getDateTo()))
                throw malformed();
            Set<Long> assignmentIds = new HashSet<>();
            for (var assignment : assignments.getAssignmentsList()) {
                if (assignment.getAssignmentId() <= 0
                        || !assignmentIds.add(assignment.getAssignmentId())
                        || assignment.getTeacherId() != actor.userId()
                        || assignment.getSemesterId() != semesterId
                        || !teacherPairs.contains(
                                new GroupSubject(
                                        assignment.getGroupId(), assignment.getSubjectId())))
                    throw denied();
            }
        }
        if (plan.hasHeadmanStatsScope()) {
            var scope = plan.getHeadmanStatsScope();
            if (teacher
                    || scope.getGroupId() != actor.groupId()
                    || scope.getMembershipActive() < 0
                    || scope.getMembershipTotal() < scope.getMembershipActive()
                    || scope.hasSelectedSemester()
                            && scope.getSelectedSemester().getId() != semesterId) throw denied();
        }
        if (Set.of("HEADMAN_STATS", "HEADMAN_STATS_TREND").contains(plan.getReportKind())) {
            if (!plan.hasHeadmanStatsScope()) throw malformed();
            var scope = plan.getHeadmanStatsScope();
            checkCount(scope.getSemestersCount());
            if (scope.hasSelectedSemester() != plan.hasSemester()) throw malformed();
            if (scope.hasSelectedSemester()) {
                var selected = scope.getSelectedSemester();
                if (!selected.getDateFrom().equals(plan.getSemester().getDateFrom())
                        || !selected.getDateTo().equals(plan.getSemester().getDateTo())
                        || !selected.getName().equals(plan.getSemester().getName()))
                    throw malformed();
                if ("HEADMAN_STATS_TREND".equals(plan.getReportKind())) validateTrendWork(from, to);
            }
        }
        if (plan.hasGroupComposition()) {
            var composition = plan.getGroupComposition();
            if (teacher || !actor.isHeadman() || composition.getGroupId() != actor.groupId())
                throw denied();
            checkCount(composition.getMembersCount());
            for (var member : composition.getMembersList()) {
                students.add(member.getUserId());
                checkCount(students.size());
            }
        }
    }

    /** Bound the buckets actually generated, preserving short selections in long semesters. */
    private void validateTrendWork(LocalDate semesterFrom, LocalDate semesterTo) {
        var query = source.getTrend();
        if (!Set.of("SEMESTER", "SUBJECT", "WEEK").contains(query.getMode())
                || query.hasBucketUnit() && !Set.of("DAY", "WEEK").contains(query.getBucketUnit()))
            throw malformed();
        boolean week = "WEEK".equals(query.getMode());
        LocalDate from = semesterFrom, to = semesterTo;
        if (week) {
            LocalDate requested = LocalDate.parse(query.getWeekStart());
            from = requested.isAfter(from) ? requested : from;
            LocalDate end = requested.plusDays(6);
            to = end.isBefore(to) ? end : to;
        }
        if (to.isBefore(from))
            return; // Existing normal validation reports an out-of-semester week.
        boolean daily =
                (week && !"WEEK".equals(query.getBucketUnit()))
                        || "DAY".equals(query.getBucketUnit());
        long buckets =
                daily
                        ? java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
                        : java.time.temporal.ChronoUnit.WEEKS.between(
                                        from.with(java.time.DayOfWeek.MONDAY), to)
                                + 1;
        if (buckets > MAX_ITEMS) throw tooLarge();
    }

    private void validateKind() {
        ReportRenderPlan.SelectorCase expected =
                switch (source.getReportKind()) {
                    case "TEACHER_JOURNAL" -> ReportRenderPlan.SelectorCase.TEACHER_JOURNAL;
                    case "TEACHER_STATS" -> ReportRenderPlan.SelectorCase.TEACHER_STATS;
                    case "HEADMAN_WEEKLY_CURRENT", "HEADMAN_WEEKLY_SELECTED" ->
                            ReportRenderPlan.SelectorCase.WEEKLY;
                    case "HEADMAN_STATS" -> ReportRenderPlan.SelectorCase.HEADMAN_STATS;
                    case "HEADMAN_STATS_TREND" -> ReportRenderPlan.SelectorCase.TREND;
                    case "HEADMAN_GROUP_COMPOSITION" -> ReportRenderPlan.SelectorCase.COMPOSITION;
                    case "HEADMAN_SUBJECT_JOURNAL" -> ReportRenderPlan.SelectorCase.SUBJECT_JOURNAL;
                    default -> throw malformed();
                };
        if (source.getSelectorCase() != expected) throw malformed();
        if (source.getReportKind().startsWith("TEACHER_")) {
            if (!"TEACHER".equals(actor.domainRole())) throw denied();
        } else {
            if (!"STUDENT".equals(actor.domainRole()) || actor.groupId() == null) throw denied();
            boolean composition = "HEADMAN_GROUP_COMPOSITION".equals(source.getReportKind());
            if (composition ? !actor.isHeadman() : !source.getViewStatsAuthorized()) throw denied();
        }
        if (source.getWeekly().getWeekStartsCount() > 64
                || source.getSubjectJournal().getWeekStartsCount() > 64) throw malformed();
        if ("HEADMAN_WEEKLY_CURRENT".equals(source.getReportKind())
                && source.getWeekly().getWeekStartsCount() != 1) throw malformed();
    }

    public ReportRenderPlan source() {
        return source;
    }

    public Clock clock() {
        return clock;
    }

    @Override
    public Long getUserId() {
        return actor.userId();
    }

    @Override
    public UserRole getRole() {
        return UserRole.valueOf(actor.domainRole());
    }

    @Override
    public Long getGroupId() {
        return actor.groupId();
    }

    @Override
    public boolean isHeadman() {
        return actor.isHeadman();
    }

    @Override
    public SemesterResponse getActiveSemester() {
        if (!source.hasSemester()) throw malformed();
        return source.getSemester();
    }

    @Override
    public GroupResponse getGroup(Long id) {
        requireGroup(id);
        return required(groups.get(id));
    }

    @Override
    public GroupMembersResponse getGroupMembers(Long group) {
        requireGroup(group);
        return required(rosters.get(new RosterKey(group, "", 0)));
    }

    @Override
    public GroupMembersResponse getGroupMembers(Long group, LocalDate date, Long semester) {
        requireGroup(group);
        return required(rosters.get(new RosterKey(group, date.toString(), semester)));
    }

    @Override
    public Map<Long, AcademicGrpcClient.SubjectDetails> getSubjectDetailsByIds(List<Long> ids) {
        Map<Long, AcademicGrpcClient.SubjectDetails> result = new LinkedHashMap<>();
        for (long id : ids) {
            var value = required(subjects.get(id));
            result.put(
                    id,
                    new AcademicGrpcClient.SubjectDetails(
                            value.getSubjectName(),
                            value.getSubjectType(),
                            value.getGroupId(),
                            value.getLessonTypesList()));
        }
        return Map.copyOf(result);
    }

    @Override
    public TeacherSubjectsResponse getCurrentTeacherSubjects(Long teacherId) {
        if (!"TEACHER".equals(actor.domainRole())
                || teacherId != actor.userId()
                || !source.hasTeacherSubjects()) throw denied();
        return source.getTeacherSubjects();
    }

    @Override
    public boolean hasAssistantPermission(Long groupId, String permission) {
        return Objects.equals(groupId, actor.groupId())
                && "STUDENT".equals(actor.domainRole())
                && "VIEW_STATS".equals(permission)
                && source.getViewStatsAuthorized();
    }

    @Override
    public HeadmanStatsScopeResponse resolveHeadmanStatsScope(long group, Long semester) {
        return resolveHeadmanStatsScope(group, semester, null);
    }

    @Override
    public HeadmanStatsScopeResponse resolveHeadmanStatsScope(
            long group, Long semester, Long student) {
        if (!Objects.equals(group, actor.groupId())
                || student != null
                || !source.hasHeadmanStatsScope()) throw denied();
        var scope = source.getHeadmanStatsScope();
        if (semester != null
                && (!scope.hasSelectedSemester()
                        || scope.getSelectedSemester().getId() != semester)) throw denied();
        return scope;
    }

    @Override
    public SemesterStateResponse getHeadmanJournalSemesterState(long group, long semester) {
        if (!Objects.equals(group, actor.groupId())) throw denied();
        return required(semesterStates.get(semester));
    }

    @Override
    public HeadmanGroupCompositionResponse getHeadmanGroupComposition(long group) {
        if (!Objects.equals(group, actor.groupId())
                || !actor.isHeadman()
                || !source.hasGroupComposition()) throw denied();
        return source.getGroupComposition();
    }

    @Override
    public TeacherAssignmentsResponse fullSemesterAssignments(long semester) {
        if (!"TEACHER".equals(actor.domainRole())
                || semester != source.getSemester().getId()
                || !source.hasTeacherAssignments()) throw denied();
        return source.getTeacherAssignments();
    }

    @Override
    public LessonResponse getLessonById(Long id) {
        return required(lessons.get(id));
    }

    @Override
    public LessonsResponse getLessonsByGroup(Long group, Long semester, String from, String to) {
        requireGroup(group);
        if (semester != source.getSemester().getId()) throw denied();
        LocalDate lower = LocalDate.parse(from), upper = LocalDate.parse(to);
        if (upper.isBefore(lower)) throw malformed();
        return LessonsResponse.newBuilder()
                .addAllLessons(
                        lessons.values().stream()
                                .filter(
                                        lesson ->
                                                lesson.getGroupId() == group
                                                        && lesson.getSemesterId() == semester)
                                .filter(
                                        lesson ->
                                                !LocalDate.parse(lesson.getDate()).isBefore(lower)
                                                        && !LocalDate.parse(lesson.getDate())
                                                                .isAfter(upper))
                                .sorted(
                                        Comparator.comparing(LessonResponse::getDate)
                                                .thenComparingInt(LessonResponse::getLessonNumber)
                                                .thenComparingLong(LessonResponse::getId))
                                .toList())
                .build();
    }

    @Override
    public List<LessonInfo> getLessonsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        checkCount(ids.size());
        return ids.stream()
                .map(
                        id -> {
                            var lesson = getLessonById(id);
                            var value =
                                    LessonInfo.newBuilder()
                                            .setLessonId(id)
                                            .setGroupId(lesson.getGroupId())
                                            .setSubjectId(lesson.getSubjectId())
                                            .setStartsAt(
                                                    lesson.getDate() + "T" + lesson.getStartTime())
                                            .setLessonNumber(lesson.getLessonNumber())
                                            .setDate(lesson.getDate())
                                            .setOccurrenceId(lesson.getOccurrenceId())
                                            .setSemesterId(lesson.getSemesterId())
                                            .setLessonType(lesson.getLessonType())
                                            .setGeneration(lesson.getGeneration())
                                            .setRevision(lesson.getRevision())
                                            .setStatus(lesson.getStatus());
                            if (lesson.hasAssignmentId())
                                value.setAssignmentId(lesson.getAssignmentId());
                            if (lesson.hasAssignedTeacherId())
                                value.setTeacherId(lesson.getAssignedTeacherId());
                            return value.build();
                        })
                .toList();
    }

    private void requireGroup(Long id) {
        if (id == null || !allowedGroups.contains(id)) throw denied();
    }

    private static <T> T required(T value) {
        if (value == null) throw malformed();
        return value;
    }

    private static <T> Map<Long, T> index(List<T> values, java.util.function.ToLongFunction<T> id) {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T value : values) {
            long key = id.applyAsLong(value);
            if (key <= 0 || result.put(key, value) != null) throw malformed();
        }
        return Collections.unmodifiableMap(result);
    }

    private static void checkCount(int size) {
        if (size > MAX_ITEMS) throw tooLarge();
    }

    private static void requireUuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value) || id.equals(new UUID(0, 0))) throw malformed();
        } catch (IllegalArgumentException invalid) {
            throw malformed();
        }
    }

    private static void rejectUnknown(Message value) {
        if (!value.getUnknownFields().asMap().isEmpty()) throw malformed();
        for (var entry : value.getAllFields().entrySet()) {
            if (entry.getKey().getJavaType()
                    != com.google.protobuf.Descriptors.FieldDescriptor.JavaType.MESSAGE) continue;
            if (entry.getKey().isRepeated())
                for (Object child : (List<?>) entry.getValue()) rejectUnknown((Message) child);
            else rejectUnknown((Message) entry.getValue());
        }
    }

    private static ReportValidationException malformed() {
        return new ReportValidationException("Неполный или некорректный план отчёта");
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("План отчёта выходит за разрешённый scope");
    }

    private static io.grpc.StatusRuntimeException tooLarge() {
        return Status.RESOURCE_EXHAUSTED
                .withDescription("Report plan exceeds its size/count limit")
                .asRuntimeException();
    }
}
