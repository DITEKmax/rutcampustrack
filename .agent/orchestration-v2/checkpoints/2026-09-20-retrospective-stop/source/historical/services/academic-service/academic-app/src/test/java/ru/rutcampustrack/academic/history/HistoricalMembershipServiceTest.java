package ru.rutcampustrack.academic.history;

import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricalMembershipServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 4, 20);

    @Test
    void currentStudentWithoutOriginFailsEvenWhenHistoryHasOtherStudents() {
        GroupRepository groups = mock(GroupRepository.class);
        GroupHistoryCoverageRepository coverage = mock(GroupHistoryCoverageRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        StudentGroupHistoryRepository histories = mock(StudentGroupHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        configureBase(groups, coverage, semesters, histories, 7L);

        StudentGroupHistory origin = history(41L, 7L, DATE.minusDays(1), null);
        User currentWithoutOrigin = mock(User.class);
        when(currentWithoutOrigin.getId()).thenReturn(42L);
        when(users.findAllStudentAssignmentsIncludingArchived(7L))
                .thenReturn(List.of(currentWithoutOrigin));
        when(histories.findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(7L))
                .thenReturn(List.of(origin));

        HistoricalMembershipService service = new HistoricalMembershipService(
                groups, coverage, semesters, histories, users);

        assertThatThrownBy(() -> service.readRoster(7L, DATE, 3L))
                .isInstanceOfSatisfying(HistoricalMembershipException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.code())
                                .isEqualTo(HistoricalMembershipException.Code.FAILED_PRECONDITION));
    }

    @Test
    void orphanOutsideRequestedDateStillFailsClosed() {
        GroupRepository groups = mock(GroupRepository.class);
        GroupHistoryCoverageRepository coverage = mock(GroupHistoryCoverageRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        StudentGroupHistoryRepository histories = mock(StudentGroupHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        configureBase(groups, coverage, semesters, histories, 7L);

        StudentGroupHistory oldOrigin = history(41L, 7L,
                DATE.minusDays(10), DATE.minusDays(5));
        when(histories.findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(7L))
                .thenReturn(List.of(oldOrigin));
        when(users.findAllStudentAssignmentsIncludingArchived(7L)).thenReturn(List.of());
        when(users.findAllIncludingArchivedByIds(List.of(41L))).thenReturn(List.of());

        HistoricalMembershipService service = new HistoricalMembershipService(
                groups, coverage, semesters, histories, users);

        assertThatThrownBy(() -> service.readRoster(7L, DATE, 3L))
                .isInstanceOf(HistoricalMembershipException.class);
    }

    @Test
    void currentStudentWithOnlyClosedHistoryFailsEvenWhenUserIdIsPresent() {
        GroupRepository groups = mock(GroupRepository.class);
        GroupHistoryCoverageRepository coverage = mock(GroupHistoryCoverageRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        StudentGroupHistoryRepository histories = mock(StudentGroupHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        configureBase(groups, coverage, semesters, histories, 7L);

        User currentStudent = mock(User.class);
        when(currentStudent.getId()).thenReturn(41L);
        StudentGroupHistory closed = history(41L, 7L,
                DATE.minusDays(10), DATE.minusDays(5));
        when(histories.findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(7L))
                .thenReturn(List.of(closed));
        when(users.findAllStudentAssignmentsIncludingArchived(7L))
                .thenReturn(List.of(currentStudent));
        when(histories.findByUserIdOrderByJoinedAtAscIdAsc(41L))
                .thenReturn(List.of(closed));
        when(users.findAllIncludingArchivedByIds(List.of(41L)))
                .thenReturn(List.of(currentStudent));

        HistoricalMembershipService service = new HistoricalMembershipService(
                groups, coverage, semesters, histories, users);

        assertThatThrownBy(() -> service.readRoster(7L, DATE, 3L))
                .isInstanceOfSatisfying(HistoricalMembershipException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.code())
                                .isEqualTo(HistoricalMembershipException.Code.FAILED_PRECONDITION));
    }

    @Test
    void currentStudentWithOpenHistoryInTwoGroupsFailsClosed() {
        GroupRepository groups = mock(GroupRepository.class);
        GroupHistoryCoverageRepository coverage = mock(GroupHistoryCoverageRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        StudentGroupHistoryRepository histories = mock(StudentGroupHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        configureBase(groups, coverage, semesters, histories, 7L);

        User currentStudent = mock(User.class);
        when(currentStudent.getId()).thenReturn(41L);
        StudentGroupHistory targetGroupHistory = history(41L, 7L, DATE.minusDays(1), null);
        StudentGroupHistory otherGroupHistory = history(41L, 8L, DATE.minusDays(1), null);
        when(histories.findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(7L))
                .thenReturn(List.of(targetGroupHistory));
        when(users.findAllStudentAssignmentsIncludingArchived(7L))
                .thenReturn(List.of(currentStudent));
        when(histories.findByUserIdOrderByJoinedAtAscIdAsc(41L))
                .thenReturn(List.of(targetGroupHistory, otherGroupHistory));
        when(users.findAllIncludingArchivedByIds(List.of(41L)))
                .thenReturn(List.of(currentStudent));

        HistoricalMembershipService service = new HistoricalMembershipService(
                groups, coverage, semesters, histories, users);

        assertThatThrownBy(() -> service.readRoster(7L, DATE, 3L))
                .isInstanceOf(HistoricalMembershipException.class);
    }

    @Test
    void nullSemesterBoundsFailWithTypedPreconditionBeforeDateComparison() {
        GroupRepository groups = mock(GroupRepository.class);
        GroupHistoryCoverageRepository coverage = mock(GroupHistoryCoverageRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        StudentGroupHistoryRepository histories = mock(StudentGroupHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        Semester semester = configureBase(groups, coverage, semesters, histories, 7L);
        when(semester.getDateFrom()).thenReturn(null);

        HistoricalMembershipService service = new HistoricalMembershipService(
                groups, coverage, semesters, histories, users);

        assertThatThrownBy(() -> service.readRoster(7L, DATE, 3L))
                .isInstanceOfSatisfying(HistoricalMembershipException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.code())
                                .isEqualTo(HistoricalMembershipException.Code.FAILED_PRECONDITION));
    }

    @Test
    void reversedSemesterBoundsFailWithTypedPreconditionBeforeDateComparison() {
        GroupRepository groups = mock(GroupRepository.class);
        GroupHistoryCoverageRepository coverage = mock(GroupHistoryCoverageRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        StudentGroupHistoryRepository histories = mock(StudentGroupHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        Semester semester = configureBase(groups, coverage, semesters, histories, 7L);
        when(semester.getDateFrom()).thenReturn(DATE.plusDays(1));
        when(semester.getDateTo()).thenReturn(DATE);

        HistoricalMembershipService service = new HistoricalMembershipService(
                groups, coverage, semesters, histories, users);

        assertThatThrownBy(() -> service.readRoster(7L, DATE, 3L))
                .isInstanceOfSatisfying(HistoricalMembershipException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.code())
                                .isEqualTo(HistoricalMembershipException.Code.FAILED_PRECONDITION));
    }

    @Test
    void asOfDateOutsideValidSemesterRangeRemainsInvalidArgument() {
        GroupRepository groups = mock(GroupRepository.class);
        GroupHistoryCoverageRepository coverage = mock(GroupHistoryCoverageRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        StudentGroupHistoryRepository histories = mock(StudentGroupHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        configureBase(groups, coverage, semesters, histories, 7L);

        HistoricalMembershipService service = new HistoricalMembershipService(
                groups, coverage, semesters, histories, users);

        assertThatThrownBy(() -> service.readRoster(7L, DATE.minusDays(11), 3L))
                .isInstanceOfSatisfying(HistoricalMembershipException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.code())
                                .isEqualTo(HistoricalMembershipException.Code.INVALID_ARGUMENT));
    }

    private static Semester configureBase(GroupRepository groups,
                                          GroupHistoryCoverageRepository coverage,
                                          SemesterRepository semesters,
                                          StudentGroupHistoryRepository histories,
                                          long groupId) {
        Group group = mock(Group.class);
        when(group.getId()).thenReturn(groupId);
        when(groups.findById(groupId)).thenReturn(Optional.of(group));
        Semester semester = mock(Semester.class);
        when(semester.getDateFrom()).thenReturn(DATE.minusDays(10));
        when(semester.getDateTo()).thenReturn(DATE.plusDays(10));
        when(semesters.findById(3L)).thenReturn(Optional.of(semester));
        GroupHistoryCoverage marker = mock(GroupHistoryCoverage.class);
        when(marker.getCoverageFrom()).thenReturn(DATE.minusDays(10));
        when(marker.getWriterVersion()).thenReturn(HistoricalMembershipService.WRITER_VERSION);
        when(coverage.findById(groupId)).thenReturn(Optional.of(marker));
        return semester;
    }

    private static StudentGroupHistory history(Long userId, Long groupId,
                                               LocalDate joinedAt, LocalDate leftAt) {
        StudentGroupHistory history = new StudentGroupHistory();
        history.setUserId(userId);
        history.setGroupId(groupId);
        history.setJoinedAt(joinedAt);
        history.setLeftAt(leftAt);
        return history;
    }
}
