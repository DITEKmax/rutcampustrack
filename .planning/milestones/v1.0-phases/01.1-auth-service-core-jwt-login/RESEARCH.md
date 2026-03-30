# Phase 1.1: Auth Service Core (JWT + Login) - Research

**Researched:** 2026-03-28
**Domain:** Spring Security 6 + JWT (RSA) + Redis + JPA (read-only)
**Confidence:** HIGH

## Summary

Phase 1.1 implements the Auth Service (port 9090) for RutCampusTrack: login via credentials, JWT access/refresh token issuance with RSA256 signing, refresh token rotation stored in Redis, and a public-key endpoint for Gateway consumption.

The project already has JJWT 0.12.6 declared in `services/auth-service/build.gradle.kts` alongside `spring-boot-starter-security` and `spring-boot-starter-data-redis`. The missing piece is `spring-boot-starter-data-jpa` + PostgreSQL driver for read-only access to the `users` table in `academic_db`. Spring Boot 3.4.1 and Java 21 are the locked versions per the root `build.gradle.kts`.

**Primary recommendation:** Use JJWT 0.12.6 (already declared) for JWT creation/parsing with RSA keys. Use Spring Security's `SecurityFilterChain` bean with stateless session policy. Use `StringRedisTemplate` for refresh token storage with TTL. Connect to `academic_db` via JPA with `ddl-auto: validate` and a read-only JPA entity mapping for the `users` table.

## Project Constraints (from CLAUDE.md)

### Coding Rules (MANDATORY)
- Auth Service has **no separate api-contract module** (exception to the contract-first pattern)
- Request DTOs = Java `record`. Response DTOs in auth can also be records (no HATEOAS for auth endpoints per phase plan -- TokenResponse is a record)
- **Lombok allowed** in auth-service (it is an `*-app` module)
- Errors: RFC 7807 Problem Details (`ErrorResponse` record already exists in academic-api-contract)
- `@ControllerAdvice` for centralized error handling, controller only throws exceptions
- Enum values in PostgreSQL: lowercase strings. Java enums: UPPER_CASE
- `LowercaseEnumConverter` with `autoApply=true`
- `ddl-auto: validate` -- Hibernate only validates, never creates schema
- Flyway for migrations (`V{N}__description.sql`)
- BCrypt for password hashing
- Package naming: `ru.rutcampustrack.auth.{module}`
- REST paths: `/auth/...` (no `/api/` prefix; Gateway will route `/api/auth/**` to auth-service:9090)

### Existing Infrastructure
- Redis: `rct-redis` container, port 6379, no password (dev mode)
- PostgreSQL academic_db: `rct-postgres-academic` container, user `rct_user`, password `rct_dev_pass`
- Spring Boot 3.4.1, Java 21, Gradle Kotlin DSL
- JJWT 0.12.6 already in build.gradle.kts
- spring-boot-starter-security already in build.gradle.kts
- spring-boot-starter-data-redis already in build.gradle.kts

## Standard Stack

### Core (already declared or needs adding)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| spring-boot-starter-security | 3.4.1 (managed) | SecurityFilterChain, PasswordEncoder | Already in build.gradle.kts |
| spring-boot-starter-data-redis | 3.4.1 (managed) | StringRedisTemplate for refresh tokens | Already in build.gradle.kts |
| spring-boot-starter-data-jpa | 3.4.1 (managed) | JPA entities for users table (read-only) | **NEEDS ADDING** |
| spring-boot-starter-web | 3.4.1 (managed) | REST controllers | Already in build.gradle.kts |
| spring-boot-starter-validation | 3.4.1 (managed) | @Valid on request DTOs | Already in build.gradle.kts |
| jjwt-api | 0.12.6 | JWT builder/parser API | Already in build.gradle.kts |
| jjwt-impl | 0.12.6 | JWT implementation (runtime) | Already in build.gradle.kts |
| jjwt-jackson | 0.12.6 | JSON serialization for JWT (runtime) | Already in build.gradle.kts |
| postgresql | managed | JDBC driver for academic_db | **NEEDS ADDING** |
| flyway-core | managed | DB migrations (for seed data V2) | **NEEDS ADDING** |
| flyway-database-postgresql | managed | Flyway PostgreSQL support | **NEEDS ADDING** |
| springdoc-openapi-starter-webmvc-ui | 2.7.0 | Swagger/OpenAPI | Already in build.gradle.kts |
| lombok | managed | Entity classes | Already in build.gradle.kts |

### Why JJWT over alternatives

| Option | Verdict | Reason |
|--------|---------|--------|
| **JJWT 0.12.6** | **USE THIS** | Already declared in project. Well-maintained, fluent API, auto-detects RSA key strength. Most popular Java JWT library. |
| nimbus-jose-jwt | Skip | More complex API, heavier. Used internally by Spring Security OAuth2 Resource Server but adds unnecessary complexity when we control both issuer and verifier. |
| spring-security-oauth2-jose | Skip | Designed for Resource Server (token verification), not for token issuance. Auth Service needs to CREATE tokens, not just verify them. |

### Dependencies to ADD to build.gradle.kts

```kotlin
// PostgreSQL + JPA (read-only access to academic_db)
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("org.postgresql:postgresql")

// Flyway (for V2 seed migration)
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-database-postgresql")
```

## Architecture Patterns

### Package Structure (from phases-plan.md, confirmed)

```
services/auth-service/src/main/java/ru/rutcampustrack/auth/
├── AuthApplication.java
├── config/
│   ├── SecurityConfig.java           # SecurityFilterChain bean, PasswordEncoder bean
│   ├── JpaConfig.java                # Optional: mark JPA as read-only if needed
│   └── EnumConverters.java           # UserRole, AccountStatus converters (same pattern as academic-app)
├── controller/
│   └── AuthController.java           # REST endpoints
├── service/
│   ├── JwtService.java               # RSA key management, token creation/parsing
│   └── AuthService.java              # Login logic, refresh, logout
├── dto/
│   ├── LoginRequest.java             # record(String login, String password)
│   ├── TokenResponse.java            # record(String accessToken, String refreshToken, long expiresIn)
│   ├── RefreshRequest.java           # record(String refreshToken)
│   └── PublicKeyResponse.java        # record(String publicKey, String algorithm)
├── entity/
│   └── User.java                     # JPA entity mapping users table (read-only)
├── repository/
│   └── UserRepository.java           # Spring Data JPA, read-only queries
└── exception/
    ├── InvalidCredentialsException.java
    ├── TokenExpiredException.java
    ├── GlobalExceptionHandler.java   # @ControllerAdvice
    └── ErrorResponse.java            # RFC 7807 record (local copy or shared)
```

### Pattern 1: RSA Key Pair Generation and File Persistence

**What:** Generate RSA 2048-bit key pair on first startup, persist to files, load on subsequent startups.
**When to use:** Auth Service startup (`@PostConstruct` or `@Bean` initialization).

```java
import java.security.*;
import java.security.spec.*;
import java.nio.file.*;
import java.util.Base64;

@Service
public class JwtService {

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String publicKeyPem;

    // Config properties
    private final String keyDir;        // e.g., "./keys"
    private final long accessTokenExp;  // 900 seconds
    private final long refreshTokenExp; // 604800 seconds

    @PostConstruct
    public void init() throws Exception {
        Path privateKeyPath = Path.of(keyDir, "private.key");
        Path publicKeyPath = Path.of(keyDir, "public.key");

        if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
            // Load existing keys
            loadKeys(privateKeyPath, publicKeyPath);
        } else {
            // Generate new key pair
            generateAndSaveKeys(privateKeyPath, publicKeyPath);
        }

        // Cache PEM string for /auth/public-key endpoint
        this.publicKeyPem = encodePublicKeyToPem(publicKey);
    }

    private void generateAndSaveKeys(Path privatePath, Path publicPath) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();

        Files.createDirectories(privatePath.getParent());
        Files.write(privatePath, privateKey.getEncoded());
        Files.write(publicPath, publicKey.getEncoded());
    }

    private void loadKeys(Path privatePath, Path publicPath) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        byte[] privateBytes = Files.readAllBytes(privatePath);
        this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

        byte[] publicBytes = Files.readAllBytes(publicPath);
        this.publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));
    }

    private String encodePublicKeyToPem(PublicKey key) {
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
               base64.replaceAll("(.{64})", "$1\n") +
               "\n-----END PUBLIC KEY-----";
    }
}
```

**Confidence:** HIGH -- standard Java `KeyPairGenerator` API, unchanged since Java 8.

### Pattern 2: JWT Creation with JJWT 0.12.x

**What:** Create access and refresh tokens with RSA256 signing.
**API version note:** In JJWT 0.12+, use `Jwts.builder().signWith(privateKey)` (auto-detects RS256 for 2048-bit RSA). For parsing, use `Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token)`.

```java
// Source: JJWT 0.12+ API (Jwts.builder / Jwts.parser)
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;

public String generateAccessToken(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim("role", user.getRole().name())
            .claim("group_id", user.getGroupId())
            .claim("is_headman", user.getIsHeadman())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessTokenExp)))
            .signWith(privateKey)  // auto-selects RS256 for 2048-bit RSA key
            .compact();
}

public String generateRefreshToken(Long userId) {
    String jti = UUID.randomUUID().toString();
    Instant now = Instant.now();

    String token = Jwts.builder()
            .subject(String.valueOf(userId))
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(refreshTokenExp)))
            .signWith(privateKey)
            .compact();

    // Store in Redis: refresh:{userId}:{jti} = "valid", TTL = refreshTokenExp
    stringRedisTemplate.opsForValue().set(
            "refresh:" + userId + ":" + jti,
            "valid",
            Duration.ofSeconds(refreshTokenExp)
    );

    return token;
}

public Claims parseAndVerify(String token) {
    return Jwts.parser()
            .verifyWith(publicKey)     // 0.12+ API: verifyWith instead of setSigningKey
            .build()
            .parseSignedClaims(token)  // 0.12+ API: parseSignedClaims instead of parseClaimsJws
            .getPayload();
}
```

**Confidence:** HIGH -- JJWT 0.12.x API verified via official GitHub discussions and migration guides. `Jwts.parser()` returns `JwtParserBuilder` (not `JwtParser`) since 0.12.0. `parseSignedClaims()` replaces deprecated `parseClaimsJws()`.

### Pattern 3: Spring Security Stateless Configuration

**What:** SecurityFilterChain for REST API with no sessions, CSRF disabled, public/authenticated route split.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/public-key",
                        "/auth/otp/**",
                        "/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll()
                    .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**Confidence:** HIGH -- Spring Security 6.x standard pattern, well-documented.

**Important:** Auth Service itself does NOT need a JWT filter. It issues tokens, it does not validate them on incoming requests (except for `/auth/change-password` and `/auth/logout` which require a valid token). For those few authenticated endpoints, a lightweight filter can extract and verify the Bearer token. But the primary JWT validation happens at the API Gateway.

### Pattern 4: Redis Refresh Token Storage

**What:** Use `StringRedisTemplate` for refresh token lifecycle (store, verify, delete).

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final StringRedisTemplate redisTemplate;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Store refresh token
    public void storeRefreshToken(Long userId, String jti, long ttlSeconds) {
        String key = "refresh:" + userId + ":" + jti;
        redisTemplate.opsForValue().set(key, "valid", Duration.ofSeconds(ttlSeconds));
    }

    // Verify refresh token exists in Redis
    public boolean isRefreshTokenValid(Long userId, String jti) {
        String key = "refresh:" + userId + ":" + jti;
        return "valid".equals(redisTemplate.opsForValue().get(key));
    }

    // Delete refresh token (logout or rotation)
    public void revokeRefreshToken(Long userId, String jti) {
        String key = "refresh:" + userId + ":" + jti;
        redisTemplate.delete(key);
    }

    // Delete ALL refresh tokens for user (force logout everywhere)
    // Uses SCAN, not KEYS (safe for production)
    public void revokeAllRefreshTokens(Long userId) {
        String pattern = "refresh:" + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);  // OK for small sets per user
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
```

**Confidence:** HIGH -- `StringRedisTemplate.opsForValue().set(key, value, Duration)` is the standard Spring Data Redis pattern.

### Pattern 5: Read-Only JPA Connection to academic_db

**What:** Auth Service connects to academic_db to read the `users` table. No Flyway in auth-service (academic_db schema is owned by Academic Service). Entity mapping must match existing V1 migration.

**application.yml additions:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres-academic:5432/academic_db
    username: rct_user
    password: ${POSTGRES_ACADEMIC_PASSWORD:rct_dev_pass}
    hikari:
      read-only: true
      maximum-pool-size: 5
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: public
    open-in-view: false
  flyway:
    enabled: false  # Auth Service does NOT own academic_db schema
```

**User Entity (read-only mapping):**
```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String login;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "telegram_id")
    private Long telegramId;

    // Enum with LowercaseEnumConverter (autoApply)
    @Column(nullable = false)
    private UserRole role;

    // Enum with LowercaseEnumConverter (autoApply)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "is_headman", nullable = false)
    private Boolean isHeadman;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "password_changed", nullable = false)
    private Boolean passwordChanged;
}
```

**UserRepository (read-only):**
```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

    Optional<User> findByTelegramId(Long telegramId);
}
```

**Confidence:** HIGH -- Standard JPA + HikariCP configuration. `hikari.read-only: true` sets connections to read-only mode. `flyway.enabled: false` prevents auth-service from running migrations on academic_db.

### Anti-Patterns to Avoid

- **DO NOT use `@Enumerated(EnumType.ORDINAL)` or `@Enumerated(EnumType.STRING)`** -- use `LowercaseEnumConverter` with autoApply as established in the project.
- **DO NOT store tokens in HttpOnly cookies** -- the clients are a Telegram Mini App and Angular SPA, both consume Bearer tokens.
- **DO NOT create a separate database for auth** -- auth reads from academic_db (users table).
- **DO NOT run Flyway migrations from auth-service** -- academic_db schema is owned by Academic Service.
- **DO NOT use HMAC (HS256) for JWT signing** -- RSA is required so Gateway can verify with the public key without knowing the private key.
- **DO NOT use `Jwts.parserBuilder()`** -- removed in JJWT 0.12, use `Jwts.parser()` which now returns `JwtParserBuilder`.
- **DO NOT use `parseClaimsJws()`** -- deprecated in JJWT 0.12, use `parseSignedClaims()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Password hashing | Custom hash + salt | `BCryptPasswordEncoder` from Spring Security | Timing attacks, salt handling, work factor tuning |
| JWT creation/parsing | Manual Base64 + JSON + RSA signing | JJWT 0.12.6 | Algorithm confusion attacks, claim validation, key type enforcement |
| Redis TTL management | Manual expiry tracking | `StringRedisTemplate.opsForValue().set(key, val, Duration)` | Atomic set+expire, connection pooling |
| RSA key serialization | Custom PEM encode/decode | `java.security.KeyFactory` + `PKCS8EncodedKeySpec` / `X509EncodedKeySpec` | Format compatibility, standard encoding |
| Error responses | Custom JSON error format | RFC 7807 `ErrorResponse` record (already in project) | Consistency across all services |

## Common Pitfalls

### Pitfall 1: JJWT 0.12 API Changes
**What goes wrong:** Using deprecated `Jwts.parserBuilder()`, `setSigningKey()`, `parseClaimsJws()` from older JJWT tutorials.
**Why it happens:** Most tutorials reference JJWT 0.9.x or 0.11.x.
**How to avoid:** Use the 0.12+ API: `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`.
**Warning signs:** Deprecation warnings at compile time.

### Pitfall 2: RSA Key File Permissions
**What goes wrong:** Private key file readable by other users on shared systems.
**Why it happens:** Default file creation permissions.
**How to avoid:** In production, use restrictive file permissions. For dev, the keys directory should be in `.gitignore`.
**Warning signs:** Private key file in git, world-readable permissions.

### Pitfall 3: Flyway Conflict on academic_db
**What goes wrong:** Auth-service tries to run Flyway migrations on academic_db, conflicting with Academic Service's migration history.
**Why it happens:** `spring-boot-starter-data-jpa` auto-configures Flyway if `flyway-core` is on classpath.
**How to avoid:** Either (a) do NOT add flyway dependencies to auth-service, or (b) set `spring.flyway.enabled=false` in auth-service's application.yml.
**Warning signs:** `FlywayMigrationInitializer` errors at startup.

### Pitfall 4: Enum Mapping Mismatch
**What goes wrong:** JPA fails to read `user_role` enum from PostgreSQL because it expects uppercase but database stores lowercase.
**Why it happens:** Missing `LowercaseEnumConverter` with `autoApply=true` in auth-service.
**How to avoid:** Create `EnumConverters.java` in auth-service's config package with `UserRoleConverter` and `AccountStatusConverter`, same pattern as academic-app.
**Warning signs:** `IllegalArgumentException: No enum constant` at runtime.

### Pitfall 5: PostgreSQL Enum Type vs VARCHAR
**What goes wrong:** JPA entity maps `role` field to `VARCHAR` but PostgreSQL column uses custom `user_role` ENUM type. Hibernate `validate` mode may complain about type mismatch.
**Why it happens:** PostgreSQL custom enum types and JPA don't always agree on column type during validation.
**How to avoid:** Use `@Column(columnDefinition = "user_role")` on the entity field if Hibernate validation fails, or configure Hibernate to use `String` type with the converter (which maps to `varchar` compatible reads). Test with `ddl-auto: validate` early.
**Warning signs:** `SchemaManagementException` during startup with `ddl-auto: validate`.

### Pitfall 6: Refresh Token Rotation Race Condition
**What goes wrong:** Two concurrent refresh requests with the same token both succeed, creating duplicate sessions.
**Why it happens:** Check-then-delete is not atomic.
**How to avoid:** Use Redis `GETDEL` (atomic get + delete) or `DELETE` and check return value (1 = existed and was deleted, 0 = didn't exist). `redisTemplate.delete(key)` returns `Boolean.TRUE` if key existed.
**Warning signs:** Multiple active refresh tokens for the same session.

## Code Examples

### Complete Login Flow

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokens = authService.login(request);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse tokens = authService.refresh(request);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        return ResponseEntity.ok(jwtService.getPublicKeyResponse());
    }
}
```

### AuthService Login Implementation

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByLogin(request.login())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid login or password"));

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException("Account is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid login or password");
        }

        return generateTokenPair(user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.parseAndVerify(request.refreshToken());
        Long userId = Long.valueOf(claims.getSubject());
        String jti = claims.getId();

        // Atomic: delete old token, fail if not found
        Boolean deleted = redisTemplate.delete("refresh:" + userId + ":" + jti);
        if (!Boolean.TRUE.equals(deleted)) {
            throw new InvalidCredentialsException("Refresh token is invalid or already used");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        return generateTokenPair(user);
    }

    public void logout(RefreshRequest request) {
        Claims claims = jwtService.parseAndVerify(request.refreshToken());
        Long userId = Long.valueOf(claims.getSubject());
        String jti = claims.getId();
        redisTemplate.delete("refresh:" + userId + ":" + jti);
    }

    private TokenResponse generateTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        return new TokenResponse(accessToken, refreshToken, jwtService.getAccessTokenExpiration());
    }
}
```

### DTO Records

```java
public record LoginRequest(
    @NotBlank String login,
    @NotBlank String password
) {}

public record RefreshRequest(
    @NotBlank String refreshToken
) {}

public record TokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn  // seconds
) {}

public record PublicKeyResponse(
    String publicKey,   // PEM format
    String algorithm    // "RS256"
) {}
```

### ErrorResponse (RFC 7807)

Auth-service should have its own copy of `ErrorResponse` (or depend on a shared module). Since there is no `auth-api-contract`, place it directly in the auth-service:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String type,
    String title,
    String detail,
    String instance,
    Instant timestamp,
    List<FieldError> fieldErrors
) {
    public record FieldError(String field, Object rejectedValue, String message) {}
}
```

### GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                401,
                "https://api.rutcampustrack.ru/problems/invalid-credentials",
                "Invalid credentials",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(401).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
                .toList();

        ErrorResponse error = new ErrorResponse(
                400,
                "https://api.rutcampustrack.ru/problems/validation-error",
                "Validation failed",
                "Request body contains invalid fields",
                request.getRequestURI(),
                Instant.now(),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(io.jsonwebtoken.JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(
            io.jsonwebtoken.JwtException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                401,
                "https://api.rutcampustrack.ru/problems/invalid-token",
                "Invalid token",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(401).body(error);
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Jwts.parserBuilder()` | `Jwts.parser()` returns `JwtParserBuilder` | JJWT 0.12.0 (2023) | `parserBuilder()` removed, `parser()` now returns builder |
| `setSigningKey()` | `verifyWith(key)` | JJWT 0.12.0 | Type-safe key specification |
| `parseClaimsJws()` | `parseSignedClaims()` | JJWT 0.12.0 | Clearer naming |
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean | Spring Security 5.7 / 6.0 | Adapter class removed entirely in 6.0 |
| `http.authorizeRequests()` | `http.authorizeHttpRequests()` | Spring Security 6.0 | New authorization API |
| `antMatchers()` | `requestMatchers()` | Spring Security 6.0 | Method renamed |

## Open Questions

1. **Seed test users in academic_db**
   - What we know: Phase plan says "Flyway V2 migration or seed data" for test users. Auth needs at least one user to test login.
   - What's unclear: Should V2 migration be added to academic-app (owner of academic_db) or auth-service?
   - Recommendation: Add V2 migration to **academic-app** (`V2__seed_test_users.sql`) since Academic Service owns the schema. Auth-service just reads.

2. **Auth-service JWT filter for authenticated endpoints**
   - What we know: `/auth/change-password` and `/auth/logout` need a valid JWT. But Auth Service primarily issues tokens.
   - What's unclear: Should auth-service have its own JWT filter, or should logout/change-password accept the refresh token in the request body instead?
   - Recommendation: For logout, accept refresh token in body (already in the plan). For change-password (Phase 1.1 scope note: this is listed but may be deferred to after OTP). If needed, add a simple `OncePerRequestFilter` that extracts Bearer token and validates it.

3. **ErrorResponse sharing between services**
   - What we know: `ErrorResponse` record exists in `academic-api-contract`. Auth has no api-contract module.
   - What's unclear: Should auth-service depend on academic-api-contract for ErrorResponse?
   - Recommendation: Create a local copy of `ErrorResponse` in auth-service. A shared `common-api` module could be extracted later but is premature now.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Compilation | Verify | ms-21.0.9 (per CLAUDE.md) | -- |
| PostgreSQL | Users table read | Docker container rct-postgres-academic | 16 | -- |
| Redis | Refresh token storage | Docker container rct-redis | 7-alpine | -- |
| Gradle | Build | gradlew.bat in repo root | Wrapper | -- |

**Missing dependencies with no fallback:** None. All infrastructure is available via docker-compose.

## Sources

### Primary (HIGH confidence)
- JJWT GitHub discussions/827 -- RSA key pair usage with JJWT 0.12+
- JJWT GitHub CHANGELOG.md -- API changes in 0.12.0 (parser() replaces parserBuilder())
- Spring Security official docs -- SecurityFilterChain, OAuth2 Resource Server JWT
- Spring Data Redis docs -- StringRedisTemplate API
- Project codebase -- existing build.gradle.kts, EnumConverters, LowercaseEnumConverter, ErrorResponse patterns

### Secondary (MEDIUM confidence)
- Baeldung -- Spring Security session management, Redis TTL configuration
- Medium articles on Spring Boot 3 + JWT + RSA (multiple, cross-verified)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all libraries already declared or well-established Spring Boot starters
- Architecture: HIGH - package structure from phases-plan.md, patterns from existing codebase
- Pitfalls: HIGH - based on known JJWT migration issues and JPA/Flyway conflicts
- JWT API: HIGH - verified JJWT 0.12+ API via official GitHub discussions and changelog

**Research date:** 2026-03-28
**Valid until:** 2026-04-28 (stable stack, no fast-moving dependencies)
