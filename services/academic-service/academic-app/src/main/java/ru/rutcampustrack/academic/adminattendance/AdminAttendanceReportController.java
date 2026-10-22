package ru.rutcampustrack.academic.adminattendance;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;

import ru.rutcampustrack.academic.contract.api.AdminAttendanceReportApi;
import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels.*;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.security.RequireRole;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class AdminAttendanceReportController implements AdminAttendanceReportApi {
    private final AdminAttendanceReportService reports;
    private final AdminAttendanceExportService exports;

    public AdminAttendanceReportController(
            AdminAttendanceReportService reports, AdminAttendanceExportService exports) {
        this.reports = reports;
        this.exports = exports;
    }

    @Override
    @RequireRole(value = UserRole.ADMIN, admin = "DASHBOARD_VIEW")
    public ResponseEntity<Semesters> semesters() {
        return response(reports.semesters());
    }

    @Override
    @RequireRole(value = UserRole.ADMIN, admin = "DASHBOARD_VIEW")
    public ResponseEntity<Page> read(
            String semesterId, String search, Sort sort, Direction direction, int page, int size) {
        return response(reports.read(semesterId, search, sort, direction, page, size));
    }

    @Override
    @RequireRole(value = UserRole.ADMIN, admin = "DASHBOARD_VIEW")
    public ResponseEntity<List<FormatOption>> formats() {
        return response(exports.formats());
    }

    @Override
    @RequireRole(value = UserRole.ADMIN, admin = "DASHBOARD_VIEW")
    public ResponseEntity<byte[]> generate(Export request, UUID jobId, UUID leaseId) {
        var file = exports.generate(request, jobId, leaseId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header("X-Export-Row-Count", Integer.toString(file.rowCount()))
                .header("X-Export-Requires-Full", "false")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file.content());
    }

    private static <T> ResponseEntity<T> response(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
