package ru.rutcampustrack.academic.studentprojection;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only data port for the student projection resolver.
 *
 * <p>The port keeps SQL authority reads separate from the deterministic
 * history/rank calculation, which also makes the fail-closed rules testable
 * without replacing the real query adapter.</p>
 */
public interface StudentProjectionQuery {

    Optional<AuthoritySnapshot> findAuthority(long userId);

    Optional<SessionSnapshot> findSession(long userId, UUID sessionId);

    Optional<SemesterSnapshot> findSemester(long semesterId);

    List<GroupHistorySnapshot> findGroupHistory(long userId);

    List<AssignmentSnapshot> findAssignments(
            long semesterId,
            Collection<Long> groupIds,
            LocalDate dateFrom,
            LocalDate dateUntilExclusive);

    Map<Long, SubjectSnapshot> findSubjectsByIds(Collection<Long> subjectIds);

    List<Long> findActiveStudentIds(
            long groupId,
            long semesterId,
            LocalDate dateFrom,
            LocalDate dateUntilExclusive);

    record AuthoritySnapshot(
            long userId,
            long rolesVersion,
            /** Current status of the authoritative STUDENT grant. */
            String accountStatus,
            List<RoleGrantSnapshot> grants) {
        public AuthoritySnapshot {
            grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
        }
    }

    record SessionSnapshot(
            UUID sessionId,
            long userId,
            Long activeRoleGrantId,
            long sessionVersion,
            Instant refreshExpiresAt,
            Instant revokedAt) {
        public SessionSnapshot {
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            if (userId <= 0 || sessionVersion <= 0) {
                throw new IllegalArgumentException("session identity must be positive");
            }
            if (activeRoleGrantId != null && activeRoleGrantId <= 0) {
                throw new IllegalArgumentException("activeRoleGrantId must be positive when present");
            }
            refreshExpiresAt = Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt");
        }

        public boolean isLiveAt(Instant now) {
            Objects.requireNonNull(now, "now");
            return revokedAt == null && now.isBefore(refreshExpiresAt);
        }
    }

    record RoleGrantSnapshot(
            long id,
            String role,
            String status,
            Long groupId) {
    }

    record SemesterSnapshot(
            long id,
            LocalDate dateFrom,
            LocalDate dateTo) {
    }

    record GroupHistorySnapshot(
            long id,
            long userId,
            long groupId,
            LocalDate joinedAt,
            LocalDate leftAt) {
    }

    record AssignmentSnapshot(
            long id,
            long subjectId,
            long groupId,
            long semesterId,
            String lessonType,
            LocalDate validFrom,
            LocalDate validUntilExclusive) {
    }

    record SubjectSnapshot(
            long id,
            String name,
            String type,
            long groupId) {
    }
}
