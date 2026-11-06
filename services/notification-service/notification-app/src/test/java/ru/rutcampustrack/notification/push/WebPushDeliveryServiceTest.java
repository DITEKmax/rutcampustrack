package ru.rutcampustrack.notification.push;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import nl.martijndwars.webpush.*;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.notification.history.*;

class WebPushDeliveryServiceTest {
    final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    final PushService provider = mock(PushService.class);
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final ObjectMapper mapper = new ObjectMapper();
    final WebPushDeliveryService service =
            spy(new WebPushDeliveryService(repository, provider, mapper, mongo, Clock.systemUTC()));
    final PushSubscriptionDocument sub =
            PushSubscriptionDocument.builder()
                    .id("teacher-sub")
                    .userId(12L)
                    .endpoint("https://push.example.test/synthetic")
                    .p256dh("synthetic-key")
                    .auth("synthetic-auth")
                    .build();

    @Test
    void legacyGroupEntryPointsFailClosedWithoutSelectingSubscriptionsOrContactingProvider() {
        assertThatThrownBy(() -> service.sendToGroup(7, "lesson.cancelled", Map.of()).join())
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                service.sendToGroup(7, "lesson.cancelled", Map.of(), Set.of(12L))
                                        .join())
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.resolveCurrentAudience(7, "lesson.cancelled"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.resolveEligibleAudience(7, "lesson.cancelled", Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.resolveReminderAudience(7, Map.of()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository, provider, mongo);
    }

    @Test
    void providerEnvelopeUsesCanonicalTeacherActionAndFingerprintWithoutRawPayloadCredentials()
            throws Exception {
        byte[][] wire = new byte[1][];
        Notification notification = mock(Notification.class);
        doAnswer(
                        call -> {
                            wire[0] = call.getArgument(1);
                            return notification;
                        })
                .when(service)
                .createNotification(eq(sub), any(byte[].class));
        when(provider.send(notification, Encoding.AES128GCM))
                .thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 201, "Created"));
        assertThat(service.sendToSubscription(sub, "lesson.cancelled", item())).isEqualTo(201);
        var json = mapper.readTree(wire[0]);
        assertThat(json.path("recipientUserId").asText()).isEqualTo("12");
        assertThat(json.path("subscriptionFingerprint").asText()).matches("[a-f0-9]{64}");
        assertThat(json.path("body").asText()).isEqualTo("Дата занятия: 2026-10-10");
        assertThat(json.path("data").size()).isZero();
        assertThat(new String(wire[0], java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("synthetic-key", "synthetic-auth", "https://push.example");
        var action =
                mapper.readTree(
                        Base64.getUrlDecoder().decode(json.path("notificationAction").asText()));
        assertThat(action.path("action").path("lessonId").asText()).isEqualTo("51");
        assertThat(action.path("action").path("role").asText()).isEqualTo("TEACHER");
        verify(mongo).updateMulti(any(), any(), eq(PushSubscriptionDocument.class));
    }

    @Test
    void roomChangeProviderBodyKeepsBothRoomsBeforeTheDetailAndTextLimits() throws Exception {
        Notification notification = mock(Notification.class);
        doReturn(notification).when(service).createNotification(eq(sub), any(byte[].class));
        when(provider.send(notification, Encoding.AES128GCM))
                .thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 201, "Created"));
        var changedRoom =
                item(
                        NotificationType.LESSON_UPDATED,
                        Map.of(
                                "lesson_number", 1,
                                "source", Map.of("room", "А-214"),
                                "target",
                                        Map.of(
                                                "lesson_id", 51L,
                                                "subject_name", "Математика".repeat(80),
                                                "lesson_type", "practice",
                                                "room", "А-508")));

        assertThat(service.sendToSubscription(sub, "lesson.updated", changedRoom)).isEqualTo(201);

        var wire = ArgumentCaptor.forClass(byte[].class);
        verify(service).createNotification(eq(sub), wire.capture());
        var json = mapper.readTree(wire.getValue());
        assertThat(json.path("title").asText()).isEqualTo("Изменилась аудитория");
        assertThat(json.path("body").asText())
                .startsWith("Предыдущая аудитория: А-214 Новая аудитория: А-508 Предмет:")
                .hasSizeLessThanOrEqualTo(601);
    }

    @Test
    void rejectedStatusIsReturnedForDurableRetryAndExpiredEndpointOnlyIsRemoved() throws Exception {
        Notification notification = mock(Notification.class);
        doReturn(notification).when(service).createNotification(eq(sub), any(byte[].class));
        when(provider.send(notification, Encoding.AES128GCM))
                .thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 503, "Unavailable"))
                .thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 410, "Gone"));
        assertThat(service.sendToSubscription(sub, "lesson.cancelled", item())).isEqualTo(503);
        verifyNoInteractions(repository, mongo);
        assertThat(service.sendToSubscription(sub, "lesson.cancelled", item())).isEqualTo(410);
        verify(repository).deleteByEndpoint(sub.getEndpoint());
        verifyNoInteractions(mongo);
    }

    private NotificationInboxDto.Item item() {
        return item(NotificationType.LESSON_CANCELLED,
                Map.of("lesson_id", 51L, "lesson_date", "2026-10-10"));
    }

    private NotificationInboxDto.Item item(NotificationType type, Map<String, Object> payload) {
        Instant now = Instant.parse("2026-10-10T10:00:00Z");
        var doc =
                NotificationHistoryDocument.builder()
                        .id("wire")
                        .type(type)
                        .sentAt(now)
                        .payload(payload)
                        .roleViews(
                                Map.of(
                                        "TEACHER",
                                        NotificationHistoryDocument.RoleView.builder()
                                                .orderAt(now)
                                                .revision(0)
                                                .build()))
                        .build();
        return new NotificationCatalog().present(doc, "TEACHER");
    }
}
