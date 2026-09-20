# Gateway integrated runtime packet — 2026-09-09

## Goal

Verify the accepted Gateway at the real HTTP boundary and then prove the
Requests write/no-write behavior through Nginx, Gateway, BFF, Attendance,
Mongo and outbox.

## Context/evidence

Gateway repair24 is statically accepted and hash-frozen. Requests transport,
P1, P2 and producer11 are independently accepted; producer focused verification
is 67/67. Direct filter tests cannot prove proxy timing or database effects.

## Relevant scope

Runtime harness/evidence only. Product code, configuration, generated clients,
lockfiles and historical evidence are read-only. Task resources use prefix
rct_student_gateway, ports 18500-18539 and conflict-checked test subnets
172.30.185.0/24 and 172.30.186.0/24.

## Required behavior

Run H1-H12 and I1-I2 from runtime-readiness.md. Preserve the distinction between
a direct streaming overflow, which may open an upstream connection, and an
Nginx-buffered external overflow, which must stop before Gateway/BFF. Preserve
the distinction between the exact-24-MiB framing probe and the valid two-file
domain submission.

## Constraints

Wait for accepted A access/admission authority and B/B1 Java-first export,
exact-25 compile and stable service baseline. Use no production Compose,
production data, secrets or real external OTP/Telegram. One runtime lease and
one task-owned resource set at a time. Light-theme UI work is unrelated.

## Existing patterns

Nginx is the only trusted proxy. Gateway disables framework forwarding header
generation and stores verified identity in exchange attributes. Redis rate
limits canonical IP/login/user keys. Requests submission is idempotent and
writes through Attendance to Mongo/outbox.

## Acceptance criteria

H1-H12 and I1-I2 have exact commands, timestamps, exit results and counters.
External oversize traffic returns 413/no-store with zero Gateway/BFF/Mongo/outbox
effects. A valid two-by-10-MiB submission writes once and an idempotent repeat
does not duplicate it. Nginx syntax/restart and security scans pass. Every
task-owned process/container is removed and ports are free.

## Verification

Before execution, rehash repair24 and name exact A/B artifact revisions. Record
container IDs, images/digests, ports/subnets, fake-upstream start/completion/body
metrics, Redis keys, HTTP status/headers, BFF/Attendance calls, Mongo documents
and outbox rows. Commands remain deliberately unspecified until final A/B
artifact names and the integrated Compose selection are frozen.

## Do not

Do not treat a fake upstream as evidence of domain no-write. Do not require zero
upstream starts for a direct chunked overflow. Do not accept a transport abort
as the public Nginx result. Do not rerun accepted unit suites without source
change. Do not deploy, migrate production, delete data, weaken limits or add
scanner suppressions.

Status: READY as a contract; heavy runtime NOT RUN / WAIT B priority.