package ru.rutcampustrack.academic.dashboard;

import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Service for aggregating dashboard statistics for admin users.
 * Implements DASH-01: totalStudents, totalTeachers, totalGroups, activeGroups, activeSemesterName.
 */
@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;

    public DashboardService(UserRepository userRepository,
                             GroupRepository groupRepository,
                             SemesterRepository semesterRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.semesterRepository = semesterRepository;
    }

    @Transactional(readOnly = true)
    public EntityModel<DashboardStatsResponse> getStats() {
        long totalStudents = userRepository.countByRole(UserRole.STUDENT.name().toLowerCase());
        long totalTeachers = userRepository.countByRole(UserRole.TEACHER.name().toLowerCase());
        long totalGroups = groupRepository.count();
        long activeGroups = groupRepository.countByIsActive(true);
        String activeSemesterName = semesterRepository.findByIsActiveTrue()
                .map(s -> s.getName())
                .orElse(null);

        DashboardStatsResponse response = new DashboardStatsResponse();
        response.setTotalStudents(totalStudents);
        response.setTotalTeachers(totalTeachers);
        response.setTotalGroups(totalGroups);
        response.setActiveGroups(activeGroups);
        response.setActiveSemesterName(activeSemesterName);

        response.add(linkTo(methodOn(DashboardController.class).getStats()).withSelfRel());

        return EntityModel.of(response);
    }
}
