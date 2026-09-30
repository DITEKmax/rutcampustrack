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
import ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentDescriptor;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.NotificationResolution;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDescriptorDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentRequestServiceAuthorizationTest {

    private static final String NOTIFICATION_REQUEST_ID = "0123456789abcdef01234567";
    private static final long NOTIFICATION_STUDENT_ID = 901L;
    private static final long NOTIFICATION_GROUP_ID = 10L;

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
    private final JournalAttachmentPort journalAttachmentPort = mock(JournalAttachmentPort.class);
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
                Clock.fixed(Instant.parse("2026-09-07T08:00:00Z"), ZoneOffset.UTC),
                journalAttachmentPort);
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

        verify(pairWriteCoordinator, never()).lock(
                any(Long.class), any(Long.class), any(Long.class), any(Long.class), any());
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

        verify(pairWriteCoordinator, never()).lock(
                any(Long.class), any(Long.class), any(Long.class), any(Long.class), any());
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

        verify(pairWriteCoordinator, never()).lock(
                any(Long.class), any(Long.class), any(Long.class), any(Long.class), any());
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

        verify(pairWriteCoordinator, never()).lock(
                any(Long.class), any(Long.class), any(Long.class), any(Long.class), any());
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

        verify(pairWriteCoordinator, never()).lock(
                any(Long.class), any(Long.class), any(Long.class), any(Long.class), any());
        verify(lateCheckinRepository, never()).save(any());
    }

    @Test
    void nullExcuseStatusFailsBeforeNotificationProjection() {
        String requestId = "0123456789abcdef01234567";
        ExcuseTicket ticket = ExcuseTicket.builder()
                .id(requestId)
                .studentId(901L)
                .groupId(10L)
                .status(null)
                .build();
        when(excuseRepository.findById(requestId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.resolveRequestNotification(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.EXCUSE, requestId))
                .isInstanceOf(BadRequestException.class);

        verify(attachmentRepository, never()).findByRequestIdAndOwnerStudentIdOrderByPositionAsc(
                requestId, 901L);
    }

    @Test
    void nullLateCheckinStatusFailsBeforeNotificationProjection() {
        String requestId = "fedcba987654321001234567";
        LateCheckinRequest request = LateCheckinRequest.builder()
                .id(requestId)
                .studentId(901L)
                .groupId(10L)
                .lessonId(44L)
                .status(null)
                .build();
        when(lateCheckinRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.resolveRequestNotification(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.LATE_CHECKIN, requestId))
                .isInstanceOf(BadRequestException.class);

        verify(attachmentRepository, never()).findByRequestIdAndOwnerStudentIdOrderByPositionAsc(
                requestId, 901L);
    }

    @Test
    void notificationResolutionRejectsPartialStoredAttachmentInventory() {
        ExcuseTicket ticket = notificationTicket(List.of(
                embeddedAttachment("attachment-a", AttachmentState.ACTIVE),
                embeddedAttachment("attachment-b", AttachmentState.ACTIVE)));

        assertNotificationFails(ticket, List.of(storedAttachment("attachment-a")));
    }

    @Test
    void notificationResolutionProjectsCompleteStoredInventoryInRepositoryOrder() {
        ExcuseTicket ticket = notificationTicket(List.of(
                embeddedAttachment("attachment-a", AttachmentState.EXPIRED),
                embeddedAttachment("attachment-b", AttachmentState.ACTIVE)));
        RequestAttachmentDocument storedB = storedAttachment("attachment-b", 1,
                AttachmentState.EXPIRED, "current-b",
                Instant.parse("2026-09-08T08:00:00Z"), Instant.parse("2026-09-06T08:00:00Z"));
        RequestAttachmentDocument storedA = storedAttachment("attachment-a", 0,
                AttachmentState.ACTIVE, "current-a",
                Instant.parse("2026-09-09T08:00:00Z"), null);
        stubNotification(ticket, List.of(storedB, storedA));

        NotificationResolution resolution = service.resolveRequestNotification(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.EXCUSE,
                NOTIFICATION_REQUEST_ID);

        assertThat(resolution.detail().attachments()).extracting(AttachmentDescriptor::id)
                .containsExactly("attachment-b", "attachment-a");
        assertThat(resolution.detail().attachments()).extracting(AttachmentDescriptor::name)
                .containsExactly("current-b", "current-a");
        assertThat(resolution.detail().attachments()).extracting(AttachmentDescriptor::state)
                .containsExactly(AttachmentState.EXPIRED, AttachmentState.ACTIVE);
        assertThat(resolution.detail().attachments()).extracting(AttachmentDescriptor::expiredAt)
                .containsExactly(Instant.parse("2026-09-06T08:00:00Z"), null);
    }

    @Test
    void notificationResolutionAcceptsZeroAttachmentInventory() {
        ExcuseTicket ticket = notificationTicket(null);
        stubNotification(ticket, List.of());

        NotificationResolution resolution = service.resolveRequestNotification(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.EXCUSE,
                NOTIFICATION_REQUEST_ID);

        assertThat(resolution.detail().attachments()).isEmpty();
    }

    @Test
    void malformedEmbeddedAttachmentDescriptorsFailClosed() {
        ExcuseTicket nullDescriptor = notificationTicket(
                Arrays.asList((RequestAttachmentDescriptorDocument) null));
        assertNotificationFails(nullDescriptor, List.of());

        ExcuseTicket blankId = notificationTicket(List.of(
                embeddedAttachment("  ", AttachmentState.ACTIVE)));
        assertNotificationFails(blankId, List.of());
    }

    @Test
    void duplicateAttachmentIdsFailClosed() {
        ExcuseTicket duplicateEmbedded = notificationTicket(List.of(
                embeddedAttachment("attachment-a", AttachmentState.ACTIVE),
                embeddedAttachment("attachment-a", AttachmentState.ACTIVE)));
        assertNotificationFails(duplicateEmbedded, List.of(storedAttachment("attachment-a")));

        ExcuseTicket duplicateStored = notificationTicket(List.of(
                embeddedAttachment("attachment-a", AttachmentState.ACTIVE)));
        assertNotificationFails(duplicateStored, List.of(
                storedAttachment("attachment-a"), storedAttachment("attachment-a")));
    }

    @Test
    void malformedStoredAttachmentDocumentsFailClosed() {
        ExcuseTicket ticket = notificationTicket(List.of(
                embeddedAttachment("attachment-a", AttachmentState.ACTIVE)));
        assertNotificationFails(ticket,
                Arrays.asList((RequestAttachmentDocument) null));

        RequestAttachmentDocument blankId = storedAttachment(" ");
        assertNotificationFails(ticket, List.of(blankId));
    }

    @Test
    void misboundStoredAttachmentDocumentsFailClosed() {
        ExcuseTicket ticket = notificationTicket(List.of(
                embeddedAttachment("attachment-a", AttachmentState.ACTIVE)));
        RequestAttachmentDocument wrongRequest = storedAttachment("attachment-a");
        wrongRequest.setRequestId("fedcba987654321001234567");
        assertNotificationFails(ticket, List.of(wrongRequest));

        RequestAttachmentDocument wrongOwner = storedAttachment("attachment-a");
        wrongOwner.setOwnerStudentId(902L);
        assertNotificationFails(ticket, List.of(wrongOwner));
    }

    @Test
    void extraStoredAttachmentFailsClosed() {
        ExcuseTicket ticket = notificationTicket(List.of(
                embeddedAttachment("attachment-a", AttachmentState.ACTIVE)));

        assertNotificationFails(ticket, List.of(
                storedAttachment("attachment-a"), storedAttachment("attachment-b")));
    }

    private void assertNotificationFails(ExcuseTicket ticket,
                                         List<RequestAttachmentDocument> storedAttachments) {
        stubNotification(ticket, storedAttachments);

        assertThatThrownBy(() -> service.resolveRequestNotification(
                ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.EXCUSE,
                NOTIFICATION_REQUEST_ID))
                .isInstanceOf(BadRequestException.class);
    }

    private void stubNotification(ExcuseTicket ticket,
                                  List<RequestAttachmentDocument> storedAttachments) {
        when(excuseRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByRequestIdAndOwnerStudentIdOrderByPositionAsc(
                ticket.getId(), ticket.getStudentId())).thenReturn(storedAttachments);
    }

    private static ExcuseTicket notificationTicket(
            List<RequestAttachmentDescriptorDocument> descriptors) {
        return ExcuseTicket.builder()
                .id(NOTIFICATION_REQUEST_ID)
                .studentId(NOTIFICATION_STUDENT_ID)
                .groupId(NOTIFICATION_GROUP_ID)
                .studentName("Student")
                .lessonIds(List.of(44L))
                .status(ExcuseTicketStatus.SUBMITTED)
                .attachmentDescriptors(descriptors)
                .build();
    }

    private static RequestAttachmentDescriptorDocument embeddedAttachment(
            String id, AttachmentState state) {
        return RequestAttachmentDescriptorDocument.builder()
                .id(id)
                .name("embedded-" + id)
                .contentType("application/pdf")
                .size(3L)
                .sha256("embedded-" + id)
                .state(state)
                .uploadedAt(Instant.parse("2026-09-06T08:00:00Z"))
                .expiresAt(Instant.parse("2026-09-06T08:00:00Z"))
                .build();
    }

    private static RequestAttachmentDocument storedAttachment(String id) {
        return storedAttachment(id, 0, AttachmentState.ACTIVE, "stored-" + id,
                Instant.parse("2026-09-09T08:00:00Z"), null);
    }

    private static RequestAttachmentDocument storedAttachment(
            String id, int position, AttachmentState state, String name,
            Instant expiresAt, Instant expiredAt) {
        return RequestAttachmentDocument.builder()
                .id(id)
                .requestId(NOTIFICATION_REQUEST_ID)
                .ownerStudentId(NOTIFICATION_STUDENT_ID)
                .groupId(NOTIFICATION_GROUP_ID)
                .position(position)
                .name(name)
                .contentType("application/pdf")
                .size(7L)
                .sha256("stored-" + name)
                .state(state)
                .uploadedAt(Instant.parse("2026-09-07T07:00:00Z"))
                .expiresAt(expiresAt)
                .expiredAt(expiredAt)
                .build();
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
