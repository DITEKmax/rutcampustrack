package ru.rutcampustrack.attendance.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.attendance.contract.dto.report.AttendanceRecordEntry;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyWeeksResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsExportRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsQueryRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentDetailResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendQueryRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendExportRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse;
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
    @Operation(summary = "List own-group composition export formats for the current headman")
    @GetMapping("/headman/group-composition/formats")
    ResponseEntity<ru.rutcampustrack.attendance.contract.dto.report.HeadmanGroupCompositionFormatsResponse>
            getHeadmanGroupCompositionFormats();

    @Operation(summary = "Export the current authoritative composition of the headman's own group")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roster file; PNG is a ZIP of pages",
                    content = {
                            @Content(mediaType = DOCX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = PDF_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = ZIP_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = HTML_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = XLSX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary"))}),
            @ApiResponse(responseCode = "400", description = "Unknown export format"),
            @ApiResponse(responseCode = "403", description = "Current own-group HEADMAN required; assistants are denied"),
            @ApiResponse(responseCode = "413", description = "Roster or rendered file exceeds the export limit"),
            @ApiResponse(responseCode = "503", description = "Academic or document renderer is unavailable")
    })
    @GetMapping("/headman/group-composition/export")
    ResponseEntity<byte[]> exportHeadmanGroupComposition(@RequestParam String format);


    String DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    String PDF_MEDIA_TYPE = "application/pdf";
    String PNG_MEDIA_TYPE = "image/png";
    String ZIP_MEDIA_TYPE = "application/zip";
    String HTML_MEDIA_TYPE = "text/html; charset=UTF-8";
    String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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

    @Operation(summary = "Weeks of the active semester available for headman weekly report export")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Weeks retrieved"),
            @ApiResponse(responseCode = "403", description = "A headman or assistant with current VIEW_STATS can export the journal",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Academic service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/headman-weekly/weeks")
    ResponseEntity<EntityModel<HeadmanWeeklyWeeksResponse>> getHeadmanWeeklyWeeks();

    @Operation(summary = "Export one headman weekly attendance journal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report file",
                    content = {
                            @Content(mediaType = DOCX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = PDF_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = ZIP_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = HTML_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = XLSX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary"))
                    }),
            @ApiResponse(responseCode = "400", description = "Unknown export format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "A headman or assistant with current VIEW_STATS can export the journal",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "The selected export exceeds a bounded document or transport limit",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Week is outside the available active-semester range",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Renderer or upstream service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/headman-weekly/current")
    ResponseEntity<byte[]> exportHeadmanWeeklyCurrent(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam String format);

    @Operation(summary = "Export selected headman weekly attendance journals")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report file",
                    content = {
                            @Content(mediaType = DOCX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = PDF_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = ZIP_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = HTML_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = XLSX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary"))
                    }),
            @ApiResponse(responseCode = "400", description = "Unknown export format or invalid request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "A headman or assistant with current VIEW_STATS can export the journal",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "The selected export exceeds a bounded document or transport limit",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Selected weeks are outside the available active-semester range",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Renderer or upstream service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/headman-weekly/export")
    ResponseEntity<byte[]> exportHeadmanWeekly(@Valid @RequestBody HeadmanWeeklyExportRequest request);

    @Operation(summary = "Query current-group semester attendance statistics for the headman")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Filtered student statistics page"),
            @ApiResponse(responseCode = "400", description = "Invalid statistics query",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Headman or current VIEW_STATS permission required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Academic or schedule data is unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/headman/stats/query")
    ResponseEntity<EntityModel<HeadmanStatsResponse>> queryHeadmanStats(
            @Valid @RequestBody HeadmanStatsQueryRequest request);

    @Operation(summary = "Query current-group semester attendance trend for the headman")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Two attendance series on one time grid"),
            @ApiResponse(responseCode = "400", description = "Invalid trend mode or range",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Headman or current VIEW_STATS permission required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Academic or schedule data is unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/headman/stats/trend")
    ResponseEntity<EntityModel<HeadmanStatsTrendResponse>> queryHeadmanStatsTrend(
            @Valid @RequestBody HeadmanStatsTrendQueryRequest request);

    @Operation(summary = "Export the current-group headman attendance trend as PNG or self-contained HTML")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rendered trend chart",
                    content = {
                            @Content(mediaType = PNG_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = HTML_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary"))
                    }),
            @ApiResponse(responseCode = "400", description = "Invalid trend selector or export format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Headman or current VIEW_STATS permission required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "The rendered export exceeds 20 MiB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/headman/stats/trend/export")
    ResponseEntity<byte[]> exportHeadmanStatsTrend(@Valid @RequestBody HeadmanStatsTrendExportRequest request);

    @Operation(summary = "Read one current-group student's headman statistics detail")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Student detail and paged request decisions"),
            @ApiResponse(responseCode = "400", description = "Invalid student identity or ticket page",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Headman or current VIEW_STATS permission required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Student is outside the current group's history",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Academic or schedule data is unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/headman/stats/students/{studentId}/detail")
    ResponseEntity<EntityModel<HeadmanStatsStudentDetailResponse>> queryHeadmanStudentStatsDetail(
            @PathVariable Long studentId,
            @RequestParam(name = "latePage", defaultValue = "0") int latePage,
            @RequestParam(name = "excusePage", defaultValue = "0") int excusePage,
            @RequestParam(name = "size", defaultValue = "20") int size);

    @Operation(summary = "Export the complete filtered current-group semester statistics block")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report file",
                    content = {
                            @Content(mediaType = DOCX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = PDF_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = ZIP_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = HTML_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = XLSX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary"))
                    }),
            @ApiResponse(responseCode = "400", description = "Unknown format or invalid statistics query",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Headman or current VIEW_STATS permission required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "The selected export exceeds a bounded document or transport limit",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Renderer or upstream service unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/headman/stats/export")
    ResponseEntity<byte[]> exportHeadmanStats(@Valid @RequestBody HeadmanStatsExportRequest request);
}
