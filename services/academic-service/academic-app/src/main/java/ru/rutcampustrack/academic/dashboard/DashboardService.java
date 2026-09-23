package ru.rutcampustrack.academic.dashboard;

import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

import java.util.Locale;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Service for aggregating dashboard statistics for admin users.
 * Implements DASH-01: totalStudents, totalTeachers, totalGroups, activeGroups, activeSemesterName.
 */
@Service
public class DashboardService {

    private final UserRoleGrantRepository userRoleGrantRepository;
    private final GroupRegistryReadRepository groupRegistryReadRepository;
    private final SemesterRepository semesterRepository;

    public DashboardService(UserRoleGrantRepository userRoleGrantRepository,
                             GroupRegistryReadRepository groupRegistryReadRepository,
                             SemesterRepository semesterRepository) {
        this.userRoleGrantRepository = userRoleGrantRepository;
        this.groupRegistryReadRepository = groupRegistryReadRepository;
        this.semesterRepository = semesterRepository;
    }

    @Transactional(readOnly = true)
    public EntityModel<DashboardStatsResponse> getStats() {
        String activeStatus = RoleGrantStatus.ACTIVE.name().toLowerCase(Locale.ROOT);
        long totalStudents = userRoleGrantRepository.countDistinctUsersByRoleAndStatus(
                UserRole.STUDENT.name().toLowerCase(Locale.ROOT), activeStatus);
        long totalTeachers = userRoleGrantRepository.countDistinctUsersByRoleAndStatus(
                UserRole.TEACHER.name().toLowerCase(Locale.ROOT), activeStatus);
        long activeGroups = groupRegistryReadRepository.countAll(null).activeCount();
        String activeSemesterName = semesterRepository.findByIsActiveTrue()
                .map(s -> s.getName())
                .orElse(null);

        DashboardStatsResponse response = new DashboardStatsResponse();
        response.setTotalStudents(totalStudents);
        response.setTotalTeachers(totalTeachers);
        response.setTotalGroups(activeGroups);
        response.setActiveGroups(activeGroups);
        response.setActiveSemesterName(activeSemesterName);

        response.add(linkTo(methodOn(DashboardController.class).getStats()).withSelfRel());

        return EntityModel.of(response);
    }
}
