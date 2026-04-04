package ru.rutcampustrack.attendance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.security.RequireRole;

/**
 * Minimal health-check endpoint used in SecuritySmokeTest to verify AOP role enforcement.
 */
@RestController
public class HealthCheckController {

    @GetMapping("/attendance/health-check")
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
