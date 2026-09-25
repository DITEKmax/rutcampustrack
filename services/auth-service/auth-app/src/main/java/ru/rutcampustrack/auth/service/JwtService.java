package ru.rutcampustrack.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.UserRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    private static final String JWT_ISSUER = "rutcampustrack-auth";
    private static final String JWT_AUDIENCE = "rutcampustrack";

    public static final String TOKEN_USE_CLAIM = "token_use";
    public static final String TOKEN_USE_ACCESS = "access";
    public static final String TOKEN_USE_REFRESH = "refresh";
    public static final String TOKEN_USE_BOOTSTRAP = "bootstrap";
    public static final String TOKEN_USE_INTERNAL = "internal";

    // M03a: internal JWT audience — validated by shared-security validator on downstream services.
    public static final String INTERNAL_JWT_AUDIENCE = "rutcampustrack-internal";

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String publicKeyPem;
    private String keyId;

    public JwtService(JwtProperties jwtProperties, StringRedisTemplate redisTemplate) {
        this.jwtProperties = jwtProperties;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() throws Exception {
        Path keyDir = Paths.get(jwtProperties.keyDir());
        Path privateKeyPath = keyDir.resolve("private.key");
        Path publicKeyPath = keyDir.resolve("public.key");

        Path kidPath = keyDir.resolve("kid.txt");

        if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
            log.info("Loading RSA keys from filesystem: {}", keyDir.toAbsolutePath());
            try {
                privateKey = loadPrivateKey(privateKeyPath);
                log.info("RSA private key parsed (PKCS#8, {} bytes)", privateKey.getEncoded().length);
                publicKey = loadPublicKey(publicKeyPath);
                log.info("RSA public key parsed (X.509, {} bytes)", publicKey.getEncoded().length);
            } catch (Exception e) {
                log.error("Failed to load RSA keys from filesystem (path={}): {}",
                        keyDir.toAbsolutePath(), e.toString(), e);
                throw e;
            }
        } else {
            // REC-04: Generate RSA 3072-bit keys (NIST recommended)
            log.info("Generating new RSA 3072-bit key pair in: {}", keyDir.toAbsolutePath());
            Files.createDirectories(keyDir);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072, nonBlockingSecureRandom());
            KeyPair keyPair = generator.generateKeyPair();
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
            writeKeyToFile(privateKey, privateKeyPath, "PRIVATE KEY");
            writeKeyToFile(publicKey, publicKeyPath, "PUBLIC KEY");
        }

        // REC-04: Load or generate key ID for JWT kid header (supports future key rotation)
        if (Files.exists(kidPath)) {
            keyId = Files.readString(kidPath).strip();
        } else {
            keyId = UUID.randomUUID().toString().substring(0, 8);
            Files.writeString(kidPath, keyId);
        }
        log.info("RSA kid resolved: {}", keyId);

        publicKeyPem = buildPem(publicKey.getEncoded(), "PUBLIC KEY");
        log.info("RSA key pair ready (kid={}), public key PEM serialized "
                + "(Redis cache scheduled to ApplicationReadyEvent)", keyId);
    }

    /**
     * M13 G25.17 — Redis cache населяется ПОСЛЕ context refresh.
     *
     * <p>JwtService.init() в @PostConstruct не должен блокироваться на
     * Redis: Lettuce/Netty lazy-инициализирует event loop и connection pool
     * при первом use, на холодном CI runner это может занять 30+ секунд из-за
     * Netty entropy/DNS init и default command timeout. Healthcheck не
     * успевает зеленеть в start_period 60s.
     *
     * <p>Перенос в ApplicationReadyEvent гарантирует: (1) Tomcat готов
     * принимать /actuator/health, (2) Redis cache best-effort — если падает,
     * downstream сервисы либо ретраят, либо забирают public key через
     * /auth/.well-known/jwks.json endpoint (если такой есть; иначе через
     * первый failed parse + retry). На prod connection pool warm после
     * первого fetch.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cachePublicKeyInRedis() {
        try {
            log.info("Caching public key in Redis (post-startup)...");
            redisTemplate.opsForValue().set("jwt:public_key", publicKeyPem, Duration.ofSeconds(3600));
            log.info("Public key cached in Redis successfully");
        } catch (Exception e) {
            log.warn("Failed to cache public key in Redis (will retry on next signing): {}", e.toString());
        }
    }

    /**
     * Legacy stage-2 access token.  It intentionally has no session-bound
     * claims and is never accepted by {@link #parseSessionAccessToken(String)}.
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.accessTokenExpiration() * 1000);

        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(user.getId().toString())
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_ACCESS)
                .claim("role", user.getRole().name())
                .claim("group_id", user.getGroupId())
                .claim("is_headman", user.isHeadman())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Signs the access-token form that is eligible for session admission.
     * Public login wiring will pass the same authoritative snapshot that owns
     * the selected grant; no caller-supplied identity is accepted here.
     */
    public String generateSessionAccessToken(
            SessionSnapshot snapshot,
            Instant issuedAt,
            Instant expiration
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiration, "expiration");
        RoleGrant activeRole = requireActiveRole(snapshot);
        requireWholeSecondWindow(issuedAt, expiration);

        var builder = Jwts.builder()
                .header().keyId(keyId).and()
                .subject(Long.toString(snapshot.userId()))
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_ACCESS)
                .claim("sid", snapshot.sessionId().toString())
                .claim("sv", Long.toString(snapshot.sessionVersion()))
                .claim("rv", Long.toString(snapshot.rolesVersion()))
                .claim("role", activeRole.role().name())
                .claim("status", activeRole.status().name())
                .claim("is_headman", activeRole.role().name().equals("HEADMAN"))
                .claim("readOnly", activeRole.isReadOnly())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration));
        if (activeRole.groupId() != null) {
            builder.claim("group_id", Long.toString(activeRole.groupId()));
        }
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.refreshTokenExpiration() * 1000);

        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(user.getId().toString())
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Parse the access-token form used by the HTTP filter and WebSocket ticket
     * endpoint. Keeping this alias strict prevents refresh tokens from being
     * accepted by callers that still use the historical method name.
     */
    public Jws<Claims> parseToken(String token) {
        return parseAccessToken(token);
    }

    public Jws<Claims> parseAccessToken(String token) {
        Jws<Claims> parsed = parseSignedToken(token, JWT_AUDIENCE, TOKEN_USE_ACCESS);
        Claims claims = parsed.getPayload();
        requirePositiveSubject(claims);
        requireExpiration(claims);
        requireKnownRole(claims);
        return parsed;
    }

    /**
     * Strict parser for the original session-bound access token used by
     * admission.  The legacy parser above remains for the stage-2 public
     * login/filter transition and deliberately does not satisfy this method.
     */
    public Jws<Claims> parseSessionAccessToken(String token) {
        Jws<Claims> parsed = parseSignedToken(token, JWT_AUDIENCE, TOKEN_USE_ACCESS);
        RawJwtPayload raw = RawJwtPayload.parse(token);
        requireSessionAccessWire(raw);
        String role = raw.requiredPlainString("role");
        String status = raw.requiredPlainString("status");
        boolean isHeadman = raw.requiredBoolean("is_headman");
        boolean readOnly = raw.requiredBoolean("readOnly");
        requireSemanticIdentity(role, status, isHeadman, readOnly);
        return parsed;
    }

    public Jws<Claims> parseRefreshToken(String token) {
        Jws<Claims> parsed = parseSignedToken(token, JWT_AUDIENCE, TOKEN_USE_REFRESH);
        Claims claims = parsed.getPayload();
        requirePositiveSubject(claims);
        requireExpiration(claims);
        requireNonBlank(claims.getId(), "jti");
        return parsed;
    }

    /** Signs the cookie form bound to one persisted session and refresh JTI. */
    public String generateSessionRefreshToken(
            SessionSnapshot snapshot,
            UUID refreshJti,
            Instant issuedAt,
            Instant expiration
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(refreshJti, "refreshJti");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiration, "expiration");
        requireWholeSecondWindow(issuedAt, expiration);
        if (expiration.isAfter(snapshot.refreshExpiresAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw new IllegalArgumentException("refresh expiration cannot extend the session");
        }
        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(Long.toString(snapshot.userId()))
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_REFRESH)
                .claim("sid", snapshot.sessionId().toString())
                .id(refreshJti.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /** Strict parser for the session-bound refresh cookie. */
    public Jws<Claims> parseSessionRefreshToken(String token) {
        Jws<Claims> parsed = parseSignedToken(token, JWT_AUDIENCE, TOKEN_USE_REFRESH);
        RawJwtPayload raw = RawJwtPayload.parse(token);
        requireSessionEnvelope(raw, TOKEN_USE_REFRESH);
        parseCanonicalUuid(raw.requiredPlainString("sid"), "sid");
        parseCanonicalUuid(raw.requiredPlainString("jti"), "jti");
        return parsed;
    }

    /** Signs a neutral capability used only while a session has no selected role. */
    public String generateBootstrapToken(SessionSnapshot snapshot, Instant issuedAt, Instant expiration) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiration, "expiration");
        if (snapshot.activeRole() != null) {
            throw new IllegalArgumentException("bootstrap token requires a neutral session");
        }
        requireWholeSecondWindow(issuedAt, expiration);
        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(Long.toString(snapshot.userId()))
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_BOOTSTRAP)
                .claim("sid", snapshot.sessionId().toString())
                .claim("sv", Long.toString(snapshot.sessionVersion()))
                .claim("rv", Long.toString(snapshot.rolesVersion()))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /** Strict parser for a neutral bootstrap access token. */
    public Jws<Claims> parseBootstrapToken(String token) {
        Jws<Claims> parsed = parseSignedToken(token, JWT_AUDIENCE, TOKEN_USE_BOOTSTRAP);
        RawJwtPayload raw = RawJwtPayload.parse(token);
        requireSessionEnvelope(raw, TOKEN_USE_BOOTSTRAP);
        parseCanonicalUuid(raw.requiredPlainString("sid"), "sid");
        requirePositiveDecimal(raw.requiredPlainString("sv"), "sv");
        requirePositiveDecimal(raw.requiredPlainString("rv"), "rv");
        for (String forbidden : List.of("role", "status", "group_id", "is_headman", "readOnly")) {
            if (raw.has(forbidden)) {
                throw new IllegalArgumentException("bootstrap token contains selected identity");
            }
        }
        return parsed;
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseRefreshToken(token).getPayload().getSubject());
    }

    public String extractJti(String token) {
        return parseRefreshToken(token).getPayload().getId();
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    /**
     * Signs an internal token exclusively from one accepted coherent snapshot.
     * The admission service supplies the already-capped whole-second window.
     */
    public String generateInternalToken(SessionSnapshot snapshot, Instant issuedAt, Instant expiration) {
        return generateInternalToken(snapshot, issuedAt, expiration, null);
    }

    /** Signs fresh authority for one stored report selector as well as the live session. */
    public String generateInternalReportDownloadToken(
            SessionSnapshot snapshot,
            Instant issuedAt,
            Instant expiration,
            String reportBindingHash,
            Instant reportTicketExpiresAt
    ) {
        if (reportBindingHash == null || !reportBindingHash.matches("[0-9a-f]{64}")
                || reportTicketExpiresAt == null) {
            throw new IllegalArgumentException("report binding hash is invalid");
        }
        return generateInternalToken(snapshot, issuedAt, expiration, reportBindingHash, reportTicketExpiresAt);
    }

    private String generateInternalToken(
            SessionSnapshot snapshot,
            Instant issuedAt,
            Instant expiration,
            String reportBindingHash
    ) {
        return generateInternalToken(snapshot, issuedAt, expiration, reportBindingHash, null);
    }

    private String generateInternalToken(
            SessionSnapshot snapshot,
            Instant issuedAt,
            Instant expiration,
            String reportBindingHash,
            Instant reportTicketExpiresAt
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiration, "expiration");
        RoleGrant activeRole = requireActiveRole(snapshot);
        requireWholeSecondWindow(issuedAt, expiration);

        var builder = Jwts.builder()
                .header().keyId(keyId).and()
                .subject(Long.toString(snapshot.userId()))
                .issuer(JWT_ISSUER)
                .audience().add(INTERNAL_JWT_AUDIENCE).and()
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_INTERNAL)
                .claim("sid", snapshot.sessionId().toString())
                .claim("sv", Long.toString(snapshot.sessionVersion()))
                .claim("rv", Long.toString(snapshot.rolesVersion()))
                .claim("role", activeRole.role().name())
                .claim("status", activeRole.status().name())
                .claim("is_headman", activeRole.role().name().equals("HEADMAN"))
                .claim("readOnly", activeRole.isReadOnly())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration));
        if (activeRole.groupId() != null) {
            builder.claim("group_id", Long.toString(activeRole.groupId()));
        }
        if (reportBindingHash != null) {
            builder.claim("report_hash", reportBindingHash);
            builder.claim("report_ticket_exp", reportTicketExpiresAt.toString());
        }
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    private Jws<Claims> parseSignedToken(String token, String audience, String purpose) {
        if (publicKey == null) {
            throw new IllegalStateException("JWT public key is not initialized");
        }
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(JWT_ISSUER)
                .requireAudience(audience)
                .require(TOKEN_USE_CLAIM, purpose)
                .build()
                .parseSignedClaims(token);
    }

    private static void requirePositiveSubject(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject is required");
        }
        try {
            if (Long.parseLong(subject) <= 0) {
                throw new IllegalArgumentException("JWT subject must be positive");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JWT subject must be numeric", e);
        }
    }

    private static void requireExpiration(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null || !expiration.after(new Date())) {
            throw new IllegalArgumentException("JWT expiration is required and must be in the future");
        }
    }

    private static void requireKnownRole(Claims claims) {
        Object role = claims.get("role");
        if (!(role instanceof String roleName) || roleName.isBlank()) {
            throw new IllegalArgumentException("JWT role is required");
        }
        try {
            UserRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT role is unknown", e);
        }
    }

    private static void requireNonBlank(String value, String claimName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JWT " + claimName + " is required");
        }
    }

    private static RoleGrant requireActiveRole(SessionSnapshot snapshot) {
        RoleGrant activeRole = snapshot.activeRole();
        if (activeRole == null || !activeRole.isSelectable()) {
            throw new IllegalArgumentException("session has no selectable active role");
        }
        return activeRole;
    }

    private static void requireWholeSecondWindow(Instant issuedAt, Instant expiration) {
        if (!issuedAt.truncatedTo(ChronoUnit.SECONDS).equals(issuedAt)
                || !expiration.truncatedTo(ChronoUnit.SECONDS).equals(expiration)
                || !issuedAt.isBefore(expiration)) {
            throw new IllegalArgumentException("JWT time claims must be whole-second values with iat before exp");
        }
    }

    private static void requirePositiveDecimal(String value, String name) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("JWT " + name + " must be a positive decimal string");
        }
        try {
            Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("JWT " + name + " is out of range", exception);
        }
    }

    private static void requireSemanticIdentity(
            String role,
            String status,
            boolean isHeadman,
            boolean readOnly
    ) {
        if (isHeadman != "HEADMAN".equals(role)) {
            throw new IllegalArgumentException("JWT headman flag is inconsistent");
        }
        if ("SUSPENDED".equals(status)
                || ("ACTIVE".equals(status) && readOnly)
                || (Set.of("EXPELLED", "GRADUATED", "ARCHIVED").contains(status) && !readOnly)) {
            throw new IllegalArgumentException("JWT role status is inconsistent");
        }
    }

    private static void requireSessionAccessWire(RawJwtPayload raw) {
        if (!JWT_ISSUER.equals(raw.requiredPlainString("iss"))) {
            throw new IllegalArgumentException("JWT issuer is invalid");
        }
        if (!Set.of(JWT_AUDIENCE).equals(raw.requiredSingletonStringArray("aud"))) {
            throw new IllegalArgumentException("JWT audience must contain exactly the expected value");
        }
        if (!TOKEN_USE_ACCESS.equals(raw.requiredPlainString(TOKEN_USE_CLAIM))) {
            throw new IllegalArgumentException("JWT token use is invalid");
        }
        requirePositiveDecimal(raw.requiredPlainString("sub"), "sub");
        parseCanonicalUuid(raw.requiredPlainString("sid"), "sid");
        requirePositiveDecimal(raw.requiredPlainString("sv"), "sv");
        requirePositiveDecimal(raw.requiredPlainString("rv"), "rv");
        String role = raw.requiredPlainString("role");
        if (!Set.of("STUDENT", "TEACHER", "HEADMAN", "ADMIN").contains(role)) {
            throw new IllegalArgumentException("JWT role is invalid");
        }
        String status = raw.requiredPlainString("status");
        if (!Set.of("ACTIVE", "SUSPENDED", "EXPELLED", "GRADUATED", "ARCHIVED").contains(status)) {
            throw new IllegalArgumentException("JWT status is invalid");
        }
        if (raw.has("group_id")) {
            requirePositiveDecimal(raw.requiredPlainString("group_id"), "group_id");
        }
        raw.requiredBoolean("is_headman");
        raw.requiredBoolean("readOnly");
        requireWholeSecondTimes(raw);
    }

    private static void requireSessionEnvelope(RawJwtPayload raw, String purpose) {
        if (!JWT_ISSUER.equals(raw.requiredPlainString("iss"))) {
            throw new IllegalArgumentException("JWT issuer is invalid");
        }
        if (!Set.of(JWT_AUDIENCE).equals(raw.requiredSingletonStringArray("aud"))) {
            throw new IllegalArgumentException("JWT audience must contain exactly the expected value");
        }
        if (!purpose.equals(raw.requiredPlainString(TOKEN_USE_CLAIM))) {
            throw new IllegalArgumentException("JWT token use is invalid");
        }
        requirePositiveDecimal(raw.requiredPlainString("sub"), "sub");
        requireWholeSecondTimes(raw);
    }

    private static void requireWholeSecondTimes(RawJwtPayload raw) {
        long issuedAt = raw.requiredWholeSecondNumber("iat");
        long expiration = raw.requiredWholeSecondNumber("exp");
        if (issuedAt >= expiration || issuedAt > Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("JWT time claims are invalid");
        }
    }

    private static UUID parseCanonicalUuid(String value, String name) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("JWT " + name + " is not canonical");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT " + name + " is invalid", exception);
        }
    }

    private static final class RawJwtPayload {
        private final Map<String, RawJsonValue> values;

        private RawJwtPayload(Map<String, RawJsonValue> values) {
            this.values = values;
        }

        private static RawJwtPayload parse(String compactToken) {
            if (compactToken == null) {
                throw new IllegalArgumentException("JWT is missing");
            }
            String[] parts = compactToken.split("\\.", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("JWT compact form is invalid");
            }
            try {
                String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                JsonObjectParser parser = new JsonObjectParser(json);
                return new RawJwtPayload(parser.parse());
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("JWT payload is invalid", exception);
            }
        }

        private boolean has(String name) {
            return values.containsKey(name);
        }

        private String requiredPlainString(String name) {
            RawJsonValue value = required(name);
            if (value.kind != RawJsonKind.STRING || value.escaped) {
                throw new IllegalArgumentException("JWT " + name + " must be a plain JSON string");
            }
            return value.stringValue;
        }

        private boolean requiredBoolean(String name) {
            RawJsonValue value = required(name);
            if (value.kind != RawJsonKind.BOOLEAN) {
                throw new IllegalArgumentException("JWT " + name + " must be a JSON boolean");
            }
            return value.booleanValue;
        }

        private Set<String> requiredSingletonStringArray(String name) {
            RawJsonValue value = required(name);
            if (value.kind != RawJsonKind.ARRAY || value.arrayValues.size() != 1) {
                throw new IllegalArgumentException("JWT " + name + " must be a singleton JSON array");
            }
            RawJsonValue item = value.arrayValues.get(0);
            if (item.kind != RawJsonKind.STRING || item.escaped) {
                throw new IllegalArgumentException("JWT " + name + " must contain a plain JSON string");
            }
            return Set.of(item.stringValue);
        }

        private long requiredWholeSecondNumber(String name) {
            RawJsonValue value = required(name);
            if (value.kind != RawJsonKind.NUMBER || !value.raw.matches("[0-9]+")) {
                throw new IllegalArgumentException("JWT " + name + " must be an integer NumericDate");
            }
            try {
                return Long.parseLong(value.raw);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("JWT " + name + " is out of range", exception);
            }
        }

        private RawJsonValue required(String name) {
            RawJsonValue value = values.get(name);
            if (value == null) {
                throw new IllegalArgumentException("JWT " + name + " is required");
            }
            return value;
        }
    }

    private enum RawJsonKind { STRING, NUMBER, BOOLEAN, NULL, OBJECT, ARRAY }

    private static final class RawJsonValue {
        private final RawJsonKind kind;
        private final String raw;
        private final String stringValue;
        private final boolean escaped;
        private final boolean booleanValue;
        private final List<RawJsonValue> arrayValues;

        private RawJsonValue(
                RawJsonKind kind,
                String raw,
                String stringValue,
                boolean escaped,
                boolean booleanValue,
                List<RawJsonValue> arrayValues
        ) {
            this.kind = kind;
            this.raw = raw;
            this.stringValue = stringValue;
            this.escaped = escaped;
            this.booleanValue = booleanValue;
            this.arrayValues = arrayValues;
        }

        private static RawJsonValue string(String raw, String value, boolean escaped) {
            return new RawJsonValue(RawJsonKind.STRING, raw, value, escaped, false, List.of());
        }

        private static RawJsonValue scalar(RawJsonKind kind, String raw) {
            return new RawJsonValue(kind, raw, null, false, "true".equals(raw), List.of());
        }

        private static RawJsonValue array(String raw, List<RawJsonValue> values) {
            return new RawJsonValue(RawJsonKind.ARRAY, raw, null, false, false, List.copyOf(values));
        }
    }

    private static final class JsonObjectParser {
        private final String text;
        private int index;

        private JsonObjectParser(String text) {
            this.text = text;
        }

        private Map<String, RawJsonValue> parse() {
            skipWhitespace();
            Map<String, RawJsonValue> object = parseObject();
            skipWhitespace();
            if (index != text.length()) {
                throw error("trailing JSON content");
            }
            return object;
        }

        private Map<String, RawJsonValue> parseObject() {
            expect('{');
            Map<String, RawJsonValue> object = new HashMap<>();
            Set<String> keys = new HashSet<>();
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                ParsedString key = parseString();
                if (!keys.add(key.value)) {
                    throw error("duplicate JSON key");
                }
                skipWhitespace();
                expect(':');
                skipWhitespace();
                object.put(key.value, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private RawJsonValue parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("missing JSON value");
            }
            char current = text.charAt(index);
            if (current == '"') {
                ParsedString parsed = parseString();
                return RawJsonValue.string(parsed.raw, parsed.value, parsed.escaped);
            }
            if (current == '{') {
                int start = index;
                parseObject();
                return RawJsonValue.scalar(RawJsonKind.OBJECT, text.substring(start, index));
            }
            if (current == '[') {
                return parseArray();
            }
            if (current == 't' && consumeLiteral("true")) {
                return RawJsonValue.scalar(RawJsonKind.BOOLEAN, "true");
            }
            if (current == 'f' && consumeLiteral("false")) {
                return RawJsonValue.scalar(RawJsonKind.BOOLEAN, "false");
            }
            if (current == 'n' && consumeLiteral("null")) {
                return RawJsonValue.scalar(RawJsonKind.NULL, "null");
            }
            if (current == '-' || Character.isDigit(current)) {
                return RawJsonValue.scalar(RawJsonKind.NUMBER, parseNumber());
            }
            throw error("invalid JSON value");
        }

        private RawJsonValue parseArray() {
            int start = index;
            expect('[');
            List<RawJsonValue> values = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return RawJsonValue.array(text.substring(start, index), values);
            }
            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return RawJsonValue.array(text.substring(start, index), values);
                }
                expect(',');
            }
        }

        private String parseNumber() {
            int start = index;
            if (consume('-')) {
                if (index >= text.length()) {
                    throw error("invalid JSON number");
                }
            }
            if (consume('0')) {
                if (index < text.length() && Character.isDigit(text.charAt(index))) {
                    throw error("leading zero in JSON number");
                }
            } else {
                requireDigits();
            }
            if (consume('.')) {
                requireDigits();
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                requireDigits();
            }
            return text.substring(start, index);
        }

        private ParsedString parseString() {
            int start = index;
            expect('"');
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return new ParsedString(text.substring(start, index), value.toString(), escaped);
                }
                if (current < 0x20) {
                    throw error("control character in JSON string");
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                escaped = true;
                if (index >= text.length()) {
                    throw error("unterminated JSON escape");
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(parseUnicodeEscape());
                    default -> throw error("invalid JSON escape");
                }
            }
            throw error("unterminated JSON string");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > text.length()) {
                throw error("short unicode escape");
            }
            String hex = text.substring(index, index + 4);
            if (!hex.matches("[0-9a-fA-F]{4}")) {
                throw error("invalid unicode escape");
            }
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private void requireDigits() {
            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw error("JSON number requires digits");
            }
        }

        private boolean consumeLiteral(String literal) {
            if (text.startsWith(literal, index)) {
                index += literal.length();
                return true;
            }
            return false;
        }

        private boolean consume(char expected) {
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("JWT payload " + message + " at offset " + index);
        }

        private record ParsedString(String raw, String value, boolean escaped) {
        }
    }

    /**
     * M13 G25.14 — explicit /dev/urandom seed + SHA1PRNG software CSPRNG.
     *
     * <p>Корневая причина hang'а на CI: на alpine musl JDK 21 ВСЕ native-PRNG
     * provider'ы (NativePRNG, NativePRNGNonBlocking — G25.13 не помог; DRBG
     * тоже instantiate'ится через default SecureRandom seed) под капотом
     * читают /dev/random при init/reseed, игнорируя
     * `-Djava.security.egd=file:/dev/./urandom`. На холодном GitHub Actions
     * Azure runner entropy pool пустой → блок на минуты.
     *
     * <p>Bullet-proof решение: читаем 32 байта напрямую из /dev/urandom
     * (Linux gurantee: urandom NEVER blocks, в отличие от /dev/random) и
     * seedим SHA1PRNG — software-only CSPRNG, никаких дальнейших I/O reads.
     * Криптостойкость 256-bit seed equivalent default SecureRandom.
     *
     * <p>На Windows /dev/urandom отсутствует — fallback на default SecureRandom
     * (Windows CSPRNG через Crypto API non-blocking).
     */
    private static SecureRandom nonBlockingSecureRandom() {
        Path urandom = Paths.get("/dev/urandom");
        if (Files.exists(urandom)) {
            try {
                byte[] seed = new byte[32];
                try (var is = Files.newInputStream(urandom)) {
                    int read = is.read(seed);
                    if (read != seed.length) {
                        throw new IOException("Short read from /dev/urandom: " + read);
                    }
                }
                SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
                random.setSeed(seed);
                return random;
            } catch (IOException | NoSuchAlgorithmException e) {
                log.warn("Failed to seed SHA1PRNG from /dev/urandom, falling back to default SecureRandom", e);
            }
        }
        return new SecureRandom();
    }

    private void writeKeyToFile(Key key, Path path, String type) throws IOException {
        String pem = buildPem(key.getEncoded(), type);
        Files.writeString(path, pem);
        // IMP-08: Restrict file permissions to owner-read-only for private keys
        try {
            java.nio.file.attribute.PosixFilePermission ownerRead =
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ;
            java.nio.file.attribute.PosixFilePermission ownerWrite =
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
            Files.setPosixFilePermissions(path, java.util.Set.of(ownerRead, ownerWrite));
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions — skip silently
            log.debug("POSIX file permissions not supported on this OS, skipping for {}", path);
        }
        log.debug("Written {} to {}", type, path);
    }

    private String buildPem(byte[] encoded, String type) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

    private PrivateKey loadPrivateKey(Path path) throws Exception {
        byte[] keyBytes = readPemBytes(path);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey(Path path) throws Exception {
        byte[] keyBytes = readPemBytes(path);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private byte[] readPemBytes(Path path) throws IOException {
        String pem = Files.readString(path);
        String stripped = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(stripped);
    }
}
