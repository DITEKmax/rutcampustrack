package ru.rutcampustrack.mobilebff.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinAck;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkCompletionRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkCompletionResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.MobileProblemDetails;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ScheduleResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.SessionResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.StudentAttendanceResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.StudentStatisticsResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.StudentStatisticsSubjectDetailResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.TodayResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Bucket;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Detail;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ExcuseRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.LateCheckinRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Options;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Page;

import java.util.List;

@Tag(name = "Student mobile", description = "PWA/TMA student read model and geo check-in")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/student")
public interface StudentApi {

    @Operation(summary = "Current student session projection")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session projection",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SessionResponse.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "403", description = "Active role is not STUDENT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Mandatory dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/session")
    ResponseEntity<SessionResponse> getSession();

    @Operation(summary = "Today projection with current or nearest lesson")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Live Today projection",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TodayResponse.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role or group scope",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Mandatory dependency unavailable; eligibility fails closed",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/today")
    ResponseEntity<TodayResponse> getToday();

    @Operation(summary = "Server-scoped semester schedule for PWA offline reads")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Materialized lessons in one authorized semester",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ScheduleResponse.class)),
                    headers = {
                            @Header(name = "Cache-Control", description = "private, no-cache"),
                            @Header(name = "ETag", description = "Authorized stable representation validator")
                    }),
            @ApiResponse(responseCode = "304", description = "Authorized representation unchanged"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "403", description = "Semester is outside the student's group scope",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Mandatory dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/schedule")
    ResponseEntity<ScheduleResponse> getSchedule(
            @RequestParam
            @Pattern(regexp = "^[1-9][0-9]*$")
            String semesterId,
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch
    );

    @Operation(summary = "Student attendance projection")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Server-calculated attendance projection",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = StudentAttendanceResponse.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "403", description = "Semester or student scope is unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Mandatory dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/attendance")
    ResponseEntity<StudentAttendanceResponse> getAttendance(
            @RequestParam @Pattern(regexp = "^[1-9][0-9]*$") String semesterId
    );

    @Operation(summary = "Student attendance statistics overview")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Server-calculated semester statistics",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = StudentStatisticsResponse.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "403", description = "Semester or student scope is unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Mandatory dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/statistics")
    ResponseEntity<StudentStatisticsResponse> getStatistics(
            @RequestParam @Pattern(regexp = "^[1-9][0-9]*$") String semesterId
    );

    @Operation(summary = "Student statistics for one subject")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Server-calculated subject statistics",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = StudentStatisticsSubjectDetailResponse.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "400", description = "Invalid range or lesson type filter",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "403", description = "Subject or semester is outside signed scope",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Mandatory dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/statistics/subjects/{subjectId}")
    ResponseEntity<StudentStatisticsSubjectDetailResponse> getStatisticsSubject(
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") String subjectId,
            @RequestParam @Pattern(regexp = "^[1-9][0-9]*$") String semesterId,
            @RequestParam(defaultValue = "weeks") @Pattern(regexp = "^(days|weeks)$") String range,
            @RequestParam(name = "types", required = false) List<String> types
    );

    @Operation(
            summary = "Student homework feed",
            description = "Active-semester homework for the authenticated student's own group. "
                    + "Missing bounds default to Moscow today through semester end."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Homework feed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HomeworkResponse.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "400", description = "Invalid or out-of-semester date range",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "403", description = "Wrong role or group scope",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "404", description = "Homework scope was not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "503", description = "Academic dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store"))
    })
    @GetMapping("/homework")
    ResponseEntity<HomeworkResponse> getHomework(
            @RequestParam(name = "from", required = false)
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String from,
            @RequestParam(name = "to", required = false)
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String to
    );

    @Operation(
            summary = "Set homework completion state",
            description = "Desired-state mutation for the authenticated student; repeated requests are safe."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completion state",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HomeworkCompletionResponse.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "403", description = "Wrong role, group or semester scope",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "404", description = "Homework not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "503", description = "Academic dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store"))
    })
    @PutMapping("/homework/{id}/completion")
    ResponseEntity<HomeworkCompletionResponse> setHomeworkCompletion(
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") String id,
            @Valid @RequestBody HomeworkCompletionRequest request
    );

    @Operation(
            summary = "Attempt student geo check-in",
            description = "The attendance domain owns the window, geofence, cooldown, idempotency and automatic request."
    )
    @Parameter(
            name = "Idempotency-Key",
            in = ParameterIn.HEADER,
            required = true,
            description = "Opaque 16-128 ASCII character command key"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PRESENT or atomically persisted PENDING_CONFIRMATION",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CheckinAck.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or idempotency key",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role or group scope",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "404", description = "Lesson not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "409", description = "Manual absence, ineligible state or idempotency payload mismatch",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "429", description = "Durable 300-second pair cooldown",
                    headers = @Header(name = "Retry-After", description = "Whole seconds until retry"),
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Mandatory dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @PostMapping("/lessons/{lessonId}/checkin")
    ResponseEntity<CheckinAck> checkin(
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") String lessonId,
            @RequestHeader("Idempotency-Key")
            @Size(min = 16, max = 128)
            @Pattern(regexp = "^[\\x21-\\x7E]+$")
            String idempotencyKey,
            @Valid @RequestBody CheckinRequest request
    );

    @Operation(summary = "List the authenticated student's requests")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request page",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Page.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "400", description = "Invalid page parameters",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role or group scope",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/requests")
    ResponseEntity<Page> listRequests(
            @RequestParam(name = "bucket", defaultValue = "OPEN") Bucket bucket,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    );

    @Operation(summary = "Get request submission options")
    @ApiResponse(responseCode = "200", description = "Request options",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = Options.class)),
            headers = @Header(name = "Cache-Control", description = "Always no-store"))
    @GetMapping("/requests/options")
    ResponseEntity<Options> requestOptions();

    @Operation(summary = "Get one of the authenticated student's requests")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request detail",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Detail.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "400", description = "Invalid request id",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "404", description = "Request not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/requests/{id}")
    ResponseEntity<Detail> getRequest(@PathVariable @Pattern(regexp = "^[0-9a-fA-F]{24}$") String id);

    @Operation(
            summary = "Submit a student excuse request",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Created or replayed request",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Detail.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "400", description = "Invalid request, file or key",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "409", description = "Request conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "413", description = "Payload too large",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @PostMapping(value = "/requests/excuse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Detail> submitExcuse(
            @RequestHeader("Idempotency-Key")
            @Size(min = 16, max = 128)
            @Pattern(regexp = "^[\\x21-\\x7E]+$") String idempotencyKey,
            @Valid @RequestPart("request") ExcuseRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    );

    @Operation(summary = "Submit a manual late-checkin request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Created or replayed request",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Detail.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "400", description = "Invalid request or key",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "409", description = "Request conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @PostMapping("/requests/late-checkin")
    ResponseEntity<Detail> submitLateCheckin(
            @RequestHeader("Idempotency-Key")
            @Size(min = 16, max = 128)
            @Pattern(regexp = "^[\\x21-\\x7E]+$") String idempotencyKey,
            @Valid @RequestBody LateCheckinRequest request
    );

    @Operation(summary = "Cancel a pending student request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled or replayed request",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Detail.class)),
                    headers = @Header(name = "Cache-Control", description = "Always no-store")),
            @ApiResponse(responseCode = "404", description = "Request not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "409", description = "Request is already decided",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @PostMapping("/requests/{id}/cancel")
    ResponseEntity<Detail> cancelRequest(@PathVariable @Pattern(regexp = "^[0-9a-fA-F]{24}$") String id);

    @Operation(summary = "Download a request attachment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Attachment bytes",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
            @ApiResponse(responseCode = "403", description = "Attachment is outside the student's scope",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "404", description = "Attachment not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class))),
            @ApiResponse(responseCode = "410", description = "Attachment expired",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = MobileProblemDetails.class)))
    })
    @GetMapping("/requests/{id}/attachments/{attachmentId}")
    ResponseEntity<byte[]> downloadRequestAttachment(
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F]{24}$") String id,
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F]{24}$") String attachmentId
    );
}
