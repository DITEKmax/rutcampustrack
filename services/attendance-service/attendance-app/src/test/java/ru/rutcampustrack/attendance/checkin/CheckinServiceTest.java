package ru.rutcampustrack.attendance.checkin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.exception.GeofenceBlockedException;
import ru.rutcampustrack.attendance.exception.GeofenceViolationException;
import ru.rutcampustrack.attendance.exception.RateLimitException;
import ru.rutcampustrack.attendance.geofence.GeofenceService;
import ru.rutcampustrack.attendance.ratelimit.CheckinRateLimiter;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    @Mock
    private CheckinRateLimiter rateLimiter;

    @Mock
    private GeofenceService geofenceService;

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceEventPublisher eventPublisher;

    @Mock
    private SemesterCacheService semesterCacheService;

    @Mock
    private RequestContext requestContext;

    @InjectMocks
    private CheckinService checkinService;

    private LessonResponse mockLesson;
    private CheckinRequest validRequest;

    @BeforeEach
    void setUp() {
        // Build a valid lesson for today with a time window that covers "now"
        // We use a very wide window: 00:00 - 23:59
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        mockLesson = LessonResponse.newBuilder()
                .setId(1L)
                .setGroupId(10L)
                .setSubjectId(5L)
                .setDate(today)
                .setStartTime("00:00")
                .setEndTime("23:59")
                .setIsGeoBlocked(false)
                .setLessonNumber(1)
                .setStatus("active")
                .build();

        validRequest = new CheckinRequest(55.7558, 37.6173);

        // Lenient defaults for happy path — individual tests override as needed
        lenient().when(requestContext.getUserId()).thenReturn(100L);
        lenient().when(requestContext.getGroupId()).thenReturn(10L);
        lenient().when(rateLimiter.checkRateLimit(anyLong())).thenReturn(true);
        lenient().when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(mockLesson);
        lenient().when(geofenceService.isWithinCampus(anyDouble(), anyDouble())).thenReturn(true);
        lenient().when(rateLimiter.acquireDedup(anyLong(), anyLong())).thenReturn(true);
        lenient().when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);
        lenient().when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // Test 1: Happy path — all checks pass
    // -------------------------------------------------------------------------

    @Test
    void checkin_allChecksPass_savesAndPublishes() {
        AttendanceDocument result = checkinService.checkin(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.getSource()).isEqualTo(AttendanceSource.STUDENT_GEO);
        assertThat(result.getLessonId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getGroupId()).isEqualTo(10L);
        assertThat(result.getMarkedBy()).isNull();

        verify(attendanceRepository).save(any());
        verify(eventPublisher).publishMarked(any());
    }

    // -------------------------------------------------------------------------
    // Test 2: Rate limit exceeded — save must NOT be called
    // -------------------------------------------------------------------------

    @Test
    void checkin_rateLimitExceeded_throwsRateLimitException() {
        when(rateLimiter.checkRateLimit(100L)).thenReturn(false);

        assertThatThrownBy(() -> checkinService.checkin(validRequest))
                .isInstanceOf(RateLimitException.class);

        verify(attendanceRepository, never()).save(any());
        verify(eventPublisher, never()).publishMarked(any());
    }

    // -------------------------------------------------------------------------
    // Test 3: Lesson is geo-blocked — throws GeofenceBlockedException
    // -------------------------------------------------------------------------

    @Test
    void checkin_lessonGeoBlocked_throwsGeofenceBlockedException() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        LessonResponse blockedLesson = LessonResponse.newBuilder()
                .setId(1L)
                .setGroupId(10L)
                .setSubjectId(5L)
                .setDate(today)
                .setStartTime("00:00")
                .setEndTime("23:59")
                .setIsGeoBlocked(true)
                .setLessonNumber(1)
                .setStatus("active")
                .build();
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(blockedLesson);

        assertThatThrownBy(() -> checkinService.checkin(validRequest))
                .isInstanceOf(GeofenceBlockedException.class);

        verify(attendanceRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Test 4: Outside geofence — throws GeofenceViolationException
    // -------------------------------------------------------------------------

    @Test
    void checkin_outsideGeofence_throwsGeofenceViolationException() {
        when(geofenceService.isWithinCampus(anyDouble(), anyDouble())).thenReturn(false);

        assertThatThrownBy(() -> checkinService.checkin(validRequest))
                .isInstanceOf(GeofenceViolationException.class)
                .hasMessageContaining("кампуса");

        verify(attendanceRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Test 5: Redis dedup fails — throws ConflictException
    // -------------------------------------------------------------------------

    @Test
    void checkin_dedupLockFails_throwsConflictException() {
        when(rateLimiter.acquireDedup(anyLong(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> checkinService.checkin(validRequest))
                .isInstanceOf(ConflictException.class);

        verify(attendanceRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Test 6: Time window outside — throws GeofenceViolationException
    // -------------------------------------------------------------------------

    @Test
    void checkin_outsideTimeWindow_throwsGeofenceViolationException() {
        // Lesson time window: past date, so "now" is outside
        LessonResponse pastLesson = LessonResponse.newBuilder()
                .setId(1L)
                .setGroupId(10L)
                .setSubjectId(5L)
                .setDate("2020-01-01")
                .setStartTime("09:00")
                .setEndTime("10:30")
                .setIsGeoBlocked(false)
                .setLessonNumber(1)
                .setStatus("closed")
                .build();
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(pastLesson);

        assertThatThrownBy(() -> checkinService.checkin(validRequest))
                .isInstanceOf(GeofenceViolationException.class)
                .hasMessageContaining("окна");

        verify(attendanceRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Test 7: Rate limit is the FIRST check — order matters
    // -------------------------------------------------------------------------

    @Test
    void checkin_rateLimitCheckedBeforeActiveLesson() {
        when(rateLimiter.checkRateLimit(100L)).thenReturn(false);

        assertThatThrownBy(() -> checkinService.checkin(validRequest))
                .isInstanceOf(RateLimitException.class);

        // scheduleGrpcClient must NOT be called if rate limit fails first
        verify(scheduleGrpcClient, never()).getActiveLesson(anyLong(), anyString());
    }
}
