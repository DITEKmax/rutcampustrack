package ru.rutcampustrack.notification.push;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.notification.contract.api.PushApi;
import ru.rutcampustrack.notification.contract.dto.push.SubscribeRequest;
import ru.rutcampustrack.notification.contract.dto.push.UnsubscribeRequest;
import ru.rutcampustrack.notification.contract.dto.push.VapidPublicKeyResponse;
import ru.rutcampustrack.notification.contract.enums.UserRole;
import ru.rutcampustrack.notification.security.RequestContext;
import ru.rutcampustrack.notification.security.RequireRole;

import java.time.Instant;

/**
 * REST controller implementing the PushApi contract for Web Push subscription management.
 *
 * Per D-11/D-12: Controller implements PushApi interface; URL mappings are in the interface only.
 * Per T-27-01: userId and groupId come from RequestContext (Gateway-injected headers),
 *              NOT from request body — prevents subscribing as another user.
 * Per T-27-05: All endpoints require STUDENT role via @RequireRole annotation.
 * Per T-27-07: Unsubscribe scoped to current userId+endpoint — cannot delete another user's subscription.
 */
@RestController
public class PushController implements PushApi {

    private final PushSubscriptionRepository repository;
    private final RequestContext requestContext;
    private final String vapidPublicKey;

    public PushController(
            PushSubscriptionRepository repository,
            RequestContext requestContext,
            @Value("${vapid.public-key}") String vapidPublicKey) {
        this.repository = repository;
        this.requestContext = requestContext;
        this.vapidPublicKey = vapidPublicKey;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<VapidPublicKeyResponse>> getVapidPublicKey() {
        VapidPublicKeyResponse response = new VapidPublicKeyResponse(vapidPublicKey);
        return ResponseEntity.ok(EntityModel.of(response));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request) {
        Instant now = Instant.now();
        Long userId = requestContext.getUserId();
        PushSubscriptionDocument doc = repository.findByUserIdAndEndpoint(userId, request.endpoint())
                .orElseGet(() -> PushSubscriptionDocument.builder()
                        .userId(userId)
                        .endpoint(request.endpoint())
                        .createdAt(now)
                        .build());

        doc.setGroupId(requestContext.getGroupId());
        doc.setP256dh(request.keys().p256dh());
        doc.setAuth(request.keys().auth());
        doc.setHeadman(requestContext.isHeadman());
        doc.setLastSeen(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }

        repository.save(doc);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        repository.deleteByUserIdAndEndpoint(requestContext.getUserId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
