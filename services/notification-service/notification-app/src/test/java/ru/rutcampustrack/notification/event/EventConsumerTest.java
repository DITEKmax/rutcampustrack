package ru.rutcampustrack.notification.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import ru.rutcampustrack.notification.history.*;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;
import ru.rutcampustrack.notification.push.*;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

class EventConsumerTest {
    final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    final WebPushDeliveryService provider = mock(WebPushDeliveryService.class);
    final IdempotencyGuard guard = mock(IdempotencyGuard.class);
    final NotificationAudienceService audience = mock(NotificationAudienceService.class);
    final NotificationHistoryEventProcessor history = mock(NotificationHistoryEventProcessor.class);
    final NotificationPushIntentStore intents = mock(NotificationPushIntentStore.class);
    final NotificationPreferencesService prefs = mock(NotificationPreferencesService.class);
    final ReminderAttendanceStateService marks = mock(ReminderAttendanceStateService.class);
    final EventConsumer consumer =
            new EventConsumer(messaging, provider, guard, marks, audience, history, intents, prefs);

    @BeforeEach
    void setup() {
        when(guard.tryClaim(anyString(), anyMap())).thenReturn(true);
        when(provider.shouldPush(anyString())).thenReturn(true);
        when(prefs.isEnabledForUser(anyLong(), anyString(), anyMap())).thenReturn(true);
    }

    @Test
    void actorKeepsAllRoleHistoryButOnlyOtherEnabledUsersReceiveDurableIntent() throws Exception {
        var event =
                event(
                        "lesson.cancelled",
                        Map.of("lesson_id", 51L, "group_id", 7L, "actor_user_id", 12L));
        var recipients =
                Map.of(
                        11L,
                        Set.of("STUDENT", "HEADMAN"),
                        12L,
                        Set.of("TEACHER"),
                        13L,
                        Set.of("TEACHER"));
        when(audience.snapshot(event)).thenReturn(recipients);
        when(prefs.isEnabledForUser(eq(13L), anyString(), anyMap())).thenReturn(false);
        consumer.onEvent(event);
        verify(history).persistResolved(event, recipients);
        verify(intents).enqueue(event, Map.of(11L, Set.of("STUDENT", "HEADMAN")));
        verifyNoInteractions(messaging);
        verify(provider, never()).sendToSubscription(any(), anyString(), any());
        verify(provider, never()).sendToGroup(anyLong(), anyString(), anyMap(), anySet());
        var terminal =
                event(
                        "semester.operation.completed",
                        Map.of("semester_id", 3L, "actor_user_id", 12L));
        when(audience.snapshot(terminal)).thenReturn(Map.of(12L, Set.of("ADMIN")));
        consumer.onEvent(terminal);
        verify(intents).enqueue(terminal, Map.of(12L, Set.of("ADMIN")));
        for (String completion : List.of("lesson.transferred", "lesson.updated")) {
            var async = event(completion, Map.of("actor_user_id", 12L));
            when(audience.snapshot(async)).thenReturn(Map.of(12L, Set.of("TEACHER")));
            consumer.onEvent(async);
            verify(intents).enqueue(async, Map.of(12L, Set.of("TEACHER")));
        }
    }

    @Test
    void requestOwnerHistoryIsRetainedWithoutExternalSubmissionEchoOrRawBroadcast() {
        var event =
                event(
                        "excuse.requested",
                        Map.of(
                                "group_id",
                                7L,
                                "user_id",
                                11L,
                                "ticket_id",
                                "ticket-1",
                                "reason",
                                "private"));
        var recipients = Map.of(11L, Set.of("STUDENT"), 12L, Set.of("HEADMAN"));
        when(audience.snapshot(event)).thenReturn(recipients);
        consumer.onEvent(event);
        verify(history).persistResolved(event, recipients);
        verify(intents).enqueue(event, Map.of(12L, Set.of("HEADMAN")));
        verifyNoInteractions(messaging);
    }

    @Test
    void authorityAndPreferenceOutagesPropagateBeforeIntentAndReplayClaimSkipsEverything() {
        var event = event("semester.activated", Map.of("semester_id", 3L));
        when(audience.snapshot(event))
                .thenThrow(new IllegalStateException("synthetic authority unavailable"));
        assertThatThrownBy(() -> consumer.onEvent(event)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(history, intents);
        doReturn(Map.of(12L, Set.of("ADMIN"))).when(audience).snapshot(event);
        when(prefs.isEnabledForUser(eq(12L), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("synthetic prefs unavailable"));
        assertThatThrownBy(() -> consumer.onEvent(event)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(intents);
        clearInvocations(audience, history);
        when(guard.tryClaim(anyString(), anyMap())).thenReturn(false);
        consumer.onEvent(event);
        verifyNoInteractions(audience, history, intents);
    }

    @Test
    void attendanceMarksAndTransferSourceCleanupRemainIndependentOfPresentation() {
        var attendance =
                event(
                        "attendance.marked",
                        Map.of("lesson_id", 51L, "user_id", 11L, "marked_by", "student"));
        consumer.onEvent(attendance);
        verify(marks).recordMarked((Map<String, Object>) attendance.get("payload"));
        verifyNoInteractions(audience, history, intents);
        var transfer =
                event(
                        "lesson.transferred",
                        Map.of(
                                "source",
                                Map.of("lesson_id", 51L),
                                "target",
                                Map.of("lesson_id", 52L)));
        when(audience.snapshot(transfer)).thenReturn(Map.of());
        consumer.onEvent(transfer);
        verify(marks).deleteLessonState(Map.of("lesson_id", 51L));
    }

    static Map<String, Object> event(String type, Map<String, Object> payload) {
        return Map.of(
                "event_id",
                UUID.randomUUID().toString(),
                "event_type",
                type,
                "occurred_at",
                "2026-10-10T10:00:00Z",
                "payload",
                payload);
    }
}
