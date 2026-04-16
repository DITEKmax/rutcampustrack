package ru.rutcampustrack.attendance.excuse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.rutcampustrack.attendance.contract.dto.excuse.CreateExcuseRequest;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceWritePort;
import ru.rutcampustrack.schedule.grpc.LessonInfo;

import java.time.Instant;
import java.util.List;
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
 * Unit tests for ExcuseService (Phase 59-02).
 * Covers AC-1..AC-6 plus D-11, D-12, D-13, D-14, D-18.
 */
@ExtendWith(MockitoExtension.class)
class ExcuseServiceTest {

    private static final Long STUDENT_ID = 100L;
    private static final Long GROUP_ID = 10L;
    private static final Long OTHER_GROUP_ID = 99L;

    @Mock
    private ExcuseRepository excuseRepository;

    @Mock
    private RequestContext requestContext;

    @Mock
    private AcademicGrpcClient academicGrpcClient;

    @Mock
    private AttendanceWritePort attendanceWritePort;

    @Mock
    private ExcuseEventPublisher excuseEventPublisher;

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    @InjectMocks
    private ExcuseService excuseService;

    private CreateExcuseRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateExcuseRequest(
                List.of(1L, 2L),
                ExcuseType.ILLNESS,
                "Болею"
        );
        // default student context — tests override as needed
        lenient().when(requestContext.getUserId()).thenReturn(STUDENT_ID);
        lenient().when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        lenient().when(requestContext.isHeadman()).thenReturn(false);
        lenient().when(academicGrpcClient.getUserDisplayName(anyLong())).thenReturn("Иванов Иван");
        // D-25: by default return LessonInfo matching the requested ids + student's group,
        // so the validateLessonIds() check passes. Per-test overrides are still allowed.
        lenient().when(scheduleGrpcClient.getLessonsByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream()
                    .map(id -> LessonInfo.newBuilder()
                            .setLessonId(id)
                            .setGroupId(GROUP_ID)
                            .build())
                    .toList();
        });
        lenient().when(excuseRepository.save(any(ExcuseTicket.class)))
                .thenAnswer(inv -> {
                    ExcuseTicket t = inv.getArgument(0);
                    if (t.getId() == null) t.setId("new-id");
                    return t;
                });
    }

    // ---------------------------------------------------------------------
    // AC-1: happy path — plain STUDENT creates a ticket
    // ---------------------------------------------------------------------
    @Test
    void createExcuse_asPlainStudent_savesSubmittedTicketWithSnapshot() {
        when(excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                STUDENT_ID, validRequest.lessonIds(),
                List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED)))
                .thenReturn(false);

        ExcuseTicket saved = excuseService.createExcuse(validRequest);

        assertThat(saved.getStatus()).isEqualTo(ExcuseTicketStatus.SUBMITTED);
        assertThat(saved.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getStudentName()).isEqualTo("Иванов Иван");
        assertThat(saved.getLessonIds()).containsExactly(1L, 2L);
        assertThat(saved.getExcuseType()).isEqualTo(ExcuseType.ILLNESS);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        verify(excuseRepository).save(any(ExcuseTicket.class));
        // 59-05: event publisher invoked after save
        verify(excuseEventPublisher).publishRequested(eq(saved), anyList());
    }

    // ---------------------------------------------------------------------
    // AC-2 / D-11: duplicate lessonId in an active ticket
    // ---------------------------------------------------------------------
    @Test
    void createExcuse_duplicateActiveLesson_throwsConflict() {
        when(excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                anyLong(), anyList(), anyList()))
                .thenReturn(true);

        assertThatThrownBy(() -> excuseService.createExcuse(validRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("активный тикет");

        verify(excuseRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // AC-3 / D-12: headman submits ticket → conflict
    // ---------------------------------------------------------------------
    @Test
    void createExcuse_asHeadman_throwsConflict() {
        when(requestContext.isHeadman()).thenReturn(true);

        assertThatThrownBy(() -> excuseService.createExcuse(validRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Старосты проставляют пропуски через журнал");

        verify(excuseRepository, never()).save(any());
        verify(academicGrpcClient, never()).getUserDisplayName(anyLong());
    }

    // ---------------------------------------------------------------------
    // AC-6 / D-13: headman approves OWN ticket → conflict
    // ---------------------------------------------------------------------
    @Test
    void updateStatus_headmanSelfApprove_throwsConflict() {
        Long headmanId = 777L;
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(headmanId);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);

        ExcuseTicket ownTicket = ExcuseTicket.builder()
                .id("t1")
                .studentId(headmanId) // same as caller
                .groupId(GROUP_ID)
                .status(ExcuseTicketStatus.SUBMITTED)
                .build();
        when(excuseRepository.findById("t1")).thenReturn(Optional.of(ownTicket));

        UpdateExcuseStatusRequest req = new UpdateExcuseStatusRequest(
                ExcuseTicketStatus.APPROVED, "ok");

        assertThatThrownBy(() -> excuseService.updateStatus("t1", req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("собственный");

        verify(excuseRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // D-18: re-approving an already-APPROVED ticket → conflict
    // ---------------------------------------------------------------------
    @Test
    void updateStatus_alreadyApproved_throwsConflict() {
        Long headmanId = 777L;
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(headmanId);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);

        ExcuseTicket approved = ExcuseTicket.builder()
                .id("t2")
                .studentId(STUDENT_ID)
                .groupId(GROUP_ID)
                .status(ExcuseTicketStatus.APPROVED)
                .build();
        when(excuseRepository.findById("t2")).thenReturn(Optional.of(approved));

        UpdateExcuseStatusRequest req = new UpdateExcuseStatusRequest(
                ExcuseTicketStatus.APPROVED, "again");

        assertThatThrownBy(() -> excuseService.updateStatus("t2", req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже принято");

        verify(excuseRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // AC-4 / D-14: student reads someone else's ticket → 403
    // ---------------------------------------------------------------------
    @Test
    void getTicketById_foreignTicketForPlainStudent_throwsAccessDenied() {
        when(requestContext.getUserId()).thenReturn(STUDENT_ID);
        when(requestContext.isHeadman()).thenReturn(false);

        ExcuseTicket someone = ExcuseTicket.builder()
                .id("t3")
                .studentId(999L) // not the caller
                .groupId(GROUP_ID)
                .status(ExcuseTicketStatus.SUBMITTED)
                .build();
        when(excuseRepository.findById("t3")).thenReturn(Optional.of(someone));

        assertThatThrownBy(() -> excuseService.getTicketById("t3"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------------
    // D-14: headman of a DIFFERENT group lists groupId → 403
    // ---------------------------------------------------------------------
    @Test
    void getGroupTickets_foreignGroupForHeadman_throwsAccessDenied() {
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);

        assertThatThrownBy(() ->
                excuseService.getGroupTickets(OTHER_GROUP_ID, Pageable.unpaged(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------------
    // D-14: non-headman requests group tickets → 403
    // ---------------------------------------------------------------------
    @Test
    void getGroupTickets_asPlainStudent_throwsAccessDenied() {
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() ->
                excuseService.getGroupTickets(GROUP_ID, Pageable.unpaged(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------------
    // Bonus: getMyTickets delegates correctly with/without status filter
    // ---------------------------------------------------------------------
    @Test
    void getMyTickets_withoutStatus_callsUnfilteredRepo() {
        Page<ExcuseTicket> empty = new PageImpl<>(List.of());
        when(excuseRepository.findByStudentId(STUDENT_ID, Pageable.unpaged())).thenReturn(empty);

        Page<ExcuseTicket> result = excuseService.getMyTickets(Pageable.unpaged(), null);

        assertThat(result).isSameAs(empty);
        verify(excuseRepository).findByStudentId(STUDENT_ID, Pageable.unpaged());
    }

    @Test
    void updateStatus_happyPath_setsDecisionFieldsAndSaves() {
        Long headmanId = 777L;
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(headmanId);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);

        ExcuseTicket submitted = ExcuseTicket.builder()
                .id("t9")
                .studentId(STUDENT_ID)
                .groupId(GROUP_ID)
                .lessonIds(List.of(1L, 2L))
                .excuseType(ExcuseType.ILLNESS)
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now())
                .build();
        when(excuseRepository.findById("t9")).thenReturn(Optional.of(submitted));

        UpdateExcuseStatusRequest req = new UpdateExcuseStatusRequest(
                ExcuseTicketStatus.APPROVED, "справка предоставлена");

        ExcuseTicket result = excuseService.updateStatus("t9", req);

        assertThat(result.getStatus()).isEqualTo(ExcuseTicketStatus.APPROVED);
        assertThat(result.getDecisionBy()).isEqualTo(headmanId);
        assertThat(result.getDecisionComment()).isEqualTo("справка предоставлена");
        assertThat(result.getDecisionAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(excuseRepository).save(submitted);
        // D-16 cascade: one mark() per lessonId with EXCUSED status + excuse reason (ILLNESS → "Болезнь")
        verify(attendanceWritePort).mark(STUDENT_ID, 1L, GROUP_ID,
                ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.EXCUSED,
                ru.rutcampustrack.attendance.contract.enums.AttendanceSource.HEADMAN_EXCUSE,
                "Болезнь");
        verify(attendanceWritePort).mark(STUDENT_ID, 2L, GROUP_ID,
                ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.EXCUSED,
                ru.rutcampustrack.attendance.contract.enums.AttendanceSource.HEADMAN_EXCUSE,
                "Болезнь");
        // 59-05: publishDecided must fire AFTER cascade, before return
        verify(excuseEventPublisher).publishDecided(result);
    }
}
