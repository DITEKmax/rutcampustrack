package ru.rutcampustrack.schedule.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.schedule.contract.dto.week.AcademicWeekPolicyResponse;

import java.time.LocalDate;

public interface AcademicWeekPolicyApi {
    @Operation(summary = "Получить учебную неделю и каталог типов недель для выбранной даты")
    @GetMapping("/schedule/academic-week-policy")
    ResponseEntity<AcademicWeekPolicyResponse> academicWeekPolicy(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);
}
