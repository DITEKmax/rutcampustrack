# Compact contract — JS-STUDENT-01 BFF R4 typed input errors

Date: 2026-09-06. Risk: S2. Integration revision: `e583f05140c43311b547bbaeaa55c1a7bc3c9ce9`.

## 1. Goal

Make all observed client-input failures at the student check-in HTTP boundary return the public typed Problem Details contract.

## 2. Context/evidence

Root/Sol confirmed that `MobileProblemHandler` handles bean-validation errors only. Missing `Idempotency-Key` and unreadable JSON/discriminator failures bypass its stable `code`; existing contract tests assert status only. The public contract already declares 400 `application/problem+json`.

## 3. Relevant scope

Sole writer: Mobile BFF `MobileProblemHandler` and `OpenApiSnapshotIT` in the integration worktree. No DTO, OpenAPI source, gateway, auth, attendance, event or frontend changes.

## 4. Required behavior

Missing `Idempotency-Key` returns 400 `INVALID_IDEMPOTENCY_KEY`. Malformed JSON and unknown geo discriminator return 400 `INVALID_REQUEST`. Invalid coordinates and a valid body reaching the mocked 404 seam return their typed codes. Every handler response uses `application/problem+json` and does not expose parser/input details.

## 5. Constraints

Do not infer a new public API, alter status mapping, relax validation/auth, or expose raw exception/input content. Preserve `Retry-After` for cooldown responses.

## 6. Existing patterns

`StudentApi` already annotates 400 as `MobileProblemDetails` with `application/problem+json`; the handler constructs this record with stable code and request instance.

## 7. Acceptance criteria

- Five observable MockMvc cases above have status, compatible content type and code assertions.
- Existing valid coordinates retain typed 404 at the seam.
- OpenAPI snapshot stays identical unless a deliberate contract drift is detected.

## 8. Verification

Run the new test first against existing handler as a red reproduction, then targeted Mobile BFF tests and OpenAPI snapshot drift check; record revision, commands, exits and evidence.

## 9. Do not

Do not touch the separately owned bot/attendance repair, frontend native-fetch repair, or repeat the PWA full runtime until their assigned changes are integrated.
