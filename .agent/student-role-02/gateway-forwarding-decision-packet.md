# Gateway forwarding authority — staged bounded decision

07.09.2026. S3. Fresh Sol xhigh read-only consultation under owner routing; not dispatched. No writes or children.

## Goal
Freeze a minimal trusted-client-IP contract that repairs the verified raw-XFF rate-limit bypass while preserving distinct real clients and login/OTP behavior. This is separate from dependency version compatibility and precedes final Requests ingress changes.

## Context/evidence
Read gateway-client-ip-finding.md, active-contract.md and accepted dependency baseline when available. Root reopened RedisRateLimiterConfig.java:107: first raw X-Forwarded-For wins; prod and e2e nginx append client-controlled chain. Actual Java path is services/api-gateway/src/main/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfig.java. Compose files are root docker-compose.prod.yml and docker-compose.e2e.yml, not inside tests/e2e/infra. Main Gateway YAML has no explicit forwarding/trusted-proxy policy. Gateway is exposed inside private_net and nginx publishes edge ports; no fixed subnet/address policy is established. Do not assume deployed network facts from source alone.

## Relevant scope
Read exact Gateway resolver/filters/tests, prod/e2e nginx, relevant sanitized compose topology and corrected Cloud2025 configuration namespace. Never print secret-bearing config values. Future writer owns narrow Gateway client-IP resolver/security tests plus coordinated nginx/compose/config changes if actually required by chosen trust model. It must start from accepted dependency26 baseline. Requests writer is forbidden Gateway/nginx writes until this ordered source is accepted.

## Required decision
Choose concrete immediate-peer trust establishment with safe defaults, exact config/storage and startup/restart behavior, not generic advice to trust proxies. Untrusted direct peers cannot select a bucket using XFF/Forwarded/X-Real-IP; trusted nginx must overwrite incoming untrusted identity and preserve actual client distinctions. Handle malformed/multiple/comma/empty/IPv4/IPv6 cases, unknown peer and proxy availability without wildcard/private-network trust or guessing current CIDR. Resolve forwarded-header processing order versus raw socket peer so an earlier transformer cannot authenticate the header it is supposed to distrust. Keep per-IP and IP+login semantics.

## Constraints
No deploy/firewall/production changes, invented secrets, blanket allowlists, role/session redesign, new proxy platform or dependency upgrades. A repository config/test change is not proof of actual deployment. Any address/DNS/CIDR approach needs explicit assumptions and tests; do not silently hardcode an arbitrary private IP as observed fact. Maintain sequential shared config ownership.

## Existing patterns
JwtAuthenticationFilter order-100 strips client X-Login; LoginBodyExtractionFilter order-50 derives it from bounded login JSON. Root verified those originals, so the stale resolver comment about future extraction is not an open X-Login vulnerability. Preserve header stripping, identity token exchange and body forwarding.

Related defect now reproduced by root: read gateway-double-dispatch-repro/evidence.md, immutable compiled source/JAR manifest and java-escalated-result.log. One mock POST login through actual LoginBodyExtractionFilter subscribes to downstream twice, headers [probe-student,null]; one-invocation assertion exits1. Source is unchanged from d3c31. Root proved filter-chain behavior, not two real Auth calls: assess actual routing guards and rate-limiter effects separately. Freeze a narrow single-dispatch repair alongside forwarding work; preserve body replay and login normalization and add regression for nonempty/empty/malformed/oversize/non-login paths. Do not change this filter during dependency-only work or overclaim real Auth side effects from the mock chain.

## Acceptance criteria
Return a finite nine-section developer contract with exact files/config assumptions, trust/normalization algorithm, filter order, startup behavior, source evidence and sequential integration plan. Prove via future regression that repeated login/OTP from one peer cannot gain new buckets by spoofing headers, while distinct trusted-edge clients remain distinct; preserve429/auth/CORS/headers. Separate confirmed findings, source-based inference and questions.

## Verification
Consultation read-only runtime N/A; future isolated real nginx→Gateway→Redis tests, synthetic downstream/auth with no OTP/Telegram messages, forwarded-header negative cases, malformed bodies and one downstream execution for valid login if related observation is confirmed. Root-owned task ports/queues/networks only. Fresh independent Sol high review after stable diff/evidence.

## Do not
No implementation, children, broad proxy trust, silently accepting changed runtime defaults as a fix, secret output, architecture creep into profile/R26, or full security/role PASS before actual tests/review.

DISPATCH FREEZE07.09.2026 21:05UTC (08.09 local): dependency plus compatibility reconciliation is now ACCEPTED, exact45 union source root dependency-combined-review-source/manifest.json SHA3E795CB3F0CB8E95944BCBF1DC623E197554FAC24C5F5D2F5BB65ADFDDEF1D6A. Rootpost-review45source+45copySHA0mismatch in dependency-acceptance.json. Fresh independent Solhigh PASS saved unchanged dependency-combined-review-result.md. Full Gradle check exit0,253suites1651reported/1647executed/4preexistingdisabled,0failures/errors; prior8JAR1141package0H/C57M security artifact relation independently verified. Thus earlier references to an unaccepted dependency26-only baseline are superseded for this consultation.

Read current accepted source in C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/dependency-checks at d3c31 plus frozen45. No active writer/check process there. Root/main remains read-only and unchanged; future writer needs another isolated worktree and exact45 import, not edits to this accepted checkout. Requests transport and Homework adapter writers are separate and active; do not use evolving source as a frozen API decision.

Root actual composite-policy correction: the full check's3skipped Gateway cases are preexisting class-level @Disabled in CompositeLoginKeyResolverIT.java:36. Accepted Gateway application.yml:96–108 explicitly has only the IP limiter on login; a second composite RequestRateLimiter was removed historically for response-commit failures. Those files' disabling behavior was not introduced by dependency migration. The earlier phrase preserve IP+login semantics must NOT be interpreted as proof that composite limiting is currently active. Inspect intended source policy and actual filter chain, preserve working per-IP protection, and state a bounded decision for the inactive composite path rather than claiming it was verified or blindly restoring two filters. The fourth skip is a preexisting shared container smoke case; actual service integration tests ran with Docker.

Adjacent ordered ingress boundary: root requests-transport-implementation-packet.md freezes exact2x10MiB/20MiBfiletotal and24MiBtransport for student excuse multipart. Gateway/nginx remain deferred to this ordered security work. Include concrete resource ownership/order for the subsequent exact-path limit/no-store/CORS Idempotency-Key change before generic student route; global codec12MB remains unchanged. Verify whether the actual built-in size filter constrains streamed/chunked requests as required instead of inferring this from its name. Do not need Requests implementation acceptance to choose the already frozen ingress contract; do not author code here. Future full nginx→Gateway→BFF→gRPC→Mongo tests must cover known-length/chunked exact/oversize paths without writes on rejected input.

Return concise finite nine-section developer packet and assumptions/decision, no files or children. Use primary actual library/docs sources for forwarding/ordering details as needed. Send concise checkpoints using collaboration.send_message target=/root, not app task-message tools. Root saves final response unchanged and accepts the concrete architecture before fresh implementation. This read-only consultation itself needs no deployment/production permission.
