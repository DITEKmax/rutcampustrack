# Auth Stage2 correction-02 compact contract

## 1. Goal

Close the four findings from the independent Stage2 review while preserving
the frozen session contract and the shared checkout's unrelated work.

## 2. Context / evidence

The unchanged review FAIL is preserved at
`.agent/student-auth-a/stage2/review-fail.md` (SHA-256
`931C3F750E2D1C245D820B6D5AAECABCFB3DAB3C624379FA526F567501678996`, 9,529
bytes). It identifies Redis cleanup turning a committed operation into HTTP
500, missing WS cleanup for cookie-only logout, non-atomic OTP proof consume,
and untyped repository dependency failures. The pre-correction final manifest
remains immutable at `.agent/student-auth-a/stage2/final-manifest.json`
(SHA-256 `95A17B2E59C108FA5A1C00EF0E226C1402EAB6C3E4100F4EC91F00EB21C50DA1`,
59,166 bytes).

## 3. Relevant scope

Production: `AuthSessionController.java`, `AuthService.java`,
`OtpService.java`, `TmaService.java`. Tests: the owned controller/OTP tests,
`LogoutLifecycleIT`, `OtpIT`, and the two narrow repository-failure tests.
Evidence and manifest files are under this `correction-02` directory.

## 4. Required behavior

- Durable logout, logout-all and password-change success remains HTTP 204 with
  no-store and cookie clearing when optional Redis WS cleanup fails.
- Cookie-only logout returns the authoritative revoke result and performs
  best-effort cleanup for that result's user; invalid/foreign cookies keep the
  existing typed failure and side-effect boundaries.
- Direct and by-code OTP verification use one atomic Redis proof check/consume;
  both forward and reverse keys must exist and match the owner, and no delete
  occurs on a missing or mismatched proof.
- `DataAccessException` from UserRepository dependency lookups maps to typed
  `AUTHORITY_UNAVAILABLE`; a real missing OTP/TMA user remains an auth failure.

## 5. Constraints

One writer in the shared checkout. Preserve unrelated dirty work. No Gateway,
BFF, shared-security, SQL/V24, OpenAPI source, frontend or WS protocol edits;
no commit, stage, reset, deploy, migration or destructive operation.

## 6. Existing patterns

`SessionStatePort.RevokeResult` carries the authoritative revoked snapshot;
`WsTicketService.invalidateAllFor` is an optional after-commit signal.
`OtpService` uses Redis for proof TTL/counters and `MessageDigest.isEqual` for
the preliminary constant-time comparison. `AuthSessionException` is the
typed public dependency failure boundary.

## 7. Acceptance criteria

The four review defects have focused behavior coverage; unit XML totals 24/24,
affected PostgreSQL/Redis XML totals 20/20, and property-free OpenAPI compare
is 1/1. Every saved XML has zero skipped, failures and errors. The source
delta and XML hashes are reproducible by the companion manifest script.

## 8. Verification

Use the already completed compile, focused unit, affected PG/Redis integration
and OpenAPI compare command records in `manifest.json`. The saved integration
artifacts came from session `67640`, which was already running before the
fast-path no-rerun instruction; no further product check is performed here.
Run `generate-correction-manifest.ps1 -Verify` only for evidence hash/XML
self-audit; it is not a product check.

## 9. Do not

Do not redesign the contract, add a second authority, weaken OTP replay
protection, clear cookies before durable success, swallow authority failures,
overwrite the pre-correction manifest/review, or claim coverage outside the
listed selectors and limitations.

