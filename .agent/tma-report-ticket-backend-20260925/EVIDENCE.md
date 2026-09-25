# Report download ticket backend — evidence

## Scope and candidate

Implement short-lived bearer-free downloads for the five frozen report kinds using an Auth-issued, opaque 256-bit capability. Auth binds a validated selector to the current session and remints signed internal authority after a fresh session/role admission on every redemption. Gateway dispatches only to fixed report endpoints and returns a bounded, safe attachment. The exact ticket route has a narrow bearer-free flow; NGINX redacts ticket paths from access logs and suppresses URI-bearing route error logs.

Worktree: `map-usage-delivery-20260922`; branch `codex/tma-report-ticket-20260925`; base/HEAD at start: `e185dc1982eadf00c909283459ce2c2b1e1510b9`. No commit, push, merge, or deployment was performed.

Changed files in this leaf’s scope:

- `nginx/nginx.conf`, `nginx/conf.d/default.conf`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/GatewayApplication.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerClient.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalReportTicketRateLimitedException.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/ReportDownloadBackendProperties.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/ReportDownloadTicketDownloadFilter.java`
- `services/api-gateway/src/main/resources/application.yml`
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/ReportDownloadTicketDownloadFilterTest.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/InternalReportDownloadTicketApi.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/ReportDownloadTicketApi.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/IssueReportDownloadTicketRequest.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/RedeemReportDownloadTicketRequest.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/ReportDownloadFormat.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/ReportDownloadKind.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/ReportDownloadTicketRedemptionResponse.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/ReportDownloadTicketResponse.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/InternalReportDownloadTicketController.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/ReportDownloadTicketController.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/InvalidReportDownloadTicketRequestException.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/RedisReportDownloadTicketStore.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/ReportDownloadTicketRateLimitException.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/ReportDownloadTicketService.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/ReportDownloadTicketStore.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/StoredReportDownloadTicket.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/arch/AuthApiContractTest.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/ReportDownloadTicketValidationTest.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/ReportDownloadTicketServiceTest.java`

Pre-existing foreign changes preserved: `.agent/orchestration-v2/RULES.md`, `.agent/orchestration-v2/LEAF-PACKET.md`, and `.agent/semester-finish-20260925/`. They are not part of this candidate.

## Required behavior and acceptance evidence

- `POST /api/auth/report-download-tickets` requires the ordinary authenticated selected session, validates one typed allowlisted selector, stores no original access token, returns a relative path, server-generated filename, and absolute expiry. Opaque URL-safe tokens contain 32 random bytes; Redis keys use a SHA-256 digest and the stored selector/identity has a fixed 60-second TTL. Issue and per-ticket redemption counts are bounded.
- Each redemption reloads current Auth session state and requires the stored user/session/version/role/status/group/headman/read-only identity to remain selectable and coherent. It remints a signed RS256 internal token bound to the selector hash, and caps token expiry by ticket, live session, and normal internal TTL. Unknown/expired capabilities return 404; dependency failure is fail-closed.
- The Gateway dispatch is closed over `TEACHER_JOURNAL`, `TEACHER_STATS`, `HEADMAN_WEEKLY_CURRENT`, `HEADMAN_WEEKLY_SELECTED`, and `HEADMAN_STATS`. Endpoints, HTTP methods, query names, and POST payload shapes are server-defined; client query/body overrides and `Range` are rejected. Downstream report services still make final domain authorization decisions.
- Every syntactically valid download ticket consumes a fail-closed Redis request budget of 20 attempts per canonical client IP per fixed 60-second window before Auth redemption, including unknown tickets. The key is a digest of the normalized address; untrusted forwarding headers are not used. Exhaustion returns 429 without calling Auth; budget-store failure returns 503 without calling Auth.
- Binary responses are capped at 20 MiB and restricted to the selected format’s expected media type. Response headers use a server-generated attachment name, `no-store`, `no-referrer`, `nosniff`, and `Accept-Ranges: none`. `Access-Control-Allow-Origin` is set only for `https://web.telegram.org`, without credentials or wildcard origins.
- NGINX retains normal access logs but replaces the complete request line with a redacted route for `/api/report-download/`, blanks referrer and user-agent for that route, and sends its error log to `/dev/null`. Other routes keep the existing log variables and behavior.
- Malformed selectors explicitly map to 400; this uses a JavaBean `is...` `@AssertTrue` accessor plus a service guard.
- Gateway error mapping preserves downstream 403/404/413/422 rather than wrapping classified response errors as 503; error bodies are sanitized.
- `HEADMAN_STATS` dispatch depends on the endpoint in the separate stats package candidate `d223c8e9`; integrate that package before this ticket package.

## Checks and outcomes

All Gradle invocations used `--no-daemon --no-parallel --max-workers=1 --no-problems-report --system-prop=org.gradle.java.compile-classpath-packaging=true --console=plain` in the assigned worktree. Commands that needed access to shared Gradle artifacts used the approved elevated shell.

| Check | Result | Evidence |
|---|---:|---|
| `:services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.controller.ReportDownloadTicketValidationTest --tests ru.rutcampustrack.auth.service.ReportDownloadTicketServiceTest` | PASS, exit 0 | Targeted compile plus selector 400, 60-second absolute TTL, retries, fresh session admission, stale identity denial, and no URL-token storage checks. |
| `:services:api-gateway:test --tests ru.rutcampustrack.gateway.security.ReportDownloadTicketDownloadFilterTest` | PASS, exit 0 | Corrected run: 7 tests. Covers fixed dispatch for HEADMAN_STATS and teacher journal, bound selector/body, exact Telegram origin headers, hostile incoming bearer/identity headers absent downstream, sanitized 403 and 422, unknown ticket, non-GET/query/Range rejection, and canonical-IP budget exhaustion with no Auth call beyond the allowed budget. |
| `git diff --check` | PASS, exit 0 | No whitespace errors in the current worktree diff. |
| Digest-pinned NGINX syntax check: `docker run --rm --network none --entrypoint nginx --mount <readonly snapshot nginx.conf> --mount <readonly snapshot conf.d> nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10 -t` | PASS, exit 0 | `nginx: configuration file /etc/nginx/nginx.conf test is successful`. Disposable container, no network or published ports. |

Earlier failed targeted attempts and corrections were evidence-driven: Auth test compilation first rejected a Jakarta validator passed to Spring’s `setValidator` (exit 1); the test was changed to use the standalone Bean Validation setup and then passed. Gateway compile first rejected `OptionalLong.orElse(null)` (exit 1); changed to `isPresent()/getAsLong()`. The next compile rejected unsupported WireMock `.withoutHeader` calls (exit 1); changed to `absent()` matchers. The next run exposed Mockito/WireMock verifier confusion and the WireMock global default port (exit 1); changed to server-instance verification. The final test run passed. No product behavior was changed in response to unrelated warnings.

Independent Sol review of `e185dc1..517876a` found two P2 issues. The corrected candidate preserves sanitized downstream 422 responses for actionable report-selection errors, and applies a 20-per-minute fail-closed Redis budget by canonical client IP before calling Auth for each syntactically valid ticket. The added bounded-budget test changes forged `X-Forwarded-For` while holding the socket peer constant, then proves a denied request returns 429 without another Auth call. The corrected Gateway test task passed; the same independent reviewer must recheck this correction commit before it is accepted.

The NGINX check used a temporary snapshot, not repository config edits: it removed only the unchanged `ssl_certificate`, `ssl_certificate_key`, and `ssl_dhparam` file directives, and removed `ssl` from `listen 443` so the syntax check did not read TLS files. All changed maps, log format, security-header mapping, proxy settings, and the report-download location were preserved. Temporary snapshot preparation first exited 1 because the assertion did not account for CRLF; the corrected preparation exited 0. Actual TLS file parsing is therefore not covered by this check.

## Runtime evidence and limits

Runtime evidence is limited to the targeted Auth/Gateway test tasks and isolated NGINX config parsing. No app stack was started. No actual NGINX request, Telegram native host download/callback, or downloaded report file was exercised. The frontend/native adapter is a separate next scope; this backend packet alone does not prove end-to-end TMA delivery. No deployment or production change was performed. The separate stats package `d223c8e9` must be integrated before enabling the HEADMAN_STATS dispatch. Independent Sol recheck of the correction commit is still required.
