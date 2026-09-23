package ru.rutcampustrack.mobilebff.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.Assignment;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.DayResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.ExcuseResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.JournalResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.LessonResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.SemesterResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.StatsResponse;

import java.util.List;

/** Additive read-only teacher PWA/TMA HTTP boundary. */
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/teacher")
public interface TeacherApi {

    @Operation(summary = "Teacher active semester bootstrap")
    @GetMapping("/semester")
    ResponseEntity<SemesterResponse> semester();

    @Operation(summary = "Teacher assignments in a dated semester range")
    @GetMapping("/assignments")
    ResponseEntity<List<Assignment>> assignments(
            @RequestParam String semesterId,
            @RequestParam String dateFrom,
            @RequestParam String dateTo
    );

    @Operation(summary = "Teacher cross-group day schedule")
    @GetMapping("/day")
    ResponseEntity<DayResponse> day(
            @RequestParam String semesterId,
            @RequestParam String date
    );

    @Operation(summary = "Concrete lesson read-only roster")
    @GetMapping("/lessons/{lessonId}")
    ResponseEntity<LessonResponse> lesson(@PathVariable String lessonId);

    @Operation(summary = "Concrete lesson journal")
    @GetMapping("/journal")
    ResponseEntity<JournalResponse> journal(
            @RequestParam String semesterId,
            @RequestParam String groupId,
            @RequestParam String subjectId,
            @RequestParam String lessonType,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "100") String pageSize
    );

    @Operation(summary = "Teacher server-side attendance statistics")
    @GetMapping("/stats")
    ResponseEntity<StatsResponse> stats(
            @RequestParam String semesterId,
            @RequestParam String scope,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false, name = "lessonType") List<String> lessonTypes,
            @RequestParam(required = false, name = "sort") List<String> sorts,
            @RequestParam(required = false, name = "filter") List<String> filters
    );

    @Operation(summary = "Server-scoped excuse detail")
    @GetMapping("/excuses/{requestId}")
    ResponseEntity<ExcuseResponse> excuse(@PathVariable String requestId);

    @Operation(summary = "Server-scoped excuse attachment")
    @GetMapping(value = "/excuses/{requestId}/attachments/{attachmentId}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<byte[]> excuseAttachment(@PathVariable String requestId,
                                             @PathVariable String attachmentId);
}
