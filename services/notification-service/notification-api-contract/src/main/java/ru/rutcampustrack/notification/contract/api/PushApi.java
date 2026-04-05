package ru.rutcampustrack.notification.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.notification.contract.dto.push.SubscribeRequest;
import ru.rutcampustrack.notification.contract.dto.push.UnsubscribeRequest;
import ru.rutcampustrack.notification.contract.dto.push.VapidPublicKeyResponse;

/**
 * REST API contract for Web Push subscription management.
 *
 * Per D-11: Contract-first design — controller implements this interface.
 * Per D-12: Mappings are ONLY in the interface, NOT in the controller class.
 * Per PUSH-01: VAPID public key endpoint is public (no auth required).
 * Per T-27-01/T-27-05: subscribe/unsubscribe require STUDENT role (enforced via @RequireRole in Plan 02).
 */
@Tag(name = "Push", description = "Web Push subscription management")
@RequestMapping("/push")
public interface PushApi {

    @Operation(summary = "Get VAPID public key")
    @ApiResponse(responseCode = "200", description = "VAPID public key in Base64url")
    @GetMapping("/vapid-public-key")
    ResponseEntity<EntityModel<VapidPublicKeyResponse>> getVapidPublicKey();

    @Operation(summary = "Subscribe to push notifications")
    @ApiResponse(responseCode = "201", description = "Subscription stored")
    @PostMapping("/subscribe")
    ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request);

    @Operation(summary = "Unsubscribe from push notifications")
    @ApiResponse(responseCode = "204", description = "Subscription removed")
    @DeleteMapping("/subscribe")
    ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request);
}
