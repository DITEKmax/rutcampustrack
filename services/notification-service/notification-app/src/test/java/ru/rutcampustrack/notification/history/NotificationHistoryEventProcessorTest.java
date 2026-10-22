package ru.rutcampustrack.notification.history;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

class NotificationHistoryEventProcessorTest {
    final NotificationHistoryService unread = mock(NotificationHistoryService.class);
    final IdempotencyGuard guard = mock(IdempotencyGuard.class);
    final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    final NotificationAudienceService audience = mock(NotificationAudienceService.class);
    final NotificationRoleHistoryWriter writer = mock(NotificationRoleHistoryWriter.class);
    final NotificationHistoryEventProcessor processor =
            new NotificationHistoryEventProcessor(
                    mock(NotificationHistoryRepository.class),
                    unread,
                    guard,
                    mock(AcademicGroupMemberClient.class),
                    messaging,
                    null,
                    audience,
                    writer);

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void cancellationSnapshotsOwnerAndPrivilegedRecipientsSeparatelyAndNotifiesOnlyAfterCommit() {
        var event =
                NotificationAudienceAdmissionTest.envelope(
                        "excuse.decided",
                        Map.of(
                                "group_id",
                                7L,
                                "user_id",
                                11L,
                                "ticket_id",
                                "ticket-1",
                                "status",
                                "cancelled",
                                "decision_comment",
                                "private decision",
                                "reason",
                                "private reason"));
        var recipients = Map.of(11L, Set.of("STUDENT"), 12L, Set.of("HEADMAN"));
        when(guard.tryClaim(anyString(), anyMap())).thenReturn(true);
        when(audience.snapshot(event)).thenReturn(recipients);
        TransactionSynchronizationManager.initSynchronization();
        processor.persist(event);
        verify(writer)
                .persist(
                        eq((String) event.get("event_id")),
                        eq(11L),
                        eq(NotificationType.EXCUSE_CANCELLED),
                        argThat(
                                payload ->
                                        payload.containsKey("decision_comment")
                                                && !payload.containsKey("reason")),
                        isNull(),
                        eq(Set.of("STUDENT")),
                        any(Instant.class));
        verify(writer)
                .persist(
                        eq((String) event.get("event_id")),
                        eq(12L),
                        eq(NotificationType.EXCUSE_CANCELLED),
                        eq(Map.of("group_id", 7L, "ticket_id", "ticket-1")),
                        isNull(),
                        eq(Set.of("HEADMAN")),
                        any(Instant.class));
        verifyNoInteractions(messaging, unread);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(messaging)
                .convertAndSend("/topic/user/12", Map.of("type", "notification.history.changed"));
        verify(unread).invalidateUnreadCount(11L);
    }

    @Test
    void authorityFailureLeavesWriterUntouchedAndDedupNeverRequeriesAuthority() {
        var event =
                NotificationAudienceAdmissionTest.envelope(
                        "semester.activated", Map.of("semester_id", 3L));
        when(guard.tryClaim(anyString(), anyMap())).thenReturn(true);
        when(audience.snapshot(event))
                .thenThrow(new IllegalStateException("synthetic unavailable"));
        assertThatThrownBy(() -> processor.persist(event))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(writer, unread, messaging);
        clearInvocations(audience);
        when(guard.tryClaim(anyString(), anyMap())).thenReturn(false);
        processor.persist(event);
        verifyNoInteractions(audience, writer);
    }
}
