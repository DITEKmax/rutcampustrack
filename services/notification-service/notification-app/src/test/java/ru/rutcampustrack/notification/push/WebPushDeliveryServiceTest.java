package ru.rutcampustrack.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.HttpResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import ru.rutcampustrack.notification.history.AcademicGroupMemberClient;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private NotificationPreferencesService preferencesService;

    @Mock
    private ReminderAttendanceStateService reminderAttendanceStateService;

    @Mock
    private AcademicGroupMemberClient academicGroupMemberClient;

    private WebPushDeliveryService service;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(preferencesService.isEnabledForUser(any(), anyString())).thenReturn(true);
        lenient().when(academicGroupMemberClient.getCurrentMemberUserIds(anyLong()))
                .thenReturn(List.of(1L, 2L, 3L));
        service = spy(new WebPushDeliveryService(
                repository,
                webPushService,
                new ObjectMapper(),
                mongoTemplate,
                Clock.systemUTC(),
                preferencesService,
                reminderAttendanceStateService,
                academicGroupMemberClient));
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

    @ParameterizedTest
    @ValueSource(strings = {"lesson.started", "lesson.reminder", "lesson.blocked", "lesson.cancelled",
            "lesson.one_off.created", "lesson.one_off.cancelled", "homework.published", "homework.updated",
            "group.renamed", "group.archived"})
    void groupPushStopsDeliveringAfterTransferOrRevocationDespiteSavedSubscription(String eventType) throws Exception {
        var member = sub(1L, "https://push.example.com/member");
        var transferred = sub(2L, "https://push.example.com/transferred");
        var revoked = sub(3L, "https://push.example.com/revoked");
        revoked.setHeadman(true);
        var anonymous = sub(4L, "https://push.example.com/anonymous");
        anonymous.setUserId(null);
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(member, transferred, revoked, anonymous));
        when(academicGroupMemberClient.getCurrentMemberUserIds(10L))
                .thenReturn(List.of(1L, 2L, 3L), List.of(1L));
        var recipients = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        doReturn(mockNotification).when(service).createNotification(recipients.capture(), any(byte[].class));

        service.sendToGroup(10L, eventType, Map.of("group_id", 10)).join();
        service.sendToGroup(10L, eventType, Map.of("group_id", 10)).join();

        assertThat(recipients.getAllValues()).containsExactly(member, transferred, revoked, member);
    }

    @Test
    void groupPushFailsClosedOnAuthorityFailureThenFreshRetryRechecksRevokedMember() throws Exception {
        var revoked = sub(2L, "https://push.example.com/revoked");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(revoked));
        when(academicGroupMemberClient.getCurrentMemberUserIds(10L))
                .thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException()).thenReturn(List.of());

        assertThatThrownBy(() -> service.sendToGroup(10L, "homework.published", Map.of("group_id", 10)).join())
                .hasCauseInstanceOf(io.grpc.StatusRuntimeException.class);
        service.sendToGroup(10L, "homework.published", Map.of("group_id", 10)).join();

        verify(webPushService, never()).send(any(Notification.class));
        verify(repository, never()).deleteByEndpoint(anyString());
    }

    @Test
    void groupPushFailsClosedWhenResolverIsMissing() throws Exception {
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(sub(1L, "https://push.example.com/member")));
        var noResolver = new WebPushDeliveryService(repository, webPushService, new ObjectMapper(),
                mongoTemplate, Clock.systemUTC());

        assertThatThrownBy(() -> noResolver.sendToGroup(10L, "lesson.started", Map.of("group_id", 10)).join())
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(webPushService, never()).send(any(Notification.class));
    }

    @Test
    void groupPushProviderRunsAsynchronouslyAfterAudienceAuthorization() throws Exception {
        var member = sub(1L, "https://push.example.com/member");
        var revoked = sub(2L, "https://push.example.com/revoked");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(member, revoked));
        when(academicGroupMemberClient.getCurrentMemberUserIds(10L)).thenReturn(List.of(1L));
        var recipients = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        doReturn(mockNotification).when(service).createNotification(recipients.capture(), any(byte[].class));
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(webPushService.send(any(Notification.class))).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return null;
        });
        var executor = Executors.newSingleThreadExecutor();
        var asyncProcessor = new AsyncAnnotationBeanPostProcessor();
        var beanFactory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beanFactory.registerSingleton("pushTaskExecutor", executor);
        asyncProcessor.setBeanFactory(beanFactory);
        var asyncService = (WebPushDeliveryService) asyncProcessor.postProcessAfterInitialization(service, "asyncPush");
        try {
            Set<Long> audience = asyncService.resolveCurrentAudience(10L, "lesson.started");
            assertThatThrownBy(() -> audience.add(2L)).isInstanceOf(UnsupportedOperationException.class);
            var pending = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                    () -> asyncService.sendToGroup(10L, "lesson.started", Map.of("group_id", 10), audience));

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(pending).isNotCompleted();
            assertThat(recipients.getAllValues()).containsExactly(member);
            release.countDown();
            pending.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
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
    void sendToGroup_bindsEachEnvelopeToItsRecipientAndEndpointWithoutCredentials() throws Exception {
        var first = sub(1L, "https://push.example.com/first");
        var second = sub(2L, "https://push.example.com/second");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(first, second));
        ArgumentCaptor<byte[]> payloads = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloads.capture());

        service.sendToGroup(10L, "lesson.started", Map.of("group_id", 10));

        var mapper = new ObjectMapper();
        var one = mapper.readTree(payloads.getAllValues().get(0));
        var two = mapper.readTree(payloads.getAllValues().get(1));
        assertThat(one.get("recipientUserId").asText()).isEqualTo("1");
        assertThat(two.get("recipientUserId").asText()).isEqualTo("2");
        assertThat(one.get("subscriptionFingerprint").asText())
                .isEqualTo(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(first.getEndpoint().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThat(one.get("subscriptionFingerprint").asText()).isNotEqualTo(two.get("subscriptionFingerprint").asText());
        assertThat(one.toString()).doesNotContain(first.getEndpoint(), first.getAuth(), first.getP256dh());
    }

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

    @Test
    void sendToGroup_lessonClosed_targetsCurrentHeadmanAndUsesExistingNotificationPayload() throws Exception {
        PushSubscriptionDocument currentHeadman = sub(1L, "https://push.example.com/current-headman");
        currentHeadman.setGroupId(42L);
        // The stored subscription flag can predate a reassignment; Academic is authoritative for this event.
        currentHeadman.setHeadman(false);
        PushSubscriptionDocument student = sub(2L, "https://push.example.com/student");
        student.setGroupId(42L);
        PushSubscriptionDocument formerHeadman = sub(3L, "https://push.example.com/former-headman");
        formerHeadman.setGroupId(42L);
        formerHeadman.setHeadman(true);
        when(repository.findAllByGroupId(42L)).thenReturn(List.of(currentHeadman, student, formerHeadman));
        when(academicGroupMemberClient.getCurrentHeadmanUserIds(42L)).thenReturn(List.of(1L));

        ArgumentCaptor<PushSubscriptionDocument> subscriptionCaptor =
                ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service)
                .createNotification(subscriptionCaptor.capture(), payloadCaptor.capture());

        service.sendToGroup(42L, "lesson.closed", Map.of(
                "group_id", 42,
                "lesson_id", 101,
                "subject_id", 8)).join();

        assertThat(subscriptionCaptor.getAllValues()).containsExactly(currentHeadman);
        verify(academicGroupMemberClient).getCurrentHeadmanUserIds(42L);
        verify(preferencesService).isEnabledForUser(1L, "lesson.closed");
        var notification = new ObjectMapper().readTree(payloadCaptor.getValue());
        assertThat(notification.get("title").asText()).isEqualTo("Пара завершена");
        assertThat(notification.get("body").asText()).isEqualTo("Откройте расписание для подробностей");
        assertThat(notification.get("event_type").asText()).isEqualTo("lesson.closed");
        assertThat(notification.get("data").get("lesson_id").asLong()).isEqualTo(101L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"excuse.requested", "late_checkin.requested"})
    void requestedPushUsesCurrentHeadmanDespiteStaleSubscriptionFlags(String eventType) throws Exception {
        var currentHeadman = sub(1L, "https://push.example.com/current-headman");
        var formerHeadman = sub(2L, "https://push.example.com/former-headman");
        formerHeadman.setHeadman(true);
        var student = sub(3L, "https://push.example.com/student");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(currentHeadman, formerHeadman, student));
        when(academicGroupMemberClient.getCurrentHeadmanUserIds(10L)).thenReturn(List.of(1L));
        var recipients = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        doReturn(mockNotification).when(service).createNotification(recipients.capture(), any(byte[].class));

        service.sendToGroup(10L, eventType, Map.of("group_id", 10, "user_id", 3)).join();

        assertThat(recipients.getAllValues()).containsExactly(currentHeadman);
        verify(webPushService).send(mockNotification);
    }

    @ParameterizedTest
    @ValueSource(strings = {"excuse.requested", "late_checkin.requested"})
    void requestedPushFailsClosedWhenCurrentHeadmanLookupFails(String eventType) throws Exception {
        var formerHeadman = sub(2L, "https://push.example.com/former-headman");
        formerHeadman.setHeadman(true);
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(formerHeadman));
        when(academicGroupMemberClient.getCurrentHeadmanUserIds(10L))
                .thenThrow(new IllegalStateException("Academic unavailable"));

        assertThatThrownBy(() -> service.sendToGroup(10L, eventType, Map.of("group_id", 10)).join())
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(webPushService, never()).send(any(Notification.class));
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
    void shouldPush_lessonReminder_isTrue() {
        assertThat(service.shouldPush("lesson.reminder")).isTrue();
    }

    @Test
    void shouldPush_lessonClosed_isTrue() {
        assertThat(service.shouldPush("lesson.closed")).isTrue();
    }

    @Test
    void shouldPush_lessonBlocked_isTrue() {
        assertThat(service.shouldPush("lesson.blocked")).isTrue();
    }

    @Test
    void shouldPush_attendanceMarked_isTrue() {
        assertThat(service.shouldPush("attendance.marked")).isTrue();
    }

    @Test
    void shouldPush_homeworkDigestAndReminder_isTrue() {
        assertThat(service.shouldPush("homework.weekly_digest")).isTrue();
        assertThat(service.shouldPush("homework.due_reminder")).isTrue();
    }

    @Test
    void sendToGroup_groupRenamed_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/gr");
        when(repository.findAllByGroupId(11L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(11L, "group.renamed",
                Map.of("group_id", 11, "new_name", "БИ-2401"));
        result.join();

        String payloadStr = new String(payloadCaptor.getValue());
        assertThat(payloadStr).contains("Группа переименована");
        // 04-17: body теперь включает новое имя для осмысленного push-уведомления.
        assertThat(payloadStr).contains("БИ-2401");
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
        assertThat(payloadStr).contains("выпустилась");
    }

    // Test 7: homework.published payload has correct title and body
    @Test
    void sendToGroup_homeworkPublished_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/s3");
        when(repository.findAllByGroupId(7L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(7L, "homework.published",
                Map.of(
                        "subject_name", "История",
                        "title", "Лабораторная 4",
                        "description", "Read chapter 4",
                        "link", "https://example.com/hw.pdf",
                        "group_id", 7));
        result.join();

        String payloadStr = new String(payloadCaptor.getValue());
        assertThat(payloadStr).contains("Новое ДЗ");
        assertThat(payloadStr).contains("История");
        assertThat(payloadStr).contains("Лабораторная 4");
        String body = new ObjectMapper().readTree(payloadStr).get("body").asText();
        assertThat(body).contains("Лабораторная 4");
        assertThat(body).contains("Read chapter 4");
        assertThat(body).contains("https://example.com/hw.pdf");
    }

    @Test
    void sendToGroup_homeworkDueReminder_targetsPayloadUserOnly() throws Exception {
        PushSubscriptionDocument target = sub(2L, "https://push.example.com/homework-target");
        PushSubscriptionDocument other = sub(3L, "https://push.example.com/homework-other");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(target, other));

        ArgumentCaptor<PushSubscriptionDocument> subCaptor = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(subCaptor.capture(), payloadCaptor.capture());

        service.sendToGroup(10L, "homework.due_reminder",
                Map.of(
                        "group_id", 10,
                        "user_id", 2,
                        "due_date", "2026-05-05",
                        "homework", Map.of(
                                "subject_name", "Math",
                                "title", "Essay",
                                "lesson_number", 2,
                                "lesson_date", "2026-05-05"
                        )
                ))
                .join();

        verify(webPushService, times(1)).send(any(Notification.class));
        assertThat(subCaptor.getValue().getUserId()).isEqualTo(2L);

        var json = new ObjectMapper().readTree(new String(payloadCaptor.getValue()));
        assertThat(json.get("event_type").asText()).isEqualTo("homework.due_reminder");
        assertThat(json.get("body").asText()).contains("Math").contains("Essay");
    }

    @Test
    void sendToGroup_homeworkWeeklyDigest_buildsStructuredBody() throws Exception {
        PushSubscriptionDocument sub = sub(2L, "https://push.example.com/homework-digest");
        PushSubscriptionDocument other = sub(3L, "https://push.example.com/homework-digest-other");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(sub, other));

        ArgumentCaptor<PushSubscriptionDocument> subCaptor = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(subCaptor.capture(), payloadCaptor.capture());

        service.sendToGroup(10L, "homework.weekly_digest",
                Map.of(
                        "group_id", 10,
                        "user_id", 2,
                        "week_start", "2026-05-04",
                        "week_end", "2026-05-10",
                        "total_count", 2,
                        "items", List.of(
                                Map.of(
                                        "subject_name", "Math",
                                        "title", "Essay",
                                        "lesson_number", 2,
                                        "lesson_date", "2026-05-05"
                                ),
                                Map.of(
                                        "subject_name", "History",
                                        "title", "Read",
                                        "lesson_number", 1,
                                        "lesson_date", "2026-05-06"
                                )
                        )
                ))
                .join();

        verify(webPushService, times(1)).send(any(Notification.class));
        assertThat(subCaptor.getValue().getUserId()).isEqualTo(2L);

        var json = new ObjectMapper().readTree(new String(payloadCaptor.getValue()));
        assertThat(json.get("event_type").asText()).isEqualTo("homework.weekly_digest");
        assertThat(json.get("body").asText())
                .contains("Math")
                .contains("Essay")
                .contains("History")
                .contains("Read");
    }

    @Test
    void sendToGroup_lessonReminder_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/reminder");
        when(repository.findAllByGroupId(7L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(7L, "lesson.reminder",
                Map.of("group_id", 7, "lesson_number", 3, "start_time", "14:30", "end_time", "16:00"));
        result.join();

        String payloadStr = new String(payloadCaptor.getValue());
        var json = new ObjectMapper().readTree(payloadStr);
        assertThat(json.get("event_type").asText()).isEqualTo("lesson.reminder");
        assertThat(json.get("body").asText()).contains("3").contains("14:30");
    }

    @Test
    void sendToGroup_lessonBlocked_buildsTitleAndBody() throws Exception {
        PushSubscriptionDocument sub = sub(1L, "https://push.example.com/blocked");
        when(repository.findAllByGroupId(7L)).thenReturn(List.of(sub));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(any(), payloadCaptor.capture());

        service.sendToGroup(7L, "lesson.blocked",
                Map.of("group_id", 7, "lesson_number", 3, "start_time", "14:30", "end_time", "16:00"))
                .join();

        String payloadStr = new String(payloadCaptor.getValue());
        var json = new ObjectMapper().readTree(payloadStr);
        assertThat(json.get("event_type").asText()).isEqualTo("lesson.blocked");
        assertThat(json.get("title").asText()).contains("заблокирована");
        assertThat(json.get("body").asText()).contains("3").contains("14:30").contains("староста");
    }

    @Test
    void sendToGroup_lessonReminder_skipsAlreadyMarkedStudents() throws Exception {
        PushSubscriptionDocument marked = sub(1L, "https://push.example.com/marked");
        PushSubscriptionDocument unmarked = sub(2L, "https://push.example.com/unmarked");
        when(repository.findAllByGroupId(7L)).thenReturn(List.of(marked, unmarked));
        when(reminderAttendanceStateService.isMarked(101L, 1L)).thenReturn(true);
        when(reminderAttendanceStateService.isMarked(101L, 2L)).thenReturn(false);

        service.sendToGroup(7L, "lesson.reminder", Map.of("group_id", 7, "lesson_id", 101)).join();

        verify(webPushService, times(1)).send(any(Notification.class));
        verify(reminderAttendanceStateService).isMarked(101L, 1L);
        verify(reminderAttendanceStateService).isMarked(101L, 2L);
    }

    @Test
    void sendToGroup_lessonStarted_respectsReminderPreferences() throws Exception {
        PushSubscriptionDocument enabled = sub(1L, "https://push.example.com/enabled");
        PushSubscriptionDocument disabled = sub(2L, "https://push.example.com/disabled");
        when(repository.findAllByGroupId(7L)).thenReturn(List.of(enabled, disabled));
        when(preferencesService.isEnabledForUser(1L, "lesson.started")).thenReturn(true);
        when(preferencesService.isEnabledForUser(2L, "lesson.started")).thenReturn(false);

        service.sendToGroup(7L, "lesson.started", Map.of("group_id", 7, "lesson_id", 101)).join();

        verify(webPushService, times(1)).send(any(Notification.class));
    }

    @Test
    void sendToGroup_attendanceMarkedByHeadman_targetsPayloadUserOnly() throws Exception {
        PushSubscriptionDocument target = sub(2L, "https://push.example.com/target");
        PushSubscriptionDocument other = sub(3L, "https://push.example.com/other");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(target, other));

        ArgumentCaptor<PushSubscriptionDocument> subCaptor = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doAnswer(inv -> mockNotification).when(service).createNotification(subCaptor.capture(), payloadCaptor.capture());

        CompletableFuture<Void> result = service.sendToGroup(10L, "attendance.marked",
                Map.of(
                        "group_id", 10,
                        "user_id", 2,
                        "marked_by", "headman",
                        "status", "present",
                        "lesson_number", 2,
                        "subject_name", "Physics",
                        "lesson_date", "2026-04-17"
                ));
        result.join();

        verify(webPushService, times(1)).send(any(Notification.class));
        assertThat(subCaptor.getValue().getUserId()).isEqualTo(2L);

        String payloadStr = new String(payloadCaptor.getValue());
        var json = new ObjectMapper().readTree(payloadStr);
        assertThat(json.get("event_type").asText()).isEqualTo("attendance.marked");
        assertThat(json.get("body").asText())
                .contains("Присутствует (+)")
                .contains("Physics")
                .contains("17.04")
                .doesNotContain("(б)");
    }

    @Test
    void sendToGroup_attendanceMarkedNotByHeadman_skipsPush() throws Exception {
        PushSubscriptionDocument sub = sub(2L, "https://push.example.com/self");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(sub));

        CompletableFuture<Void> result = service.sendToGroup(10L, "attendance.marked",
                Map.of(
                        "group_id", 10,
                        "user_id", 2,
                        "marked_by", "self",
                        "status", "present"
                ));
        result.join();

        verify(webPushService, never()).send(any(Notification.class));
    }

    @Test
    void sendToGroup_geoCancelledLateCheckinDoesNotSendFalseRejectedPush() throws Exception {
        PushSubscriptionDocument target = sub(2L, "https://push.example.com/target");
        when(repository.findAllByGroupId(10L)).thenReturn(List.of(target));

        service.sendToGroup(10L, "late_checkin.decided", Map.of(
                "group_id", 10,
                "user_id", 2,
                "status", "cancelled",
                "resolution_reason", "geo_confirmed"
        )).join();

        verify(webPushService, never()).send(any(Notification.class));
    }
}
