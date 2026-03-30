package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;

@Tag(name = "Dashboard", description = "Статистика для администратора")
@RequestMapping("/academic/dashboard")
public interface DashboardApi {

    @Operation(summary = "Сводная статистика (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/stats")
    ResponseEntity<EntityModel<DashboardStatsResponse>> getStats();
}
