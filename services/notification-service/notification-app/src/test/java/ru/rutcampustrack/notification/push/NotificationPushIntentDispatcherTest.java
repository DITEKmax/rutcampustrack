package ru.rutcampustrack.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;

import ru.rutcampustrack.notification.history.NotificationAudienceService;
import ru.rutcampustrack.notification.history.NotificationCatalog;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Outcome reporting must preserve the durable worker's terminal ACK and ambiguity boundaries. */
class NotificationPushIntentDispatcherTest {
    final NotificationPushIntentStore store = mock(NotificationPushIntentStore.class);
    final NotificationAudienceService audience = mock(NotificationAudienceService.class);
    final NotificationPreferencesService preferences = mock(NotificationPreferencesService.class);
    final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    final WebPushDeliveryService delivery = mock(WebPushDeliveryService.class);
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final WebPushOutcomeMetrics metrics = new WebPushOutcomeMetrics(registry);
    final NotificationPushIntentDispatcher dispatcher =
            new NotificationPushIntentDispatcher(
                    store,
                    audience,
                    preferences,
                    subscriptions,
                    delivery,
                    new NotificationCatalog(),
                    metrics);
    final PushSubscriptionDocument sub =
            PushSubscriptionDocument.builder().id("synthetic-sub").userId(12L).build();
    final String key = "12:synthetic-sub";
    final Document job =
            new Document("_id", "synthetic-intent")
                    .append(
                            "context",
                            new Document("event_type", "lesson.cancelled")
                                    .append(
                                            "payload",
                                            new Document("lesson_id", 51L).append("group_id", 7L)))
                    .append("roles", new Document("12", List.of("TEACHER")))
                    .append("delivered", List.of())
                    .append("created_at", Date.from(Instant.parse("2026-11-09T10:00:00Z")));

    @BeforeEach
    void setUp() {
        when(store.claim()).thenReturn(job).thenReturn(null);
        when(subscriptions.findAllByUserIdIn(any())).thenReturn(List.of(sub));
        when(preferences.isEnabledForUser(eq(12L), anyString(), anyMap())).thenReturn(true);
        when(audience.current(anyMap(), anyMap())).thenReturn(Map.of(12L, Set.of("TEACHER")));
        when(store.startProvider(job, key)).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 404, 410})
    void terminalProcessedKeysAreNotRetriedOrMistakenForDispatchUncertainty(int status)
            throws Exception {
        when(delivery.sendToSubscription(eq(sub), anyString(), any())).thenReturn(status);
        dispatcher.dispatchOne();
        dispatcher.dispatchOne();
        verify(store).acknowledge(job, key);
        verify(store).complete(job);
        verify(store, never()).retry(any(), anyString());
        verify(store, never()).uncertain(any(), anyString());
        verify(delivery, times(1)).sendToSubscription(eq(sub), anyString(), any());
        assertThat(outcome("uncertain")).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503, 403})
    void explicitProviderRejectionKeepsExistingRetrySemantics(int status) throws Exception {
        when(delivery.sendToSubscription(eq(sub), anyString(), any())).thenReturn(status);
        dispatcher.dispatchOne();
        verify(store).retry(job, "ProviderRejected" + status);
        verify(store, never()).acknowledge(any(), anyString());
        verify(store, never()).complete(any());
        verify(store, never()).uncertain(any(), anyString());
        // The real delivery service records the factual retryable provider response, once.
        assertThat(outcome("uncertain")).isZero();
    }

    @Test
    void timeoutAfterAttemptStartIsUncertainAndNeverAutomaticallyRetried() throws Exception {
        when(delivery.sendToSubscription(eq(sub), anyString(), any()))
                .thenThrow(new SocketTimeoutException("synthetic timeout"));
        dispatcher.dispatchOne();
        verify(store).uncertain(job, "SocketTimeoutException");
        verify(store, never()).retry(any(), anyString());
        verify(store, never()).complete(any());
        assertThat(outcome("uncertain")).isEqualTo(1);
    }

    @Test
    void providerAcceptedButUnpersistedAckRemainsUncertain() throws Exception {
        sub.setEndpoint("https://push.example.test/synthetic");
        var provider = mock(PushService.class);
        var wireNotification = mock(Notification.class);
        var realDelivery =
                spy(
                        new WebPushDeliveryService(
                                subscriptions,
                                provider,
                                new ObjectMapper(),
                                mock(MongoTemplate.class),
                                Clock.systemUTC(),
                                metrics));
        doReturn(wireNotification)
                .when(realDelivery)
                .createNotification(eq(sub), any(byte[].class));
        when(provider.send(wireNotification, Encoding.AES128GCM))
                .thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 201, "Created"));
        doThrow(new IllegalStateException("synthetic lease loss"))
                .when(store)
                .acknowledge(job, key);
        new NotificationPushIntentDispatcher(
                        store,
                        audience,
                        preferences,
                        subscriptions,
                        realDelivery,
                        new NotificationCatalog(),
                        metrics)
                .dispatchOne();
        assertThat(
                        registry.get("notification.webpush.provider.responses")
                                .tag("outcome", "accepted_by_provider")
                                .counter()
                                .count())
                .isEqualTo(1);
        assertThat(outcome("uncertain")).isEqualTo(1);
        verify(store).uncertain(job, "IllegalStateException");
        verify(store, never()).retry(any(), anyString());
        verify(store, never()).complete(any());
    }

    @Test
    void completedWithoutSubscriptionsIsAnExplicitSkip() {
        when(subscriptions.findAllByUserIdIn(any())).thenReturn(List.of());
        dispatcher.dispatchOne();
        assertThat(outcome("skipped_no_subscriptions")).isEqualTo(1);
        verify(store).complete(job);
        verifyNoInteractions(delivery);
    }

    @Test
    void disabledPreferencesAreSkippedBeforeAuthorityOrProviderAttempt() {
        when(preferences.isEnabledForUser(eq(12L), anyString(), anyMap())).thenReturn(false);
        dispatcher.dispatchOne();
        assertThat(outcome("skipped_preferences")).isEqualTo(1);
        verify(store).complete(job);
        verifyNoInteractions(audience, delivery);
    }

    @Test
    void revokedCurrentAudienceIsSkippedDespiteStoredRole() {
        when(audience.current(anyMap(), anyMap())).thenReturn(Map.of());
        dispatcher.dispatchOne();
        assertThat(outcome("skipped_audience")).isEqualTo(1);
        verify(store).complete(job);
        verifyNoInteractions(delivery);
    }

    @Test
    void authorityFailureBeforeAttemptRemainsRetryable() {
        when(audience.current(anyMap(), anyMap()))
                .thenThrow(new IllegalStateException("synthetic"));
        dispatcher.dispatchOne();
        assertThat(outcome("retryable")).isEqualTo(1);
        verify(store).retry(job, "IllegalStateException");
        verifyNoInteractions(delivery);
    }

    @Test
    void processedKeyAndLostLeaseNeverCauseAnotherProviderAttempt() {
        job.put("delivered", List.of(key));
        dispatcher.dispatchOne();
        verifyNoInteractions(delivery);
        verify(store).complete(job);
        job.put("delivered", List.of());
        when(store.claim()).thenReturn(job);
        when(store.startProvider(job, key)).thenReturn(false);
        dispatcher.dispatchOne();
        verifyNoInteractions(delivery);
        assertThat(outcome("uncertain")).isZero();
    }

    private double outcome(String value) {
        return registry.get("notification.webpush.dispatch.outcomes")
                .tag("outcome", value)
                .counter()
                .count();
    }
}
