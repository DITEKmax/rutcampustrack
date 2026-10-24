package ru.rutcampustrack.auth.controller;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.DevicePreferencesApi;
import ru.rutcampustrack.auth.device.DevicePreferencesService;
import ru.rutcampustrack.auth.dto.DevicePreferencesResponse;
import ru.rutcampustrack.auth.dto.UpdateDevicePreferencesRequest;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;

@RestController
public final class DevicePreferencesController implements DevicePreferencesApi {
    private final DevicePreferencesService service;

    public DevicePreferencesController(DevicePreferencesService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<DevicePreferencesResponse> getDevicePreferences(
            String selector, Authentication authentication) {
        var context = service.read(principal(authentication), selector);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        ResponseCookie.from(COOKIE_NAME, context.selector())
                                .secure(true)
                                .httpOnly(true)
                                .sameSite("Strict")
                                .path("/")
                                .maxAge(service.ttlSeconds())
                                .build()
                                .toString())
                .body(context.response());
    }

    @Override
    public ResponseEntity<DevicePreferencesResponse> putDevicePreferences(
            String selector,
            UpdateDevicePreferencesRequest request,
            Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.write(principal(authentication), selector, request));
    }

    private static SessionPrincipal principal(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal))
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        return principal;
    }
}
