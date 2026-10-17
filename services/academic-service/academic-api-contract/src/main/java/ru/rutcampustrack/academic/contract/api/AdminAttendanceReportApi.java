package ru.rutcampustrack.academic.contract.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels.*;

import java.util.List;
import java.util.UUID;

/** The D026 report has its own read surface; registry permissions are unrelated. */
@RequestMapping("/academic/admin/attendance")
public interface AdminAttendanceReportApi {
    @GetMapping("/semesters")
    ResponseEntity<Semesters> semesters();

    @GetMapping
    ResponseEntity<Page> read(
            @RequestParam String semesterId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "GROUP_NAME") Sort sort,
            @RequestParam(defaultValue = "ASC") Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size);

    @GetMapping("/formats")
    ResponseEntity<List<FormatOption>> formats();

    @PostMapping("/export")
    ResponseEntity<byte[]> generate(
            @Valid @RequestBody Export request,
            @RequestHeader("X-Export-Job-Id") UUID jobId,
            @RequestHeader("X-Export-Job-Lease") UUID leaseId);
}
