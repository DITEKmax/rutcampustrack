package ru.rutcampustrack.academic.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardStatsResponse;

/**
 * REST API contract for admin dashboard statistics.
 */
@Tag(name = "Dashboard", description = "Сводная статистика для администратора")
@RequestMapping("/academic/dashboard")
public interface DashboardApi {

    @Operation(summary = "Сводная статистика системы (ADMIN)", description = "Возвращает количество пользователей, групп, активный семестр и другие ключевые показатели.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статистика получена"),
            @ApiResponse(responseCode = "403", description = "Нет прав доступа")
    })
    @GetMapping("/stats")
    ResponseEntity<EntityModel<DashboardStatsResponse>> getStats();
}
