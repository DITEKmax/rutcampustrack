package ru.rutcampustrack.attendance.report.planned;

import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;

/** A teacher report reads either the existing live authority or one admitted immutable snapshot. */
@FunctionalInterface
public interface TeacherAssignmentReadPort {
    TeacherAssignmentsResponse fullSemesterAssignments(long semesterId);
}
