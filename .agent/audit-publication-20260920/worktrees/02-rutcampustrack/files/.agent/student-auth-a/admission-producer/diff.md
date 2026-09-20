# Diff and limitations

## Intended producer diff

- `JwtService`: strict raw session-access parser, session-bound access/internal
  signing, purpose-aware access/refresh parsing, and removal of caller-claim
  internal issuance.
- `SessionAdmissionService` and `SessionAdmissionException`: one live snapshot,
  authoritative identity/version checks, capped expiry, and typed denial codes.
- `InternalSessionAdmissionController`: immutable accepted API mapping with
  `no-store` success response.
- `GlobalExceptionHandler`: RFC problem response with `extras.code` and
  `no-store` for typed admission denials.
- `InternalJwtClaims`/`InternalJwtValidator`/test fixture/tests: frozen internal
  identity wire and strict representation validation.
- Old `InternalIssuerController` and `InternalIssuerIT`: removed; old API/DTO
  contract files remain untouched as required.
- Focused service, HTTP, purpose, architecture, and validator tests: cover the
  producer boundary, including the authoritative suspended-grant case.

## Limitations

The working tree is shared and contains unrelated foreign changes. This leaf
did not stage, commit, reset, reformat, or revert them. The exact source diff is
ready for root's independent audit; runtime/test/build evidence is intentionally
open as recorded in `runtime-evidence.md`.
