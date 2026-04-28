package ru.rutcampustrack.attendance.latecheckin;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceWritePort;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LateCheckinService} — M09 G3.1 (14 P0-1).
 *
 * <p>Covers create / applyDecision / applyDecisionFromWeb / listPendingForHeadman
 * + all domain guards (role, group ownership, lesson status, idempotency).
 */
@ExtendWith(MockitoExtension.class)
class LateCheckinServiceTest {

    private static final Long STUDENT_ID = 100L;
    private static final Long GROUP_ID = 10L;
    private static final Long OTHER_GROUP_ID = 99L;
    private static final Long LESSON_ID = 42L;
    private static final Long SUBJECT_ID = 7L;
    private static final String REQUEST_ID = "req-1";
    private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");

    @Mock private LateCheckinRepository repository;
    @Mock private RequestContext requestContext;
    @Mock private ScheduleGrpcClient scheduleGrpcClient;
    @Mock private AcademicGrpcClient academicGrpcClient;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private AttendanceWritePort attendanceWritePort;
    @Mock private LateCheckinEventPublisher eventPublisher;
    @Mock private BusinessMetrics businessMetrics;
    @Mock private Counter lateCheckinCreatedCounter;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    private LateCheckinService service;

    @BeforeEach
    void setUp() {
        service = new LateCheckinService(
                repository, requestContext, scheduleGrpcClient, academicGrpcClient,
                attendanceRepository, attendanceWritePort, eventPublisher,
                businessMetrics, clock);
        lenient().when(businessMetrics.lateCheckinCreatedCounter()).thenReturn(lateCheckinCreatedCounter);
    }

    // ============================================================== createRequest

    @Test
    void createRequest_happyPath_savesPendingAndPublishesRequestedEvent() {
        stubStudentCaller();
        stubActiveLesson();
        when(attendanceRepository.findByLessonIdAndUserId(LESSON_ID, STUDENT_ID))
                .thenReturn(Optional.empty());
        when(repository.existsByStudentIdAndLessonIdAndStatus(
                STUDENT_ID, LESSON_ID, LateCheckinRequestStatus.PENDING))
                .thenReturn(false);
        when(academicGrpcClient.getUserDisplayName(STUDENT_ID)).thenReturn("Иванов И.");
        when(academicGrpcClient.getSubjectsByIds(List.of(SUBJECT_ID)))
                .thenReturn(Map.of(SUBJECT_ID, "Математика"));
        when(repository.save(any(LateCheckinRequest.class)))
                .thenAnswer(inv -> {
                    LateCheckinRequest r = inv.getArgument(0);
                    r.setId(REQUEST_ID);
                    return r;
                });

        LateCheckinRequest saved = service.createRequest(LESSON_ID);

        assertThat(saved.getId()).isEqualTo(REQUEST_ID);
        assertThat(saved.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getLessonId()).isEqualTo(LESSON_ID);
        assertThat(saved.getStatus()).isEqualTo(LateCheckinRequestStatus.PENDING);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);

        verify(eventPublisher).publishRequested(eq(saved), any(), eq(3), eq(SUBJECT_ID), eq("Математика"));
        verify(lateCheckinCreatedCounter).increment();
    }

    @Test
    void createRequest_byHeadman_throwsConflict() {
        when(requestContext.isHeadman()).thenReturn(true);

        assertThatThrownBy(() -> service.createRequest(LESSON_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("журнал");

        verify(scheduleGrpcClient, never()).getLessonById(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void createRequest_lessonFromOtherGroup_throwsBadRequest() {
        when(requestContext.isHeadman()).thenReturn(false);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        when(scheduleGrpcClient.getLessonById(LESSON_ID))
                .thenReturn(LessonResponse.newBuilder()
                        .setGroupId(OTHER_GROUP_ID)
                        .setStatus("active")
                        .build());

        assertThatThrownBy(() -> service.createRequest(LESSON_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("не принадлежит");
    }

    @Test
    void createRequest_lessonPlanned_throwsBadRequest() {
        stubStudentCaller();
        when(scheduleGrpcClient.getLessonById(LESSON_ID))
                .thenReturn(LessonResponse.newBuilder()
                        .setGroupId(GROUP_ID)
                        .setStatus("planned")
                        .build());

        assertThatThrownBy(() -> service.createRequest(LESSON_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("идущей");
    }

    @Test
    void createRequest_alreadyMarkedPresent_throwsConflict() {
        stubStudentCaller();
        stubActiveLesson();
        AttendanceDocument existing = AttendanceDocument.builder()
                .status(AttendanceStatus.PRESENT)
                .build();
        when(attendanceRepository.findByLessonIdAndUserId(LESSON_ID, STUDENT_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createRequest(LESSON_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже отмечены");
    }

    @Test
    void createRequest_duplicatePending_throwsConflict() {
        stubStudentCaller();
        stubActiveLesson();
        when(attendanceRepository.findByLessonIdAndUserId(LESSON_ID, STUDENT_ID))
                .thenReturn(Optional.empty());
        when(repository.existsByStudentIdAndLessonIdAndStatus(
                STUDENT_ID, LESSON_ID, LateCheckinRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createRequest(LESSON_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже отправлен");
    }

    // ============================================================== listPendingForHeadman

    @Test
    void listPendingForHeadman_happyPath_returnsGroupRequests() {
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        List<LateCheckinRequest> pending = List.of(newPending(REQUEST_ID));
        when(repository.findByGroupIdAndStatusOrderByCreatedAtAsc(
                GROUP_ID, LateCheckinRequestStatus.PENDING))
                .thenReturn(pending);

        List<LateCheckinRequest> result = service.listPendingForHeadman();

        assertThat(result).hasSize(1).first()
                .extracting(LateCheckinRequest::getId).isEqualTo(REQUEST_ID);
    }

    @Test
    void listPendingForHeadman_notHeadman_throwsAccessDenied() {
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.listPendingForHeadman())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listPendingForHeadman_headmanWithoutGroup_throwsAccessDenied() {
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(null);

        assertThatThrownBy(() -> service.listPendingForHeadman())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("не привязан");
    }

    @Test
    void listGroupRequestsForHeadman_returnsRecentGroupRequests() {
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        LateCheckinRequest request = newPending(REQUEST_ID);
        when(repository.findByGroupIdAndUpdatedAtGreaterThanEqual(
                eq(GROUP_ID), eq(NOW.minus(30, ChronoUnit.DAYS)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request)));

        Page<LateCheckinRequest> result = service.listGroupRequestsForHeadman(
                GROUP_ID, Pageable.ofSize(50), null);

        assertThat(result.getContent()).containsExactly(request);
    }

    @Test
    void listGroupRequestsForHeadman_filtersByStatusWhenProvided() {
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        LateCheckinRequest request = newPending(REQUEST_ID);
        request.setStatus(LateCheckinRequestStatus.APPROVED);
        when(repository.findByGroupIdAndStatusAndUpdatedAtGreaterThanEqual(
                eq(GROUP_ID),
                eq(LateCheckinRequestStatus.APPROVED),
                eq(NOW.minus(30, ChronoUnit.DAYS)),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request)));

        Page<LateCheckinRequest> result = service.listGroupRequestsForHeadman(
                GROUP_ID, Pageable.ofSize(50), LateCheckinRequestStatus.APPROVED);

        assertThat(result.getContent()).containsExactly(request);
    }

    @Test
    void listGroupRequestsForHeadman_differentGroup_throwsAccessDenied() {
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);

        assertThatThrownBy(() -> service.listGroupRequestsForHeadman(OTHER_GROUP_ID, Pageable.ofSize(50), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================== applyDecisionFromWeb

    @Test
    void applyDecisionFromWeb_approveHappyPath_upsertsAttendanceAndPublishes() {
        Long headmanId = 777L;
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(headmanId);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        LateCheckinRequest pending = newPending(REQUEST_ID);
        when(repository.findById(REQUEST_ID))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubDecidedLesson();
        when(academicGrpcClient.getSubjectsByIds(List.of(SUBJECT_ID)))
                .thenReturn(Map.of(SUBJECT_ID, "Математика"));

        LateCheckinRequest decided = service.applyDecisionFromWeb(REQUEST_ID, true);

        ArgumentCaptor<LateCheckinRequest> captor = ArgumentCaptor.forClass(LateCheckinRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LateCheckinRequestStatus.APPROVED);
        assertThat(captor.getValue().getDecisionBy()).isEqualTo(headmanId);
        assertThat(captor.getValue().getDecisionAt()).isEqualTo(NOW);

        verify(attendanceWritePort).mark(
                STUDENT_ID, LESSON_ID, GROUP_ID,
                AttendanceStatus.PRESENT, AttendanceSource.LATE_CHECKIN);
        verify(eventPublisher).publishDecided(any(), any(), eq(3), eq(SUBJECT_ID), eq("Математика"));
        assertThat(decided).isNotNull();
    }

    @Test
    void applyDecisionFromWeb_reject_doesNotTouchAttendance() {
        Long headmanId = 777L;
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(headmanId);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        LateCheckinRequest pending = newPending(REQUEST_ID);
        when(repository.findById(REQUEST_ID))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubDecidedLesson();
        when(academicGrpcClient.getSubjectsByIds(List.of(SUBJECT_ID)))
                .thenReturn(Map.of(SUBJECT_ID, "Математика"));

        service.applyDecisionFromWeb(REQUEST_ID, false);

        ArgumentCaptor<LateCheckinRequest> captor = ArgumentCaptor.forClass(LateCheckinRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LateCheckinRequestStatus.REJECTED);

        verify(attendanceWritePort, never()).mark(anyLong(), anyLong(), anyLong(), any(), any());
        verify(eventPublisher).publishDecided(any(), any(), any(), any(), any());
    }

    @Test
    void applyDecisionFromWeb_notHeadman_throwsAccessDenied() {
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.applyDecisionFromWeb(REQUEST_ID, true))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findById(any());
    }

    @Test
    void applyDecisionFromWeb_differentGroup_throwsAccessDenied() {
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        LateCheckinRequest pending = newPending(REQUEST_ID);
        pending.setGroupId(OTHER_GROUP_ID);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.applyDecisionFromWeb(REQUEST_ID, true))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("другой группе");
    }

    @Test
    void applyDecisionFromWeb_notFound_throwsResourceNotFound() {
        when(requestContext.isHeadman()).thenReturn(true);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyDecisionFromWeb(REQUEST_ID, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================== applyDecision (bot path)

    @Test
    void applyDecision_alreadyDecided_isNoOp() {
        LateCheckinRequest alreadyApproved = newPending(REQUEST_ID);
        alreadyApproved.setStatus(LateCheckinRequestStatus.APPROVED);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(alreadyApproved));

        service.applyDecision(REQUEST_ID, 777L, false);

        verify(repository, never()).save(any());
        verify(attendanceWritePort, never()).mark(anyLong(), anyLong(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishDecided(any(), any(), any(), any(), any());
    }

    @Test
    void applyDecision_notFound_throwsResourceNotFound() {
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyDecision(REQUEST_ID, 777L, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================== helpers

    private void stubStudentCaller() {
        lenient().when(requestContext.isHeadman()).thenReturn(false);
        lenient().when(requestContext.getUserId()).thenReturn(STUDENT_ID);
        lenient().when(requestContext.getGroupId()).thenReturn(GROUP_ID);
    }

    private void stubActiveLesson() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(
                LessonResponse.newBuilder()
                        .setGroupId(GROUP_ID)
                        .setStatus("active")
                        .setDate("2026-04-23")
                        .setLessonNumber(3)
                        .setSubjectId(SUBJECT_ID)
                        .build());
    }

    private void stubDecidedLesson() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(
                LessonResponse.newBuilder()
                        .setGroupId(GROUP_ID)
                        .setStatus("closed")
                        .setDate("2026-04-23")
                        .setLessonNumber(3)
                        .setSubjectId(SUBJECT_ID)
                        .build());
    }

    private LateCheckinRequest newPending(String id) {
        return LateCheckinRequest.builder()
                .id(id)
                .studentId(STUDENT_ID)
                .groupId(GROUP_ID)
                .lessonId(LESSON_ID)
                .studentName("Иванов И.")
                .status(LateCheckinRequestStatus.PENDING)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
