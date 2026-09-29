package ru.rutcampustrack.academic.studentprojection;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentProjectionScopeServiceTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final long STUDENT_ID = 100L;
    private static final long SEMESTER_ID = 42L;
    private static final UUID SESSION_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void activeScopeClipsHistoryUnionsEffectiveSubjectsAndUsesExactCohort() {
        FakeQuery query = baseQuery();
        query.assignments = List.of(
                assignment(701L, 501L, 10L, LocalDate.of(2026, 9, 1), null, "LECTURE"),
                assignment(702L, 501L, 10L, LocalDate.of(2026, 9, 12),
                        LocalDate.of(2026, 9, 20), "SEMINAR"),
                // Same display name, different identity: both IDs remain visible.
                assignment(703L, 502L, 10L, LocalDate.of(2026, 9, 5),
                        LocalDate.of(2026, 9, 8), "LAB"));
        query.subjects.put(501L, subject(501L, "Mathematics", "LECTURE", 10L));
        query.subjects.put(502L, subject(502L, "Mathematics", "LECTURE", 10L));
        query.roster = List.of(1002L, 1001L, 1002L);

        StudentProjectionScope scope = service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L));

        assertThat(scope.serverDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(scope.dateFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(scope.dateTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(scope.terminalReadOnly()).isFalse();
        assertThat(scope.rankVisibility()).isEqualTo(StudentProjectionScope.RankVisibility.VISIBLE);
        assertThat(scope.rankGroupId()).isEqualTo(10L);
        assertThat(scope.rankEligible()).isTrue();
        assertThat(scope.activeRosterUserIds()).containsExactly(1001L, 1002L);
        assertThat(scope.subjects()).extracting(StudentProjectionScope.Subject::subjectId)
                .containsExactly(501L, 502L);
        assertThat(scope.subjects().get(0).lessonTypes()).containsExactly("lecture", "seminar");
        assertThat(scope.ownMembershipSegments()).hasSize(1);
        assertThat(scope.ownMembershipSegments().get(0).dateFrom())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(scope.ownMembershipSegments().get(0).dateUntilExclusive())
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(scope.ownMembershipSegments().get(0).subjectIds())
                .containsExactly(501L, 502L);
        assertThat(query.rosterCalls).isEqualTo(1);
    }

    @Test
    void rankSubjectsIncludeSemesterAssignmentsThatEndedBeforeLateJoin() {
        FakeQuery query = baseQuery();
        query.history = List.of(
                history(1L, 10L, LocalDate.of(2026, 9, 10), null));
        query.assignments = List.of(
                assignment(701L, 501L, 10L, LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 6), "LECTURE"));
        query.subjects.put(501L, subject(501L, "Mathematics", "LECTURE", 10L));

        StudentProjectionScope scope = service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L));

        assertThat(scope.rankEligible()).isTrue();
        assertThat(scope.rankSubjects()).singleElement()
                .satisfies(subject -> {
                    assertThat(subject.subjectId()).isEqualTo(501L);
                    assertThat(subject.groupId()).isEqualTo(10L);
                });
        assertThat(scope.subjects()).isEmpty();
        assertThat(scope.ownMembershipSegments()).singleElement()
                .satisfies(segment -> assertThat(segment.subjectIds()).isEmpty());
        assertThat(query.assignmentCalls).isEqualTo(2);
    }

    @Test
    void pastTransferredScopeHidesRankAndDoesNotQueryRoster() {
        FakeQuery query = baseQuery();
        query.history = List.of(
                history(1L, 10L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 10)),
                // Zero duration is a valid chronological change and still counts as a transfer.
                history(2L, 20L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)),
                history(3L, 10L, LocalDate.of(2026, 9, 10), null));
        query.assignments = List.of(
                assignment(701L, 501L, 10L, LocalDate.of(2026, 9, 1), null, "LECTURE"));
        query.subjects.put(501L, subject(501L, "Mathematics", "LECTURE", 10L));

        StudentProjectionScope scope = service(query, at("2026-10-02T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L));

        assertThat(scope.rankVisibility()).isEqualTo(StudentProjectionScope.RankVisibility.HIDDEN);
        assertThat(scope.rankGroupId()).isNull();
        assertThat(scope.rankEligible()).isFalse();
        assertThat(scope.activeRosterUserIds()).isEmpty();
        assertThat(scope.ownMembershipSegments()).hasSize(2);
        assertThat(scope.ownMembershipSegments().get(0).groupId()).isEqualTo(10L);
        assertThat(scope.ownMembershipSegments().get(1).groupId()).isEqualTo(10L);
        assertThat(query.rosterCalls).isZero();
    }

    @Test
    void terminalReadOnlyRetainsOwnHistoryButCannotJoinRank() {
        FakeQuery query = baseQuery();
        query.authority = Optional.of(new StudentProjectionQuery.AuthoritySnapshot(
                STUDENT_ID, 7L, "EXPELLED",
                List.of(new StudentProjectionQuery.RoleGrantSnapshot(11L, "STUDENT", "EXPELLED", null))));

        StudentProjectionScope scope = service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("EXPELLED", true, null));

        assertThat(scope.terminalReadOnly()).isTrue();
        assertThat(scope.ownMembershipSegments()).isNotEmpty();
        assertThat(scope.rankVisibility()).isEqualTo(StudentProjectionScope.RankVisibility.VISIBLE);
        assertThat(scope.rankGroupId()).isNull();
        assertThat(scope.rankEligible()).isFalse();
        assertThat(scope.activeRosterUserIds()).isEmpty();
        assertThat(query.rosterCalls).isZero();
    }

    @Test
    void terminalPastTransferIsHiddenAndTerminalWithoutSemesterMembershipFailsClosed() {
        FakeQuery transferred = baseQuery();
        transferred.authority = Optional.of(new StudentProjectionQuery.AuthoritySnapshot(
                STUDENT_ID, 7L, "GRADUATED",
                List.of(new StudentProjectionQuery.RoleGrantSnapshot(11L, "STUDENT", "GRADUATED", null))));
        transferred.history = List.of(
                history(1L, 10L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 10)),
                history(2L, 20L, LocalDate.of(2026, 9, 10), null));

        StudentProjectionScope scope = service(transferred, at("2026-10-02T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("GRADUATED", true, null));
        assertThat(scope.rankVisibility()).isEqualTo(StudentProjectionScope.RankVisibility.HIDDEN);
        assertThat(scope.rankGroupId()).isNull();
        assertThat(scope.rankEligible()).isFalse();
        assertThat(transferred.rosterCalls).isZero();

        FakeQuery outsideSemester = baseQuery();
        outsideSemester.authority = Optional.of(new StudentProjectionQuery.AuthoritySnapshot(
                STUDENT_ID, 7L, "EXPELLED",
                List.of(new StudentProjectionQuery.RoleGrantSnapshot(11L, "STUDENT", "EXPELLED", null))));
        outsideSemester.history = List.of(
                history(1L, 10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)));
        assertCode(outsideSemester,
                StudentProjectionException.Code.STUDENT_SCOPE_UNRESOLVED,
                claims("EXPELLED", true, null));
    }

    @Test
    void currentRankGroupComparisonUsesValuesForIdsBeyondIntegerCache() {
        FakeQuery query = baseQuery();
        long groupId = 300L;
        query.authority = Optional.of(new StudentProjectionQuery.AuthoritySnapshot(
                STUDENT_ID, 7L, "ACTIVE",
                List.of(new StudentProjectionQuery.RoleGrantSnapshot(11L, "STUDENT", "ACTIVE", groupId))));
        query.history = List.of(history(1L, groupId, LocalDate.of(2026, 1, 1), null));

        StudentProjectionScope scope = service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, groupId));
        assertThat(scope.rankGroupId()).isEqualTo(groupId);
        assertThat(scope.rankEligible()).isTrue();
        assertThat(query.rosterCalls).isEqualTo(1);
    }

    @Test
    void currentGroupMismatchWithSemesterMembershipIsInconsistent() {
        FakeQuery query = baseQuery();
        query.history = List.of(history(
                1L, 20L, LocalDate.of(2026, 1, 1), null));

        assertCode(query,
                StudentProjectionException.Code.INCONSISTENT_SOURCE,
                claims("ACTIVE", false, 10L));
        assertThat(query.rosterCalls).isZero();
    }

    @Test
    void missingCurrentGroupWithSemesterMembershipIsInconsistent() {
        FakeQuery query = baseQuery();
        query.history = List.of(history(
                1L, 10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 15)));

        assertCode(query,
                StudentProjectionException.Code.INCONSISTENT_SOURCE,
                claims("ACTIVE", false, 10L));
        assertThat(query.rosterCalls).isZero();
    }

    @Test
    void pastUntransferredClosedHistoryIsInconsistentBeforeRoster() {
        FakeQuery query = baseQuery();
        query.semester = Optional.of(new StudentProjectionQuery.SemesterSnapshot(
                SEMESTER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        query.history = List.of(history(
                1L, 10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 1)));

        assertThatThrownBy(() -> service(query, at("2026-10-02T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L)))
                .isInstanceOfSatisfying(StudentProjectionException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(StudentProjectionException.Code.INCONSISTENT_SOURCE));
        assertThat(query.rosterCalls).isZero();
    }

    @Test
    void absentSelectedSemesterMembershipRemainsUnresolvedBeforeDependentReads() {
        FakeQuery query = baseQuery();
        query.history = List.of(history(
                1L, 10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)));

        assertCode(query,
                StudentProjectionException.Code.STUDENT_SCOPE_UNRESOLVED,
                claims("ACTIVE", false, 10L));
        assertThat(query.assignmentCalls).isZero();
        assertThat(query.subjectCalls).isZero();
        assertThat(query.rosterCalls).isZero();
    }

    @Test
    void staleAuthorityIsRejectedBeforeSemesterOrHistoryDisclosure() {
        FakeQuery query = baseQuery();
        query.authority = Optional.of(new StudentProjectionQuery.AuthoritySnapshot(
                STUDENT_ID, 8L, "ACTIVE",
                List.of(new StudentProjectionQuery.RoleGrantSnapshot(11L, "STUDENT", "ACTIVE", 10L))));

        assertThatThrownBy(() -> service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claimsWithRolesVersion(7L, "ACTIVE", false, 10L)))
                .isInstanceOfSatisfying(StudentProjectionException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(StudentProjectionException.Code.INVALID_SESSION));
        assertThat(query.semesterCalls).isZero();
        assertThat(query.historyCalls).isZero();
        assertThat(query.assignmentCalls).isZero();
    }

    @Test
    void missingRevokedOrExpiredSessionIsRejectedBeforeDependentReads() {
        FakeQuery missing = baseQuery();
        missing.session = Optional.empty();
        assertSessionDenied(missing, claims("ACTIVE", false, 10L));

        FakeQuery revoked = baseQuery();
        revoked.session = Optional.of(sessionSnapshot(
                SESSION_ID, STUDENT_ID, 11L, 1L,
                Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z")));
        assertSessionDenied(revoked, claims("ACTIVE", false, 10L));

        FakeQuery expired = baseQuery();
        expired.session = Optional.of(sessionSnapshot(
                SESSION_ID, STUDENT_ID, 11L, 1L,
                Instant.parse("2026-09-15T10:00:00Z"), null));
        assertSessionDenied(expired, claims("ACTIVE", false, 10L));
    }

    @Test
    void sessionBindingRejectsWrongIdentityVersionsAndRoleSelectionBeforeDependentReads() {
        FakeQuery wrongUser = baseQuery();
        wrongUser.session = Optional.of(sessionSnapshot(
                SESSION_ID, 101L, 11L, 1L,
                Instant.parse("2030-01-01T00:00:00Z"), null));
        assertSessionDenied(wrongUser, claims("ACTIVE", false, 10L));

        FakeQuery wrongSession = baseQuery();
        wrongSession.session = Optional.of(sessionSnapshot(
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                STUDENT_ID, 11L, 1L,
                Instant.parse("2030-01-01T00:00:00Z"), null));
        assertSessionDenied(wrongSession, claims("ACTIVE", false, 10L));

        FakeQuery staleVersion = baseQuery();
        staleVersion.session = Optional.of(sessionSnapshot(
                SESSION_ID, STUDENT_ID, 11L, 2L,
                Instant.parse("2030-01-01T00:00:00Z"), null));
        assertSessionDenied(staleVersion, claims("ACTIVE", false, 10L));

        FakeQuery roleSwitch = baseQuery();
        roleSwitch.authority = Optional.of(new StudentProjectionQuery.AuthoritySnapshot(
                STUDENT_ID, 7L, "ACTIVE",
                List.of(
                        new StudentProjectionQuery.RoleGrantSnapshot(11L, "STUDENT", "ACTIVE", 10L),
                        new StudentProjectionQuery.RoleGrantSnapshot(12L, "TEACHER", "ACTIVE", null))));
        roleSwitch.session = Optional.of(sessionSnapshot(
                SESSION_ID, STUDENT_ID, 12L, 1L,
                Instant.parse("2030-01-01T00:00:00Z"), null));
        assertSessionDenied(roleSwitch, claims("ACTIVE", false, 10L));
    }

    @Test
    void selectedGrantIdentityMustMatchSignedClaimsBeforeDependentReads() {
        FakeQuery wrongGroup = baseQuery();
        assertSessionDenied(wrongGroup, claims("ACTIVE", false, 99L));

        FakeQuery headman = baseQuery();
        InternalJwtClaims headmanClaims = new InternalJwtClaims(
                STUDENT_ID, SESSION_ID, 1L, 7L,
                "STUDENT", "ACTIVE", 10L, true, false);
        assertSessionDenied(headman, headmanClaims);

        FakeQuery wrongReadOnly = baseQuery();
        assertSessionDenied(wrongReadOnly, claims("ACTIVE", true, 10L));
    }

    @Test
    void wrongRoleAndSuspendedAuthorityAreDeniedBeforeDependentReads() {
        FakeQuery wrongRoleQuery = baseQuery();
        assertThatThrownBy(() -> service(wrongRoleQuery, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L, "TEACHER")))
                .isInstanceOfSatisfying(StudentProjectionException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(StudentProjectionException.Code.WRONG_ROLE));
        assertThat(wrongRoleQuery.authorityCalls).isZero();

        FakeQuery suspendedQuery = baseQuery();
        suspendedQuery.authority = Optional.of(new StudentProjectionQuery.AuthoritySnapshot(
                STUDENT_ID, 7L, "SUSPENDED",
                List.of(new StudentProjectionQuery.RoleGrantSnapshot(11L, "STUDENT", "SUSPENDED", 10L))));
        assertThatThrownBy(() -> service(suspendedQuery, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("SUSPENDED", true, 10L)))
                .isInstanceOfSatisfying(StudentProjectionException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(StudentProjectionException.Code.OUT_OF_SCOPE));
        assertThat(suspendedQuery.semesterCalls).isZero();
        assertThat(suspendedQuery.historyCalls).isZero();
    }

    @Test
    void invalidHistoryFailsClosedForMissingInvertedAndOverlappingRows() {
        FakeQuery missing = baseQuery();
        missing.history = List.of();
        assertCode(missing, StudentProjectionException.Code.STUDENT_SCOPE_UNRESOLVED);

        FakeQuery inverted = baseQuery();
        inverted.history = List.of(
                history(1L, 10L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 9)));
        assertCode(inverted, StudentProjectionException.Code.INCONSISTENT_SOURCE);

        FakeQuery overlapping = baseQuery();
        overlapping.history = List.of(
                history(1L, 10L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20)),
                history(2L, 20L, LocalDate.of(2026, 9, 10), null));
        assertCode(overlapping, StudentProjectionException.Code.INCONSISTENT_SOURCE);
    }

    @Test
    void moscowBoundaryDeterminesPastSemesterWithoutUsingJvmDate() {
        FakeQuery beforeMidnight = baseQuery();
        StudentProjectionScope before = service(beforeMidnight,
                        at("2026-09-30T20:59:59Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L));
        assertThat(before.serverDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(before.rankGroupId()).isEqualTo(10L);
        assertThat(before.rankVisibility()).isEqualTo(StudentProjectionScope.RankVisibility.VISIBLE);

        FakeQuery afterMidnight = baseQuery();
        StudentProjectionScope after = service(afterMidnight,
                        at("2026-09-30T21:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L));
        assertThat(after.serverDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        // A sole historical group remains eligible for an untransferred past semester.
        assertThat(after.rankGroupId()).isEqualTo(10L);
        assertThat(after.rankEligible()).isTrue();
    }

    @Test
    void dependencyFailureIsTypedAndDoesNotReturnPartialScope() {
        FakeQuery query = baseQuery();
        query.assignmentFailure = new DataAccessResourceFailureException("assignments unavailable");

        assertThatThrownBy(() -> service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L)))
                .isInstanceOfSatisfying(StudentProjectionException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(StudentProjectionException.Code.DEPENDENCY_UNAVAILABLE));
        assertThat(query.subjectCalls).isZero();
        assertThat(query.rosterCalls).isZero();
    }

    @Test
    void sessionAuthorityFailureIsTypedAndDoesNotReturnPartialScope() {
        FakeQuery query = baseQuery();
        query.sessionFailure = new DataAccessResourceFailureException("sessions unavailable");

        assertThatThrownBy(() -> service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims("ACTIVE", false, 10L)))
                .isInstanceOfSatisfying(StudentProjectionException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(StudentProjectionException.Code.DEPENDENCY_UNAVAILABLE));
        assertThat(query.semesterCalls).isZero();
        assertThat(query.historyCalls).isZero();
        assertThat(query.assignmentCalls).isZero();
        assertThat(query.subjectCalls).isZero();
        assertThat(query.rosterCalls).isZero();
    }

    private static void assertCode(FakeQuery query, StudentProjectionException.Code code) {
        assertCode(query, code, claims("ACTIVE", false, 10L));
    }

    private static void assertCode(
            FakeQuery query,
            StudentProjectionException.Code code,
            InternalJwtClaims claims) {
        assertThatThrownBy(() -> service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims))
                .isInstanceOfSatisfying(StudentProjectionException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
    }

    private static void assertSessionDenied(FakeQuery query, InternalJwtClaims claims) {
        assertThatThrownBy(() -> service(query, at("2026-09-15T10:00:00Z"))
                .resolve(SEMESTER_ID, claims))
                .isInstanceOfSatisfying(StudentProjectionException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(StudentProjectionException.Code.INVALID_SESSION));
        assertThat(query.sessionCalls).isEqualTo(1);
        assertThat(query.semesterCalls).isZero();
        assertThat(query.historyCalls).isZero();
        assertThat(query.assignmentCalls).isZero();
        assertThat(query.subjectCalls).isZero();
        assertThat(query.rosterCalls).isZero();
    }

    private static StudentProjectionScopeService service(FakeQuery query, Clock clock) {
        return new StudentProjectionScopeService(query, clock);
    }

    private static FakeQuery baseQuery() {
        FakeQuery query = new FakeQuery();
        query.authority = Optional.of(new StudentProjectionQuery.AuthoritySnapshot(
                STUDENT_ID, 7L, "ACTIVE",
                List.of(new StudentProjectionQuery.RoleGrantSnapshot(11L, "STUDENT", "ACTIVE", 10L))));
        query.semester = Optional.of(new StudentProjectionQuery.SemesterSnapshot(
                SEMESTER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        query.history = List.of(
                history(1L, 10L, LocalDate.of(2026, 1, 1), null));
        query.roster = List.of(1002L, 1001L);
        query.session = Optional.of(sessionSnapshot(
                SESSION_ID, STUDENT_ID, 11L, 1L,
                Instant.parse("2030-01-01T00:00:00Z"), null));
        return query;
    }

    private static StudentProjectionQuery.SessionSnapshot sessionSnapshot(
            UUID sessionId,
            long userId,
            Long activeRoleGrantId,
            long sessionVersion,
            Instant refreshExpiresAt,
            Instant revokedAt) {
        return new StudentProjectionQuery.SessionSnapshot(
                sessionId, userId, activeRoleGrantId, sessionVersion, refreshExpiresAt, revokedAt);
    }

    private static InternalJwtClaims claims(
            String status,
            boolean readOnly,
            Long groupId) {
        return claimsWithRolesVersion(7L, status, readOnly, groupId, "STUDENT");
    }

    private static InternalJwtClaims claims(
            String status,
            boolean readOnly,
            Long groupId,
            String role) {
        return claimsWithRolesVersion(7L, status, readOnly, groupId, role);
    }

    private static InternalJwtClaims claimsWithRolesVersion(
            long rolesVersion,
            String status,
            boolean readOnly,
            Long groupId) {
        return claimsWithRolesVersion(rolesVersion, status, readOnly, groupId, "STUDENT");
    }

    private static InternalJwtClaims claimsWithRolesVersion(
            long rolesVersion,
            String status,
            boolean readOnly,
            Long groupId,
            String role) {
        return new InternalJwtClaims(
                STUDENT_ID, SESSION_ID, 1L, rolesVersion, role, status, groupId, false, readOnly);
    }

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), MOSCOW);
    }

    private static StudentProjectionQuery.GroupHistorySnapshot history(
            long id,
            long groupId,
            LocalDate joinedAt,
            LocalDate leftAt) {
        return new StudentProjectionQuery.GroupHistorySnapshot(
                id, STUDENT_ID, groupId, joinedAt, leftAt);
    }

    private static StudentProjectionQuery.AssignmentSnapshot assignment(
            long id,
            long subjectId,
            long groupId,
            LocalDate validFrom,
            LocalDate validUntilExclusive,
            String lessonType) {
        return new StudentProjectionQuery.AssignmentSnapshot(
                id, subjectId, groupId, SEMESTER_ID, lessonType, validFrom, validUntilExclusive);
    }

    private static StudentProjectionQuery.SubjectSnapshot subject(
            long id,
            String name,
            String type,
            long groupId) {
        return new StudentProjectionQuery.SubjectSnapshot(id, name, type, groupId);
    }

    private static final class FakeQuery implements StudentProjectionQuery {
        private Optional<AuthoritySnapshot> authority = Optional.empty();
        private Optional<SessionSnapshot> session = Optional.empty();
        private Optional<SemesterSnapshot> semester = Optional.empty();
        private List<GroupHistorySnapshot> history = List.of();
        private List<AssignmentSnapshot> assignments = List.of();
        private final Map<Long, SubjectSnapshot> subjects = new HashMap<>();
        private List<Long> roster = List.of();
        private RuntimeException assignmentFailure;
        private RuntimeException sessionFailure;
        private int authorityCalls;
        private int sessionCalls;
        private int semesterCalls;
        private int historyCalls;
        private int assignmentCalls;
        private int subjectCalls;
        private int rosterCalls;

        @Override
        public Optional<AuthoritySnapshot> findAuthority(long userId) {
            authorityCalls++;
            return authority;
        }

        @Override
        public Optional<SessionSnapshot> findSession(long userId, UUID sessionId) {
            sessionCalls++;
            if (sessionFailure != null) {
                throw sessionFailure;
            }
            return session;
        }

        @Override
        public Optional<SemesterSnapshot> findSemester(long semesterId) {
            semesterCalls++;
            return semester;
        }

        @Override
        public List<GroupHistorySnapshot> findGroupHistory(long userId) {
            historyCalls++;
            return new ArrayList<>(history);
        }

        @Override
        public List<AssignmentSnapshot> findAssignments(
                long semesterId,
                java.util.Collection<Long> groupIds,
                LocalDate dateFrom,
                LocalDate dateUntilExclusive) {
            assignmentCalls++;
            if (assignmentFailure != null) {
                throw assignmentFailure;
            }
            return new ArrayList<>(assignments);
        }

        @Override
        public Map<Long, SubjectSnapshot> findSubjectsByIds(java.util.Collection<Long> subjectIds) {
            subjectCalls++;
            Map<Long, SubjectSnapshot> selected = new HashMap<>();
            subjectIds.forEach(id -> {
                if (subjects.containsKey(id)) {
                    selected.put(id, subjects.get(id));
                }
            });
            return selected;
        }

        @Override
        public List<Long> findActiveStudentIds(
                long groupId,
                long semesterId,
                LocalDate dateFrom,
                LocalDate dateUntilExclusive) {
            rosterCalls++;
            return new ArrayList<>(roster);
        }
    }
}
