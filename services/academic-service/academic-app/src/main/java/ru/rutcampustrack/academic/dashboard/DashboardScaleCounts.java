package ru.rutcampustrack.academic.dashboard;

import java.util.Locale;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

/** The same canonical aggregate supplies current cards and prospective observations. */
record DashboardScaleCounts(long students, long teachers, long groups) {
    static DashboardScaleCounts read(
            UserRoleGrantRepository grants, GroupRegistryReadRepository groups) {
        String active = RoleGrantStatus.ACTIVE.name().toLowerCase(Locale.ROOT);
        return new DashboardScaleCounts(
                grants.countDistinctUsersByRoleAndStatus(
                        UserRole.STUDENT.name().toLowerCase(Locale.ROOT), active),
                grants.countDistinctUsersByRoleAndStatus(
                        UserRole.TEACHER.name().toLowerCase(Locale.ROOT), active),
                groups.countAll(null).activeCount());
    }
}
