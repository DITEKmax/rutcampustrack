package ru.rutcampustrack.academic.assignment;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

import java.time.LocalDate;
import java.util.Objects;

/** Live read authority for an exact dated teacher assignment, never a whole group. */
@Service
public class TeacherAssignmentReadAuthority {
    private final AssignmentRepository assignments;
    private final SemesterRepository semesters;
    private final UserRoleGrantRepository grants;

    public TeacherAssignmentReadAuthority(AssignmentRepository assignments,
                                         SemesterRepository semesters,
                                         UserRoleGrantRepository grants) {
        this.assignments = assignments;
        this.semesters = semesters;
        this.grants = grants;
    }

    public void requireCanRead(Long teacherId, Long groupId, Long subjectId,
                               Long semesterId, SubjectType lessonType, LocalDate date) {
        if (teacherId == null || teacherId <= 0 || groupId == null || groupId <= 0
                || subjectId == null || subjectId <= 0 || semesterId == null || semesterId <= 0
                || lessonType == null || date == null
                || grants.findByUserIdAndRoleAndStatus(teacherId,
                        AssignmentAuthority.TEACHER_ROLE, AssignmentAuthority.ACTIVE_STATUS).isEmpty()) {
            throw denied();
        }
        var semester = semesters.findById(semesterId).orElseThrow(TeacherAssignmentReadAuthority::denied);
        if (semester.getDateFrom() == null || semester.getDateTo() == null
                || date.isBefore(semester.getDateFrom()) || date.isAfter(semester.getDateTo())) {
            throw denied();
        }
        boolean ownsDate = assignments.findPublishedActiveBySubjectIdAndGroupId(subjectId, groupId)
                .stream().anyMatch(assignment -> "ACTIVE".equals(assignment.getLifecycleState())
                        && Objects.equals(teacherId, assignment.getTeacherId())
                        && Objects.equals(groupId, assignment.getGroupId())
                        && Objects.equals(subjectId, assignment.getSubjectId())
                        && Objects.equals(semesterId, assignment.getSemesterId())
                        && lessonType == assignment.getLessonType()
                        && assignment.getValidFrom() != null
                        && !date.isBefore(assignment.getValidFrom())
                        && date.isBefore(AssignmentAuthority.effectiveEnd(assignment, semester)));
        if (!ownsDate) throw denied();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("ДЗ не входит в действующее назначение преподавателя");
    }
}
