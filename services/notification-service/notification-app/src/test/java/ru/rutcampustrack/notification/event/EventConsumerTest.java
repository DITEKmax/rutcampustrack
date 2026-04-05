package ru.rutcampustrack.notification.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private WebPushDeliveryService webPushDeliveryService;

    private EventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EventConsumer(messagingTemplate, webPushDeliveryService);
    }

    // --- Existing STOMP routing tests (must all still pass) ---

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
    void unknownEventType_routesToGroupTopic() {
        Map<String, Object> payload = Map.of("group_id", 42);
        Map<String, Object> envelope = Map.of("event_type", "some.unknown", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/42"),
                eq(Map.of("type", "some.unknown", "payload", payload))
        );
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
        verify(webPushDeliveryService).sendToGroup(42L, "lesson.started", payload);
    }

    // Test 2: lesson.cancelled calls push
    @Test
    void lessonCancelled_triggersPush() {
        when(webPushDeliveryService.shouldPush("lesson.cancelled")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 5, "subject_name", "Физика");
        Map<String, Object> envelope = Map.of("event_type", "lesson.cancelled", "payload", payload);

        consumer.onEvent(envelope);

        verify(webPushDeliveryService).sendToGroup(5L, "lesson.cancelled", payload);
    }

    // Test 3: homework.published calls push
    @Test
    void homeworkPublished_triggersPush() {
        when(webPushDeliveryService.shouldPush("homework.published")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 7, "subject_name", "История", "title", "Лаб 1");
        Map<String, Object> envelope = Map.of("event_type", "homework.published", "payload", payload);

        consumer.onEvent(envelope);

        verify(webPushDeliveryService).sendToGroup(7L, "homework.published", payload);
    }

    // Test 4: excuse.requested does NOT call push
    @Test
    void excuseRequested_doesNotTriggerPush() {
        when(webPushDeliveryService.shouldPush("excuse.requested")).thenReturn(false);
        Map<String, Object> payload = Map.of("group_id", 42, "user_id", 7);
        Map<String, Object> envelope = Map.of("event_type", "excuse.requested", "payload", payload);

        consumer.onEvent(envelope);

        verify(webPushDeliveryService, never()).sendToGroup(anyLong(), anyString(), any());
    }

    // Test 5: attendance.marked does NOT call push
    @Test
    void attendanceMarked_doesNotTriggerPush() {
        when(webPushDeliveryService.shouldPush("attendance.marked")).thenReturn(false);
        Map<String, Object> payload = Map.of("group_id", 42, "user_id", 5);
        Map<String, Object> envelope = Map.of("event_type", "attendance.marked", "payload", payload);

        consumer.onEvent(envelope);

        verify(webPushDeliveryService, never()).sendToGroup(anyLong(), anyString(), any());
    }

    // Test 6: push is called AFTER STOMP (verify call order)
    @Test
    void lessonStarted_pushCalledAfterStomp() {
        when(webPushDeliveryService.shouldPush("lesson.started")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 42, "subject_name", "Химия");
        Map<String, Object> envelope = Map.of("event_type", "lesson.started", "payload", payload);

        var inOrder = org.mockito.Mockito.inOrder(messagingTemplate, webPushDeliveryService);

        consumer.onEvent(envelope);

        inOrder.verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        inOrder.verify(webPushDeliveryService).sendToGroup(42L, "lesson.started", payload);
    }
}
