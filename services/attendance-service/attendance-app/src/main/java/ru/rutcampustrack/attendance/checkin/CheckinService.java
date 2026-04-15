package ru.rutcampustrack.attendance.checkin;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.exception.GeofenceBlockedException;
import ru.rutcampustrack.attendance.exception.GeofenceViolationException;
import ru.rutcampustrack.attendance.exception.RateLimitException;
import ru.rutcampustrack.attendance.geofence.GeofenceService;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.ratelimit.CheckinRateLimiter;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Orchestrates the geo-checkin write path.
 * Execution order per CHKN requirements:
 * 1. Rate limit (CHKN-07)
 * 2. Active lesson resolution (CHKN-02)
 * 3. Time window validation (CHKN-03)
 * 4. Geo-block check (CHKN-04)
 * 5. Geofence validation (CHKN-01)
 * 6. Redis dedup (CHKN-06)
 * 7. MongoDB save (CHKN-05)
 * 8. Event publish (INFRA-06)
 */
@Service
public class CheckinService {

    private static final ZoneId SERVER_ZONE = ZoneId.of("Europe/Moscow");
    private static final Duration CHECKIN_BUFFER = Duration.ofMinutes(5);

    private final CheckinRateLimiter rateLimiter;
    private final GeofenceService geofenceService;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceEventPublisher eventPublisher;
    private final SemesterCacheService semesterCacheService;
    private final RequestContext requestContext;

    public CheckinService(CheckinRateLimiter rateLimiter,
                          GeofenceService geofenceService,
                          ScheduleGrpcClient scheduleGrpcClient,
                          AttendanceRepository attendanceRepository,
                          AttendanceEventPublisher eventPublisher,
                          SemesterCacheService semesterCacheService,
                          RequestContext requestContext) {
        this.rateLimiter = rateLimiter;
        this.geofenceService = geofenceService;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.attendanceRepository = attendanceRepository;
        this.eventPublisher = eventPublisher;
        this.semesterCacheService = semesterCacheService;
        this.requestContext = requestContext;
    }

    /**
     * Performs geo-checkin for the authenticated student.
     *
     * @param request DTO containing lat/lng coordinates
     * @return saved AttendanceDocument with status=PRESENT and source=STUDENT_GEO
     * @throws RateLimitException          if more than 3 attempts in 60 seconds (CHKN-07)
     * @throws ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException if no active lesson (CHKN-02)
     * @throws GeofenceViolationException  if outside time window (CHKN-03) or outside campus (CHKN-01)
     * @throws GeofenceBlockedException   if teacher disabled geo-checkin for this lesson (CHKN-04)
     * @throws ConflictException           if Redis dedup lock already held (CHKN-06)
     */
    public AttendanceDocument checkin(CheckinRequest request) {
        // Step 1: Rate limit (CHKN-07) — must be FIRST check
        if (!rateLimiter.checkRateLimit(requestContext.getUserId())) {
            throw new RateLimitException("Превышен лимит запросов: максимум 3 попытки в минуту");
        }

        // Step 2: Active lesson (CHKN-02) — ResourceNotFoundException propagates as 404
        Instant now = Instant.now();
        String moscowTimestamp = LocalDateTime.ofInstant(now, SERVER_ZONE).toString();
        LessonResponse lesson = scheduleGrpcClient.getActiveLesson(requestContext.getGroupId(), moscowTimestamp);

        // Step 3: Time window (CHKN-03) — 5 min before start to 5 min after end
        if (!isWithinCheckinWindow(lesson, now)) {
            throw new GeofenceViolationException("Вне временного окна отметки");
        }

        // Step 4: Geo-block (CHKN-04) — teacher has disabled geo-checkin
        if (lesson.getIsGeoBlocked()) {
            throw new GeofenceBlockedException("Геоотметка заблокирована преподавателем");
        }

        // Step 5: Geofence (CHKN-01) — student must be on campus
        if (!geofenceService.isWithinCampus(request.lat(), request.lng())) {
            throw new GeofenceViolationException("Вы находитесь вне зоны кампуса");
        }

        // Step 6: Redis dedup (CHKN-06) — 5-second lock prevents double submission
        if (!rateLimiter.acquireDedup(lesson.getId(), requestContext.getUserId())) {
            throw new ConflictException("Отметка уже зафиксирована");
        }

        // Step 7: Save to MongoDB (CHKN-05) — DuplicateKeyException propagates to GlobalExceptionHandler -> 409
        AttendanceDocument doc = AttendanceDocument.builder()
                .lessonId(lesson.getId())
                .userId(requestContext.getUserId())
                .groupId(requestContext.getGroupId())
                .subjectId(lesson.getSubjectId())
                .semesterId(semesterCacheService.getActiveSemesterId())
                .lessonNumber(lesson.getLessonNumber())
                .lessonDate(LocalDate.parse(lesson.getDate()))
                .status(AttendanceStatus.PRESENT)
                .source(AttendanceSource.STUDENT_GEO)
                .markedBy(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        AttendanceDocument savedDoc = attendanceRepository.save(doc);

        // Step 8: Publish event (INFRA-06)
        eventPublisher.publishMarked(savedDoc);

        return savedDoc;
    }

    private boolean isWithinCheckinWindow(LessonResponse lesson, Instant now) {
        LocalDate date = LocalDate.parse(lesson.getDate());
        LocalTime start = LocalTime.parse(lesson.getStartTime());
        LocalTime end = LocalTime.parse(lesson.getEndTime());
        Instant windowOpen = date.atTime(start).atZone(SERVER_ZONE).toInstant().minus(CHECKIN_BUFFER);
        Instant windowClose = date.atTime(end).atZone(SERVER_ZONE).toInstant().plus(CHECKIN_BUFFER);
        return !now.isBefore(windowOpen) && !now.isAfter(windowClose);
    }
}
