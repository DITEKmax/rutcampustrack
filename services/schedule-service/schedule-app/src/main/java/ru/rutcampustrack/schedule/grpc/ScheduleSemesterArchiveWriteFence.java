package ru.rutcampustrack.schedule.grpc;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.academic.grpc.SemesterTransition;
import ru.rutcampustrack.schedule.exception.ConflictException;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Acquires Schedule's archive lock before any domain-row lock or mutation. */
@Service
public class ScheduleSemesterArchiveWriteFence {

    private static final int BARRIER_LOCK_NAMESPACE = 5_452_097;

    private final JdbcTemplate jdbc;
    private final AcademicGrpcClient academicGrpcClient;

    public ScheduleSemesterArchiveWriteFence(JdbcTemplate jdbc, AcademicGrpcClient academicGrpcClient) {
        this.jdbc = jdbc;
        this.academicGrpcClient = academicGrpcClient;
    }

    /**
     * Serializes against Schedule PREPARE and rechecks its durable barrier.
     * Academic is queried only when the local participant says RELEASED: that
     * is the interval in which the central release-pending gate still blocks
     * business writes. The query happens before any local lock.
     */
    public void lockForBusinessWrite(long semesterId) {
        Map<String, Object> observed = localBarrier(semesterId);
        SemesterStateResponse authority = null;
        if (observed == null || "RELEASED".equals(observed.get("participant_state"))) {
            authority = readAuthority(semesterId);
            if (authorityBlocksWrites(authority)) throw blocked(semesterId);
        }
        lockSemester(jdbc, semesterId);
        Map<String, Object> current = localBarrier(semesterId);
        if (localBarrierBlocks(current)
                || currentIsNewReleasedEpoch(current, observed)) {
            throw blocked(semesterId);
        }
    }

    /** Allows only the exact admitted binding drain during archive/delete preparation. */
    public void lockForPendingBindingConfirmation(long semesterId) {
        Map<String, Object> observed = localBarrier(semesterId);
        if (observed == null || "RELEASED".equals(observed.get("participant_state"))) {
            lockForBusinessWrite(semesterId);
            return;
        }
        String barrierState = String.valueOf(observed.get("participant_state"));
        boolean deletePreparing = "DELETE_PREPARING".equals(barrierState);
        if (!"PENDING".equals(barrierState) && !deletePreparing) {
            lockSemester(jdbc, semesterId);
            throw blocked(semesterId);
        }
        SemesterStateResponse authority = readAuthority(semesterId);
        long observedVersion = ((Number) observed.get("state_version")).longValue();
        boolean archiveAuthority = !deletePreparing
                && authority.getTransition() == SemesterTransition.ARCHIVING;
        boolean deletionAuthority = deletePreparing
                && authority.getTransition() == SemesterTransition.DELETING
                && "PREPARING".equals(authority.getDeletionPhase())
                && String.valueOf(observed.get("operation_id")).equals(authority.getTransitionOperationId());
        if (authority.getArchived() || authority.getReleasePending()
                || (!archiveAuthority && !deletionAuthority)
                || authority.getStateVersion() != observedVersion) {
            throw blocked(semesterId);
        }
        lockSemester(jdbc, semesterId);
        Map<String, Object> current = localBarrier(semesterId);
        if (current == null || !barrierState.equals(current.get("participant_state"))
                || !current.get("state_version").equals(observed.get("state_version"))
                || !current.get("operation_id").equals(observed.get("operation_id"))) {
            throw blocked(semesterId);
        }
    }

    /**
     * For multi-semester background/cascade writes, fetches every remote
     * authority before taking any local lock, then locks eligible semesters in
     * numeric order. Returned IDs remain locked until the caller's transaction ends.
     */
    public Set<Long> lockWritableSemesters(Collection<Long> semesterIds) {
        TreeSet<Long> ordered = new TreeSet<>();
        if (semesterIds != null) {
            for (Long semesterId : semesterIds) {
                if (semesterId != null && semesterId > 0) ordered.add(semesterId);
            }
        }

        Map<Long, Map<String, Object>> observed = new java.util.LinkedHashMap<>();
        Map<Long, SemesterStateResponse> authorities = new java.util.LinkedHashMap<>();
        for (Long semesterId : ordered) {
            Map<String, Object> barrier = localBarrier(semesterId);
            observed.put(semesterId, barrier);
            if (barrier == null || "RELEASED".equals(barrier.get("participant_state"))) {
                authorities.put(semesterId, readAuthority(semesterId));
            }
        }

        TreeSet<Long> writable = new TreeSet<>();
        for (Long semesterId : ordered) {
            SemesterStateResponse authority = authorities.get(semesterId);
            if (authority != null && authorityBlocksWrites(authority)) continue;
            lockSemester(jdbc, semesterId);
            Map<String, Object> current = localBarrier(semesterId);
            if (!localBarrierBlocks(current)
                    && !currentIsNewReleasedEpoch(current, observed.get(semesterId))) {
                writable.add(semesterId);
            }
        }
        return Set.copyOf(writable);
    }

    public void requireWritableSemesters(Collection<Long> semesterIds) {
        TreeSet<Long> ordered = new TreeSet<>();
        if (semesterIds != null) {
            for (Long semesterId : semesterIds) {
                if (semesterId == null || semesterId <= 0) {
                    throw new ConflictException("Нельзя изменить данные без подтверждённого semester scope");
                }
                ordered.add(semesterId);
            }
        }
        Set<Long> writable = lockWritableSemesters(ordered);
        if (writable.size() != ordered.size()) {
            throw new ConflictException("Один из семестров временно заблокирован переходом архивации");
        }
    }

    static void lockSemester(JdbcTemplate jdbc, long semesterId) {
        if (semesterId <= 0) {
            throw new ConflictException("Нельзя изменить данные без подтверждённого semester scope");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, BARRIER_LOCK_NAMESPACE);
                statement.setInt(2, (int) (semesterId % Integer.MAX_VALUE));
                statement.execute();
            }
            return null;
        });
    }

    private SemesterStateResponse readAuthority(long semesterId) {
        if (semesterId <= 0) {
            throw new ConflictException("Нельзя изменить данные без подтверждённого semester scope");
        }
        SemesterStateResponse authority = academicGrpcClient.getSemesterArchiveAuthorityState(semesterId);
        if (authority.getId() != semesterId) {
            throw new ConflictException("Academic вернул состояние другого семестра");
        }
        return authority;
    }

    private boolean localBarrierBlocks(Map<String, Object> barrier) {
        if (barrier == null) return false;
        String state = String.valueOf(barrier.get("participant_state"));
        return "PENDING".equals(state) || "READY".equals(state) || "PREPARED_RESTORE".equals(state)
                || "DELETE_PREPARING".equals(state) || "DELETE_SEALED".equals(state)
                || "DELETED".equals(state);
    }

    private static boolean currentIsNewReleasedEpoch(Map<String, Object> current,
                                                     Map<String, Object> observed) {
        if (current == null || !"RELEASED".equals(current.get("participant_state"))) return false;
        if (observed == null || !"RELEASED".equals(observed.get("participant_state"))) return true;
        return !current.get("state_version").equals(observed.get("state_version"));
    }

    private Map<String, Object> localBarrier(long semesterId) {
        return jdbc.query("""
                SELECT operation_id, state_version, participant_state
                  FROM schedule_semester_archive_barriers
                 WHERE semester_id = ?
                """, resultSet -> resultSet.next() ? Map.of(
                "operation_id", resultSet.getObject("operation_id", UUID.class),
                "state_version", resultSet.getLong("state_version"),
                "participant_state", resultSet.getString("participant_state")) : null,
                semesterId);
    }

    private static boolean authorityBlocksWrites(SemesterStateResponse authority) {
        return authority.getWriteBlocked() || authority.getArchived() || authority.getReleasePending()
                || authority.getTransition() != SemesterTransition.NONE;
    }

    private static ConflictException blocked(long semesterId) {
        return new ConflictException("Семестр " + semesterId
                + " временно заблокирован переходом архивации или восстановления");
    }
}
