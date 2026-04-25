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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    private static final String JWT_ISSUER = "rutcampustrack-auth";
    private static final String JWT_AUDIENCE = "rutcampustrack";

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

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.accessTokenExpiration() * 1000);

        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(user.getId().toString())
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .claim("role", user.getRole().name())
                .claim("group_id", user.getGroupId())
                .claim("is_headman", user.isHeadman())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.refreshTokenExpiration() * 1000);

        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(user.getId().toString())
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(JWT_ISSUER)
                .requireAudience(JWT_AUDIENCE)
                .build()
                .parseSignedClaims(token);
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseToken(token).getPayload().getSubject());
    }

    public String extractJti(String token) {
        return parseToken(token).getPayload().getId();
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    /**
     * M03a: sign an Internal JWT for downstream-service authentication (token exchange).
     * Called by {@code InternalIssuerController} after the shared secret is verified.
     * Claims mirror the external access-token schema so downstream filters stay uniform.
     */
    public String generateInternalToken(long userId, String role, Long groupId, boolean isHeadman, long ttlSeconds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttlSeconds * 1000);

        var builder = Jwts.builder()
                .header().keyId(keyId).and()
                .subject(String.valueOf(userId))
                .issuer(JWT_ISSUER)
                .audience().add(INTERNAL_JWT_AUDIENCE).and()
                .claim("role", role)
                .claim("is_headman", isHeadman)
                .issuedAt(now)
                .expiration(expiration);
        if (groupId != null) {
            builder.claim("group_id", groupId);
        }
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
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
