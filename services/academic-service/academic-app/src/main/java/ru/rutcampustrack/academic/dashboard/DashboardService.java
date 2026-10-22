package ru.rutcampustrack.academic.dashboard;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

/**
 * Service for aggregating dashboard statistics for admin users. Implements DASH-01: totalStudents,
 * totalTeachers, totalGroups, activeGroups, activeSemesterName.
 */
@Service
public class DashboardService {

    private final UserRoleGrantRepository userRoleGrantRepository;
    private final GroupRegistryReadRepository groupRegistryReadRepository;
    private final SemesterRepository semesterRepository;
    private final DashboardScaleObservationService observations;

    public DashboardService(
            UserRoleGrantRepository userRoleGrantRepository,
            GroupRegistryReadRepository groupRegistryReadRepository,
            SemesterRepository semesterRepository) {
        this(userRoleGrantRepository, groupRegistryReadRepository, semesterRepository, null);
    }

    @Autowired
    public DashboardService(
            UserRoleGrantRepository userRoleGrantRepository,
            GroupRegistryReadRepository groupRegistryReadRepository,
            SemesterRepository semesterRepository,
            DashboardScaleObservationService observations) {
        this.userRoleGrantRepository = userRoleGrantRepository;
        this.groupRegistryReadRepository = groupRegistryReadRepository;
        this.semesterRepository = semesterRepository;
        this.observations = observations;
    }

    @Transactional(readOnly = true)
    public EntityModel<DashboardStatsResponse> getStats() {
        var snapshot = observations == null ? null : observations.capture();
        DashboardScaleCounts counts =
                snapshot == null
                        ? DashboardScaleCounts.read(
                                userRoleGrantRepository, groupRegistryReadRepository)
                        : snapshot.counts();
        String activeSemesterName =
                semesterRepository.findByIsActiveTrue().map(s -> s.getName()).orElse(null);

        DashboardStatsResponse response = new DashboardStatsResponse();
        response.setTotalStudents(counts.students());
        response.setTotalTeachers(counts.teachers());
        response.setTotalGroups(counts.groups());
        response.setActiveGroups(counts.groups());
        response.setActiveSemesterName(activeSemesterName);
        if (snapshot != null) response.setDailyGrowth(snapshot.dailyGrowth());

        response.add(linkTo(methodOn(DashboardController.class).getStats()).withSelfRel());

        return EntityModel.of(response);
    }
}
