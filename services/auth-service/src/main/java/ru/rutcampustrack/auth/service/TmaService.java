package ru.rutcampustrack.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.config.TmaProperties;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.AccountStatus;
import ru.rutcampustrack.auth.exception.InvalidCredentialsException;
import ru.rutcampustrack.auth.exception.TmaValidationException;
import ru.rutcampustrack.auth.repository.UserRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TmaService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final TmaProperties tmaProperties;
    private final ObjectMapper objectMapper;

    public TmaService(UserRepository userRepository,
                      JwtService jwtService,
                      StringRedisTemplate redisTemplate,
                      JwtProperties jwtProperties,
                      TmaProperties tmaProperties,
                      ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
        this.tmaProperties = tmaProperties;
        this.objectMapper = objectMapper;
    }

    public TokenResponse authenticateWithInitData(TmaAuthRequest request) {
        String initData = request.initData();

        if (!validateInitData(initData)) {
            throw new TmaValidationException("Invalid or tampered initData");
        }

        Map<String, String> params = parseQueryString(initData);

        long authDate = Long.parseLong(params.get("auth_date"));
        long now = System.currentTimeMillis() / 1000;
        if (now - authDate > tmaProperties.authDateMaxAgeSeconds()) {
            throw new TmaValidationException("initData expired");
        }

        String userJson = params.get("user");
        Long telegramId = extractTelegramId(userJson);

        User user = userRepository.findByTelegramId(telegramId)
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(InvalidCredentialsException::new);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        String jti = jwtService.extractJti(refreshToken);

        redisTemplate.opsForValue().set(
                "refresh:" + user.getId() + ":" + jti, "valid",
                Duration.ofSeconds(jwtProperties.refreshTokenExpiration()));

        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    private boolean validateInitData(String initData) {
        try {
            Map<String, String> params = parseQueryString(initData);
            String receivedHash = params.remove("hash");
            if (receivedHash == null) return false;

            String dataCheckString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("\n"));

            byte[] secretKey = hmacSha256(
                    tmaProperties.botToken().getBytes(StandardCharsets.UTF_8),
                    "WebAppData".getBytes(StandardCharsets.UTF_8));
            byte[] computedHash = hmacSha256(
                    dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);

            String computedHex = HexFormat.of().formatHex(computedHash);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    receivedHash.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                result.put(key, value);
            }
        }
        return result;
    }

    private Long extractTelegramId(String userJson) {
        try {
            return objectMapper.readTree(userJson).get("id").asLong();
        } catch (Exception e) {
            throw new TmaValidationException("Invalid user JSON in initData");
        }
    }
}
