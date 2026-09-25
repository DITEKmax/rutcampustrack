package ru.rutcampustrack.academic.history;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.GroupHistoryCoverage;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the managed historical roster from one repeatable-read PostgreSQL
 * snapshot.  Current role, status and group columns are never eligibility
 * predicates for a dated request.
 */
@Service
public class HistoricalMembershipService {

    public static final String WRITER_VERSION = "managed_v1";

    private final GroupRepository groupRepository;
    private final GroupHistoryCoverageRepository coverageRepository;
    private final SemesterRepository semesterRepository;
    private final StudentGroupHistoryRepository historyRepository;
    private final UserRepository userRepository;

    public HistoricalMembershipService(GroupRepository groupRepository,
                                       GroupHistoryCoverageRepository coverageRepository,
                                       SemesterRepository semesterRepository,
                                       StudentGroupHistoryRepository historyRepository,
                                       UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.coverageRepository = coverageRepository;
        this.semesterRepository = semesterRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RosterSnapshot readRoster(long groupId, LocalDate asOfDate, long semesterId) {
        if (groupId <= 0 || semesterId <= 0 || asOfDate == null) {
            throw HistoricalMembershipException.invalid("group_id, as_of_date and semester_id are required");
        }

        groupRepository.findById(groupId)
                .orElseThrow(() -> HistoricalMembershipException.notFound("Group " + groupId + " not found"));
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> HistoricalMembershipException.notFound("Semester " + semesterId + " not found"));
        if (semester.getDateFrom() == null || semester.getDateTo() == null
                || semester.getDateFrom().isAfter(semester.getDateTo())) {
            throw HistoricalMembershipException.precondition("Semester has invalid dates");
        }
        if (asOfDate.isBefore(semester.getDateFrom()) || asOfDate.isAfter(semester.getDateTo())) {
            throw HistoricalMembershipException.invalid("as_of_date must belong to semester");
        }

        ActiveRoster roster = readManagedRoster(groupId, asOfDate);
        return new RosterSnapshot(groupId, asOfDate, semesterId, roster.students());
    }

    /**
     * Reads only historical member IDs for dated internal consumers whose event
     * date is not guaranteed to belong to a single semester (for example, a
     * group broadcast published between semesters).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Long> readMemberUserIds(long groupId, LocalDate asOfDate) {
        if (groupId <= 0 || asOfDate == null) {
            throw HistoricalMembershipException.invalid("group_id and as_of_date are required");
        }
        groupRepository.findById(groupId)
                .orElseThrow(() -> HistoricalMembershipException.notFound("Group " + groupId + " not found"));
        return readManagedRoster(groupId, asOfDate).userIds();
    }

    private ActiveRoster readManagedRoster(long groupId, LocalDate asOfDate) {
        GroupHistoryCoverage coverage = coverageRepository.findById(groupId)
                .orElseThrow(() -> HistoricalMembershipException.precondition(
                        "Group history coverage is not established"));
        if (coverage.getCoverageFrom() == null || asOfDate.isBefore(coverage.getCoverageFrom())) {
            throw HistoricalMembershipException.precondition(
                    "Requested date is before managed group history coverage");
        }
        if (!WRITER_VERSION.equals(coverage.getWriterVersion())) {
            throw HistoricalMembershipException.precondition("Unknown group history writer version");
        }

        List<StudentGroupHistory> histories = historyRepository
                .findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(groupId);
        if (histories == null) {
            throw HistoricalMembershipException.precondition(
                    "Membership history lookup returned no coherent snapshot");
        }
        validateHistory(histories, groupId, coverage.getCoverageFrom());

        List<Long> allHistoryUserIds = histories.stream()
                .map(StudentGroupHistory::getUserId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, List<StudentGroupHistory>> historyByUser = new LinkedHashMap<>();
        for (Long userId : allHistoryUserIds) {
            List<StudentGroupHistory> allUserHistory = historyRepository
                    .findByUserIdOrderByJoinedAtAscIdAsc(userId);
            if (allUserHistory == null) {
                throw HistoricalMembershipException.precondition(
                        "Membership history lookup returned no coherent snapshot");
            }
            validateUserTimeline(allUserHistory, userId);
            historyByUser.put(userId, allUserHistory);
        }

        // A covered group cannot have a current STUDENT assignment with no
        // managed origin row.  This is a completeness guard only; historical
        // eligibility below still ignores current role/status/group values.
        Set<Long> historyUserIds = histories.stream()
                .map(StudentGroupHistory::getUserId)
                .collect(Collectors.toSet());
        List<User> currentStudents = userRepository
                .findAllStudentAssignmentsIncludingArchived(groupId);
        if (currentStudents == null) {
            throw HistoricalMembershipException.precondition(
                    "Current membership lookup returned no coherent snapshot");
        }
        for (User currentStudent : currentStudents) {
            if (!historyUserIds.contains(currentStudent.getId())) {
                throw HistoricalMembershipException.precondition(
                        "Covered group has a current student without membership history");
            }
            List<StudentGroupHistory> allUserHistory = historyByUser.get(currentStudent.getId());
            if (allUserHistory == null) {
                allUserHistory = historyRepository
                        .findByUserIdOrderByJoinedAtAscIdAsc(currentStudent.getId());
                if (allUserHistory == null) {
                    throw HistoricalMembershipException.precondition(
                            "Membership history lookup returned no coherent snapshot");
                }
                validateUserTimeline(allUserHistory, currentStudent.getId());
            }
            List<StudentGroupHistory> openUserHistory = allUserHistory.stream()
                    .filter(row -> row.getLeftAt() == null)
                    .toList();
            if (openUserHistory.size() != 1
                    || !groupIdEquals(openUserHistory.get(0), groupId)) {
                throw HistoricalMembershipException.precondition(
                        "Current student assignment has no unique open membership in the assigned group");
            }
        }

        List<StudentGroupHistory> activeRows = histories.stream()
                .filter(row -> !row.getJoinedAt().isAfter(asOfDate))
                .filter(row -> row.getLeftAt() == null || asOfDate.isBefore(row.getLeftAt()))
                .toList();
        Map<Long, StudentGroupHistory> activeByUser = new LinkedHashMap<>();
        for (StudentGroupHistory row : activeRows) {
            if (activeByUser.put(row.getUserId(), row) != null) {
                throw HistoricalMembershipException.precondition("Overlapping membership history");
            }
        }

        List<Long> activeUserIds = activeByUser.keySet().stream().sorted().toList();
        List<User> users = allHistoryUserIds.isEmpty()
                ? List.of()
                : userRepository.findAllIncludingArchivedByIds(allHistoryUserIds);
        if (users == null) {
            throw HistoricalMembershipException.precondition(
                    "User lookup returned no coherent snapshot");
        }
        Set<Long> foundIds = users.stream().map(User::getId).collect(Collectors.toSet());
        if (foundIds.size() != allHistoryUserIds.size()
                || !foundIds.containsAll(allHistoryUserIds)) {
            throw HistoricalMembershipException.precondition("Membership history contains an orphan user");
        }
        Map<Long, User> usersById = users.stream().collect(Collectors.toMap(
                User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
        List<User> orderedUsers = activeUserIds.stream().map(usersById::get).toList();
        return new ActiveRoster(activeUserIds, orderedUsers);
    }

    private static void validateHistory(List<StudentGroupHistory> histories,
                                        long groupId,
                                        LocalDate coverageFrom) {
        Map<Long, LocalDate> previousEndByUser = new LinkedHashMap<>();
        Map<Long, Boolean> openByUser = new LinkedHashMap<>();
        for (StudentGroupHistory row : histories) {
            if (row.getUserId() == null || row.getUserId() <= 0
                    || row.getGroupId() == null || row.getGroupId() != groupId
                    || row.getJoinedAt() == null
                    || (row.getLeftAt() != null && row.getLeftAt().isBefore(row.getJoinedAt()))) {
                throw HistoricalMembershipException.precondition("Invalid membership interval");
            }
            if (row.getJoinedAt().isBefore(coverageFrom)) {
                throw HistoricalMembershipException.precondition(
                        "Membership history starts before managed coverage");
            }
            if (Boolean.TRUE.equals(openByUser.put(row.getUserId(), row.getLeftAt() == null))) {
                throw HistoricalMembershipException.precondition("Multiple open membership intervals");
            }
            LocalDate previousEnd = previousEndByUser.get(row.getUserId());
            if (previousEnd != null && previousEnd.isAfter(row.getJoinedAt())) {
                throw HistoricalMembershipException.precondition("Overlapping membership intervals");
            }
            previousEndByUser.put(row.getUserId(), row.getLeftAt());
        }
    }

    private static boolean groupIdEquals(StudentGroupHistory row, long groupId) {
        return row.getGroupId() != null && row.getGroupId() == groupId;
    }

    private static void validateUserTimeline(List<StudentGroupHistory> histories, long userId) {
        LocalDate previousEnd = null;
        boolean openSeen = false;
        for (StudentGroupHistory row : histories) {
            if (row.getUserId() == null || row.getUserId() != userId
                    || row.getGroupId() == null || row.getGroupId() <= 0
                    || row.getJoinedAt() == null
                    || (row.getLeftAt() != null && row.getLeftAt().isBefore(row.getJoinedAt()))) {
                throw HistoricalMembershipException.precondition("Invalid user membership timeline");
            }
            if (openSeen) {
                throw HistoricalMembershipException.precondition(
                        "Membership history continues after an open interval");
            }
            if (previousEnd != null && previousEnd.isAfter(row.getJoinedAt())) {
                throw HistoricalMembershipException.precondition(
                        "Membership intervals overlap across groups");
            }
            if (row.getLeftAt() == null) {
                openSeen = true;
            } else {
                previousEnd = row.getLeftAt();
            }
        }
    }

    public record RosterSnapshot(long groupId, LocalDate asOfDate, long semesterId, List<User> students) {
        public RosterSnapshot {
            if (groupId <= 0 || semesterId <= 0) {
                throw new IllegalArgumentException("roster identity must be positive");
            }
            Objects.requireNonNull(asOfDate, "asOfDate");
            students = List.copyOf(Objects.requireNonNull(students, "students"));
        }
    }

    private record ActiveRoster(List<Long> userIds, List<User> students) {
        private ActiveRoster {
            userIds = List.copyOf(Objects.requireNonNull(userIds, "userIds"));
            students = List.copyOf(Objects.requireNonNull(students, "students"));
        }
    }

    public record EnrollmentContext(Group group, Semester semester, GroupHistoryCoverage coverage) {
    }
}
