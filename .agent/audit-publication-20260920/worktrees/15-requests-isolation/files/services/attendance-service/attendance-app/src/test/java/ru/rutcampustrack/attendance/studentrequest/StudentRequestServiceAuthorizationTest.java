package ru.rutcampustrack.attendance.studentrequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.excuse.ExcuseEventPublisher;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentRequestServiceAuthorizationTest {

    private final ExcuseRepository excuseRepository = mock(ExcuseRepository.class);
    private final LateCheckinRepository lateCheckinRepository = mock(LateCheckinRepository.class);
    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final StudentRequestReceiptRepository receiptRepository = mock(StudentRequestReceiptRepository.class);
    private final StudentLateCheckinBudgetRepository budgetRepository = mock(StudentLateCheckinBudgetRepository.class);
    private final RequestAttachmentRepository attachmentRepository = mock(RequestAttachmentRepository.class);
    private final ScheduleGrpcClient scheduleGrpcClient = mock(ScheduleGrpcClient.class);
    private final AcademicGrpcClient academicGrpcClient = mock(AcademicGrpcClient.class);
    private final SemesterCacheService semesterCacheService = mock(SemesterCacheService.class);
    private final PairWriteCoordinator pairWriteCoordinator = mock(PairWriteCoordinator.class);
    private final ExcuseEventPublisher excuseEventPublisher = mock(ExcuseEventPublisher.class);
    private final LateCheckinEventPublisher lateCheckinEventPublisher = mock(LateCheckinEventPublisher.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private StudentRequestService service;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        service = new StudentRequestService(
                excuseRepository, lateCheckinRepository, attendanceRepository,
                receiptRepository, budgetRepository, attachmentRepository,
                scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, pairWriteCoordinator, excuseEventPublisher,
                lateCheckinEventPublisher, mongoTemplate, transactionTemplate,
                Clock.fixed(Instant.parse("2026-09-07T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void headmanCannotDecideExcuseFromForeignGroupEvenWithExplicitActor() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("excuse-foreign")
                .studentId(901L)
                .groupId(20L)
                .lessonIds(java.util.List.of(44L))
                .status(ExcuseTicketStatus.SUBMITTED)
                .build();
        when(excuseRepository.findById("excuse-foreign")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.decideExcuse(headman(900L, 10L), "excuse-foreign", false, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(pairWriteCoordinator, never()).lock(any(Long.class), any(Long.class), any(Long.class), any());
        verify(excuseRepository, never()).save(any());
    }

    @Test
    void headmanCannotDecideLateCheckinFromForeignGroupWithoutExplicitActor() {
        LateCheckinRequest request = LateCheckinRequest.builder()
                .id("late-foreign")
                .studentId(901L)
                .groupId(20L)
                .lessonId(44L)
                .status(ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus.PENDING)
                .build();
        when(lateCheckinRepository.findById("late-foreign")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.decideLateCheckin(headman(900L, 10L), "late-foreign", true))
                .isInstanceOf(AccessDeniedException.class);

        verify(pairWriteCoordinator, never()).lock(any(Long.class), any(Long.class), any(Long.class), any());
        verify(lateCheckinRepository, never()).save(any());
    }

    @Test
    void ordinaryStudentCannotDecideAsHeadman() {
        when(lateCheckinRepository.findById("late-any")).thenReturn(Optional.of(pendingLate("late-any")));

        assertThatThrownBy(() -> service.decideLateCheckin(student(901L, 10L), "late-any", true))
                .isInstanceOf(AccessDeniedException.class);

        verify(academicGrpcClient, never()).isHeadman(any(), any());
    }

    @Test
    void missingRoleCannotDecideEvenWhenHeadmanFlagIsSet() {
        when(lateCheckinRepository.findById("late-any")).thenReturn(Optional.of(pendingLate("late-any")));

        assertThatThrownBy(() -> service.decideLateCheckin(
                new StudentRequestModels.Identity(900L, null, 10L, true), "late-any", true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingRoleCannotSubmitStudentRequest() {
        assertThatThrownBy(() -> service.submitLateCheckin(
                new StudentRequestModels.Identity(900L, null, 10L, false),
                new StudentRequestModels.LateCheckinSubmission(44L, "role-missing-key-01")))
                .isInstanceOf(AccessDeniedException.class);

        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void headmanCannotApproveOwnExcuseSubmittedBeforeAppointment() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("excuse-own")
                .studentId(900L)
                .groupId(10L)
                .lessonIds(java.util.List.of(44L))
                .status(ExcuseTicketStatus.SUBMITTED)
                .build();
        when(excuseRepository.findById("excuse-own")).thenReturn(Optional.of(ticket));
        when(academicGrpcClient.isHeadman(900L, 10L)).thenReturn(
                HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());

        assertThatThrownBy(() -> service.decideExcuse(headman(900L, 10L), "excuse-own", true, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(pairWriteCoordinator, never()).lock(any(Long.class), any(Long.class), any(Long.class), any());
        verify(excuseRepository, never()).save(any());
    }

    @Test
    void headmanCannotRejectOwnLateRequestSubmittedBeforeAppointment() {
        LateCheckinRequest request = LateCheckinRequest.builder()
                .id("late-own")
                .studentId(900L)
                .groupId(10L)
                .lessonId(44L)
                .status(ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus.PENDING)
                .build();
        when(lateCheckinRepository.findById("late-own")).thenReturn(Optional.of(request));
        when(academicGrpcClient.isHeadman(900L, 10L)).thenReturn(
                HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());

        assertThatThrownBy(() -> service.decideLateCheckin(headman(900L, 10L), "late-own", false))
                .isInstanceOf(AccessDeniedException.class);

        verify(pairWriteCoordinator, never()).lock(any(Long.class), any(Long.class), any(Long.class), any());
        verify(lateCheckinRepository, never()).save(any());
    }

    @Test
    void nullAcademicAuthorityResponseFailsClosed() {
        when(lateCheckinRepository.findById("late-null-authority"))
                .thenReturn(Optional.of(pendingLate("late-null-authority")));
        when(academicGrpcClient.isHeadman(900L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> service.decideLateCheckin(
                headman(900L, 10L), "late-null-authority", true))
                .isInstanceOf(AccessDeniedException.class);

        verify(pairWriteCoordinator, never()).lock(any(Long.class), any(Long.class), any(Long.class), any());
        verify(lateCheckinRepository, never()).save(any());
    }

    private static StudentRequestModels.Identity headman(long userId, long groupId) {
        return new StudentRequestModels.Identity(userId, UserRole.STUDENT, groupId, true);
    }

    private static StudentRequestModels.Identity student(long userId, long groupId) {
        return new StudentRequestModels.Identity(userId, UserRole.STUDENT, groupId, false);
    }

    private static LateCheckinRequest pendingLate(String id) {
        return LateCheckinRequest.builder()
                .id(id).studentId(901L).groupId(10L).lessonId(44L)
                .status(ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus.PENDING)
                .build();
    }
}
