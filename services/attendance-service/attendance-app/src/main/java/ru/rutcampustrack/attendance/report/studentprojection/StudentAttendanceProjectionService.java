package ru.rutcampustrack.attendance.report.studentprojection;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.AcademicSubjectInfo;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.academic.grpc.StudentProjectionMembershipSegment;
import ru.rutcampustrack.academic.grpc.StudentProjectionRankVisibility;
import ru.rutcampustrack.academic.grpc.StudentProjectionScopeResponse;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.student.StudentCheckinException;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Server-side student attendance read model.
 *
 * <p>The Academic resolver is the authorization source.  Schedule supplies
 * lifecycle and membership-segment rows, while Attendance supplies only
 * persisted marks.  The client receives calculated aggregates and opaque
 * lesson ids; it never receives a roster or peer records.</p>
 */
@Service
public class StudentAttendanceProjectionService {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final Set<String> SUPPORTED_TYPES = Set.of("LECTURE", "PRACTICE", "LAB");

    private final AttendanceReadPort attendanceReadPort;
    private final AcademicGrpcClient academicGrpcClient;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final Clock clock;

    public StudentAttendanceProjectionService(
            AttendanceReadPort attendanceReadPort,
            AcademicGrpcClient academicGrpcClient,
            ScheduleGrpcClient scheduleGrpcClient,
            Clock clock) {
        this.attendanceReadPort = Objects.requireNonNull(attendanceReadPort, "attendanceReadPort");
        this.academicGrpcClient = Objects.requireNonNull(academicGrpcClient, "academicGrpcClient");
        this.scheduleGrpcClient = Objects.requireNonNull(scheduleGrpcClient, "scheduleGrpcClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Projection project(
            InternalJwtClaims claims,
            long semesterId,
            Long subjectId,
            String range,
            List<String> requestedTypes) {
        requireStudent(claims);
        if (semesterId <= 0) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "semester_id должен быть положительным");
        }
        String normalizedRange = normalizeRange(range);
        Set<String> normalizedTypes = normalizeTypes(requestedTypes);

        StudentProjectionScopeResponse scope = academicGrpcClient
                .resolveStudentProjectionScope(semesterId);
        validateScope(scope, claims, semesterId, subjectId);
        Map<Long, AcademicSubjectInfo> subjects = subjectMap(scope);
        if (subjectId != null && !subjects.containsKey(subjectId)) {
            throw new StudentCheckinException(StudentCheckinException.Code.OUT_OF_SCOPE,
                    "Предмет не входит в student scope");
        }

        LocalDate serverDate = parseRequiredDate(scope.getServerDate(), "server_date");
        // Materialize the complete authorized membership view first.  Subject
        // and lesson-type filters affect only the returned slice; they must
        // never change semester totals or the canonical rank denominator.
        List<Lesson> allLessons = loadLessons(scope, semesterId, subjects, null, Set.of());
        Map<Long, AttendanceRecord> ownMarks = markIndex(
                attendanceReadPort.findByUserId(claims.userId(), semesterId), allLessons, claims.userId());
        List<Lesson> materializedAll = allLessons.stream()
                .map(lesson -> lesson.withMark(ownMarks.get(lesson.lessonId())))
                .toList();

        List<Lesson> selectedLessons = materializedAll.stream()
                .filter(lesson -> subjectId == null || lesson.subjectId() == subjectId)
                .filter(lesson -> normalizedTypes.isEmpty() || normalizedTypes.contains(lesson.lessonType()))
                .toList();
        MetricsBundle overall = metrics(selectedLessons);
        List<Day> days = days(selectedLessons, serverDate);
        Graph graph = new Graph(
                series(selectedLessons, BucketUnit.DAY, serverDate),
                series(selectedLessons, BucketUnit.WEEK, serverDate));
        List<Subject> subjectViews = subjectViews(
                materializedAll, selectedLessons, subjects, subjectId,
                normalizedTypes, normalizedRange, serverDate);
        Rank rank = rank(scope, claims.userId(), materializedAll, subjects, semesterId);

        return new Projection(
                claims.userId(),
                semesterId,
                parseRequiredDate(scope.getDateFrom(), "date_from"),
                parseRequiredDate(scope.getDateTo(), "date_to"),
                clock.instant(),
                scope.getTerminalReadOnly(),
                overall.metrics(),
                days,
                subjectViews,
                graph,
                rank);
    }

    /** Returns the bounded ranking page for the cohort resolved from the signed student scope. */
    public RankingPage ranking(
            InternalJwtClaims claims,
            long semesterId,
            Integer requestedPage,
            int pageSize) {
        requireStudent(claims);
        if (semesterId <= 0) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "semester_id должен быть положительным");
        }
        if (requestedPage != null && requestedPage < 0) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "page должен быть неотрицательным");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "size должен быть от 1 до 100");
        }

        StudentProjectionScopeResponse scope = academicGrpcClient
                .resolveStudentProjectionScope(semesterId);
        validateScope(scope, claims, semesterId, null);
        if (!rankingVisible(scope)) {
            return RankingPage.unavailable(requestedPage == null ? 0 : requestedPage, pageSize);
        }

        Map<Long, AcademicSubjectInfo> subjects = subjectMap(scope);
        List<Lesson> ownLessons = loadLessons(scope, semesterId, subjects, null, Set.of());
        RankedCohort cohort = rankedCohort(
                scope, claims.userId(), ownLessons, subjects, semesterId);
        if (!cohort.ownRank().available()) {
            return RankingPage.unavailable(requestedPage == null ? 0 : requestedPage, pageSize);
        }

        List<Long> roster = scope.getActiveRosterUserIdsList();
        Map<Long, String> names = rosterNames(scope.getRankGroupId(), roster);
        List<RankingRow> rows = cohort.participants().stream()
                .map(participant -> {
                    String name = names.get(participant.participantId());
                    if (name == null || participant.position() == null || participant.percentage() == null) {
                        throw StudentProjectionException.invalidRoster(
                                "available ranking contains an incomplete participant");
                    }
                    return new RankingRow(participant.participantId(), name, participant.position(),
                            participant.percentage(), participant.participantId() == claims.userId());
                })
                .toList();
        int ownIndex = -1;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).studentId() == claims.userId()) {
                ownIndex = index;
                break;
            }
        }
        if (ownIndex < 0) {
            throw StudentProjectionException.invalidRoster("the current student is missing from the ranking");
        }
        int page = requestedPage == null ? ownIndex / pageSize : requestedPage;
        long offset = (long) page * pageSize;
        List<RankingRow> pageRows = offset >= rows.size()
                ? List.of()
                : rows.subList((int) offset, (int) Math.min(rows.size(), offset + pageSize));
        return new RankingPage(true, page, pageSize, rows.size(), cohort.ownRank().position(), pageRows);
    }

    private Map<Long, String> rosterNames(long groupId, List<Long> authorizedRoster) {
        GroupMembersResponse response = academicGrpcClient.getGroupMembers(groupId);
        if (response == null) {
            throw StudentProjectionException.invalidRoster("Academic returned no group roster");
        }
        Set<Long> authorizedIds = new HashSet<>(authorizedRoster);
        Map<Long, String> names = new LinkedHashMap<>();
        for (StudentInfo student : response.getStudentsList()) {
            if (student == null || !authorizedIds.contains(student.getUserId())) {
                continue;
            }
            String name = student.getDisplayName() == null ? "" : student.getDisplayName().trim();
            if (student.getUserId() <= 0 || name.isBlank()
                    || names.putIfAbsent(student.getUserId(), name) != null) {
                throw StudentProjectionException.invalidRoster("Academic returned an invalid cohort identity");
            }
        }
        if (!names.keySet().containsAll(authorizedIds)) {
            throw StudentProjectionException.invalidRoster("Academic omitted a current cohort identity");
        }
        return Map.copyOf(names);
    }

    private List<Lesson> loadLessons(
            StudentProjectionScopeResponse scope,
            long semesterId,
            Map<Long, AcademicSubjectInfo> subjects,
            Long requestedSubjectId,
            Set<String> requestedTypes) {
        return loadLessons(scope.getOwnMembershipSegmentsList(), semesterId, subjects,
                requestedSubjectId, requestedTypes);
    }

    private List<Lesson> loadLessons(
            List<StudentProjectionMembershipSegment> segments,
            long semesterId,
            Map<Long, AcademicSubjectInfo> subjects,
            Long requestedSubjectId,
            Set<String> requestedTypes) {
        Map<Long, Lesson> byOccurrence = new LinkedHashMap<>();
        for (StudentProjectionMembershipSegment segment : segments) {
            LocalDate from = parseRequiredDate(segment.getDateFrom(), "membership.date_from");
            LocalDate untilExclusive = parseRequiredDate(
                    segment.getDateUntilExclusive(), "membership.date_until_exclusive");
            if (!untilExclusive.isAfter(from) || segment.getGroupId() <= 0) {
                throw StudentProjectionException.invalidOccurrence("invalid membership segment");
            }
            LessonsResponse response = scheduleGrpcClient.getLessonsByGroup(
                    segment.getGroupId(), semesterId, from.toString(), untilExclusive.minusDays(1).toString());
            if (response == null) {
                throw StudentProjectionException.invalidOccurrence("Schedule returned no response");
            }
            Set<Long> segmentSubjects = new HashSet<>(segment.getSubjectIdsList());
            for (LessonResponse source : response.getLessonsList()) {
                Lesson lesson = materialize(source, semesterId, segment, segmentSubjects, subjects,
                        requestedSubjectId, requestedTypes);
                if (lesson == null) {
                    continue;
                }
                Lesson previous = byOccurrence.putIfAbsent(lesson.occurrenceId(), lesson);
                if (previous != null && !previous.sameSource(lesson)) {
                    throw StudentProjectionException.duplicateOccurrence(lesson.occurrenceId());
                }
            }
        }
        return byOccurrence.values().stream()
                .sorted(Comparator.comparing(Lesson::date)
                        .thenComparingInt(Lesson::lessonNumber)
                        .thenComparingLong(Lesson::lessonId))
                .toList();
    }

    private Lesson materialize(
            LessonResponse source,
            long semesterId,
            StudentProjectionMembershipSegment segment,
            Set<Long> segmentSubjects,
            Map<Long, AcademicSubjectInfo> subjects,
            Long requestedSubjectId,
            Set<String> requestedTypes) {
        if (source == null || source.getId() <= 0 || source.getOccurrenceId() <= 0
                || source.getGroupId() != segment.getGroupId()
                || source.getSubjectId() <= 0
                || source.getSemesterId() > 0 && source.getSemesterId() != semesterId
                || !segmentSubjects.contains(source.getSubjectId())) {
            return null;
        }
        LocalDate date = parseRequiredDate(source.getDate(), "lesson.date");
        LocalDate from = parseRequiredDate(segment.getDateFrom(), "membership.date_from");
        LocalDate untilExclusive = parseRequiredDate(
                segment.getDateUntilExclusive(), "membership.date_until_exclusive");
        if (date.isBefore(from) || !date.isBefore(untilExclusive)) {
            return null;
        }
        if (requestedSubjectId != null && source.getSubjectId() != requestedSubjectId) {
            return null;
        }
        AcademicSubjectInfo subject = subjects.get(source.getSubjectId());
        if (subject == null || subject.getSubjectId() != source.getSubjectId()) {
            throw StudentProjectionException.invalidOccurrence("lesson refers to an unknown subject");
        }
        String type = normalizeType(source.getLessonType());
        if (type == null) {
            List<String> allowed = subject.getLessonTypesList().stream()
                    .map(StudentAttendanceProjectionService::normalizeType)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (allowed.size() != 1) {
                throw StudentProjectionException.invalidOccurrence(
                        "lesson type is missing for subject " + source.getSubjectId());
            }
            type = allowed.get(0);
        }
        if (requestedTypes != null && !requestedTypes.isEmpty() && !requestedTypes.contains(type)) {
            return null;
        }
        ScheduleState state = ScheduleState.from(source.getStatus());
        LocalTime startsAt = parseRequiredTime(source.getStartTime(), "lesson.start_time");
        LocalTime endsAt = parseRequiredTime(source.getEndTime(), "lesson.end_time");
        if (!endsAt.isAfter(startsAt)) {
            throw StudentProjectionException.invalidOccurrence("lesson time range is inverted");
        }
        String room = source.getRoom() == null || source.getRoom().isBlank() ? null : source.getRoom();
        return new Lesson(
                source.getId(),
                source.getOccurrenceId(),
                source.getGroupId(),
                date,
                source.getLessonNumber(),
                source.getSubjectId(),
                subject.getSubjectName(),
                type,
                startsAt,
                endsAt,
                room,
                state,
                null);
    }

    private Map<Long, AttendanceRecord> markIndex(
            List<AttendanceRecord> records,
            List<Lesson> lessons,
            long userId) {
        if (records == null) {
            throw StudentProjectionException.invalidOccurrence("Attendance returned no record list");
        }
        Map<Long, Lesson> lessonsById = lessons.stream()
                .collect(Collectors.toMap(Lesson::lessonId, value -> value, (left, right) -> left));
        Map<Long, AttendanceRecord> result = new HashMap<>();
        for (AttendanceRecord record : records) {
            if (record == null || record.lessonId() == null || record.lessonId() <= 0) {
                throw StudentProjectionException.invalidOccurrence("attendance record has no lesson id");
            }
            canonicalStatus(record.status(), record.lessonId());
            Lesson lesson = lessonsById.get(record.lessonId());
            if (lesson == null) {
                continue;
            }
            if (record.userId() == null || record.userId() != userId) {
                // The query is already actor-scoped; this check still guards
                // a broken adapter from attaching another user's mark.
                throw StudentProjectionException.invalidOccurrence("attendance record actor mismatch");
            }
            if (record.groupId() != null && record.groupId() != lesson.groupId()
                    || record.subjectId() != null && record.subjectId() != lesson.subjectId()
                    || record.lessonDate() != null && !record.lessonDate().equals(lesson.date())) {
                throw StudentProjectionException.invalidOccurrence("attendance record disagrees with schedule");
            }
            AttendanceRecord previous = result.putIfAbsent(record.lessonId(), record);
            if (previous != null && !Objects.equals(previous.status(), record.status())) {
                throw StudentProjectionException.duplicateOccurrence(record.lessonId());
            }
        }
        return result;
    }

    private List<Subject> subjectViews(
            List<Lesson> allLessons,
            List<Lesson> selectedLessons,
            Map<Long, AcademicSubjectInfo> subjectInfo,
            Long requestedSubjectId,
            Set<String> requestedTypes,
            String range,
            LocalDate serverDate) {
        Set<Long> ids = new TreeSet<>();
        if (requestedSubjectId != null) {
            ids.add(requestedSubjectId);
        } else {
            ids.addAll(subjectInfo.keySet());
        }
        List<Subject> result = new ArrayList<>();
        for (Long id : ids) {
            AcademicSubjectInfo source = subjectInfo.get(id);
            if (source == null) continue;
            List<Lesson> subjectLessons = allLessons.stream()
                    .filter(lesson -> lesson.subjectId() == id)
                    .toList();
            List<Lesson> selectedSubjectLessons = selectedLessons.stream()
                    .filter(lesson -> lesson.subjectId() == id)
                    .toList();
            List<String> available = subjectLessons.stream()
                    .map(Lesson::lessonType)
                    .distinct()
                    .sorted()
                    .toList();
            List<String> selected = requestedTypes == null || requestedTypes.isEmpty()
                    ? available : requestedTypes.stream().filter(available::contains).sorted().toList();
            List<Lesson> selectedTypeLessons = selectedSubjectLessons.stream()
                    .filter(lesson -> selected.contains(lesson.lessonType()))
                    .toList();
            List<TypeCard> cards = selected.stream()
                    .map(type -> typeCard(subjectLessons.stream()
                            .filter(lesson -> lesson.lessonType().equals(type)).toList(), type))
                    .toList();
            result.add(new Subject(
                    id,
                    source.getSubjectName(),
                    metrics(subjectLessons).metrics(),
                    available,
                    selected,
                    metrics(selectedTypeLessons).metrics(),
                    cards,
                    range.equals("days")
                            ? series(selectedTypeLessons, BucketUnit.DAY, serverDate)
                            : series(selectedTypeLessons, BucketUnit.WEEK, serverDate)));
        }
        return List.copyOf(result);
    }

    private TypeCard typeCard(List<Lesson> lessons, String type) {
        List<HistorySegment> history = lessons.stream()
                .filter(lesson -> lesson.scheduleState() != ScheduleState.CANCELLED)
                .sorted(Comparator.comparing(Lesson::date).thenComparingInt(Lesson::lessonNumber))
                .map(lesson -> new HistorySegment(
                        Long.toString(lesson.occurrenceId()), lesson.historyStatus()))
                .toList();
        return new TypeCard(type, metrics(lessons).metrics(), history);
    }

    private List<Day> days(List<Lesson> lessons, LocalDate serverDate) {
        Map<LocalDate, List<Lesson>> byDate = new TreeMap<>();
        lessons.forEach(lesson -> byDate.computeIfAbsent(lesson.date(), ignored -> new ArrayList<>()).add(lesson));
        return byDate.entrySet().stream()
                .map(entry -> new Day(
                        entry.getKey(),
                        entry.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, RU),
                        Integer.toString(entry.getKey().getDayOfMonth()),
                        entry.getKey().isBefore(serverDate) ? "PAST"
                                : entry.getKey().isAfter(serverDate) ? "FUTURE" : "CURRENT",
                        entry.getValue().stream().sorted(lessonComparator()).toList()))
                .toList();
    }

    private List<SeriesPoint> series(List<Lesson> lessons, BucketUnit unit, LocalDate serverDate) {
        Map<LocalDate, List<Lesson>> buckets = new TreeMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.scheduleState() == ScheduleState.CANCELLED) continue;
            LocalDate bucket = unit == BucketUnit.DAY
                    ? lesson.date()
                    : lesson.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(lesson);
        }
        return buckets.entrySet().stream().map(entry -> {
            LocalDate from = entry.getKey();
            LocalDate to = unit == BucketUnit.DAY ? from : from.plusDays(6);
            MetricsBundle metrics = metrics(entry.getValue());
            boolean onlyFuture = entry.getValue().stream().allMatch(lesson ->
                    lesson.scheduleState() == ScheduleState.PLANNED
                            || lesson.scheduleState() == ScheduleState.ACTIVE);
            boolean onlyClosedWithoutMark = entry.getValue().stream().allMatch(lesson ->
                    lesson.scheduleState() == ScheduleState.CLOSED && lesson.mark() == null);
            String state = from.isAfter(serverDate) || onlyFuture ? "FUTURE"
                    : onlyClosedWithoutMark ? "NO_DATA" : "DATA";
            String id = (unit == BucketUnit.DAY ? "day:" : "week:") + from;
            String label = unit == BucketUnit.DAY ? from.toString() : from + "–" + to;
            return new SeriesPoint(id, label, from, to, state, metrics.metrics());
        }).toList();
    }

    private Rank rank(
            StudentProjectionScopeResponse scope,
            long ownId,
            List<Lesson> ownLessons,
            Map<Long, AcademicSubjectInfo> subjects,
            long semesterId) {
        return rankedCohort(scope, ownId, ownLessons, subjects, semesterId).ownRank();
    }

    private RankedCohort rankedCohort(
            StudentProjectionScopeResponse scope,
            long ownId,
            List<Lesson> ownLessons,
            Map<Long, AcademicSubjectInfo> subjects,
            long semesterId) {
        List<Long> roster = scope.getActiveRosterUserIdsList();
        if (!rankingVisible(scope)) {
            return new RankedCohort(new Rank(null, roster.size(), false), List.of());
        }
        long rankGroup = scope.getRankGroupId();
        Map<Long, AcademicSubjectInfo> rankSubjects = rankSubjectMap(scope, semesterId, rankGroup);
        List<Lesson> rankLessons = canonicalRankLessons(scope, rankSubjects, semesterId, rankGroup);
        Set<Long> ownAuthorizedLessonIds = ownLessons.stream()
                .filter(lesson -> lesson.groupId() == rankGroup)
                .map(Lesson::lessonId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, List<AttendanceRecord>> records = attendanceReadPort
                .findByUserIds(roster, semesterId);
        if (records == null) {
            throw StudentProjectionException.invalidRoster("Attendance roster read returned null");
        }
        List<OwnRankCalculator.Participant> participants = roster.stream()
                .distinct()
                .map(userId -> {
                    List<AttendanceRecord> participantRecords = records.getOrDefault(userId, List.of());
                    if (userId == ownId) {
                        // The rank denominator is the canonical semester cohort
                        // for every roster member.  A late joiner contributes
                        // marks only from their authorized membership history.
                        participantRecords = participantRecords.stream()
                                .filter(record -> ownAuthorizedLessonIds.contains(record.lessonId()))
                                .toList();
                    }
                    Map<Long, AttendanceRecord> marks = markIndex(
                            participantRecords, rankLessons, userId);
                    List<Lesson> markedLessons = rankLessons.stream()
                            .map(lesson -> lesson.withMark(marks.get(lesson.lessonId())))
                            .toList();
                    AttendanceMetricCalculator.Metrics metrics = metrics(markedLessons).metrics();
                    return OwnRankCalculator.Participant.withCounts(
                            userId, metrics.heldCount(), metrics.presentCount());
                })
                .toList();
        List<OwnRankCalculator.RankedParticipant> ranked = OwnRankCalculator.rankAll(participants);
        OwnRankCalculator.Rank result = OwnRankCalculator.summarize(ownId, ranked);
        return new RankedCohort(
                new Rank(result.position(), result.participantCount(), result.available()), ranked);
    }

    private static boolean rankingVisible(StudentProjectionScopeResponse scope) {
        return scope.getRankVisibility() == StudentProjectionRankVisibility
                .STUDENT_PROJECTION_RANK_VISIBILITY_VISIBLE
                && scope.getRankEligible() && scope.hasRankGroupId();
    }

    private List<Lesson> canonicalRankLessons(
            StudentProjectionScopeResponse scope,
            Map<Long, AcademicSubjectInfo> rankSubjectsById,
            long semesterId,
            long rankGroup) {
        List<Long> rankSubjectIds = rankSubjectsById.keySet().stream().sorted().toList();
        if (rankSubjectIds.isEmpty()) return List.of();
        LocalDate from = parseRequiredDate(scope.getDateFrom(), "date_from");
        LocalDate to = parseRequiredDate(scope.getDateTo(), "date_to");
        StudentProjectionMembershipSegment canonicalSegment = StudentProjectionMembershipSegment.newBuilder()
                .setGroupId(rankGroup)
                .setDateFrom(from.toString())
                .setDateUntilExclusive(to.plusDays(1).toString())
                .addAllSubjectIds(rankSubjectIds)
                .build();
        return loadLessons(List.of(canonicalSegment), semesterId, rankSubjectsById, null, Set.of()).stream()
                .filter(lesson -> lesson.groupId() == rankGroup)
                .toList();
    }

    private static Map<Long, AcademicSubjectInfo> rankSubjectMap(
            StudentProjectionScopeResponse scope,
            long semesterId,
            long rankGroup) {
        if (scope.getSemesterId() != semesterId
                || !scope.hasRankGroupId()
                || scope.getRankGroupId() != rankGroup) {
            throw StudentProjectionException.invalidOccurrence("Academic rank scope authority is inconsistent");
        }
        Map<Long, AcademicSubjectInfo> result = new LinkedHashMap<>();
        for (AcademicSubjectInfo subject : scope.getRankSubjectsList()) {
            if (subject == null || subject.getSubjectId() <= 0 || subject.getGroupId() != rankGroup
                    || subject.getSubjectName().isBlank() || subject.getSubjectType().isBlank()) {
                throw StudentProjectionException.invalidOccurrence("Academic rank subject is invalid");
            }
            if (result.putIfAbsent(subject.getSubjectId(), subject) != null) {
                throw StudentProjectionException.invalidOccurrence("Academic rank subjects contain duplicates");
            }
        }
        return Map.copyOf(result);
    }

    private MetricsBundle metrics(Collection<Lesson> lessons) {
        return metrics(lessons, null);
    }

    private MetricsBundle metrics(Collection<Lesson> lessons, List<AttendanceRecord> records) {
        Map<Long, AttendanceStatus> statuses = new HashMap<>();
        if (records != null) {
            for (AttendanceRecord record : records) {
                if (record != null && record.lessonId() != null) {
                    statuses.put(record.lessonId(), canonicalStatus(record.status(), record.lessonId()));
                }
            }
        }
        List<AttendanceMetricCalculator.Occurrence> occurrences = lessons.stream()
                .map(lesson -> {
                    AttendanceStatus status = statuses.containsKey(lesson.lessonId())
                            ? statuses.get(lesson.lessonId())
                            : lesson.mark() == null
                            ? null
                            : canonicalStatus(lesson.mark().status(), lesson.lessonId());
                    return occurrence(lesson, status);
                })
                .toList();
        return new MetricsBundle(AttendanceMetricCalculator.calculate(occurrences));
    }

    private AttendanceMetricCalculator.Occurrence occurrence(Lesson lesson, AttendanceStatus status) {
        return switch (lesson.scheduleState()) {
            case PLANNED -> AttendanceMetricCalculator.Occurrence.planned(lesson.occurrenceId());
            case ACTIVE -> AttendanceMetricCalculator.Occurrence.active(lesson.occurrenceId());
            case CLOSED -> status == null
                    ? AttendanceMetricCalculator.Occurrence.closedWithoutMark(lesson.occurrenceId())
                    : AttendanceMetricCalculator.Occurrence.closed(lesson.occurrenceId(), status);
            case CANCELLED -> AttendanceMetricCalculator.Occurrence.cancelled(lesson.occurrenceId());
        };
    }

    private static AttendanceStatus canonicalStatus(AttendanceStatus status, long lessonId) {
        if (status == null) {
            throw StudentProjectionException.invalidOccurrence(
                    "attendance record " + lessonId + " has no persisted status");
        }
        if (status == AttendanceStatus.FREE_ATTENDANCE) {
            throw StudentProjectionException.unsupportedAttendanceStatus(lessonId, status);
        }
        if (status == AttendanceStatus.CANCELLED) {
            throw StudentProjectionException.invalidOccurrence(
                    "attendance record " + lessonId + " has CANCELLED status");
        }
        return status;
    }

    private static Map<Long, AcademicSubjectInfo> subjectMap(StudentProjectionScopeResponse scope) {
        Map<Long, AcademicSubjectInfo> result = new LinkedHashMap<>();
        for (AcademicSubjectInfo subject : scope.getSubjectsList()) {
            if (subject == null || subject.getSubjectId() <= 0
                    || subject.getSubjectName().isBlank()) {
                throw StudentProjectionException.invalidOccurrence("Academic subject is invalid");
            }
            if (result.putIfAbsent(subject.getSubjectId(), subject) != null) {
                throw StudentProjectionException.invalidOccurrence("Academic subjects contain duplicates");
            }
        }
        return result;
    }

    private static void validateScope(
            StudentProjectionScopeResponse scope,
            InternalJwtClaims claims,
            long requestedSemesterId,
            Long requestedSubjectId) {
        if (scope == null || scope.getStudentId() != claims.userId()
                || scope.getSemesterId() != requestedSemesterId
                || scope.getDateFrom().isBlank() || scope.getDateTo().isBlank()
                || scope.getServerDate().isBlank()) {
            throw StudentProjectionException.invalidOccurrence("Academic scope echo is inconsistent");
        }
        try {
            claims.domainRole();
        } catch (RuntimeException error) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_SESSION,
                    "Подписанная student-сессия недействительна");
        }
        if (requestedSubjectId != null && requestedSubjectId <= 0) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "subject_id должен быть положительным");
        }
    }

    private static void requireStudent(InternalJwtClaims claims) {
        if (claims == null || claims.userId() <= 0) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_SESSION,
                    "Подписанная student-сессия отсутствует");
        }
        try {
            if (!"STUDENT".equalsIgnoreCase(claims.domainRole())) {
                throw new StudentCheckinException(StudentCheckinException.Code.WRONG_ROLE,
                        "Attendance projection requires a student identity");
            }
        } catch (IllegalStateException error) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_SESSION,
                    "Подписанная student-сессия недействительна");
        }
    }

    private static String normalizeRange(String range) {
        String normalized = range == null || range.isBlank() ? "weeks" : range.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("days") && !normalized.equals("weeks")) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "range должен быть days или weeks");
        }
        return normalized;
    }

    private static Set<String> normalizeTypes(List<String> types) {
        if (types == null || types.isEmpty()) return Set.of();
        Set<String> normalized = new TreeSet<>();
        for (String type : types) {
            String value = normalizeType(type);
            if (value == null) {
                throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                        "lesson_types содержит неизвестный тип");
            }
            normalized.add(value);
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return null;
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_TYPES.contains(normalized) ? normalized : null;
    }

    private static LocalDate parseRequiredDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw StudentProjectionException.invalidOccurrence(field + " is not an ISO date");
        }
    }

    private static LocalTime parseRequiredTime(String value, String field) {
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException error) {
            throw StudentProjectionException.invalidOccurrence(field + " is not an ISO time");
        }
    }

    private static Comparator<Lesson> lessonComparator() {
        return Comparator.comparing(Lesson::date)
                .thenComparingInt(Lesson::lessonNumber)
                .thenComparingLong(Lesson::lessonId);
    }

    private enum BucketUnit { DAY, WEEK }

    public enum ScheduleState {
        PLANNED, ACTIVE, CLOSED, CANCELLED;

        static ScheduleState from(String value) {
            if (value == null) {
                throw StudentProjectionException.invalidOccurrence("lesson status is missing");
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw StudentProjectionException.invalidOccurrence("unknown lesson status " + value);
            }
        }
    }

    private record MetricsBundle(AttendanceMetricCalculator.Metrics metrics) {
    }

    public record Projection(
            long studentId,
            long semesterId,
            LocalDate dateFrom,
            LocalDate dateTo,
            java.time.Instant serverNow,
            boolean terminalReadOnly,
            AttendanceMetricCalculator.Metrics metrics,
            List<Day> days,
            List<Subject> subjects,
            Graph graph,
            Rank ownRank) {
    }

    public record Day(
            LocalDate date,
            String weekday,
            String dayNumber,
            String state,
            List<Lesson> lessons) {
    }

    public record Lesson(
            long lessonId,
            long occurrenceId,
            long groupId,
            LocalDate date,
            int lessonNumber,
            long subjectId,
            String subjectName,
            String lessonType,
            LocalTime startsAt,
            LocalTime endsAt,
            String room,
            ScheduleState scheduleState,
            AttendanceRecord mark) {

        Lesson withMark(AttendanceRecord value) {
            return new Lesson(lessonId, occurrenceId, groupId, date, lessonNumber, subjectId,
                    subjectName, lessonType, startsAt, endsAt, room, scheduleState, value);
        }

        boolean sameSource(Lesson other) {
            return lessonId == other.lessonId && groupId == other.groupId
                    && subjectId == other.subjectId && date.equals(other.date)
                    && lessonType.equals(other.lessonType);
        }

        public String uiStatus() {
            if (scheduleState == ScheduleState.CANCELLED) return "CANCELLED";
            if (mark != null && canonicalStatus(mark.status(), lessonId) != null) {
                return canonicalStatus(mark.status(), lessonId).name();
            }
            return switch (scheduleState) {
                case ACTIVE -> "ACTIVE";
                case PLANNED -> "FUTURE";
                case CLOSED -> "NO_DATA";
                case CANCELLED -> "CANCELLED";
            };
        }

        String historyStatus() {
            return switch (uiStatus()) {
                case "ACTIVE", "FUTURE" -> "FUTURE";
                case "PRESENT", "ABSENT", "EXCUSED" -> uiStatus();
                default -> "NO_DATA";
            };
        }
    }

    public record Subject(
            long subjectId,
            String name,
            AttendanceMetricCalculator.Metrics metrics,
            List<String> availableTypes,
            List<String> selectedTypes,
            AttendanceMetricCalculator.Metrics selectedAggregate,
            List<TypeCard> typeCards,
            List<SeriesPoint> series) {
    }

    public record TypeCard(
            String lessonType,
            AttendanceMetricCalculator.Metrics metrics,
            List<HistorySegment> history) {
    }

    public record HistorySegment(String id, String status) {
    }

    public record Graph(List<SeriesPoint> days, List<SeriesPoint> weeks) {
    }

    public record SeriesPoint(
            String id,
            String label,
            LocalDate dateFrom,
            LocalDate dateTo,
            String state,
            AttendanceMetricCalculator.Metrics metrics) {
    }

    public record Rank(Integer position, int participantCount, boolean available) {
    }

    public record RankingPage(
            boolean available,
            int page,
            int size,
            int total,
            Integer ownPosition,
            List<RankingRow> rows) {
        public RankingPage {
            rows = List.copyOf(rows);
        }

        private static RankingPage unavailable(int page, int size) {
            return new RankingPage(false, page, size, 0, null, List.of());
        }
    }

    public record RankingRow(
            long studentId,
            String name,
            Integer position,
            BigDecimal percentage,
            boolean isSelf) {
    }

    private record RankedCohort(Rank ownRank, List<OwnRankCalculator.RankedParticipant> participants) {
        private RankedCohort {
            participants = List.copyOf(participants);
        }
    }
}
