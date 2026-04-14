package ru.rutcampustrack.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.HttpResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebPushDeliveryServiceTest {

    @Mock
    private PushSubscriptionRepository repository;

    @Mock
    private PushService webPushService;

    @Mock
    private Notification mockNotification;

    private WebPushDeliveryService service;

    @BeforeEach
    void setUp() throws Exception {
        service = spy(new WebPushDeliveryService(repository, webPushService, new ObjectMapper()));
        // Stub createNotification to avoid real EC key parsing in all tests
        doReturn(mockNotification).when(service).createNotification(any(PushSubscriptionDocument.class), any(byte[].class));
    }

    private PushSubscriptionDocument sub(long userId, String endpoint) {
        return PushSubscriptionDocument.builder()
                .id("id-" + userId)
                .userId(userId)
                .groupId(10L)
                .endpoint(endpoint)
                .p256dh("p256dh-key")
                .auth("auth-key")
                .build();
    }

    // Test 1: sendToGroup fetches all subscriptions for given groupId
    @Test
    void sendToGroup_fetchesSubscriptionsForGroupId() throws Exception {
        when(repository.findAllByGroupId(42L)).thenReturn(List.of());

        service.sendToGroup(42L, "lesson.started", Map.of("subject_name", "Математика", "group_id", 42));

        verify(repository).findAllByGroupId(42L);
    }

    // Test 2: sendToGroup calls webPushService.send() for each subscription
    @Test
    void sendToGroup_callsSendForEachSubscription() throws Exception {
        PushSubscriptionDocument sub1 = sub(1L, "https://push.example.com/sub1");
        PushSubscriptionDocument sub2 = sub(2L, "https://push.example.com/sub2");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(sub1, sub2));

        service.sendToGroup(10L, "lesson.started", Map.of("subject_name", "Физика", "group_id", 10));

        verify(webPushService, times(2)).send(any(Notification.class));
    }

    // Test 3: 410 response causes subscription deletion
    @Test
    void sendToGroup_on410_deletesSubscription() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/gone");
        when(repository.findAllByGroupId(5L)).thenReturn(List.of(sub));
        doThrow(new HttpResponseException(410, "Gone"))
                .when(webPushService).send(any(Notification.class));

        service.sendToGroup(5L, "lesson.started", Map.of("subject_name", "Химия", "group_id", 5));

        verify(repository).deleteByEndpoint("https://push.example.com/gone");
    }

    // Test 4: Non-410 exception does NOT trigger deletion, processing continues
    @Test
    void sendToGroup_onNon410Error_doesNotDeleteSubscription() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/err");
        when(repository.findAllByGroupId(5L)).thenReturn(List.of(sub));
        doThrow(new HttpResponseException(500, "Internal Server Error"))
                .when(webPushService).send(any(Notification.class));

        service.sendToGroup(5L, "lesson.started", Map.of("subject_name", "Биология", "group_id", 5));

        verify(repository, never()).deleteByEndpoint(anyString());
    }

    // Test 5: lesson.started payload has correct title and body
    @Test
    void sendToGroup_lessonStarted_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/s1");
        when(repository.findAllByGroupId(7L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(7L, "lesson.started",
                Map.of("subject_name", "Математика", "group_id", 7));
        result.join();

        String payloadStr = new String(payloadCaptor.getValue());
        assertThat(payloadStr).contains("Пара началась");
        assertThat(payloadStr).contains("Математика");
    }

    // Test 6: lesson.cancelled payload has correct title and body
    @Test
    void sendToGroup_lessonCancelled_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/s2");
        when(repository.findAllByGroupId(7L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(7L, "lesson.cancelled",
                Map.of("subject_name", "Физика", "group_id", 7));
        result.join();

        String payloadStr = new String(payloadCaptor.getValue());
        assertThat(payloadStr).contains("Пара отменена");
        assertThat(payloadStr).contains("Физика");
    }

    // --- 58-07 / BUG-006-6: group.renamed / group.archived ---

    @Test
    void shouldPush_groupRenamed_isTrue() {
        assertThat(service.shouldPush("group.renamed")).isTrue();
    }

    @Test
    void shouldPush_groupArchived_isTrue() {
        assertThat(service.shouldPush("group.archived")).isTrue();
    }

    @Test
    void sendToGroup_groupRenamed_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/gr");
        when(repository.findAllByGroupId(11L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(11L, "group.renamed",
                Map.of("group_id", 11));
        result.join();

        String payloadStr = new String(payloadCaptor.getValue());
        assertThat(payloadStr).contains("Группа переименована");
        assertThat(payloadStr).contains("новое название");
    }

    @Test
    void sendToGroup_groupArchived_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/ga");
        when(repository.findAllByGroupId(22L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(22L, "group.archived",
                Map.of("group_id", 22));
        result.join();

        String payloadStr = new String(payloadCaptor.getValue());
        assertThat(payloadStr).contains("Группа архивирована");
        assertThat(payloadStr).contains("выпуск");
    }

    // Test 7: homework.published payload has correct title and body
    @Test
    void sendToGroup_homeworkPublished_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/s3");
        when(repository.findAllByGroupId(7L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(7L, "homework.published",
                Map.of("subject_name", "История", "title", "Лабораторная 4", "group_id", 7));
        result.join();

        String payloadStr = new String(payloadCaptor.getValue());
        assertThat(payloadStr).contains("Новое ДЗ");
        assertThat(payloadStr).contains("История");
        assertThat(payloadStr).contains("Лабораторная 4");
    }
}
