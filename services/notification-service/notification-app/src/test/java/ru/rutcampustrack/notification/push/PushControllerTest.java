package ru.rutcampustrack.notification.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.notification.contract.dto.push.SubscribeRequest;
import ru.rutcampustrack.notification.contract.dto.push.UnsubscribeRequest;
import ru.rutcampustrack.notification.contract.dto.push.VapidPublicKeyResponse;
import ru.rutcampustrack.notification.contract.enums.UserRole;
import ru.rutcampustrack.notification.security.RequestContext;
import ru.rutcampustrack.notification.security.RequireRole;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushControllerTest {

    @Mock
    private PushSubscriptionRepository repository;

    @Mock
    private RequestContext requestContext;

    private static final String TEST_VAPID_KEY = "BKR2jT5k1Iu93_V8AHx3cpqE";

    private PushController controller;

    @BeforeEach
    void setUp() {
        controller = new PushController(repository, requestContext, TEST_VAPID_KEY);
    }

    // Test 1: PushController.getVapidPublicKey() returns 200 with EntityModel containing publicKey string
    @Test
    void getVapidPublicKey_returns200WithPublicKey() {
        ResponseEntity<EntityModel<VapidPublicKeyResponse>> response = controller.getVapidPublicKey();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isNotNull();
        assertThat(response.getBody().getContent().getPublicKey()).isEqualTo(TEST_VAPID_KEY);
    }

    // Test 2: PushController.subscribe() stores subscription in repository and returns 201
    @Test
    void subscribe_storesSubscriptionAndReturns201() {
        when(requestContext.getUserId()).thenReturn(10L);
        when(requestContext.getGroupId()).thenReturn(3L);

        SubscribeRequest request = new SubscribeRequest(
                "https://push.example.com/sub/abc",
                new SubscribeRequest.Keys("p256dh-value", "auth-value")
        );

        ResponseEntity<Void> response = controller.subscribe(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(repository).save(any(PushSubscriptionDocument.class));
    }

    // Test 3: PushController.subscribe() uses userId from RequestContext (not from request body)
    @Test
    void subscribe_usesUserIdFromRequestContext() {
        when(requestContext.getUserId()).thenReturn(42L);
        when(requestContext.getGroupId()).thenReturn(5L);

        SubscribeRequest request = new SubscribeRequest(
                "https://push.example.com/sub/xyz",
                new SubscribeRequest.Keys("p256dh-value", "auth-value")
        );

        controller.subscribe(request);

        ArgumentCaptor<PushSubscriptionDocument> captor = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
    }

    // Test 4: PushController.subscribe() uses groupId from RequestContext
    @Test
    void subscribe_usesGroupIdFromRequestContext() {
        when(requestContext.getUserId()).thenReturn(42L);
        when(requestContext.getGroupId()).thenReturn(7L);

        SubscribeRequest request = new SubscribeRequest(
                "https://push.example.com/sub/xyz",
                new SubscribeRequest.Keys("p256dh-value", "auth-value")
        );

        controller.subscribe(request);

        ArgumentCaptor<PushSubscriptionDocument> captor = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGroupId()).isEqualTo(7L);
    }

    @Test
    void subscribe_updatesExistingSubscriptionAndKeepsCreatedAt() {
        when(requestContext.getUserId()).thenReturn(42L);
        when(requestContext.getGroupId()).thenReturn(9L);
        when(requestContext.isHeadman()).thenReturn(true);

        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        PushSubscriptionDocument existing = PushSubscriptionDocument.builder()
                .id("existing-id")
                .userId(42L)
                .groupId(5L)
                .endpoint("https://push.example.com/sub/xyz")
                .p256dh("old-p256dh")
                .auth("old-auth")
                .headman(false)
                .createdAt(createdAt)
                .lastSeen(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
        when(repository.findByUserIdAndEndpoint(42L, "https://push.example.com/sub/xyz"))
                .thenReturn(Optional.of(existing));

        SubscribeRequest request = new SubscribeRequest(
                "https://push.example.com/sub/xyz",
                new SubscribeRequest.Keys("new-p256dh", "new-auth")
        );

        ResponseEntity<Void> response = controller.subscribe(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<PushSubscriptionDocument> captor = ArgumentCaptor.forClass(PushSubscriptionDocument.class);
        verify(repository).save(captor.capture());
        PushSubscriptionDocument saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("existing-id");
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getGroupId()).isEqualTo(9L);
        assertThat(saved.getEndpoint()).isEqualTo("https://push.example.com/sub/xyz");
        assertThat(saved.getP256dh()).isEqualTo("new-p256dh");
        assertThat(saved.getAuth()).isEqualTo("new-auth");
        assertThat(saved.isHeadman()).isTrue();
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getLastSeen()).isAfter(createdAt);
    }

    // Test 5: PushController.unsubscribe() deletes subscription by userId+endpoint and returns 204
    @Test
    void unsubscribe_deletesByUserIdAndEndpointAndReturns204() {
        when(requestContext.getUserId()).thenReturn(42L);

        UnsubscribeRequest request = new UnsubscribeRequest("https://push.example.com/sub/abc");

        ResponseEntity<Void> response = controller.unsubscribe(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(repository).deleteByUserIdAndEndpoint(42L, "https://push.example.com/sub/abc");
    }

    // Test 6: PushController.getVapidPublicKey() has @RequireRole(STUDENT)
    @Test
    void getVapidPublicKey_hasRequireRoleStudentAnnotation() throws NoSuchMethodException {
        Method method = PushController.class.getMethod("getVapidPublicKey");
        RequireRole annotation = method.getAnnotation(RequireRole.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(UserRole.STUDENT);
    }

    // Test 7: PushController.subscribe() has @RequireRole(STUDENT)
    @Test
    void subscribe_hasRequireRoleStudentAnnotation() throws NoSuchMethodException {
        Method method = PushController.class.getMethod("subscribe", SubscribeRequest.class);
        RequireRole annotation = method.getAnnotation(RequireRole.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(UserRole.STUDENT);
    }

    // Test 8: PushController.unsubscribe() has @RequireRole(STUDENT)
    @Test
    void unsubscribe_hasRequireRoleStudentAnnotation() throws NoSuchMethodException {
        Method method = PushController.class.getMethod("unsubscribe", UnsubscribeRequest.class);
        RequireRole annotation = method.getAnnotation(RequireRole.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(UserRole.STUDENT);
    }
}
