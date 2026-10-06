package ru.rutcampustrack.schedule.week;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.schedule.contract.api.AcademicWeekPolicyApi;
import ru.rutcampustrack.schedule.contract.dto.week.AcademicWeekPolicyResponse;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.security.RequireRole;

import java.time.LocalDate;

@RestController
public class AcademicWeekPolicyController implements AcademicWeekPolicyApi {
    @Override
    @RequireRole({UserRole.ADMIN, UserRole.TEACHER, UserRole.STUDENT})
    public ResponseEntity<AcademicWeekPolicyResponse> academicWeekPolicy(LocalDate date) {
        return ResponseEntity.ok(AcademicWeekPolicy.forDate(date));
    }
}
