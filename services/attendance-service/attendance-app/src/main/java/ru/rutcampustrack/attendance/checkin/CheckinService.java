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
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    private static final Duration CHECKIN_BUFFER = Duration.ofMinutes(5);

    private final CheckinRateLimiter rateLimiter;
    private final GeofenceService geofenceService;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceEventPublisher eventPublisher;
    private final SemesterCacheService semesterCacheService;
    private final RequestContext requestContext;
    private final BusinessMetrics businessMetrics;
    private final Clock clock;

    public CheckinService(CheckinRateLimiter rateLimiter,
                          GeofenceService geofenceService,
                          ScheduleGrpcClient scheduleGrpcClient,
                          AttendanceRepository attendanceRepository,
                          AttendanceEventPublisher eventPublisher,
                          SemesterCacheService semesterCacheService,
                          RequestContext requestContext,
                          BusinessMetrics businessMetrics,
                          Clock clock) {
        this.rateLimiter = rateLimiter;
        this.geofenceService = geofenceService;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.attendanceRepository = attendanceRepository;
        this.eventPublisher = eventPublisher;
        this.semesterCacheService = semesterCacheService;
        this.requestContext = requestContext;
        this.businessMetrics = businessMetrics;
        this.clock = clock;
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
        Instant now = clock.instant();
        String moscowTimestamp = LocalDateTime.ofInstant(now, clock.getZone()).toString();
        LessonResponse lesson = scheduleGrpcClient.getActiveLesson(requestContext.getGroupId(), moscowTimestamp);

        // Step 3: Time window (CHKN-03) — 5 min before start to 5 min after end
        if (!isWithinCheckinWindow(lesson, now)) {
            throw new GeofenceViolationException("Вне временного окна отметки");
        }

        // Step 4: Geo-block (CHKN-04) — teacher has disabled geo-checkin
        if (lesson.getIsGeoBlocked()) {
            throw new GeofenceBlockedException("Геоотметка заблокирована преподавателем");
        }

        // Step 4.5 (v9.0): Headman hard-lock — старост вручную ведёт посещаемость,
        // геоотметка студентов запрещена. Старост сам — исключение (см. ниже).
        if (lesson.getIsBlockedByHeadman() && !requestContext.isHeadman()) {
            throw new GeofenceBlockedException(
                    "Пара заблокирована старостой — посещаемость проставляет староста вручную");
        }

        // Step 5: Geofence (CHKN-01) — student must be on campus.
        // Headman is exempt: staroste отмечается без проверки геолокации
        // (присутствует физически и ведёт пару — ей доверяем).
        if (!requestContext.isHeadman()
                && !geofenceService.isWithinCampus(request.lat(), request.lng())) {
            throw new GeofenceViolationException("Вы находитесь вне зоны кампуса");
        }

        // Step 6: Redis dedup (CHKN-06) — 5-second lock prevents double submission
        if (!rateLimiter.acquireDedup(lesson.getId(), requestContext.getUserId())) {
            throw new ConflictException("Отметка уже зафиксирована");
        }

        // Step 7: Upsert to MongoDB keyed on (lesson_id, user_id).
        // Schema-level compound unique index prevents duplicates (see AttendanceDocument).
        // If an auto-ABSENT doc exists (from lesson.closed), we update it to PRESENT.
        AttendanceDocument existing = attendanceRepository
                .findByLessonIdAndUserId(lesson.getId(), requestContext.getUserId())
                .orElse(null);

        AttendanceDocument doc = existing != null ? existing : AttendanceDocument.builder()
                .lessonId(lesson.getId())
                .userId(requestContext.getUserId())
                .groupId(requestContext.getGroupId())
                .subjectId(lesson.getSubjectId())
                .semesterId(semesterCacheService.getActiveSemesterId())
                .lessonNumber(lesson.getLessonNumber())
                .lessonDate(LocalDate.parse(lesson.getDate()))
                .createdAt(now)
                .build();

        doc.setStatus(AttendanceStatus.PRESENT);
        doc.setSource(AttendanceSource.STUDENT_GEO);
        doc.setMarkedBy(null);
        doc.setUpdatedAt(now);

        AttendanceDocument savedDoc = attendanceRepository.save(doc);

        // Step 8: Publish event (INFRA-06)
        eventPublisher.publishMarked(savedDoc);

        // M04 Группа 8 — attendance.checkin{status=present}. Статус здесь
        // всегда PRESENT (геоотметка); late/absent/excused фиксируются
        // другими путями и не должны инкрементировать counter геоотметки.
        businessMetrics.checkinCounter(savedDoc.getStatus().name().toLowerCase()).increment();

        return savedDoc;
    }

    private boolean isWithinCheckinWindow(LessonResponse lesson, Instant now) {
        LocalDate date = LocalDate.parse(lesson.getDate());
        LocalTime start = LocalTime.parse(lesson.getStartTime());
        LocalTime end = LocalTime.parse(lesson.getEndTime());
        Instant windowOpen = date.atTime(start).atZone(clock.getZone()).toInstant().minus(CHECKIN_BUFFER);
        Instant windowClose = date.atTime(end).atZone(clock.getZone()).toInstant().plus(CHECKIN_BUFFER);
        return !now.isBefore(windowOpen) && !now.isAfter(windowClose);
    }
}
