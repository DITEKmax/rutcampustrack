package ru.rutcampustrack.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String publicKeyPem;

    public JwtService(JwtProperties jwtProperties, StringRedisTemplate redisTemplate) {
        this.jwtProperties = jwtProperties;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() throws Exception {
        Path keyDir = Paths.get(jwtProperties.keyDir());
        Path privateKeyPath = keyDir.resolve("private.key");
        Path publicKeyPath = keyDir.resolve("public.key");

        if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
            log.info("Loading RSA keys from filesystem: {}", keyDir.toAbsolutePath());
            privateKey = loadPrivateKey(privateKeyPath);
            publicKey = loadPublicKey(publicKeyPath);
        } else {
            log.info("Generating new RSA key pair in: {}", keyDir.toAbsolutePath());
            Files.createDirectories(keyDir);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
            writeKeyToFile(privateKey, privateKeyPath, "PRIVATE KEY");
            writeKeyToFile(publicKey, publicKeyPath, "PUBLIC KEY");
        }

        publicKeyPem = buildPem(publicKey.getEncoded(), "PUBLIC KEY");
        redisTemplate.opsForValue().set("jwt:public_key", publicKeyPem, Duration.ofSeconds(3600));
        log.info("RSA key pair ready, public key cached in Redis");
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.accessTokenExpiration() * 1000);

        return Jwts.builder()
                .subject(user.getId().toString())
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
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
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

    private void writeKeyToFile(Key key, Path path, String type) throws IOException {
        String pem = buildPem(key.getEncoded(), type);
        Files.writeString(path, pem);
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
