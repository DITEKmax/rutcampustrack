# Portable local Nginx fixes — independent narrow source review

**PASS. Blocking findings0.**

Reviewed Nginx commit `f81d14d74a8591bddbfa5265b697d66ecb1de5fc` (two files) and local handoff commit `d464edf83a6321edbe5a0bb75027be20bcfd27b0` (three files) in their assigned preserved worktrees. These close the two concrete source gaps from ASSESSMENT.md. Reviewer did not execute tests/build/Docker/Auth/API/browser/owner-local commands and edited no source. Requested gpt-6.1-sol/high; telemetry unavailable.

## Direct API trust

`direct_api_trust()` requires API base + exact CA SHA for HTTPS, reads the CA once, verifies bytes, and constructs a separately verified SSLContext from that same immutable bytes snapshot. Static Nginx context continues trusting its own leaf. Standard hostname and certificate verification remain enabled; no insecure SSLContext/Origin rewrite is added. A malformed certificate cannot establish trust because the standard constructor fails.

No-API rejects CA arguments; HTTP API rejects irrelevant CA arguments and returns the previous None context, which HTTPConnection does not use. Missing HTTPS CA/hash or hash mismatch fails before Docker inspection/HTTP. Existing default HTTP cloud command therefore retains its actual behavior; optional local CA does not justify repeating an unchanged cloud run. Receipt records only the public CA hash and explicitly keeps external HTTPS forwarding identity NOT_RUN.

Author tool-only pure controls were reused: trusted public ISRG Root X1 CA with correct SHA produced check_hostname=true/CERT_REQUIRED and unchanged input bytes; four negative configurations rejected (HTTPS missingCA, wrongSHA, HTTP+CA, noAPI+CA); defaultHTTP returned (None,None). Invalid-PEM execution was not performed or claimed. Reviewer assessed the standard constructor path in source and did not rerun those controls.

README now gives the exact existing fresh-local network `rct-pr3-local_private_net`, alias `api-gateway:8080`, direct host API HTTPS18443 and separate CA path/hash. Keys/passwords/cloud state do not transfer. The API CA option does not claim browser trust or native lifecycle acceptance.

## Local generated origins

The local template appends exactly HTTP127.0.0.1:14240 and HTTPS127.0.0.1:14443 to both Gateway explicit globalcors origins and Notification WS origins. Independently parsed old/new full templates and removed only those two additions from each list: the remaining full objects are equal. Dev14250–52, `.10` trusted proxy, all resources/networks/service definitions and operational bounds are preserved. No `.11`, wildcard, subnet trust, static-IP profile, Main/cloud config or new service was introduced.

These changes enter generated state before the existing prepare/input freeze. README/source contract accurately keep optional Nginx ports outside the group four-port preflight and preserve owner-local NOT_RUN until actual output. Editing already frozen state is not required or authorized by this review.

## Limits

Forwarded external HTTPS identity/proxy semantics remain a distinct NOT_RUN: fresh Gateway trusts its own TLS edge `.10`, while optional common edge is another peer. This was not promoted into a proved login/STOMP blocker or an infrastructure expansion.

Common Nginx static/API/cloud ScriptMIME failure and its diagnosis are outside these two deltas; this source PASS does not convert any actual failed gate to PASS. Native Auth/WS, browser PWA activation/update/offline, TMA provider and external WebPush retain their separate statuses. No main/CI activation/merge/deploy/production/owner-local execution is authorized here.
