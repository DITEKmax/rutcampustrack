package ru.rutcampustrack.mobilebff.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.Assignment;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.CatalogResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.DayResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.ExcuseResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.ExportFormatsResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.JournalResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.LessonResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.SearchResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.SemesterResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.StatsResponse;

/** Additive read-only teacher PWA/TMA HTTP boundary. */
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/teacher")
public interface TeacherApi {
    @Operation(summary = "Search current authorised teacher groups and students")
    @GetMapping("/search")
    ResponseEntity<SearchResponse> search(@RequestParam String semesterId, @RequestParam String q);

    @Operation(summary = "Teacher active semester bootstrap")
    @GetMapping("/semester")
    ResponseEntity<SemesterResponse> semester();

    @Operation(summary = "Current teacher groups and scheduled subject catalog")
    @GetMapping("/catalog")
    ResponseEntity<CatalogResponse> catalog(@RequestParam String semesterId);

    @Operation(summary = "Teacher assignments in a dated semester range")
    @GetMapping("/assignments")
    ResponseEntity<List<Assignment>> assignments(
            @RequestParam String semesterId,
            @RequestParam String dateFrom,
            @RequestParam String dateTo);

    @Operation(summary = "Teacher cross-group day schedule")
    @GetMapping("/day")
    ResponseEntity<DayResponse> day(@RequestParam String semesterId, @RequestParam String date);

    @Operation(summary = "Concrete lesson read-only roster")
    @GetMapping("/lessons/{lessonId}")
    ResponseEntity<LessonResponse> lesson(@PathVariable String lessonId);

    @Operation(summary = "Concrete lesson journal")
    @GetMapping("/journal")
    ResponseEntity<JournalResponse> journal(
            @RequestParam String semesterId,
            @RequestParam String groupId,
            @RequestParam String subjectId,
            @RequestParam(name = "lessonType") List<String> lessonTypes,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "100") String pageSize,
            @RequestParam(defaultValue = "false") boolean pastOnly,
            @RequestParam(defaultValue = "false") boolean includeMetrics,
            @RequestParam(required = false) String metricsStudentId);

    /** Source compatibility for existing Java callers; HTTP defaults remain unchanged. */
    default ResponseEntity<JournalResponse> journal(
            String semesterId,
            String groupId,
            String subjectId,
            List<String> lessonTypes,
            String dateFrom,
            String dateTo,
            String page,
            String pageSize,
            boolean pastOnly) {
        return journal(
                semesterId,
                groupId,
                subjectId,
                lessonTypes,
                dateFrom,
                dateTo,
                page,
                pageSize,
                pastOnly,
                false,
                null);
    }

    @Operation(summary = "Server-supported teacher journal export formats")
    @GetMapping("/journal/export/formats")
    ResponseEntity<ExportFormatsResponse> journalExportFormats();

    @Operation(summary = "Download a server-generated complete teacher journal")
    @GetMapping(value = "/journal/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<byte[]> exportJournal(
            @RequestParam String semesterId,
            @RequestParam String groupId,
            @RequestParam String subjectId,
            @RequestParam(name = "lessonType") List<String> lessonTypes,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam String format,
            @RequestParam(defaultValue = "false") boolean pastOnly,
            @RequestParam(required = false) String lessonId);

    @Operation(summary = "Teacher server-side attendance statistics")
    @GetMapping("/stats")
    ResponseEntity<StatsResponse> stats(
            @RequestParam String semesterId,
            @RequestParam String scope,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false, name = "lessonType") List<String> lessonTypes,
            @RequestParam(required = false, name = "sort") List<String> sorts,
            @RequestParam(required = false, name = "filter") List<String> filters);

    @Operation(summary = "Server-supported teacher statistics export formats")
    @GetMapping("/stats/export/formats")
    ResponseEntity<ExportFormatsResponse> statsExportFormats();

    @Operation(summary = "Download a server-generated teacher statistics summary")
    @GetMapping(value = "/stats/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<byte[]> exportStats(
            @RequestParam String semesterId,
            @RequestParam String scope,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false, name = "lessonType") List<String> lessonTypes,
            @RequestParam(required = false, name = "sort") List<String> sorts,
            @RequestParam(required = false, name = "filter") List<String> filters,
            @RequestParam String format);

    @Operation(summary = "Server-scoped excuse detail")
    @GetMapping("/excuses/{requestId}")
    ResponseEntity<ExcuseResponse> excuse(@PathVariable String requestId);

    @Operation(summary = "Server-scoped excuse attachment")
    @GetMapping(
            value = "/excuses/{requestId}/attachments/{attachmentId}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<byte[]> excuseAttachment(
            @PathVariable String requestId, @PathVariable String attachmentId);
}
