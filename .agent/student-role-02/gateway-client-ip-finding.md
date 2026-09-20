# Gateway client-IP rate-limit finding — root verified, repair pending

07.09.2026. S3, separate from dependency version repair. Consultant identified HIGH; root reopened originals and confirmed code path.

## Goal
Prevent client-controlled forwarding headers from selecting arbitrary OTP/login/IP rate-limit buckets.
## Context/evidence
RedisRateLimiterConfig.java resolveIp at line107 accepts first raw X-Forwarded-For; nginx/conf.d/default.conf at99,115,129,138 and other locations appends client-controlled input via proxy_add_x_forwarded_for. Existing tests explicitly expect first-header behavior. This is source evidence of bypass path; new isolated nginx/gateway/Redis reproduction still required before claiming runtime evidence.
## Relevant scope
Future isolated gateway/security writer: rate-limit resolver/tests, bounded proxy trust configuration and nginx forwarding policy/tests. Root must inspect actual ingress topology and exposure before freezing exact trust policy. Dependency writer owns gateway build/application prefix migration; no concurrent overlapping writer.
## Required behavior
Authenticated/trusted proxy boundary determines client address; untrusted direct callers cannot change their bucket by spoofing XFF or related headers. Preserve intended per-client and IP+login limits. Ingress must not preserve attacker-supplied leftmost identity as authority. No wildcard trust and no silent grouping of every real client into nginx's shared IP bucket.
## Constraints
Local code/tests only, no deployment/firewall/production changes or secrets. Do not assume Cloud2025 forwarding defaults repair custom raw parsing. Preserve route/auth/CORS behavior and existing owner files.
## Existing patterns
Current gateway KeyResolver/RedisRateLimiter and nginx edge; actual environment/trusted hop policy remains to be verified. Explicit properties and typed tests preferred over guessed network addresses.
## Acceptance criteria
Same real peer and repeated login/OTP requests remain in the same bucket despite arbitrary/spliced/multiple XFF headers; real clients through approved proxy remain distinct. Untrusted direct gateway peer cannot gain authority through forwarding headers. Boundary malformed/null/IPv4/IPv6/multi-hop cases covered under chosen topology.
## Verification
Failing regression first, then actual isolated nginx-to-gateway-to-Redis flow, expected429 and preserved auth/header stripping; exact revision/commands/exits/evidence; fresh independent Sol high review. No live external messages or OTP delivery; synthetic downstream only.
## Do not
No blanket ignore, unlimited trust, style-only rewrite, unrequested deployment or security PASS before runtime/review. This finding note is not a frozen implementation policy yet.

Root topology originals: prod and e2e compose put nginx and gateway on shared private_net bridge; gateway uses expose8080, nginx publishes80/443. Production loads nginx/conf.d, e2e loads separate tests/e2e/infra/nginx/default.conf; that fixture repeats proxy_add_x_forwarded_for at42/52/79, so both ingress configurations need attention in eventual parity tests. No fixed private_net subnet or real-ip trust config was found in these originals. Do not invent a CIDR or treat every private peer as nginx. Local override exposes infrastructure ports, not a trusted proxy policy. These file observations do not verify an actual deployed topology.

Root current-source follow-up: LoginBodyExtractionFilter already derives X-Login after JwtAuthenticationFilter strips caller value; do not report stale resolver-comment gap as a new vulnerability. A separate source-only observation on flatMap(Mono<Void>).switchIfEmpty(chain.filter(original)) needs downstream-execution reproduction before any repair. Bounded decision packet gateway-forwarding-decision-packet.md records it and exact actual source paths. No source change or runtime reproduction yet.
