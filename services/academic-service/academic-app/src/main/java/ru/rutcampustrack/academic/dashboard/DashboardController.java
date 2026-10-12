package ru.rutcampustrack.academic.dashboard;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.DashboardApi;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;
import ru.rutcampustrack.academic.security.RequireRole;

/**
 * REST controller implementing DashboardApi contract. Provides admin-only aggregate statistics
 * endpoint (DASH-01).
 */
@RestController
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;
    private final DashboardActivityService activityService;

    public DashboardController(
            DashboardService dashboardService, DashboardActivityService activityService) {
        this.dashboardService = dashboardService;
        this.activityService = activityService;
    }

    @Override
    @RequireRole(
            value = {ADMIN},
            admin = {"DASHBOARD_VIEW"})
    public ResponseEntity<EntityModel<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }

    @Override
    @RequireRole(
            value = {ADMIN},
            admin = {"DASHBOARD_VIEW"})
    public ResponseEntity<DashboardActivityResponse> getActivity(String period, String bucket) {
        return ResponseEntity.ok(activityService.read(period, bucket));
    }
}
