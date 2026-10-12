package ru.rutcampustrack.academic.assignment;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

/** Live read authority for an exact dated teacher assignment, never a whole group. */
@Service
public class TeacherAssignmentReadAuthority {
    private final AssignmentRepository assignments;
    private final SemesterRepository semesters;
    private final UserRoleGrantRepository grants;

    public TeacherAssignmentReadAuthority(
            AssignmentRepository assignments,
            SemesterRepository semesters,
            UserRoleGrantRepository grants) {
        this.assignments = assignments;
        this.semesters = semesters;
        this.grants = grants;
    }

    public void requireCanRead(
            Long teacherId,
            Long groupId,
            Long subjectId,
            Long semesterId,
            SubjectType lessonType,
            LocalDate date) {
        if (teacherId == null
                || teacherId <= 0
                || groupId == null
                || groupId <= 0
                || subjectId == null
                || subjectId <= 0
                || semesterId == null
                || semesterId <= 0
                || lessonType == null
                || date == null
                || grants.findByUserIdAndRoleAndStatus(
                                teacherId,
                                AssignmentAuthority.TEACHER_ROLE,
                                AssignmentAuthority.ACTIVE_STATUS)
                        .isEmpty()) {
            throw denied();
        }
        var semester =
                semesters.findById(semesterId).orElseThrow(TeacherAssignmentReadAuthority::denied);
        if (semester.getDateFrom() == null
                || semester.getDateTo() == null
                || date.isBefore(semester.getDateFrom())
                || date.isAfter(semester.getDateTo())) {
            throw denied();
        }
        boolean ownsDate =
                assignments.findPublishedActiveBySubjectIdAndGroupId(subjectId, groupId).stream()
                        .anyMatch(
                                assignment ->
                                        Objects.equals(teacherId, assignment.getTeacherId())
                                                && matches(
                                                        assignment,
                                                        groupId,
                                                        subjectId,
                                                        semester,
                                                        lessonType,
                                                        date));
        if (!ownsDate) throw denied();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("ДЗ не входит в действующее назначение преподавателя");
    }

    /** Service-admitted audience lookup with the same published, exact dated authority. */
    public List<Long> findTeacherIds(
            Long groupId,
            Long subjectId,
            Long semesterId,
            SubjectType lessonType,
            LocalDate date,
            Long assignmentId) {
        if (groupId == null
                || groupId <= 0
                || subjectId == null
                || subjectId <= 0
                || semesterId == null
                || semesterId <= 0
                || lessonType == null
                || date == null
                || assignmentId != null && assignmentId <= 0) throw denied();
        var semester =
                semesters.findById(semesterId).orElseThrow(TeacherAssignmentReadAuthority::denied);
        if (semester.getDateFrom() == null
                || semester.getDateTo() == null
                || date.isBefore(semester.getDateFrom())
                || date.isAfter(semester.getDateTo())) throw denied();
        return assignments.findPublishedActiveBySubjectIdAndGroupId(subjectId, groupId).stream()
                .filter(
                        assignment ->
                                assignmentId == null
                                        || Objects.equals(assignmentId, assignment.getId()))
                .filter(
                        assignment ->
                                matches(assignment, groupId, subjectId, semester, lessonType, date))
                .map(assignment -> assignment.getTeacherId())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .filter(
                        teacher ->
                                !grants.findByUserIdAndRoleAndStatus(
                                                teacher,
                                                AssignmentAuthority.TEACHER_ROLE,
                                                AssignmentAuthority.ACTIVE_STATUS)
                                        .isEmpty())
                .toList();
    }

    private static boolean matches(
            ru.rutcampustrack.academic.entity.Assignment assignment,
            Long group,
            Long subject,
            ru.rutcampustrack.academic.entity.Semester semester,
            SubjectType type,
            LocalDate date) {
        return "ACTIVE".equals(assignment.getLifecycleState())
                && Objects.equals(group, assignment.getGroupId())
                && Objects.equals(subject, assignment.getSubjectId())
                && Objects.equals(semester.getId(), assignment.getSemesterId())
                && type == assignment.getLessonType()
                && assignment.getValidFrom() != null
                && !date.isBefore(assignment.getValidFrom())
                && date.isBefore(AssignmentAuthority.effectiveEnd(assignment, semester));
    }
}
