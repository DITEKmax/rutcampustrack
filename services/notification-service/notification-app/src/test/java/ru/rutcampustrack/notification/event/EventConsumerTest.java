package ru.rutcampustrack.notification.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private WebPushDeliveryService webPushDeliveryService;

    @Mock
    private ru.rutcampustrack.shared.events.IdempotencyGuard idempotencyGuard;

    private EventConsumer consumer;

    @BeforeEach
    void setUp() {
        // M13 G24-fix-6: real IdempotencyGuard теперь fail-closed на missing
        // event_id (throws IllegalStateException). Test envelope'ы — STOMP
        // routing focus, не event_id semantics. Mock'аем guard.tryClaim →
        // true чтобы тесты остались о routing'е. lenient — некоторые тесты
        // (unknown event_type) early-return до tryClaim.
        org.mockito.Mockito.lenient()
                .when(idempotencyGuard.tryClaim(any(), any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(webPushDeliveryService.sendToGroup(anyLong(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        consumer = new EventConsumer(messagingTemplate, webPushDeliveryService, idempotencyGuard);
    }

    // --- Existing STOMP routing tests (must all still pass) ---

    @ParameterizedTest
    @ValueSource(strings = {"excuse.requested", "late_checkin.requested", "lesson.closed"})
    void headmanLookupFailureReachesListenerWithoutPublishingWsAndRetrySucceedsOnce(String eventType) {
        Map<String, Object> payload = Map.of("group_id", 42, "user_id", 7);
        Map<String, Object> envelope = Map.of("event_type", eventType, "payload", payload);
        when(webPushDeliveryService.shouldPush(eventType)).thenReturn(true);
        when(webPushDeliveryService.resolveCurrentAudience(42L, eventType))
                .thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException()).thenReturn(Set.of(7L));

        assertThatThrownBy(() -> consumer.onEvent(envelope))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        verifyNoInteractions(messagingTemplate);
        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend("/topic/group/42/headman", Map.of("type", eventType, "payload", payload));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lesson.started", "homework.published", "group.archived"})
    void groupLookupFailureReachesListenerWithoutPublishingWsAndRetrySucceedsOnce(String eventType) {
        Map<String, Object> payload = Map.of("group_id", 42);
        Map<String, Object> envelope = Map.of("event_type", eventType, "payload", payload);
        when(webPushDeliveryService.shouldPush(eventType)).thenReturn(true);
        when(webPushDeliveryService.resolveCurrentAudience(42L, eventType))
                .thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException()).thenReturn(Set.of(7L));

        assertThatThrownBy(() -> consumer.onEvent(envelope))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        verifyNoInteractions(messagingTemplate);
        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend("/topic/group/42", Map.of("type", eventType, "payload", payload));
    }

    @Test
    void lessonStarted_routesToGroupTopic() {
        Map<String, Object> payload = Map.of("group_id", 42, "lesson_id", 101);
        Map<String, Object> envelope = Map.of("event_type", "lesson.started", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/42"),
                eq(Map.of("type", "lesson.started", "payload", payload))
        );
    }

    @Test
    void lessonCancelled_routesToGroupTopic() {
        Map<String, Object> payload = Map.of("group_id", 42, "subject_id", 10);
        Map<String, Object> envelope = Map.of("event_type", "lesson.cancelled", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/42"),
                eq(Map.of("type", "lesson.cancelled", "payload", payload))
        );
    }

    @Test
    void homeworkPublished_routesToGroupTopic() {
        Map<String, Object> payload = Map.of("group_id", 99, "title", "Лабораторная 3");
        Map<String, Object> envelope = Map.of("event_type", "homework.published", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/99"),
                eq(Map.of("type", "homework.published", "payload", payload))
        );
    }

    @Test
    void excuseRequested_routesToHeadmanTopic() {
        Map<String, Object> payload = Map.of("group_id", 42, "user_id", 7);
        Map<String, Object> envelope = Map.of("event_type", "excuse.requested", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/42/headman"),
                eq(Map.of("type", "excuse.requested", "payload", payload))
        );
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/group/42"),
                any(Object.class)
        );
    }

    @Test
    void lateCheckinRequested_routesToHeadmanTopic() {
        Map<String, Object> payload = Map.of("group_id", 42, "user_id", 5, "lesson_id", 200);
        Map<String, Object> envelope = Map.of("event_type", "late_checkin.requested", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/42/headman"),
                eq(Map.of("type", "late_checkin.requested", "payload", payload))
        );
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/group/42"),
                any(Object.class)
        );
    }

    @Test
    void lessonClosed_routesOnlyToHeadmanAndExistingGuardDropsReplay() {
        when(webPushDeliveryService.shouldPush("lesson.closed")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 42, "lesson_id", 101, "subject_id", 8);
        Map<String, Object> envelope = Map.of(
                "event_type", "lesson.closed",
                "event_id", UUID.randomUUID().toString(),
                "payload", payload);
        when(idempotencyGuard.tryClaim(eq(EventConsumer.CONSUMER_ID), eq(envelope)))
                .thenReturn(true, false);

        consumer.onEvent(envelope);
        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/42/headman"),
                eq(Map.of("type", "lesson.closed", "payload", payload)));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/group/42"), any(Object.class));
        verify(webPushDeliveryService).sendToGroup(42L, "lesson.closed", payload, Set.of());
        verify(idempotencyGuard, org.mockito.Mockito.times(2))
                .tryClaim(EventConsumer.CONSUMER_ID, envelope);
    }

    @Test
    void missingEventType_ignored() {
        Map<String, Object> envelope = Map.of("payload", Map.of("group_id", 42));

        consumer.onEvent(envelope);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void missingPayload_ignored() {
        Map<String, Object> envelope = Map.of("event_type", "lesson.started");

        consumer.onEvent(envelope);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void missingGroupId_ignored() {
        Map<String, Object> payload = Map.of("lesson_id", 101);
        Map<String, Object> envelope = Map.of("event_type", "lesson.started", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void unknownEventType_isIgnoredByWebSocketRouting() {
        Map<String, Object> payload = Map.of("group_id", 42);
        Map<String, Object> envelope = Map.of("event_type", "some.unknown", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void excuseDecided_routesToPayloadUserWhenProducerOmitsGroupId() {
        Map<String, Object> payload = Map.of("user_id", 7, "status", "approved");
        Map<String, Object> envelope = Map.of("event_type", "excuse.decided", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/user/7"),
                eq(Map.of("type", "excuse.decided", "payload", payload))
        );
        verifyNoMoreInteractions(messagingTemplate);
        verify(webPushDeliveryService, never()).sendToGroup(anyLong(), anyString(), any());
    }

    @Test
    void homeworkDueReminder_routesToUserAndKeepsExistingGroupPush() {
        when(webPushDeliveryService.shouldPush("homework.due_reminder")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 42, "user_id", 7, "days_before_due", 2);
        Map<String, Object> envelope = Map.of("event_type", "homework.due_reminder", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/user/7"),
                eq(Map.of("type", "homework.due_reminder", "payload", payload))
        );
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/group/42"), any(Object.class));
        verify(webPushDeliveryService).sendToGroup(42L, "homework.due_reminder", payload);
    }

    @Test
    void userScopedEventWithInvalidUserIdDoesNotFallBackToGroup() {
        Map<String, Object> payload = Map.of("group_id", 42, "user_id", new BigDecimal("7.5"));
        Map<String, Object> envelope = Map.of("event_type", "homework.due_reminder", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void groupNotificationWithInvalidGroupIdIsIgnored() {
        Map<String, Object> payload = Map.of("group_id", new BigDecimal("42.5"));
        Map<String, Object> envelope = Map.of("event_type", "lesson.started", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        verify(webPushDeliveryService, never()).sendToGroup(anyLong(), anyString(), any());
    }

    // --- New push hook tests ---

    // Test 1: lesson.started calls both STOMP and push
    @Test
    void lessonStarted_triggersBothStompAndPush() {
        when(webPushDeliveryService.shouldPush("lesson.started")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 42, "subject_name", "Математика", "lesson_id", 101);
        Map<String, Object> envelope = Map.of("event_type", "lesson.started", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(eq("/topic/group/42"), any(Object.class));
        verify(webPushDeliveryService).sendToGroup(42L, "lesson.started", payload, Set.of());
    }

    // Test 2: lesson.cancelled calls push
    @Test
    void lessonCancelled_triggersPush() {
        when(webPushDeliveryService.shouldPush("lesson.cancelled")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 5, "subject_name", "Физика");
        Map<String, Object> envelope = Map.of("event_type", "lesson.cancelled", "payload", payload);

        consumer.onEvent(envelope);

        verify(webPushDeliveryService).sendToGroup(5L, "lesson.cancelled", payload, Set.of());
    }

    // Test 3: homework.published calls push
    @Test
    void homeworkPublished_triggersPush() {
        when(webPushDeliveryService.shouldPush("homework.published")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 7, "subject_name", "История", "title", "Лаб 1");
        Map<String, Object> envelope = Map.of("event_type", "homework.published", "payload", payload);

        consumer.onEvent(envelope);

        verify(webPushDeliveryService).sendToGroup(7L, "homework.published", payload, Set.of());
    }

    // Test 4: events are not pushed when WebPushDeliveryService says they are ineligible
    @Test
    void excuseRequested_doesNotTriggerPush() {
        when(webPushDeliveryService.shouldPush("excuse.requested")).thenReturn(false);
        Map<String, Object> payload = Map.of("group_id", 42, "user_id", 7);
        Map<String, Object> envelope = Map.of("event_type", "excuse.requested", "payload", payload);

        consumer.onEvent(envelope);

        verify(webPushDeliveryService, never()).sendToGroup(anyLong(), anyString(), any());
    }

    @Test
    void lessonReminder_routesEligiblePersonalWsAndSameAsyncSnapshot() {
        when(webPushDeliveryService.shouldPush("lesson.reminder")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 42, "lesson_id", 101);
        Map<String, Object> envelope = Map.of("event_type", "lesson.reminder", "payload", payload);
        when(webPushDeliveryService.resolveReminderAudience(42L, payload)).thenReturn(Set.of(7L));
        var pendingProvider = new CompletableFuture<Void>();
        when(webPushDeliveryService.sendToGroup(42L, "lesson.reminder", payload, Set.of(7L)))
                .thenReturn(pendingProvider);

        consumer.onEvent(envelope);

        var ordered = org.mockito.Mockito.inOrder(webPushDeliveryService, messagingTemplate);
        ordered.verify(webPushDeliveryService).resolveReminderAudience(42L, payload);
        ordered.verify(messagingTemplate).convertAndSend("/topic/user/7", Map.of("type", "lesson.reminder", "payload", payload));
        ordered.verify(webPushDeliveryService).sendToGroup(42L, "lesson.reminder", payload, Set.of(7L));
        verifyNoMoreInteractions(messagingTemplate);
        org.assertj.core.api.Assertions.assertThat(pendingProvider).isNotCompleted();
    }

    @Test
    void lessonReminder_lookupFailureBeforeWsReachesRetryAndEmptyAudienceSendsNothing() {
        Map<String, Object> payload = Map.of("group_id", 42, "lesson_id", 101);
        when(webPushDeliveryService.shouldPush("lesson.reminder")).thenReturn(true);
        when(webPushDeliveryService.resolveReminderAudience(42L, payload))
                .thenThrow(new org.springframework.dao.TransientDataAccessResourceException("test unavailable"))
                .thenReturn(Set.of());
        Map<String, Object> envelope = Map.of("event_type", "lesson.reminder", "payload", payload);
        assertThatThrownBy(() -> consumer.onEvent(envelope))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        consumer.onEvent(envelope);
        verifyNoInteractions(messagingTemplate);
        verify(webPushDeliveryService, never()).sendToGroup(anyLong(), anyString(), any(), any());
    }

    // Test 5: attendance.marked calls push when it is a user-facing headman edit
    @Test
    void attendanceMarked_triggersPush() {
        when(webPushDeliveryService.shouldPush("attendance.marked")).thenReturn(true);
        Map<String, Object> payload = Map.of(
                "group_id", 42,
                "user_id", 5,
                "marked_by", "headman"
        );
        Map<String, Object> envelope = Map.of("event_type", "attendance.marked", "payload", payload);

        consumer.onEvent(envelope);

        verify(webPushDeliveryService).sendToGroup(42L, "attendance.marked", payload);
    }

    // Group authority resolution must succeed before any STOMP side effect.
    @Test
    void lessonStarted_pushAuthorityResolvedBeforeStomp() {
        when(webPushDeliveryService.shouldPush("lesson.started")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 42, "subject_name", "Химия");
        Map<String, Object> envelope = Map.of("event_type", "lesson.started", "payload", payload);

        var inOrder = org.mockito.Mockito.inOrder(messagingTemplate, webPushDeliveryService);

        consumer.onEvent(envelope);

        inOrder.verify(webPushDeliveryService).resolveCurrentAudience(42L, "lesson.started");
        inOrder.verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        inOrder.verify(webPushDeliveryService).sendToGroup(42L, "lesson.started", payload, Set.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lesson.started", "excuse.requested"})
    void slowProviderDoesNotBlockWebSocketOrEventProcessing(String eventType) {
        Map<String, Object> payload = Map.of("group_id", 42);
        when(webPushDeliveryService.shouldPush(eventType)).thenReturn(true);
        when(webPushDeliveryService.resolveCurrentAudience(42L, eventType)).thenReturn(Set.of(7L));
        var providerPending = new CompletableFuture<Void>();
        org.mockito.Mockito.lenient().when(webPushDeliveryService.sendToGroup(42L, eventType, payload))
                .thenReturn(providerPending);
        when(webPushDeliveryService.sendToGroup(42L, eventType, payload, Set.of(7L))).thenReturn(providerPending);

        try {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                    () -> consumer.onEvent(Map.of("event_type", eventType, "payload", payload)));
            String destination = "excuse.requested".equals(eventType) ? "/topic/group/42/headman" : "/topic/group/42";
            verify(messagingTemplate).convertAndSend(destination, Map.of("type", eventType, "payload", payload));
            org.assertj.core.api.Assertions.assertThat(providerPending).isNotCompleted();
        } finally {
            providerPending.complete(null);
        }
    }
}
