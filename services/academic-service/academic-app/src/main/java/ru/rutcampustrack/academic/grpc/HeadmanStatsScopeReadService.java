package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.Comparator;

/** Catalog metadata is returned only after current durable own-group authorization. */
@Service
public class HeadmanStatsScopeReadService {
    private final AssistantPermissionAuthority authority;
    private final GroupRepository groups;
    private final SemesterRepository semesters;
    private final JdbcTemplate jdbc;
    private final StudentGroupHistoryRepository history;

    public HeadmanStatsScopeReadService(AssistantPermissionAuthority authority, GroupRepository groups,
                                       SemesterRepository semesters, JdbcTemplate jdbc, StudentGroupHistoryRepository history) {
        this.authority = authority;
        this.groups = groups;
        this.semesters = semesters;
        this.jdbc = jdbc;
        this.history = history;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HeadmanStatsScopeResponse read(InternalJwtClaims actor, HeadmanStatsScopeRequest request) {
        long groupId = request.getGroupId();
        if (groupId <= 0 || request.hasSemesterId() && request.getSemesterId() <= 0
                || request.hasStudentId() && request.getStudentId() <= 0) {
            throw Status.INVALID_ARGUMENT.withDescription("group_id/semester_id must be positive").asRuntimeException();
        }
        if (!authority.allows(actor, groupId, "VIEW_STATS")) {
            throw Status.PERMISSION_DENIED.withDescription("current own-group VIEW_STATS required").asRuntimeException();
        }
        var group = groups.findById(groupId).filter(value -> value.isActive()).orElseThrow(() ->
                Status.PERMISSION_DENIED.withDescription("current group unavailable").asRuntimeException());
        var catalog = semesters.findAll().stream()
                .filter(value -> value.getDeletionPhase() == null)
                .sorted(Comparator.comparing(Semester::getDateFrom).reversed().thenComparing(Semester::getId))
                .toList();
        var active = catalog.stream().filter(Semester::isActive).toList();
        if (active.size() > 1) throw Status.UNAVAILABLE.withDescription("ambiguous active semester").asRuntimeException();
        var response = HeadmanStatsScopeResponse.newBuilder().setGroupId(groupId).setGroupName(group.getName());
        if (!active.isEmpty()) response.setActiveSemesterId(active.getFirst().getId());
        for (Semester semester : catalog) {
            if (semester.getDateFrom() == null || semester.getDateTo() == null
                    || semester.getDateTo().isBefore(semester.getDateFrom())) {
                throw Status.UNAVAILABLE.withDescription("invalid semester catalog").asRuntimeException();
            }
            response.addSemesters(toProto(semester));
        }
        Semester selected = request.hasSemesterId()
                ? catalog.stream().filter(value -> value.getId() == request.getSemesterId()).findFirst().orElseThrow(() ->
                    Status.NOT_FOUND.withDescription("requested semester unavailable").asRuntimeException())
                : active.isEmpty() ? null : active.getFirst();
        if (selected != null) response.setSelectedSemester(toProto(selected));
        if (request.hasStudentId()) {
            boolean current = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM user_role_grants g JOIN users u ON u.id = g.user_id
                        WHERE g.user_id = ? AND g.role = 'student' AND g.group_id = ?
                          AND g.status <> 'archived' AND u.status <> 'archived')
                    """, Boolean.class, request.getStudentId(), groupId));
            boolean historical = false;
            if (selected != null) {
                var rows = history.findByUserIdOrderByJoinedAtAscIdAsc(request.getStudentId());
                java.time.LocalDate previousEnd = null;
                boolean openSeen = false;
                for (var row : rows) {
                    if (row.getUserId() != request.getStudentId() || row.getGroupId() == null || row.getGroupId() <= 0
                            || row.getJoinedAt() == null || openSeen
                            || row.getLeftAt() != null && row.getLeftAt().isBefore(row.getJoinedAt())
                            || previousEnd != null && previousEnd.isAfter(row.getJoinedAt())) {
                        throw Status.UNAVAILABLE.withDescription("invalid student membership timeline").asRuntimeException();
                    }
                    if (row.getGroupId() == groupId && !row.getJoinedAt().isAfter(selected.getDateTo())
                            && (row.getLeftAt() == null || row.getLeftAt().isAfter(row.getJoinedAt())
                                && row.getLeftAt().isAfter(selected.getDateFrom()))) historical = true;
                    openSeen = row.getLeftAt() == null;
                    previousEnd = row.getLeftAt();
                }
            }
            if (selected != null ? !historical : !current) {
                throw Status.NOT_FOUND.withDescription("student outside selected group scope").asRuntimeException();
            }
        }
        // Current assigned student grants, including temporarily inactive grants; archived members excluded.
        jdbc.query("""
                SELECT count(DISTINCT g.user_id) FILTER (WHERE g.status = 'active') AS active,
                       count(DISTINCT g.user_id) AS total
                FROM user_role_grants g JOIN users u ON u.id = g.user_id
                WHERE g.role = 'student' AND g.group_id = ?
                  AND g.status <> 'archived' AND u.status <> 'archived'
                """, result -> {
            response.setMembershipActive(result.getLong("active"));
            response.setMembershipTotal(result.getLong("total"));
        }, groupId);
        return response.build();
    }

    private static HeadmanStatsSemester toProto(Semester value) {
        return HeadmanStatsSemester.newBuilder().setId(value.getId()).setName(value.getName())
                .setDateFrom(value.getDateFrom().toString()).setDateTo(value.getDateTo().toString())
                .setActive(value.isActive()).setArchived(value.isArchived()).build();
    }
}
