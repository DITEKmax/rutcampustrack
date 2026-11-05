package ru.rutcampustrack.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.TmaProperties;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.entity.User;
import java.security.GeneralSecurityException;
import ru.rutcampustrack.auth.exception.TmaValidationException;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.session.AuthSessionException;

@Service
public class TmaService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final TmaProperties tmaProperties;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final Clock clock;

    /** Validated initData uses the common session seam. */
    @Autowired
    public TmaService(
            UserRepository userRepository,
            AuthService authService,
            StringRedisTemplate redisTemplate,
            TmaProperties tmaProperties,
            ObjectMapper objectMapper) {
        this(
                userRepository,
                authService,
                redisTemplate,
                tmaProperties,
                objectMapper,
                Clock.systemUTC());
    }

    TmaService(
            UserRepository userRepository,
            AuthService authService,
            StringRedisTemplate redisTemplate,
            TmaProperties tmaProperties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.tmaProperties = tmaProperties;
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TokenResponse authenticateWithInitData(TmaAuthRequest request) {
        String initData = request.initData();

        if (!validateInitData(initData)) {
            throw new TmaValidationException("Invalid or tampered initData");
        }

        Map<String, String> params = parseQueryString(initData);

        long authDate = parseAuthDate(params.get("auth_date"));
        long now = clock.instant().getEpochSecond();
        // A proof must have been issued within the configured past window.
        // Reject future/negative timestamps before subtraction to avoid overflow.
        if (authDate > now) {
            throw new TmaValidationException(TmaValidationException.Code.TMA_INIT_DATA_NOT_YET_VALID);
        }
        if (now - authDate > tmaProperties.authDateMaxAgeSeconds()) {
            throw new TmaValidationException(TmaValidationException.Code.TMA_INIT_DATA_EXPIRED);
        }

        String userJson = params.get("user");
        Long telegramId = extractTelegramId(userJson);

        User user;
        try {
            user =
                    userRepository
                            .findByTelegramId(telegramId)
                            .orElseThrow(() -> new TmaValidationException(TmaValidationException.Code.TMA_TELEGRAM_NOT_LINKED));
        } catch (DataAccessException exception) {
            throw new AuthSessionException(
                    AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        return authService.issueSession(
                user, ru.rutcampustrack.auth.session.model.AuthMethod.TMA, null, null, null);
    }

    private static long parseAuthDate(String value) {
        if (value == null || !value.matches("[0-9]{1,19}")) {
            throw new TmaValidationException("Invalid auth_date in initData");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            // Never return the rejected timestamp or parser exception text.
            throw new TmaValidationException("Invalid auth_date in initData");
        }
    }

    private boolean validateInitData(String initData) {
        if (tmaProperties.botToken() == null || tmaProperties.botToken().isBlank()
                || tmaProperties.authDateMaxAgeSeconds() < 0) {
            throw new IllegalStateException("Telegram proof verification is not configured");
        }
        try {
            Map<String, String> params = parseQueryString(initData);
            String receivedHash = params.remove("hash");
            if (receivedHash == null) return false;

            String dataCheckString =
                    params.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining("\n"));

            byte[] secretKey =
                    hmacSha256(
                            tmaProperties.botToken().getBytes(StandardCharsets.UTF_8),
                            "WebAppData".getBytes(StandardCharsets.UTF_8));
            byte[] computedHash =
                    hmacSha256(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);

            String computedHex = HexFormat.of().formatHex(computedHash);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    receivedHash.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return false;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Telegram proof verification is unavailable", e);
        }
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> result = new LinkedHashMap<>();
        if (queryString == null || queryString.isBlank()) throw new IllegalArgumentException();
        String[] pairs = queryString.split("&", -1);
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
            } else {
                throw new IllegalArgumentException();
            }
        }
        return result;
    }

    private Long extractTelegramId(String userJson) {
        try {
            var id = objectMapper.readTree(userJson).get("id");
            if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
                throw new IllegalArgumentException();
            }
            return id.longValue();
        } catch (Exception e) {
            throw new TmaValidationException("Invalid user JSON in initData");
        }
    }
}
