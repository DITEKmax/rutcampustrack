**FAIL — текущий Gateway gate.** Архитектурное решение принято, но raw-XFF bypass и double dispatch требуют исправления и независимой recheck.

Findings:

- **HIGH — client-controlled rate-limit key.** `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfig.java:106` берёт первый raw `X-Forwarded-For`; `nginx/conf.d/default.conf:99,115,138` и `tests/e2e/infra/nginx/default.conf:42,52` дополняют входную цепочку через `$proxy_add_x_forwarded_for`. Impact: клиент меняет IP bucket login/OTP, сохраняя один реальный адрес. Reproduction: через Nginx повторять OTP/login с меняющимся первым XFF; запросы получают разные Redis keys вместо общего 429.
- **MEDIUM — подтверждён двойной downstream subscription.** `LoginBodyExtractionFilter.java:58-69` применяет `switchIfEmpty` к `Mono<Void>` после `chain.filter(mutated)`. Immutable probe на d3c31 дал `DOWNSTREAM_SUBSCRIPTIONS=2`, headers `[probe-student,null]`, exit 1. Impact: route chain исполняется дважды; две реальные Auth HTTP операции пока не доказаны. Reproduction: `.agent/student-role-02/gateway-double-dispatch-repro/GatewayDispatchProbe.java`.
- **MEDIUM — заявленный per-user resolver после sanitization теряет user id.** `InternalJwtIssuerFilter.java:72-85` в prod удаляет `X-User-*` до route filters, тогда как `RedisRateLimiterConfig.java:46-55` читает только `X-User-Id` и иначе переходит на IP. Impact: per-user route фактически может стать per-IP, объединяя NAT-пользователей и позволяя одному user менять bucket сменой IP. Reproduction: valid JWT, `strip-legacy-headers=true`, затем вызвать `userIdKeyResolver`; ожидается `user:<id>`, текущий результат — `ip:<address>`.
- **HIGH contract gap — built-in `RequestSize` не закрывает chunked.** Принятый SCG 4.3.5 `RequestSizeGatewayFilterFactory$1.filter` читает только `content-length` и при его отсутствии сразу продолжает chain. Одновременно `nginx/nginx.conf:21-28` оставляет generic 2 MiB, а специального `/api/v1/student/requests/excuse` route ещё нет. Impact: легальный `2 × 10 MiB` не проходит Nginx; простое добавление built-in `RequestSize` оставит direct/chunked oversize без Gateway enforcement. Reproduction: legal >2 MiB multipart через текущий Nginx получает 413; chunked request без Content-Length не проверяется самим SCG factory.

## 1. Goal

Устранить spoofing rate-limit identity, сохранить отдельные реальные IP за единственным Nginx edge, исправить single-dispatch login-body replay и последовательно добавить точный Requests ingress: 2 файла по 10 MiB, 20 MiB domain total, 24 MiB transport.

## 2. Context/evidence

Frozen baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` плюс accepted 45-file snapshot из `.agent/student-role-02/dependency-combined-review-source/manifest.json`, SHA-256 `3E795CB3F0CB8E95944BCBF1DC623E197554FAC24C5F5D2F5BB65ADFDDEF1D6A`.

Dependency gate принят: full Gradle exit 0, 1,647 executed, 4 pre-existing disabled, fresh Sol PASS. Эти результаты исключают Gateway trusted-IP/composite/single-dispatch и Requests ingress.

Подтверждённые originals:

- `RedisRateLimiterConfig.java:27-37,94-112`;
- `LoginBodyExtractionFilter.java:50-69,72-117`;
- `JwtAuthenticationFilter.java:56-81,145-148`;
- `InternalJwtIssuerFilter.java:72-85,114-117`;
- `application.yml:66-134,168-191`;
- `CompositeLoginKeyResolverIT.java:37-40` — class-level `@Disabled`;
- `docker-compose.prod.yml:508-535,875-902,1006-1008`;
- `docker-compose.e2e.yml:314-337,506-524`;
- prod/e2e Nginx locations выше.

## 3. Relevant scope

Fresh isolated writer импортирует exact45 в новый worktree; accepted dependency checkout остаётся read-only.

Owned files:

- Existing:
  - `RedisRateLimiterConfig.java`
  - `LoginBodyExtractionFilter.java`
  - `JwtAuthenticationFilter.java`
  - `application.yml`, `application-prod.yml`
  - `docker-compose.prod.yml`, `docker-compose.e2e.yml`
  - `nginx/nginx.conf`, `nginx/conf.d/default.conf`
  - `tests/e2e/infra/nginx/nginx.conf`, `tests/e2e/infra/nginx/default.conf`
  - соответствующие Gateway rate-limit/filter tests.
- New, with these responsibilities:
  - `clientip/TrustedClientIpProperties.java`
  - `clientip/TrustedClientIpResolver.java`
  - `clientip/TrustedClientIpFilter.java`
  - `filter/StudentRequestsNoStoreFilter.java`
  - `filter/StreamingRequestSizeGatewayFilterFactory.java`
  - focused unit/integration tests for each.

CI/env wiring may change only where the existing prod/e2e Compose commands receive the new network values; не создавать второй config source.

## 4. Required behavior

1. Set `server.forward-headers-strategy: none`; raw socket peer must remain authoritative before every GlobalFilter.

2. Configure `rutcampustrack.gateway.client-ip.trusted-proxy-addresses` as IP literals only. No DNS names, regex, wildcard, CIDR or implicit private ranges. Base/dev default is empty trust. Prod requires a nonempty valid list and fails startup otherwise.

3. Prod/e2e Compose owns an explicit subnet and stable Nginx `ipv4_address`; values come from required environment configuration, not guessed source facts. Gateway receives the same exact Nginx address. Nginx starts after healthy Gateway as today; no circular DNS dependency.

4. Nginx overwrites every Gateway-facing request:

   - `X-Forwarded-For $remote_addr`
   - `X-Real-IP $remote_addr`
   - authoritative proto/host/port
   - empty `Forwarded`

   Never use `$proxy_add_x_forwarded_for` on Gateway locations.

5. `TrustedClientIpFilter`, order `-300`, reads raw remote peer, computes one canonical IP, stores it in an exchange attribute and sanitizes forwarding headers before PWA/JWT/login filters. Disable SCG’s automatic Forwarded/X-Forwarded generation and let this filter be the sole normalization owner.

6. Trusted peer accepts only one XFF header containing one strict IP literal. Trim whitespace; reject comma chains, multiple header lines, empty/unknown, ports, brackets and IPv6 zone identifiers. Canonicalize IPv4/IPv6 and IPv4-mapped IPv6. Invalid trusted input falls back to the proxy peer bucket. Untrusted or null peer ignores every forwarding header and uses the raw peer or constant `unknown`. It must never derive identity from `Forwarded` or `X-Real-IP`.

7. Preserve Redis key shapes. `ipKeyResolver` uses canonical IP; login/user fallback keeps `ip:<canonical>`. Store authenticated user id in an internal exchange attribute in `JwtAuthenticationFilter`; `userIdKeyResolver` reads that attribute before headers so later legacy-header stripping cannot convert per-user routes to per-IP.

8. Keep login route as the single existing IP limiter. Do not restore a second `RequestRateLimiter`, enable the disabled composite IT or claim active IP+login protection. `ipLoginKeyResolver` remains a dormant seam pending a separate custom composite-policy decision.

9. Repair `LoginBodyExtractionFilter`:

   - order `-90`, after JWT sanitization and before `InternalJwtIssuerFilter`;
   - apply empty-body default before `flatMap(chain::filter)`, never `switchIfEmpty` on `Mono<Void>`;
   - valid, empty and malformed bodies reach downstream exactly once;
   - replay exact bytes once; when setting Content-Length, remove Transfer-Encoding;
   - strip/normalize login with `Locale.ROOT`, preserving body bytes;
   - known or chunked body above 4,096 bytes returns one 413 Problem Details with `Cache-Control: no-store`, zero downstream calls and released buffers.

10. Add `StudentRequestsNoStoreFilter`, order `-200`, for `/api/v1/student/requests` and descendants so JWT/PWA/rate-limit/size/downstream responses all commit `Cache-Control: no-store`.

11. Add the exact `POST /api/v1/student/requests/excuse` route before generic mobile BFF. Keep codec `12MB`. Use custom `StreamingRequestSize` with exact maximum `25,165,824` bytes; built-in `RequestSize` alone is forbidden. Reject oversized known length before chain; count chunked buffers, release the offending/discarded buffers and map the overflow to 413/no-store.

12. Add exact Nginx location `= /api/v1/student/requests/excuse` with `client_max_body_size 24m`. Preserve global 2m. Add no-store at server scope through an `http`-level `$uri` map; do not add a location-level `add_header` that suppresses inherited prod security headers. Add `Idempotency-Key` to prod CORS allowed headers.

## 5. Constraints

No deploy, firewall, production env mutation, secrets, broad proxy trust, dependency upgrade, composite limiter restoration, Requests/BFF/domain/profile/auth-session/R26 refactor, or edits in active writers’ worktrees.

Static trust supports the source-owned single Nginx edge. An upstream CDN/LB, Nginx replicas or dynamic proxy addresses require a new explicit hop policy. Changing the trusted IP requires coordinated config plus Gateway restart; restarting/recreating Nginx with its configured static address does not.

A streaming Gateway cap may expose a prefix to BFF before chunk overflow. Therefore “no Mongo writes” is an integration invariant to prove, not something the filter alone establishes.

## 6. Existing patterns

Preserve:

- PWA filter `-110`, JWT `-100`, Internal issuer `-50`, rate-limit response decorator `-40`;
- header stripping and internal token exchange;
- IP limiter thresholds and 429 Problem Details/Retry-After;
- exact route-before-generic ordering;
- global codec 12 MB;
- Nginx request buffering and existing TLS/security headers;
- fail-open Redis behavior as existing out-of-scope policy.

Final order: client IP `-300` → Requests no-store `-200` → PWA `-110` → JWT `-100` → login extraction `-90` → internal issuer `-50` → rate-limit response `-40` → ordered route filters.

## 7. Acceptance criteria

- Rotating/spliced/multiple XFF from one untrusted peer never changes its Redis bucket.
- Nginx overwrites hostile forwarding headers; two actual edge client source addresses retain separate buckets.
- Missing/malformed trusted XFF collapses to the fixed proxy bucket and cannot select attacker input.
- IPv4, IPv6, mapped IPv6, null remote and malformed forms have deterministic keys.
- Five accepted login/OTP attempts and the next 429 retain current thresholds, Problem Details and Retry-After.
- Valid authenticated Requests routing resolves `user:<id>` even after legacy headers are stripped.
- Login valid/empty/malformed/non-login requests call downstream once; oversize calls it zero times.
- Composite remains disabled and no second RL response-commit regression returns.
- Exact 2 × 10 MiB multipart succeeds; known-length and chunked >24 MiB return external 413/no-store; rejected input produces zero Mongo/domain/outbox writes.
- CORS preflight accepts `Idempotency-Key`; auth, CORS and security headers remain intact.
- Nginx restart retains trusted identity; empty/malformed prod trust config fails Gateway startup.

## 8. Verification

Future evidence must record revision, command, exit, environment and hashes:

- exact45 source/destination SHA equality and clean scoped diff;
- focused Gateway unit tests for strict literal parsing, trusted/untrusted resolution, header normalization, internal user attribute, body replay and buffer cleanup;
- Gateway + Redis + synthetic downstream integration for spoofed login/OTP, 429 and exact downstream request counts;
- isolated two-network Nginx test with two client containers, hostile headers and direct untrusted Gateway peer;
- Nginx config test plus Compose config validation with task-owned subnet/IP;
- Nginx restart/recreate check;
- full nginx → Gateway → BFF → gRPC → Mongo known-length/chunked exact/oversize scenarios, with zero writes on rejection and no external OTP/Telegram;
- stable diff, then fresh independent Sol high recheck of affected Gateway/nginx/config/runtime evidence.

Consultation runtime: N/A; no files changed.

## 9. Do not

Do not trust hostname resolution, private ranges, arbitrary CIDRs, first/last raw XFF, `Forwarded`, `X-Real-IP`, or current deployed topology assumptions. Do not restore two sequential rate limiters, raise the global codec, rely on built-in `RequestSize` for chunked input, add location-level Nginx headers that drop inherited security headers, claim two real Auth calls from the mock probe, or mark the Gateway/Requests gate PASS before implementation, runtime and independent recheck.
