package ru.rutcampustrack.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.dto.RefreshRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.AccountStatus;
import ru.rutcampustrack.auth.exception.InvalidCredentialsException;
import ru.rutcampustrack.auth.exception.TokenRefreshException;
import ru.rutcampustrack.auth.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       StringRedisTemplate redisTemplate,
                       JwtProperties jwtProperties,
                       LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
        this.loginRateLimiter = loginRateLimiter;
    }

    public TokenResponse login(LoginRequest request, String ipAddress) {
        // IMP-02 + M03a Группа 11: composite (ip, login) блокировка
        loginRateLimiter.checkBlocked(ipAddress, request.login());

        User user = userRepository.findByLogin(request.login())
                .orElseThrow(() -> {
                    loginRateLimiter.recordFailure(ipAddress, request.login());
                    return new InvalidCredentialsException();
                });

        if (user.getStatus() != AccountStatus.ACTIVE) {
            loginRateLimiter.recordFailure(ipAddress, request.login());
            throw new InvalidCredentialsException();
        }

        if (user.getPasswordHash() == null) {
            loginRateLimiter.recordFailure(ipAddress, request.login());
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(ipAddress, request.login());
            throw new InvalidCredentialsException();
        }

        // Successful login — clear failure counter
        loginRateLimiter.clearFailures(ipAddress, request.login());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        String jti = jwtService.extractJti(refreshToken);

        String redisKey = "refresh:" + user.getId() + ":" + jti;
        redisTemplate.opsForValue().set(redisKey, "valid",
                Duration.ofSeconds(jwtProperties.refreshTokenExpiration()));

        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    public TokenResponse refresh(RefreshRequest request) {
        Long userId;
        String jti;

        try {
            userId = jwtService.extractUserId(request.refreshToken());
            jti = jwtService.extractJti(request.refreshToken());
        } catch (Exception e) {
            throw new TokenRefreshException("Invalid or expired refresh token");
        }

        String redisKey = "refresh:" + userId + ":" + jti;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new TokenRefreshException("Refresh token has been revoked");
        }

        // Token rotation: delete old refresh token
        redisTemplate.delete(redisKey);

        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new TokenRefreshException("User not found or not active"));

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        String newJti = jwtService.extractJti(newRefreshToken);

        String newRedisKey = "refresh:" + user.getId() + ":" + newJti;
        redisTemplate.opsForValue().set(newRedisKey, "valid",
                Duration.ofSeconds(jwtProperties.refreshTokenExpiration()));

        return new TokenResponse(newAccessToken, newRefreshToken, jwtProperties.accessTokenExpiration());
    }

    public void logout(String refreshToken) {
        try {
            Long userId = jwtService.extractUserId(refreshToken);
            String jti = jwtService.extractJti(refreshToken);
            redisTemplate.delete("refresh:" + userId + ":" + jti);
        } catch (Exception e) {
            // Idempotent logout — silently ignore unparseable tokens
        }
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String newHash = passwordEncoder.encode(request.newPassword());
        userRepository.updatePassword(userId, newHash);

        // IMP-10: Revoke all refresh tokens for this user
        java.util.Set<String> keys = redisTemplate.keys("refresh:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public PublicKeyResponse getPublicKey() {
        return new PublicKeyResponse(jwtService.getPublicKeyPem(), "RS256");
    }
}
