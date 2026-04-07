# Phase 34: Auth Service TMA - Research

**Researched:** 2026-04-06
**Domain:** Telegram Mini App initData validation + body-based JWT refresh in Spring Boot
**Confidence:** HIGH

## Summary

Phase 34 adds two new endpoints to the existing auth-service: `POST /api/auth/tma` for exchanging Telegram initData for a JWT pair, and `POST /api/auth/refresh-body` as a body-based alternative to the cookie-based refresh (needed because Telegram WebView drops httpOnly cookies).

The auth-service already has strong patterns to follow: `OtpService` demonstrates the exact same flow — look up user by `telegram_id`, validate, issue `TokenResponse`. The TMA endpoint reuses that exact structure but replaces OTP verification with HMAC-SHA256 validation of Telegram's initData. The refresh-body endpoint is even simpler — it is a rename/copy of the existing `/auth/refresh` endpoint (which already accepts `RefreshRequest` in the body), just made explicit for Mini App consumers.

The Gateway `JwtAuthenticationFilter` already has both new paths (`/api/auth/tma` and `/api/auth/refresh-body`) registered in `PUBLIC_PATHS` as of Phase 33. No Gateway changes are required. The Telegram initData validation algorithm is well-documented and straightforward: derive a secret key with `HMAC_SHA256(bot_token, "WebAppData")`, build a sorted data-check-string from all fields except `hash`, compute `HMAC_SHA256(data_check_string, secret_key)`, compare hex. All primitives needed (`javax.crypto.Mac`, `HmacSHA256`) are part of Java standard library — no new dependencies required.

**Primary recommendation:** Implement `TmaService` with pure Java HMAC validation, reuse `UserRepository.findByTelegramId()` already present, add `TmaAuthRequest` and `TmaProperties` records, expose via two new controller methods. `refresh-body` delegates directly to existing `AuthService.refresh()`.

## Project Constraints (from CLAUDE.md)

- Contract-first: auth-service does NOT use the `*-api-contract` split (it has no separate contract module — it's a single-module service). No separate contract module to update.
- Request DTOs = Java `record`. No Lombok in DTO records.
- Lombok allowed in entity classes (`User.java` uses `@Getter`, `@NoArgsConstructor`).
- `@ControllerAdvice` handles all error responses — controller only throws exceptions.
- Errors: RFC 7807 Problem Details (`ErrorResponse` record with `application/problem+json`).
- Enums in PostgreSQL: lowercase strings, `LowercaseEnumConverter` with `autoApply=true`.
- `ddl-auto: validate` — no schema changes trigger Hibernate auto-creation.
- Tests: Testcontainers (PostgreSQL + Redis), `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `TestRestTemplate`.
- `@Sql` annotations for test fixture setup/teardown.

## Standard Stack

### Core (already in auth-service)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.x | Web + Security + JPA | Project standard |
| jjwt | 0.12.6 | JWT generation/parsing | Already used [VERIFIED: build.gradle.kts] |
| Spring Data Redis | (Boot-managed) | Refresh token storage | Already used [VERIFIED: build.gradle.kts] |
| Spring Data JPA | (Boot-managed) | User lookup | Already used [VERIFIED: build.gradle.kts] |
| `javax.crypto.Mac` | JDK 21 stdlib | HMAC-SHA256 computation | No new dependency needed [VERIFIED: JDK stdlib] |

### New Dependencies Needed
**None.** All cryptographic primitives for HMAC-SHA256 (`javax.crypto.Mac`, `javax.crypto.spec.SecretKeySpec`) are part of JDK 21. [VERIFIED: JDK stdlib — Java SE 21]

**Installation:** No new dependencies to add to `build.gradle.kts`.

## Architecture Patterns

### Recommended File Structure (additions only)
```
services/auth-service/src/main/java/ru/rutcampustrack/auth/
├── config/
│   └── TmaProperties.java          # @ConfigurationProperties(prefix = "tma") — bot token
├── dto/
│   └── TmaAuthRequest.java         # record { @NotBlank String initData }
├── exception/
│   └── TmaValidationException.java # extends RuntimeException — for tampered initData
└── service/
    └── TmaService.java             # HMAC validation + user lookup + TokenResponse

services/auth-service/src/main/resources/
└── application.yml                 # add tma.bot-token property

services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/
└── TmaIntegrationTest.java         # extends AbstractIntegrationTest
```

### Pattern 1: HMAC-SHA256 Telegram initData Validation

**What:** Validate Telegram's initData string by replicating the HMAC-SHA256 check server-side.
**When to use:** Every call to `POST /api/auth/tma`.
**Algorithm** [CITED: https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app]:

1. Parse initData as URL query string (e.g., `URLDecoder` or `HttpUtils`)
2. Extract the `hash` parameter and remove it from the map
3. Sort remaining parameters alphabetically by key
4. Build data-check-string: `key=value\nkey=value\n...` (LF-separated, no trailing newline)
5. Derive secret key: `HMAC_SHA256("WebAppData", bot_token)` (key = `"WebAppData"`, data = bot_token)
6. Compute: `HMAC_SHA256(data_check_string, secret_key)`
7. Hex-encode result and compare to extracted `hash`
8. Also validate `auth_date` is not older than a configurable threshold (e.g., 86400 seconds)

```java
// Source: Telegram docs + JDK stdlib
private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data);
}

public boolean validate(String initData, String botToken) throws Exception {
    Map<String, String> params = parseQueryString(initData); // URLDecoder parse
    String receivedHash = params.remove("hash");
    if (receivedHash == null) return false;

    String dataCheckString = params.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining("\n"));

    byte[] secretKey = hmacSha256(botToken.getBytes(StandardCharsets.UTF_8),
                                   "WebAppData".getBytes(StandardCharsets.UTF_8));
    byte[] computedHash = hmacSha256(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);
    String computedHex = HexFormat.of().formatHex(computedHash); // Java 17+ stdlib
    return computedHex.equals(receivedHash);
}
```

### Pattern 2: User Lookup by telegram_id (already established)

**What:** `UserRepository.findByTelegramId(Long telegramId)` is already defined and indexed.
**Source:** [VERIFIED: UserRepository.java, V1__baseline.sql `idx_users_telegram`]

The `user` field in initData is JSON-serialized. Parse it to extract `id` (the Telegram user ID), then call `findByTelegramId()`. Optionally update `telegram_username` from the initData user object if it has changed.

```java
// initData user field is JSON: {"id":123456789,"first_name":"...","username":"..."}
// Use Jackson ObjectMapper (already on Spring Boot classpath) to parse
Long telegramId = objectMapper.readTree(userJson).get("id").asLong();
User user = userRepository.findByTelegramId(telegramId)
    .orElseThrow(InvalidCredentialsException::new);
```

### Pattern 3: refresh-body Endpoint

**What:** Body-based refresh that delegates entirely to existing `AuthService.refresh()`.
**When:** TMA clients that cannot use httpOnly cookies (Telegram WebView).

The existing `/auth/refresh` already accepts `RefreshRequest { @NotBlank String refreshToken }` in the body and returns `TokenResponse`. The `refresh-body` endpoint is functionally identical — it can simply call `authService.refresh(request)` exactly like the existing `/auth/refresh` method. The reason for a separate path is clarity for the Mini App client.

```java
@PostMapping("/refresh-body")
public ResponseEntity<TokenResponse> refreshBody(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(authService.refresh(request));
}
```

### Pattern 4: TmaProperties Configuration

```java
// Source: established ConfigurationProperties pattern in this service
@ConfigurationProperties(prefix = "tma")
public record TmaProperties(
    String botToken,
    long authDateMaxAgeSeconds  // default: 86400 (24 hours) for replay protection
) {}
```

```yaml
# application.yml addition
tma:
  bot-token: ${TMA_BOT_TOKEN:test_bot_token_for_dev}
  auth-date-max-age-seconds: 86400
```

### Pattern 5: SecurityConfig — permitAll new paths

Add to the `requestMatchers` list in `SecurityConfig`:
```java
"/auth/tma",
"/auth/refresh-body"
```

Note: Gateway `JwtAuthenticationFilter.PUBLIC_PATHS` already contains `/api/auth/tma` and `/api/auth/refresh-body` [VERIFIED: JwtAuthenticationFilter.java lines 36-37]. The auth-service's own `SecurityConfig` also needs these paths added so its internal filter doesn't require authentication.

### Pattern 6: Exception handling for TMA

Add `TmaValidationException` (extends `RuntimeException`) and a handler in `GlobalExceptionHandler`:

```java
@ExceptionHandler(TmaValidationException.class)
public ResponseEntity<ErrorResponse> handleTmaValidation(
        TmaValidationException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .contentType(PROBLEM_JSON)
            .body(new ErrorResponse(..., "TMA Validation Failed", 401, ex.getMessage(), ...));
}
```

### Anti-Patterns to Avoid

- **Using `String.equals()` for hash comparison without constant-time check:** Timing attacks are theoretical at this level but `MessageDigest.isEqual()` is the safe standard — use it for the final hash comparison.
- **Keeping `hash` field in the data-check-string:** The algorithm requires removing `hash` before building the sorted string. Including it gives wrong results.
- **URL-decoding the `user` JSON field twice:** The initData is URL-encoded. Parse query parameters once with `URLDecoder`; the `user` value will be a JSON string after single decode.
- **Hardcoding bot token in source:** Must use environment variable `TMA_BOT_TOKEN`, never committed.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HMAC-SHA256 | Custom crypto | `javax.crypto.Mac` (JDK stdlib) | Already in classpath, correct, audited |
| JSON parsing of `user` field | Manual string split | Jackson `ObjectMapper` (already on Spring Boot classpath) | Correct escape handling |
| URL query string parsing | Manual split on `&` | `java.net.URLDecoder` + split, or Spring's `UriComponentsBuilder` | Edge cases in percent-encoding |
| Token generation | New logic | `JwtService.generateAccessToken/RefreshToken()` + `AuthService` Redis storage | Exact same code as login/OTP flows |
| Refresh token rotation | New logic | `AuthService.refresh()` delegate | Already implemented, tested |

**Key insight:** The entire "issue tokens after successful auth" flow is already fully implemented. `TmaService` needs only the HMAC validation + user lookup; token issuance reuses `JwtService` + `AuthService` internals.

## Common Pitfalls

### Pitfall 1: HMAC key order confusion
**What goes wrong:** Developers swap the key and data in HMAC calls. The Telegram algorithm requires `HMAC_SHA256(key="WebAppData", data=botToken)` to get the secret, then `HMAC_SHA256(key=secret, data=dataCheckString)` for the final hash.
**Why it happens:** `Mac.init()` takes the key; `mac.doFinal()` takes the data. It's easy to confuse which is "key" and which is "data" in the first step.
**How to avoid:** Name the method `hmacSha256(byte[] data, byte[] key)` consistently and verify against test vectors from official Telegram examples.
**Warning signs:** Valid initData always returns 401.

### Pitfall 2: auth_date replay window not enforced
**What goes wrong:** No expiry check on `auth_date` — a captured initData token works forever.
**Why it happens:** The HMAC check passes; developers forget the timestamp check.
**How to avoid:** Check `System.currentTimeMillis()/1000 - authDate < maxAgeSeconds` in `TmaService`. Throw `TmaValidationException("initData expired")` if stale.
**Warning signs:** Security review flags missing replay protection.

### Pitfall 3: user not linked to any account (telegram_id not in DB)
**What goes wrong:** Student hasn't linked their Telegram account via bot /start flow. `findByTelegramId()` returns empty.
**Why it happens:** This is a valid operational scenario — student has the Mini App but hasn't run /start on the bot.
**How to avoid:** Throw `InvalidCredentialsException` (reuse existing) which maps to 401. Client shows "Please start @RutTrackBot first to link your account."
**Warning signs:** NullPointerException or 500 if not handled.

### Pitfall 4: initData field percent-encoding
**What goes wrong:** The `user` JSON field contains characters like `{`, `}`, `"` which are percent-encoded in the query string. Failing to decode before JSON parsing causes Jackson to fail.
**Why it happens:** Raw initData from Telegram WebView is URL-encoded.
**How to avoid:** Use `URLDecoder.decode(value, StandardCharsets.UTF_8)` when parsing each query param value.
**Warning signs:** Jackson `JsonParseException` when parsing user field.

### Pitfall 5: refresh-body vs refresh — no functional difference
**What goes wrong:** Over-engineering by creating a new `RefreshBodyService` or new DTOs.
**Why it happens:** The name "refresh-body" sounds different.
**How to avoid:** The endpoint is literally `authService.refresh(request)` — one line. Same `RefreshRequest`, same `TokenResponse`, same Redis rotation logic.

## Code Examples

### Full TmaService skeleton
```java
// Source: pattern derived from OtpService.java (verified in codebase) + Telegram docs
@Service
public class TmaService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final TmaProperties tmaProperties;
    private final ObjectMapper objectMapper;

    // ... constructor injection

    public TokenResponse authenticateWithInitData(TmaAuthRequest request) {
        String initData = request.initData();

        if (!validateInitData(initData)) {
            throw new TmaValidationException("Invalid or tampered initData");
        }

        Map<String, String> params = parseQueryString(initData);

        // Check auth_date for replay protection
        long authDate = Long.parseLong(params.get("auth_date"));
        long now = System.currentTimeMillis() / 1000;
        if (now - authDate > tmaProperties.authDateMaxAgeSeconds()) {
            throw new TmaValidationException("initData expired");
        }

        // Extract telegram user id from user JSON field
        String userJson = params.get("user");
        Long telegramId = extractTelegramId(userJson);

        User user = userRepository.findByTelegramId(telegramId)
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(InvalidCredentialsException::new);

        // Issue tokens (same as login/OTP flow)
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
            // Constant-time comparison
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
}
```

### Test pattern for TMA (from established OtpIntegrationTest pattern)
```java
// Source: OtpIntegrationTest.java pattern (verified in codebase)
@Sql(scripts = "classpath:sql/set-telegram-id.sql")
@Sql(scripts = "classpath:sql/clear-telegram-id.sql",
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TmaIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    @Test
    void tma_withValidInitData_returnsTokenPair() {
        // Build valid initData using same bot token from test config
        String initData = buildValidInitData(123456789L, testBotToken);

        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
            "/auth/tma", request, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void tma_withTamperedInitData_returns401() {
        String tamperedInitData = "auth_date=1234567890&user=%7B%22id%22%3A123456789%7D&hash=deadbeef";

        TmaAuthRequest request = new TmaAuthRequest(tamperedInitData);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/auth/tma", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString())
            .contains("application/problem+json");
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| httpOnly cookie refresh | Body-based refresh (`/refresh-body`) | v6.0 decision | WebView-compatible |
| OTP-based Telegram auth | initData HMAC auth | Phase 34 | No OTP round-trip needed for Mini App |

**Context for refresh-body:** The existing `/auth/refresh` already uses body-based `RefreshRequest` (not cookie). The PWA uses this. The decision to have a *separate* `/refresh-body` endpoint exists purely for client clarity and explicitness, not because the mechanism differs.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `TMA_BOT_TOKEN` env var is the correct name and will be set in docker-compose/deployment | Standard Stack, Code Examples | Build/runtime config mismatch — low risk, easily renamed |
| A2 | `auth_date` max-age of 86400 seconds (24 hours) is acceptable | Architecture Patterns | Too tight: rejecting valid users; too loose: replay window — configurable via `TmaProperties` mitigates |
| A3 | The bot token used in Mini App is the same bot already used for notification-bot | Architecture Patterns | If separate bots, separate `TMA_BOT_TOKEN` vs `BOT_TOKEN` env vars needed |

## Open Questions

1. **Bot token for TMA**
   - What we know: notification-bot uses `BOT_TOKEN` in its Python config
   - What's unclear: Is the Mini App launched from the same bot? If yes, use the same token. If a different bot, need a separate env var.
   - Recommendation: Use `TMA_BOT_TOKEN` as a separate env var that can point to the same value as `BOT_TOKEN` in docker-compose. This avoids coupling.

2. **auth_date max-age in development**
   - What we know: Tests need to generate valid initData on the fly
   - What's unclear: Should the test use a very large max-age or generate fresh auth_date in the helper?
   - Recommendation: Test helper generates initData with `auth_date = System.currentTimeMillis()/1000`. Keep max-age configurable. Test overrides to large value or generates fresh.

## Environment Availability

Step 2.6: SKIPPED — Phase 34 adds pure Java code to an existing Spring Boot service. No new external tools or services are required. All dependencies already in docker-compose.yml (PostgreSQL/academic_db, Redis).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers |
| Config file | `src/test/resources/application-test.yml` |
| Quick run command | `./gradlew.bat :services:auth-service:test --tests "*TmaIntegrationTest*"` |
| Full suite command | `./gradlew.bat :services:auth-service:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | Valid initData returns JWT pair | integration | `./gradlew.bat :services:auth-service:test --tests "*TmaIntegrationTest.tma_withValidInitData*"` | Wave 0 |
| AUTH-01 | Tampered initData returns 401 problem+json | integration | `./gradlew.bat :services:auth-service:test --tests "*TmaIntegrationTest.tma_withTamperedInitData*"` | Wave 0 |
| AUTH-02 | Valid refresh token in body returns new pair | integration | `./gradlew.bat :services:auth-service:test --tests "*TmaIntegrationTest.refreshBody_withValidToken*"` | Wave 0 |
| AUTH-01 | User without telegram_id linked returns 401 | integration | `./gradlew.bat :services:auth-service:test --tests "*TmaIntegrationTest.tma_withUnlinkedUser*"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew.bat :services:auth-service:test --tests "*TmaIntegrationTest*"`
- **Per wave merge:** `./gradlew.bat :services:auth-service:test`
- **Phase gate:** Full auth-service test suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/ru/rutcampustrack/auth/integration/TmaIntegrationTest.java` — covers AUTH-01, AUTH-02
- [ ] `src/test/resources/sql/set-telegram-id.sql` already exists — reuse it

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | HMAC-SHA256 initData validation per Telegram spec |
| V3 Session Management | yes | Refresh token rotation in Redis (already implemented) |
| V4 Access Control | no | N/A — new endpoints are public auth entry points |
| V5 Input Validation | yes | `@NotBlank` on `initData`, Jackson for JSON parsing |
| V6 Cryptography | yes | `javax.crypto.Mac` (JDK stdlib) — never hand-rolled |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Forged initData (missing bot token) | Spoofing | HMAC-SHA256 validation — invalid without bot token |
| Replay attack with old initData | Repudiation | `auth_date` max-age check (configurable, default 24h) |
| Timing oracle on hash comparison | Information Disclosure | `MessageDigest.isEqual()` constant-time comparison |
| Unlinked Telegram account | Spoofing | `findByTelegramId()` returns empty → 401, no user enumeration |
| Bot token leak | Elevation of Privilege | `TMA_BOT_TOKEN` env var, never in source code or logs |

## Sources

### Primary (HIGH confidence)
- Codebase: `services/auth-service/src/main/java/**` — all existing code verified by direct read
- Codebase: `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java` — PUBLIC_PATHS already includes tma and refresh-body paths
- [CITED: https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app] — initData HMAC algorithm
- JDK 21 stdlib — `javax.crypto.Mac`, `HmacSHA256`, `MessageDigest.isEqual()`, `HexFormat`

### Secondary (MEDIUM confidence)
- [CITED: https://core.telegram.org/bots/webapps#initializing-mini-apps] — initData field structure, user JSON format

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all verified from codebase and JDK stdlib
- Architecture: HIGH — patterns directly observed in OtpService and AuthService
- Telegram algorithm: HIGH — official Telegram documentation
- Pitfalls: HIGH — derived from algorithm specifics and observed codebase patterns

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (stable domain — Telegram initData validation algorithm is stable)

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | `POST /api/auth/tma` validates Telegram initData (HMAC-SHA256) and returns JWT | `TmaService.validateInitData()` + `UserRepository.findByTelegramId()` + `JwtService.generateAccessToken/RefreshToken()` |
| AUTH-02 | `POST /api/auth/refresh-body` accepts refresh token in request body and returns new token pair | Delegates directly to existing `AuthService.refresh()` — one-line implementation |
</phase_requirements>
