package ru.rutcampustrack.academic.studentprojection;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolves the signed student's own semester projection and rank cohort.
 *
 * <p>All reads happen in one read-only transaction.  Authorization is
 * re-read before semester, history, subject, or roster data is disclosed.  A
 * response is assembled only after every required source has succeeded.</p>
 */
@Service
public class StudentProjectionScopeService {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final String STUDENT_ROLE = "STUDENT";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final Set<String> TERMINAL_STATUSES = Set.of("EXPELLED", "GRADUATED", "ARCHIVED");

    private final StudentProjectionQuery query;
    private final Clock clock;

    public StudentProjectionScopeService(StudentProjectionQuery query, Clock clock) {
        this.query = Objects.requireNonNull(query, "query");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudentProjectionScope resolve(long semesterId, InternalJwtClaims claims) {
        if (semesterId <= 0) {
            throw StudentProjectionException.invalidRequest("semester_id must be positive");
        }

        try {
            return resolveInternal(semesterId, claims);
        } catch (StudentProjectionException error) {
            throw error;
        } catch (DataAccessException error) {
            throw StudentProjectionException.dependency("Academic projection data is unavailable", error);
        } catch (DateTimeException error) {
            throw StudentProjectionException.inconsistent("Academic projection contains an invalid date");
        }
    }

    private StudentProjectionScope resolveInternal(long semesterId, InternalJwtClaims claims) {
        Authorization authorization = authorize(claims);

        Optional<StudentProjectionQuery.SemesterSnapshot> semesterResult = query.findSemester(semesterId);
        if (semesterResult == null) {
            throw StudentProjectionException.dependency("Academic semester data is unavailable", null);
        }
        StudentProjectionQuery.SemesterSnapshot semester = semesterResult
                .orElseThrow(() -> StudentProjectionException.unresolved("Requested semester is unavailable"));
        validateSemester(semesterId, semester);
        LocalDate dateUntilExclusive = exclusiveEnd(semester.dateTo());
        LocalDate serverDate = LocalDate.now(clock.withZone(MOSCOW));

        List<StudentProjectionQuery.GroupHistorySnapshot> historyRows =
                query.findGroupHistory(authorization.userId());
        HistoryTimeline history = HistoryTimeline.validate(historyRows, authorization.userId());
        List<StudentProjectionScope.MembershipSegment> clippedSegments =
                history.clip(semester.dateFrom(), dateUntilExclusive);
        if (clippedSegments.isEmpty()) {
            throw StudentProjectionException.unresolved("Student has no valid membership in the semester");
        }

        SubjectResolution subjects = resolveSubjects(
                semesterId, semester.dateFrom(), dateUntilExclusive, clippedSegments);
        RankDecision rank = resolveRank(
                authorization,
                history,
                semesterId,
                semester.dateFrom(),
                dateUntilExclusive,
                semester.dateTo(),
                serverDate);
        List<StudentProjectionScope.Subject> rankSubjects = resolveRankSubjects(
                semesterId, semester.dateFrom(), dateUntilExclusive, rank);

        return new StudentProjectionScope(
                authorization.userId(),
                semester.id(),
                semester.dateFrom(),
                semester.dateTo(),
                authorization.terminalReadOnly(),
                rank.rosterUserIds(),
                subjects.subjects(),
                subjects.segments(),
                rankSubjects,
                rank.visibility(),
                rank.groupId(),
                rank.eligible(),
                serverDate);
    }

    private List<StudentProjectionScope.Subject> resolveRankSubjects(
            long semesterId,
            LocalDate semesterFrom,
            LocalDate semesterUntilExclusive,
            RankDecision rank) {
        if (!rank.eligible()) {
            return List.of();
        }
        if (rank.groupId() == null || rank.groupId() <= 0) {
            throw StudentProjectionException.inconsistent("Eligible rank scope has no authoritative group");
        }

        // Ranking uses the whole semester cohort, not only the student's own
        // membership intervals. A subject whose assignment ended before a
        // late joiner arrived still contributes the group's semester lessons.
        StudentProjectionScope.MembershipSegment semesterGroup =
                new StudentProjectionScope.MembershipSegment(
                        rank.groupId(), semesterFrom, semesterUntilExclusive, List.of());
        return resolveSubjects(semesterId, semesterFrom, semesterUntilExclusive, List.of(semesterGroup))
                .subjects();
    }

    private Authorization authorize(InternalJwtClaims claims) {
        if (claims == null || claims.userId() <= 0 || claims.sessionId() == null
                || claims.sessionVersion() <= 0 || claims.rolesVersion() <= 0) {
            throw StudentProjectionException.invalidSession("Signed student identity is required");
        }
        if (!STUDENT_ROLE.equals(normalize(claims.domainRole()))) {
            throw StudentProjectionException.wrongRole("Student projection requires a signed STUDENT identity");
        }

        Optional<StudentProjectionQuery.AuthoritySnapshot> authorityResult =
                query.findAuthority(claims.userId());
        if (authorityResult == null) {
            throw StudentProjectionException.dependency("Academic authority data is unavailable", null);
        }
        StudentProjectionQuery.AuthoritySnapshot authority = authorityResult
                .orElseThrow(() -> StudentProjectionException.invalidSession("Signed student authority is unavailable"));
        if (authority.userId() != claims.userId()) {
            throw StudentProjectionException.inconsistent("Authority user identity is inconsistent");
        }
        if (authority.rolesVersion() <= 0 || authority.rolesVersion() != claims.rolesVersion()) {
            throw StudentProjectionException.invalidSession("Signed student authority is stale");
        }

        List<StudentProjectionQuery.RoleGrantSnapshot> grants = authority.grants();
        List<StudentProjectionQuery.RoleGrantSnapshot> studentGrants = grants.stream()
                .filter(Objects::nonNull)
                .filter(grant -> STUDENT_ROLE.equals(normalize(grant.role())))
                .toList();
        if (studentGrants.size() != 1) {
            throw StudentProjectionException.invalidSession("Signed student role grant is unavailable");
        }
        StudentProjectionQuery.RoleGrantSnapshot studentGrant = studentGrants.get(0);
        String accountStatus = normalize(studentGrant.status());
        String authorityStatus = normalize(authority.accountStatus());
        if (!isKnownStatus(accountStatus) || !authorityStatus.equals(accountStatus)) {
            throw StudentProjectionException.inconsistent("Student role grant disagrees with account authority");
        }
        if (studentGrant.id() <= 0) {
            throw StudentProjectionException.inconsistent("Student role grant has no durable identity");
        }
        if (!accountStatus.equals(normalize(claims.status()))) {
            throw StudentProjectionException.invalidSession("Signed student status is stale");
        }

        Optional<StudentProjectionQuery.SessionSnapshot> sessionResult =
                query.findSession(claims.userId(), claims.sessionId());
        if (sessionResult == null) {
            throw StudentProjectionException.dependency("Academic session authority is unavailable", null);
        }
        StudentProjectionQuery.SessionSnapshot session = sessionResult
                .orElseThrow(() -> StudentProjectionException.invalidSession(
                        "Signed student session is unavailable"));
        boolean terminal = TERMINAL_STATUSES.contains(accountStatus);
        if (!session.sessionId().equals(claims.sessionId())
                || session.userId() != claims.userId()
                || !session.isLiveAt(clock.instant())
                || session.sessionVersion() != claims.sessionVersion()
                || !Objects.equals(session.activeRoleGrantId(), studentGrant.id())) {
            throw StudentProjectionException.invalidSession("Signed student session is stale");
        }
        if (!Objects.equals(claims.groupId(), studentGrant.groupId())
                || claims.isHeadman()) {
            throw StudentProjectionException.invalidSession("Signed student identity is stale");
        }

        if ("SUSPENDED".equals(accountStatus)) {
            throw StudentProjectionException.outOfScope("Suspended students have no projection scope");
        }
        if (claims.readOnly() != terminal) {
            throw StudentProjectionException.invalidSession("Signed student identity is stale");
        }
        if (ACTIVE_STATUS.equals(accountStatus)
                && (studentGrant.groupId() == null || studentGrant.groupId() <= 0)) {
            throw StudentProjectionException.inconsistent("Active student grant has no authoritative group");
        }
        return new Authorization(claims.userId(), studentGrant, terminal && claims.readOnly());
    }

    private SubjectResolution resolveSubjects(
            long semesterId,
            LocalDate dateFrom,
            LocalDate dateUntilExclusive,
            List<StudentProjectionScope.MembershipSegment> segments) {
        if (segments.isEmpty()) {
            return new SubjectResolution(List.of(), List.of());
        }

        Set<Long> groupIds = segments.stream()
                .map(StudentProjectionScope.MembershipSegment::groupId)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        List<StudentProjectionQuery.AssignmentSnapshot> assignments = query.findAssignments(
                semesterId, groupIds, dateFrom, dateUntilExclusive);
        if (assignments == null) {
            throw StudentProjectionException.dependency("Academic assignments are unavailable", null);
        }

        List<Set<Long>> subjectIdsBySegment = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            subjectIdsBySegment.add(new TreeSet<>());
        }
        Map<Long, Set<String>> lessonTypesBySubject = new LinkedHashMap<>();
        Map<Long, Long> groupBySubject = new LinkedHashMap<>();
        Set<Long> subjectIds = new TreeSet<>();

        for (StudentProjectionQuery.AssignmentSnapshot assignment : assignments) {
            validateAssignment(assignment, semesterId);
            boolean matched = false;
            for (int i = 0; i < segments.size(); i++) {
                StudentProjectionScope.MembershipSegment segment = segments.get(i);
                if (segment.groupId() != assignment.groupId()
                        || !overlaps(segment.dateFrom(), segment.dateUntilExclusive(),
                        assignment.validFrom(), assignment.validUntilExclusive())) {
                    continue;
                }
                matched = true;
                subjectIdsBySegment.get(i).add(assignment.subjectId());
            }
            if (!matched) {
                continue;
            }
            subjectIds.add(assignment.subjectId());
            lessonTypesBySubject
                    .computeIfAbsent(assignment.subjectId(), ignored -> new TreeSet<>())
                    .add(normalize(assignment.lessonType()).toLowerCase(Locale.ROOT));
            Long previousGroup = groupBySubject.putIfAbsent(assignment.subjectId(), assignment.groupId());
            if (previousGroup != null && previousGroup.longValue() != assignment.groupId()) {
                throw StudentProjectionException.inconsistent("A subject is assigned to multiple groups");
            }
        }

        Map<Long, StudentProjectionQuery.SubjectSnapshot> sourceSubjects =
                query.findSubjectsByIds(subjectIds);
        if (sourceSubjects == null) {
            throw StudentProjectionException.dependency("Academic subjects are unavailable", null);
        }
        List<StudentProjectionScope.Subject> resultSubjects = new ArrayList<>(subjectIds.size());
        for (Long subjectId : subjectIds) {
            StudentProjectionQuery.SubjectSnapshot source = sourceSubjects.get(subjectId);
            if (source == null || source.id() != subjectId) {
                throw StudentProjectionException.inconsistent("Effective assignment refers to a missing subject");
            }
            if (source.groupId() <= 0 || source.groupId() != groupBySubject.get(subjectId)
                    || source.name() == null || source.name().isBlank()
                    || normalize(source.type()).isBlank()) {
                throw StudentProjectionException.inconsistent("Subject source is inconsistent");
            }
            resultSubjects.add(new StudentProjectionScope.Subject(
                    source.id(),
                    source.name(),
                    normalize(source.type()).toLowerCase(Locale.ROOT),
                    source.groupId(),
                    List.copyOf(lessonTypesBySubject.get(subjectId))));
        }

        List<StudentProjectionScope.MembershipSegment> enrichedSegments = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            enrichedSegments.add(new StudentProjectionScope.MembershipSegment(
                    segments.get(i).groupId(),
                    segments.get(i).dateFrom(),
                    segments.get(i).dateUntilExclusive(),
                    List.copyOf(subjectIdsBySegment.get(i))));
        }
        return new SubjectResolution(List.copyOf(enrichedSegments), List.copyOf(resultSubjects));
    }

    private RankDecision resolveRank(
            Authorization authorization,
            HistoryTimeline history,
            long semesterId,
            LocalDate semesterFrom,
            LocalDate semesterUntilExclusive,
            LocalDate semesterTo,
            LocalDate serverDate) {
        boolean past = serverDate.isAfter(semesterTo);
        if (past && history.everTransferred()) {
            // This branch intentionally returns before any roster query.
            return RankDecision.hidden();
        }
        if (authorization.terminalReadOnly()) {
            return RankDecision.visible(null, false, List.of());
        }

        Long grantGroupId = authorization.studentGrant().groupId();
        if (grantGroupId == null || grantGroupId <= 0) {
            throw StudentProjectionException.inconsistent("Active student grant has no authoritative group");
        }

        Optional<Long> currentGroup = history.currentGroupAt(serverDate);
        if (currentGroup.isEmpty() || !grantGroupId.equals(currentGroup.get())) {
            throw StudentProjectionException.inconsistent(
                    "Current student grant does not match authoritative history");
        }

        long rankGroupId;
        if (past) {
            rankGroupId = history.soleHistoricalGroup()
                    .orElseThrow(() -> StudentProjectionException.unresolved(
                        "Untransferred past scope has no historical group"));
            if (rankGroupId != grantGroupId) {
                throw StudentProjectionException.inconsistent("Current student grant disagrees with history");
            }
        } else {
            rankGroupId = grantGroupId;
        }

        if (!history.hasMembership(rankGroupId, semesterFrom, semesterUntilExclusive)) {
            return RankDecision.visible(null, false, List.of());
        }

        List<Long> roster = query.findActiveStudentIds(
                rankGroupId, semesterId, semesterFrom, semesterUntilExclusive);
        if (roster == null || roster.stream().anyMatch(Objects::isNull)) {
            throw StudentProjectionException.dependency("Academic rank cohort is unavailable", null);
        }
        if (roster.stream().anyMatch(id -> id <= 0)) {
            throw StudentProjectionException.inconsistent("Rank cohort contains an invalid student id");
        }
        List<Long> orderedRoster = roster.stream().distinct().sorted().toList();
        return RankDecision.visible(rankGroupId, true, orderedRoster);
    }

    private static void validateSemester(long requestedId, StudentProjectionQuery.SemesterSnapshot semester) {
        if (semester.id() != requestedId || semester.id() <= 0
                || semester.dateFrom() == null || semester.dateTo() == null
                || semester.dateTo().isBefore(semester.dateFrom())) {
            throw StudentProjectionException.inconsistent("Semester source is inconsistent");
        }
    }

    private static LocalDate exclusiveEnd(LocalDate dateTo) {
        try {
            return dateTo.plusDays(1);
        } catch (DateTimeException error) {
            throw StudentProjectionException.inconsistent("Semester date range cannot be made half-open");
        }
    }

    private static void validateAssignment(
            StudentProjectionQuery.AssignmentSnapshot assignment,
            long semesterId) {
        if (assignment == null || assignment.id() <= 0 || assignment.subjectId() <= 0
                || assignment.groupId() <= 0 || assignment.semesterId() != semesterId
                || assignment.validFrom() == null || normalize(assignment.lessonType()).isBlank()) {
            throw StudentProjectionException.inconsistent("Assignment source is inconsistent");
        }
        if (assignment.validUntilExclusive() != null
                && !assignment.validUntilExclusive().isAfter(assignment.validFrom())) {
            throw StudentProjectionException.inconsistent("Assignment validity is inverted");
        }
    }

    private static boolean overlaps(
            LocalDate firstFrom,
            LocalDate firstUntilExclusive,
            LocalDate secondFrom,
            LocalDate secondUntilExclusive) {
        // A null assignment end is an open-ended effective interval.  The
        // selected semester and every history segment are already finite, so
        // the open interval overlaps whenever the assignment starts before
        // that finite end.
        return (secondUntilExclusive == null || firstFrom.isBefore(secondUntilExclusive))
                && secondFrom.isBefore(firstUntilExclusive);
    }

    private static boolean isKnownStatus(String status) {
        return ACTIVE_STATUS.equals(status)
                || "SUSPENDED".equals(status)
                || TERMINAL_STATUSES.contains(status);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record Authorization(
            long userId,
            StudentProjectionQuery.RoleGrantSnapshot studentGrant,
            boolean terminalReadOnly) {
    }

    private record SubjectResolution(
            List<StudentProjectionScope.MembershipSegment> segments,
            List<StudentProjectionScope.Subject> subjects) {
    }

    private record RankDecision(
            StudentProjectionScope.RankVisibility visibility,
            Long groupId,
            boolean eligible,
            List<Long> rosterUserIds) {
        private RankDecision {
            rosterUserIds = List.copyOf(rosterUserIds);
        }

        private static RankDecision hidden() {
            return new RankDecision(StudentProjectionScope.RankVisibility.HIDDEN, null, false, List.of());
        }

        private static RankDecision visible(Long groupId, boolean eligible, List<Long> rosterUserIds) {
            return new RankDecision(StudentProjectionScope.RankVisibility.VISIBLE,
                    groupId, eligible, rosterUserIds);
        }
    }

    private static final class HistoryTimeline {
        private final List<StudentProjectionQuery.GroupHistorySnapshot> rows;
        private final Set<Long> historicalGroups;
        private final boolean everTransferred;

        private HistoryTimeline(
                List<StudentProjectionQuery.GroupHistorySnapshot> rows,
                Set<Long> historicalGroups,
                boolean everTransferred) {
            this.rows = rows;
            this.historicalGroups = historicalGroups;
            this.everTransferred = everTransferred;
        }

        private static HistoryTimeline validate(
                List<StudentProjectionQuery.GroupHistorySnapshot> sourceRows,
                long expectedUserId) {
            if (sourceRows == null || sourceRows.isEmpty()) {
                throw StudentProjectionException.unresolved("Student group history is unavailable");
            }
            if (sourceRows.stream().anyMatch(Objects::isNull)) {
                throw StudentProjectionException.inconsistent("Student group history is inconsistent");
            }
            List<StudentProjectionQuery.GroupHistorySnapshot> rows = sourceRows.stream()
                    .sorted(Comparator
                            .comparing(StudentProjectionQuery.GroupHistorySnapshot::joinedAt,
                                    Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparingLong(StudentProjectionQuery.GroupHistorySnapshot::id))
                    .toList();
            LinkedHashSet<Long> groups = new LinkedHashSet<>();
            boolean transferred = false;
            StudentProjectionQuery.GroupHistorySnapshot previous = null;
            for (StudentProjectionQuery.GroupHistorySnapshot row : rows) {
                if (row.id() <= 0 || row.userId() != expectedUserId
                        || row.groupId() <= 0 || row.joinedAt() == null
                        || (row.leftAt() != null && row.leftAt().isBefore(row.joinedAt()))) {
                    throw StudentProjectionException.inconsistent("Student group history is inconsistent");
                }
                if (previous != null) {
                    if (previous.leftAt() == null
                            || row.joinedAt().isBefore(previous.leftAt())) {
                        throw StudentProjectionException.inconsistent("Student group history overlaps");
                    }
                    if (previous.groupId() != row.groupId()) {
                        transferred = true;
                    }
                }
                groups.add(row.groupId());
                previous = row;
            }
            return new HistoryTimeline(List.copyOf(rows), Set.copyOf(groups), transferred);
        }

        private boolean everTransferred() {
            return everTransferred;
        }

        private Optional<Long> soleHistoricalGroup() {
            return historicalGroups.size() == 1
                    ? historicalGroups.stream().findFirst()
                    : Optional.empty();
        }

        private Optional<Long> currentGroupAt(LocalDate date) {
            Long current = null;
            for (StudentProjectionQuery.GroupHistorySnapshot row : rows) {
                boolean positive = row.leftAt() == null || row.leftAt().isAfter(row.joinedAt());
                if (!positive || row.joinedAt().isAfter(date)
                        || (row.leftAt() != null && !date.isBefore(row.leftAt()))) {
                    continue;
                }
                if (current != null) {
                    throw StudentProjectionException.inconsistent("Student group history is ambiguous");
                }
                current = row.groupId();
            }
            return Optional.ofNullable(current);
        }

        private boolean hasMembership(long groupId, LocalDate from, LocalDate untilExclusive) {
            return rows.stream().anyMatch(row -> {
                if (row.groupId() != groupId
                        || (row.leftAt() != null && !row.leftAt().isAfter(row.joinedAt()))) {
                    return false;
                }
                LocalDate rowUntil = row.leftAt() == null ? untilExclusive : row.leftAt();
                return overlaps(row.joinedAt(), rowUntil, from, untilExclusive);
            });
        }

        private List<StudentProjectionScope.MembershipSegment> clip(
                LocalDate from,
                LocalDate untilExclusive) {
            List<StudentProjectionScope.MembershipSegment> clipped = new ArrayList<>();
            for (StudentProjectionQuery.GroupHistorySnapshot row : rows) {
                LocalDate rowUntil = row.leftAt() == null ? untilExclusive : row.leftAt();
                LocalDate segmentFrom = row.joinedAt().isAfter(from) ? row.joinedAt() : from;
                LocalDate segmentUntil = rowUntil.isBefore(untilExclusive) ? rowUntil : untilExclusive;
                if (!segmentUntil.isAfter(segmentFrom)) {
                    continue;
                }
                clipped.add(new StudentProjectionScope.MembershipSegment(
                        row.groupId(), segmentFrom, segmentUntil, List.of()));
            }
            return List.copyOf(clipped);
        }
    }
}
