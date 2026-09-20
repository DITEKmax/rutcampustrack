# Correction-02 evidence

## Scope and correction

The four recorded findings are addressed in the bounded auth slice. The
controller now treats WS-ticket invalidation as best-effort after the durable
operation and uses the cookie logout revoke result's snapshot for attribution.
OTP direct and by-code verification share one Redis Lua consume operation;
missing or mismatched reverse proof returns before any `DEL`. Repository
dependency failures in AuthService, OtpService and TmaService are translated
to `AUTHORITY_UNAVAILABLE`.

## Artifact evidence

The companion `manifest.json` records exact current and previous hashes for all
10 correction source/test files, the immutable review-fail hash, and eight
saved JUnit XML files. The saved artifacts contain 24 focused unit tests,
20 affected PostgreSQL/Redis integration tests and one OpenAPI compare test;
all skipped/failure/error fields are zero.

## Runtime evidence

The affected integration run exercised cookie-only logout ticket invalidation
and mixed direct/by-code OTP concurrency against real PostgreSQL/Redis. Its
observable assertions are one `200` and one `401` for the concurrent same OTP
and a single `auth_sessions` row delta. The integration suites and OpenAPI
compare completed with exit code 0 as listed in the manifest.

## Independence and limits

The original independent FAIL remains byte-preserved outside this directory.
Fresh independent Sol recheck of this stable correction diff remains a root
workflow step. Gateway/BFF, full WS protocol/open-socket, frontend UI and
genuine TMA-host QA are outside this correction.

