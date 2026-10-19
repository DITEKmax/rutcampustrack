package ru.rutcampustrack.academic.assignment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;

/** Academic domain eligibility, independent of the nonauthoritative Auth directory projection. */
@Component
public class AssignmentTeacherEligibility {
    private final JdbcTemplate jdbc;
    private final AcademicIdentityReadFence identityFence;

    public AssignmentTeacherEligibility(
            JdbcTemplate jdbc, AcademicIdentityReadFence identityFence) {
        this.jdbc = jdbc;
        this.identityFence = identityFence;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireEligible(long teacherId) {
        // Identity prepared()/APPLY take this same profile lock. Keep it until the assignment
        // transaction commits; then neither a new pending fence nor local eligibility can change
        // between these checks and admission/activation. Read eligibility in a separate statement
        // AFTER any lock wait, so READ COMMITTED sees the writer's committed eligibility/fence.
        var profiles =
                jdbc.queryForList(
                        "SELECT id FROM academic_profiles WHERE id=? FOR UPDATE",
                        Long.class,
                        teacherId);
        if (profiles.size() != 1) throw new ResourceNotFoundException("Teacher", "id", teacherId);
        identityFence.requireUserReadable(teacherId);
        Boolean eligible =
                jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM academic_profiles p
                          JOIN academic_role_eligibility e ON e.user_id=p.id
                         WHERE p.id=? AND p.archived_at IS NULL
                           AND e.role='TEACHER' AND e.status='ACTIVE')
                        """,
                        Boolean.class,
                        teacherId);
        // directory_status describes a profile, not each role of a multi-role person.
        if (!Boolean.TRUE.equals(eligible))
            throw new AccessDeniedException("У преподавателя нет активного права TEACHER");
    }
}
