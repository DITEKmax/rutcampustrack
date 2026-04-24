package ru.rutcampustrack.attendance.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.attendance.contract.dto.report.AttendanceRecordEntry;
import ru.rutcampustrack.attendance.contract.dto.report.JournalResponse;
import ru.rutcampustrack.attendance.contract.dto.report.LessonAttendanceResponse;
import ru.rutcampustrack.attendance.contract.dto.report.StudentDashboardResponse;
import ru.rutcampustrack.attendance.contract.dto.report.StudentStatsResponse;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.time.LocalDate;

/**
 * Contract interface for attendance report API (D-01, D-02).
 * Mappings declared here — controller implements this interface.
 */
@Tag(name = "Reports", description = "Attendance reports")
@RequestMapping("/attendance/reports")
public interface ReportApi {

    @Operation(summary = "Lesson attendance list")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Attendance list retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Lesson not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/lesson/{lessonId}")
    ResponseEntity<EntityModel<LessonAttendanceResponse>> getLessonAttendance(@PathVariable Long lessonId);

    @Operation(summary = "Journal grid (students x dates)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Journal retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/journal")
    ResponseEntity<EntityModel<JournalResponse>> getJournal(
            @RequestParam Long groupId,
            @RequestParam Long subjectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo);

    @Operation(summary = "Student attendance stats")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stats retrieved")
    })
    @GetMapping("/student/stats")
    ResponseEntity<EntityModel<StudentStatsResponse>> getStudentStats();

    @Operation(summary = "Student attendance records")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Records retrieved")
    })
    @GetMapping("/student/records")
    ResponseEntity<CollectionModel<EntityModel<AttendanceRecordEntry>>> getStudentRecords(
            @RequestParam(required = false) Long subjectId);

    @Operation(summary = "Aggregated dashboard for the student PWA/Mini-App home screen (v9.0): overall %, donut breakdown, weekly timeseries, top missed subjects")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard retrieved")
    })
    @GetMapping("/student/dashboard")
    ResponseEntity<EntityModel<StudentDashboardResponse>> getStudentDashboard(
            @RequestParam(required = false, defaultValue = "5") Integer topLimit);
}
