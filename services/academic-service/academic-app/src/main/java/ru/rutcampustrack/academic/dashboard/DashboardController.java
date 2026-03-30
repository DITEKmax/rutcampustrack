package ru.rutcampustrack.academic.dashboard;

import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.DashboardApi;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;
import ru.rutcampustrack.academic.security.RequireRole;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;

/**
 * REST controller implementing DashboardApi contract.
 * Provides admin-only aggregate statistics endpoint (DASH-01).
 */
@RestController
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}
