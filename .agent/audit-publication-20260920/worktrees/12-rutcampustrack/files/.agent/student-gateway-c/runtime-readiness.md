# Gateway C runtime readiness

Status: NOT RUN / WAIT B priority. No runtime PASS is claimed by this packet.
The accepted unit checks do not prove Netty framing, Nginx forwarding, Redis
bucket isolation, or downstream write absence.

## Prerequisites and run gate

- A access authority: accepted signed access JWT plus its admission/session
  authority. H6 and integrated tests must name the final fixture or checkout.
- B/B1 backend baseline: accepted Java-first union/export, exact-25 compile
  result, and stable database/service baseline.
- Requests producer authority:
  C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract
  with final manifest SHA-256
  1DE6B9AF78677A28199E7503C0DA04813715352D238F438897011DC89ED96502
  and independent PASS report SHA-256
  802EAF24E88C918DB3363353E1AF5C5F4EDECC70E840B015F6B841512018C96C.
- Gateway repair24: .agent/student-gateway-c/repair-manifest.json remains
  immutable and all 24 recorded file hashes must match.
- UI/E are not prerequisites for the server-path runtime.

## Fixed topology and resources

Nginx is the only trusted edge and has an explicit static address. Gateway owns
forwarding normalization and disables framework Forwarded/X-Forwarded
generation. Redis is isolated with the Gateway. The fake upstream records
request starts, completed request bodies, received byte counts and headers.
The integrated stack records BFF/Attendance calls plus Mongo and outbox deltas.

Use only project prefix rct_student_gateway, ports 18500-18539, and candidate
test subnets 172.30.185.0/24 and 172.30.186.0/24 after a fresh conflict check.
Do not start production Compose, read or change secrets, or call real OTP,
Telegram or any external service.

## Gateway and edge matrix

| ID | Path / transport | Expected evidence |
| --- | --- | --- |
| H1 | Direct Gateway; forged/duplicate/spliced forwarding headers | Raw peer selects the bucket. Fake upstream receives one canonical X-Forwarded-For and no Forwarded header. |
| H2 | Nginx to Gateway; trusted static edge and two client IPs | Edge is trusted, each single source IP is canonicalized, and sources use separate buckets. |
| H3 | Login; fixed and chunked valid/empty/malformed JSON | Each non-oversize request dispatches exactly once, replays exact bytes, normalizes login, and strips forged identity headers. |
| H4 | Login; 4097 fixed and 4096+1 chunked | 413 Problem Details plus Cache-Control no-store; downstream start count zero. |
| H5 | Login plus Redis; six same-source attempts | First five reach upstream, sixth is 429 with Retry-After 60; no duplicate limiter or dispatch. |
| H6 | Protected Requests route; accepted access JWT and forged identity headers | Bucket key is user:positive-long from verified exchange state; forged headers cannot select it; unauthenticated traffic stops before upstream. |
| H7 | Exact excuse route; synthetic raw body exactly 25165824 bytes | Direct Gateway and Nginx admit it; fake upstream completes exactly 25165824 bytes without truncation. This is a framing probe, not a domain-valid multipart submission. |
| H8 | Direct Gateway; chunked 25165825 bytes, no Content-Length | A request start or bounded prefix at fake upstream is allowed because Netty may connect before the streaming decorator observes overflow. Completed upstream body/write count must stay zero. If the response is uncommitted, client receives 413 Problem Details plus no-store; otherwise the diagnostic must show a connection abort. Any 2xx or completed upstream body is FAIL. This direct diagnostic alone does not satisfy the public edge gate. |
| H9 | Nginx edge; fixed and chunked body greater than 25165824 bytes | With request buffering and client_max_body_size 24m, external response is 413 plus inherited no-store; Gateway/BFF request count and Mongo/outbox deltas remain zero. |
| H10 | Nginx restart/recreate, then repeat H1/H2/H6 | Trusted edge identity, sanitized headers and user/IP buckets remain stable. |
| H11 | Prod/e2e config with required network values, then each missing | Config parses with values; startup fails closed when a required trust input is missing. |
| H12 | Pinned Nginx image, ephemeral test certificates | nginx -t passes for prod/e2e mounts; exact Requests location retains security headers and no-store behavior. |

## Integrated Requests matrix

| ID | Flow | Expected evidence |
| --- | --- | --- |
| I1 | Nginx to Gateway to BFF to Attendance; valid multipart with two 10 MiB files and total request below 24 MiB | One accepted request, complete attachment bytes, one domain mutation and expected outbox event. Repeating the same idempotency key creates no second mutation/event. |
| I2 | Same edge with fixed and chunked request greater than 24 MiB | External 413 plus no-store before Gateway/BFF; request counters and Mongo/outbox snapshots are unchanged. |

The 24 MiB H7 framing probe and I1 domain acceptance are intentionally separate:
a synthetic 24 MiB body does not prove that the domain accepts an otherwise
invalid payload.

## Security and cleanup

Build the current Gateway bootJar once after final A/B union. Run the pinned
offline Trivy rootfs/config scans with the dated local DB and the pinned Gitleaks
scan without new suppressions. Record package coverage, artifact SHA, scanner
image digests and DB date. Record every container ID, mapped port and subnet.
Stop only task-owned processes/containers and prove ports 18500-18539 are free.
Do not remove foreign resources.

## Existing evidence

Focused Gateway suites previously passed (47 tests before the JWT test-only
expansion; final JWT selector 32 tests). Prod and e2e Compose config-only parsing
passed. Image-backed Nginx syntax, Redis/fake-upstream behavior, security scans
and the integrated I1/I2 runtime remain open.