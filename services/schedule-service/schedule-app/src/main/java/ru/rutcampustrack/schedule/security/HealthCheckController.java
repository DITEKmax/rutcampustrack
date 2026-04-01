package ru.rutcampustrack.schedule.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.schedule.contract.enums.UserRole;

import java.util.Map;

/**
 * Placeholder endpoint for Phase 10 security smoke test.
 * Will be superseded by real schedule endpoints in Phase 11.
 */
@RestController
public class HealthCheckController {

    @RequireRole({UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT})
    @GetMapping("/schedule/health-check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
