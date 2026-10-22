package ru.rutcampustrack.academic.studentprojection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.authauthority.grpc.AuthorityAccountState;
import ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * PostgreSQL read adapter for the signed student projection.
 *
 * <p>Auth owns live admission and current grants through directed TLS RPCs. Only Academic profile
 * revisions, eligibility, history and effective assignments are read with local parameterized SQL.
 */
@Repository
public class JdbcStudentProjectionQueryAdapter implements StudentProjectionQuery {

    private static final String SEMESTER_SQL =
            """
            SELECT id, date_from, date_to
            FROM semesters
            WHERE id = ?
            """;

    private static final String HISTORY_SQL =
            """
            SELECT id, user_id, group_id, joined_at, left_at
            FROM student_group_history
            WHERE user_id = ?
            ORDER BY joined_at, id
            """;

    private static final String ASSIGNMENT_SQL_PREFIX =
            """
            SELECT id, subject_id, group_id, semester_id,
                   lesson_type::text AS lesson_type,
                   valid_from, valid_until_exclusive
            FROM assignments
            WHERE semester_id = ?
              AND group_id IN (\
            """;

    private static final String ASSIGNMENT_SQL_SUFFIX =
            """
            )
              AND valid_from < ?
              AND (valid_until_exclusive IS NULL OR valid_until_exclusive > ?)
            ORDER BY group_id, subject_id, valid_from, id
            """;

    private static final String ROSTER_SQL =
            """
            SELECT DISTINCT g.user_id
            FROM academic_role_eligibility g
            JOIN academic_profiles p ON p.id = g.user_id
            JOIN student_group_history h
              ON h.user_id = g.user_id
             AND h.group_id = g.group_id
            WHERE g.role = 'STUDENT'
              AND g.status = 'ACTIVE'
              AND g.group_id = ?
              AND h.joined_at < ?
              AND (h.left_at IS NULL OR h.left_at > h.joined_at)
              AND (h.left_at IS NULL OR h.left_at > ?)
            ORDER BY g.user_id
            """;

    private static final String SUBJECT_SQL_PREFIX =
            """
            SELECT id, name, type::text AS subject_type, group_id
            FROM subjects
            WHERE id IN (\
            """;

    private static final String SUBJECT_SQL_SUFFIX = ") ORDER BY id";

    private final JdbcTemplate jdbcTemplate;
    private final AuthIdentityDataClient identities;
    private final AcademicLiveSessionClient sessions;
    private final AcademicIdentityReadFence fences;
    private final TransactionTemplate reads;

    public JdbcStudentProjectionQueryAdapter(
            JdbcTemplate jdbcTemplate,
            AuthIdentityDataClient identities,
            AcademicLiveSessionClient sessions,
            AcademicIdentityReadFence fences,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.identities = identities;
        this.sessions = sessions;
        this.fences = fences;
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setReadOnly(true);
        this.reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Optional<AuthoritySnapshot> findAuthority(long userId) {
        outsideTransaction();
        var source = identities.readServiceIdentity(List.of(userId), deadline()).get(userId);
        return source == null ? Optional.empty() : Optional.of(authority(source));
    }

    @Override
    public Optional<SessionSnapshot> findSession(InternalJwtClaims claims) {
        outsideTransaction();
        var admitted = sessions.admitProjection(claims);
        return Optional.of(
                new SessionSnapshot(
                        UUID.fromString(admitted.getIdentity().getSessionId()),
                        admitted.getIdentity().getUserId(),
                        admitted.hasActiveRoleGrantId() ? admitted.getActiveRoleGrantId() : null,
                        admitted.getIdentity().getSessionVersion(),
                        Instant.parse(admitted.getRefreshExpiresAt()),
                        admitted.hasRevokedAt() ? Instant.parse(admitted.getRevokedAt()) : null));
    }

    @Override
    public <T> T readSnapshot(AuthoritySnapshot authority, Supplier<T> action) {
        outsideTransaction();
        return reads.execute(
                ignored -> {
                    requireLocalAuthority(authority);
                    return action.get();
                });
    }

    @Override
    public void verifyCurrent(AuthoritySnapshot admitted, StudentProjectionScope scope) {
        outsideTransaction();
        var selected = new java.util.TreeSet<Long>(scope.activeRosterUserIds());
        selected.add(admitted.userId());
        var ids = List.copyOf(selected);
        var current = new LinkedHashMap<Long, AuthoritySnapshot>();
        long deadline = deadline();
        for (int offset = 0; offset < ids.size(); offset += 200) {
            identities
                    .readServiceIdentity(
                            ids.subList(offset, Math.min(offset + 200, ids.size())), deadline)
                    .forEach((id, identity) -> current.put(id, authority(identity)));
        }
        if (!admitted.equals(current.get(admitted.userId()))) {
            throw StudentProjectionException.invalidSession(
                    "Student authority changed during projection");
        }
        reads.executeWithoutResult(
                ignored -> {
                    requireLocalAuthority(admitted);
                    for (long group :
                            scope.ownMembershipSegments().stream()
                                    .map(StudentProjectionScope.MembershipSegment::groupId)
                                    .distinct()
                                    .toList()) {
                        fences.requireGroupReadable(group);
                    }
                    for (long userId : scope.activeRosterUserIds()) {
                        var member = current.get(userId);
                        if (member == null || !hasActiveStudentGrant(member, scope.rankGroupId())) {
                            throw StudentProjectionException.dependency(
                                    "Rank cohort authority changed", null);
                        }
                        requireLocalAuthority(member);
                    }
                    if (scope.rankEligible()) {
                        fences.requireGroupReadable(scope.rankGroupId());
                        var roster =
                                findActiveStudentIds(
                                        scope.rankGroupId(),
                                        scope.semesterId(),
                                        scope.dateFrom(),
                                        scope.dateTo().plusDays(1));
                        if (!roster.equals(scope.activeRosterUserIds())) {
                            throw StudentProjectionException.dependency(
                                    "Rank cohort changed", null);
                        }
                    }
                });
    }

    private void requireLocalAuthority(AuthoritySnapshot authority) {
        fences.requireUserReadable(authority.userId());
        var grants =
                authority.grants().stream()
                        .filter(grant -> "STUDENT".equalsIgnoreCase(grant.role()))
                        .toList();
        if (grants.size() != 1 || authority.academicRevision() <= 0) {
            throw StudentProjectionException.dependency(
                    "Current student domain revision unavailable", null);
        }
        var grant = grants.getFirst();
        Boolean matches =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                          SELECT 1 FROM academic_profiles p
                          JOIN academic_role_eligibility e ON e.user_id=p.id AND e.role='STUDENT'
                          WHERE p.id=? AND p.applied_auth_roles_version=? AND p.academic_revision=?
                            AND e.status=? AND e.group_id IS NOT DISTINCT FROM ?::bigint)
                        """,
                        Boolean.class,
                        authority.userId(),
                        authority.rolesVersion(),
                        authority.academicRevision(),
                        grant.status().toUpperCase(java.util.Locale.ROOT),
                        grant.groupId());
        if (!Boolean.TRUE.equals(matches)) {
            throw StudentProjectionException.dependency("Student domain revision is stale", null);
        }
        if (grant.groupId() != null) fences.requireGroupReadable(grant.groupId());
    }

    private static boolean hasActiveStudentGrant(AuthoritySnapshot authority, Long groupId) {
        var grants =
                authority.grants().stream()
                        .filter(grant -> "STUDENT".equalsIgnoreCase(grant.role()))
                        .toList();
        return grants.size() == 1
                && grants.getFirst().id() > 0
                && "ACTIVE".equalsIgnoreCase(grants.getFirst().status())
                && java.util.Objects.equals(grants.getFirst().groupId(), groupId);
    }

    private static AuthoritySnapshot authority(IdentityAuthoritySnapshot source) {
        if (source.getUserId() <= 0
                || source.getRolesVersion() <= 0
                || source.getAcademicRevision() <= 0
                || (source.getAccountState() != AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE
                        && source.getAccountState()
                                != AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ARCHIVED)) {
            throw StudentProjectionException.dependency(
                    "Current identity authority unavailable", null);
        }
        var grants =
                source.getGrantsList().stream()
                        .map(
                                grant ->
                                        new RoleGrantSnapshot(
                                                grant.getGrantId(),
                                                grant.getRole(),
                                                grant.getStatus(),
                                                grant.hasGroupId() ? grant.getGroupId() : null))
                        .toList();
        String status =
                grants.stream()
                        .filter(grant -> "STUDENT".equalsIgnoreCase(grant.role()))
                        .map(RoleGrantSnapshot::status)
                        .findFirst()
                        .orElse(null);
        return new AuthoritySnapshot(
                source.getUserId(),
                source.getRolesVersion(),
                status,
                grants,
                source.getAcademicRevision());
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw StudentProjectionException.dependency(
                    "Auth admission cannot run inside a transaction", null);
        }
    }

    @Override
    public Optional<SemesterSnapshot> findSemester(long semesterId) {
        List<SemesterSnapshot> semesters =
                jdbcTemplate.query(
                        SEMESTER_SQL,
                        ps -> ps.setLong(1, semesterId),
                        (rs, rowNum) ->
                                new SemesterSnapshot(
                                        rs.getLong("id"),
                                        rs.getObject("date_from", LocalDate.class),
                                        rs.getObject("date_to", LocalDate.class)));
        return semesters.stream().findFirst();
    }

    @Override
    public List<GroupHistorySnapshot> findGroupHistory(long userId) {
        return jdbcTemplate.query(
                HISTORY_SQL,
                ps -> ps.setLong(1, userId),
                (rs, rowNum) ->
                        new GroupHistorySnapshot(
                                rs.getLong("id"),
                                rs.getLong("user_id"),
                                rs.getLong("group_id"),
                                rs.getObject("joined_at", LocalDate.class),
                                rs.getObject("left_at", LocalDate.class)));
    }

    @Override
    public List<AssignmentSnapshot> findAssignments(
            long semesterId,
            Collection<Long> groupIds,
            LocalDate dateFrom,
            LocalDate dateUntilExclusive) {
        List<Long> groups = groupIds.stream().distinct().sorted().toList();
        if (groups.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", groups.stream().map(ignored -> "?").toList());
        String sql = ASSIGNMENT_SQL_PREFIX + placeholders + ASSIGNMENT_SQL_SUFFIX;
        List<Object> args = new ArrayList<>(3 + groups.size());
        args.add(semesterId);
        args.addAll(groups);
        args.add(dateUntilExclusive);
        args.add(dateFrom);
        return jdbcTemplate.query(
                sql,
                args.toArray(),
                (rs, rowNum) ->
                        new AssignmentSnapshot(
                                rs.getLong("id"),
                                rs.getLong("subject_id"),
                                rs.getLong("group_id"),
                                rs.getLong("semester_id"),
                                rs.getString("lesson_type"),
                                rs.getObject("valid_from", LocalDate.class),
                                rs.getObject("valid_until_exclusive", LocalDate.class)));
    }

    @Override
    public Map<Long, SubjectSnapshot> findSubjectsByIds(Collection<Long> subjectIds) {
        List<Long> ids = subjectIds.stream().distinct().sorted().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(", ", ids.stream().map(ignored -> "?").toList());
        String sql = SUBJECT_SQL_PREFIX + placeholders + SUBJECT_SQL_SUFFIX;
        List<SubjectSnapshot> subjects =
                jdbcTemplate.query(
                        sql,
                        ids.toArray(),
                        (rs, rowNum) ->
                                new SubjectSnapshot(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        rs.getString("subject_type"),
                                        rs.getLong("group_id")));
        Map<Long, SubjectSnapshot> result = new LinkedHashMap<>();
        subjects.forEach(subject -> result.put(subject.id(), subject));
        return Map.copyOf(result);
    }

    @Override
    public List<Long> findActiveStudentIds(
            long groupId, long semesterId, LocalDate dateFrom, LocalDate dateUntilExclusive) {
        // semesterId is part of the port contract so the caller cannot
        // accidentally reuse a roster read for another semester.  History is
        // date based; the selected semester bounds are the query's scope.
        if (semesterId <= 0) {
            return List.of();
        }
        fences.requireGroupReadable(groupId);
        return jdbcTemplate.query(
                ROSTER_SQL,
                ps -> {
                    ps.setLong(1, groupId);
                    ps.setObject(2, dateUntilExclusive);
                    ps.setObject(3, dateFrom);
                },
                (rs, rowNum) -> rs.getLong("user_id"));
    }
}
