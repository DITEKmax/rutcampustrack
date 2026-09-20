package ru.rutcampustrack.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.AuthApi;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyByCodeRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.security.AuthCookies;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.OtpService;
import ru.rutcampustrack.auth.service.TmaService;

import java.util.Objects;

@RestController
public final class AuthController implements AuthApi {

    private final AuthService authService;
    private final OtpService otpService;
    private final TmaService tmaService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService,
                          OtpService otpService,
                          TmaService tmaService,
                          JwtProperties jwtProperties) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.otpService = Objects.requireNonNull(otpService, "otpService");
        this.tmaService = Objects.requireNonNull(tmaService, "tmaService");
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties");
    }

    @Override
    public ResponseEntity<TokenResponse> login(LoginRequest request, HttpServletRequest httpRequest) {
        return respondWithCookie(authService.login(request, resolveClientIp(httpRequest)),
                jwtProperties.refreshTokenExpiration());
    }

    @Override
    public ResponseEntity<TokenResponse> refresh(String refreshCookie) {
        long remaining = authService.refreshRemainingSeconds(refreshCookie);
        return respondWithCookie(authService.refresh(refreshCookie), remaining);
    }

    @Override
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.getPublicKey());
    }

    @Override
    public ResponseEntity<Void> requestOtp(OtpRequest request) {
        otpService.requestOtp(request);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @Override
    public ResponseEntity<TokenResponse> verifyOtp(OtpVerifyRequest request) {
        return respondWithCookie(otpService.verifyOtp(request),
                jwtProperties.refreshTokenExpiration());
    }

    @Override
    public ResponseEntity<TokenResponse> verifyOtpByCode(OtpVerifyByCodeRequest request,
                                                         HttpServletRequest httpRequest) {
        return respondWithCookie(otpService.verifyOtpByCode(request, resolveClientIp(httpRequest)),
                jwtProperties.refreshTokenExpiration());
    }

    @Override
    public ResponseEntity<TokenResponse> tmaAuth(TmaAuthRequest request) {
        return respondWithCookie(tmaService.authenticateWithInitData(request),
                jwtProperties.refreshTokenExpiration());
    }

    private ResponseEntity<TokenResponse> respondWithCookie(TokenResponse tokens, long maxAgeSeconds) {
        if (tokens == null || tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
            throw new IllegalStateException("session issuance did not return a refresh cookie");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        AuthCookies.issue(tokens.refreshToken(), maxAgeSeconds).toString())
                .cacheControl(CacheControl.noStore())
                .body(tokens);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
