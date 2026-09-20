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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinAck;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.CheckinRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkCompletionRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkCompletionResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.HomeworkResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.MobileProblemDetails;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ScheduleResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.SessionResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.TodayResponse;

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
}
