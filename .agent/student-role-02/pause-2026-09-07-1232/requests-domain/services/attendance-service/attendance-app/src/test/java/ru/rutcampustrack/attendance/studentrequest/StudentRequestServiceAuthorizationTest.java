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
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentRequestServiceAuthorizationTest {

    private final ExcuseRepository excuseRepository = mock(ExcuseRepository.class);
    private final LateCheckinRepository lateCheckinRepository = mock(LateCheckinRepository.class);
    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final StudentRequestReceiptRepository receiptRepository = mock(StudentRequestReceiptRepository.class);
    private final StudentLateCheckinBudgetRepository budgetRepository = mock(StudentLateCheckinBudgetRepository.class);
    private final RequestAttachmentRepository attachmentRepository = mock(RequestAttachmentRepository.class);
    private final RequestContext requestContext = mock(RequestContext.class);
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
        when(requestContext.getUserId()).thenReturn(900L);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        service = new StudentRequestService(
                excuseRepository, lateCheckinRepository, attendanceRepository,
                receiptRepository, budgetRepository, attachmentRepository,
                requestContext, scheduleGrpcClient, academicGrpcClient,
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

        assertThatThrownBy(() -> service.decideExcuse("excuse-foreign", false, 900L, null))
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

        assertThatThrownBy(() -> service.decideLateCheckin("late-foreign", true, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(pairWriteCoordinator, never()).lock(any(Long.class), any(Long.class), any(Long.class), any());
        verify(lateCheckinRepository, never()).save(any());
    }

    @Test
    void ordinaryStudentCannotImpersonateHeadmanWithExplicitDecisionBy() {
        reset(requestContext);
        when(requestContext.getUserId()).thenReturn(901L);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.decideLateCheckin("late-any", true, 900L))
                .isInstanceOf(AccessDeniedException.class);

        verify(transactionTemplate, never()).execute(any());
        verify(academicGrpcClient, never()).isHeadman(any(), any());
    }

    @Test
    void missingRoleCannotDecideEvenWhenHeadmanFlagIsSet() {
        reset(requestContext);
        when(requestContext.getUserId()).thenReturn(900L);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(requestContext.getRole()).thenReturn(null);
        when(requestContext.isHeadman()).thenReturn(true);

        assertThatThrownBy(() -> service.decideLateCheckin("late-any", true, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void missingRoleCannotSubmitStudentRequest() {
        reset(requestContext);
        when(requestContext.getUserId()).thenReturn(900L);
        when(requestContext.getGroupId()).thenReturn(10L);
        when(requestContext.getRole()).thenReturn(null);
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.submitLateCheckin(
                new StudentRequestModels.LateCheckinSubmission(44L, "role-missing-key-01")))
                .isInstanceOf(AccessDeniedException.class);

        verify(transactionTemplate, never()).execute(any());
    }
}
