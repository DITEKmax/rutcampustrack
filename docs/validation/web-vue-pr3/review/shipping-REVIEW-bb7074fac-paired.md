# Independent review: paired legacy Vue browser specs

Verdict: **CHANGES REQUIRED — one P2**. Admin creation source and paired seven-site helper correction have no additional confirmed P1/P2 findings.

Frozen scope: `e7bb0a7fd4d4ebd93be0c0f91bbdbb01dbac7641..c1ec3de72940e7c6ef243446f8334e3dcbddf03e`, exactly `tests/e2e/specs/admin-create-user.spec.ts`, `tests/e2e/specs/student-excuse.spec.ts`, and `tests/e2e/fixtures/web-panel-route.ts`. The two specs are frozen at `bb7074facf4c76c2a6b317da9ef9b983569f6d64`; helper delta is its child `c1ec3de72940e7c6ef243446f8334e3dcbddf03e`.

## Findings

### P2 — EXCUSE case supplies a corrupt PNG instead of the required valid small image

Location: `tests/e2e/specs/student-excuse.spec.ts:12` (also present unchanged at paired HEAD).

The embedded 68-byte PNG has a correct signature, 1×1 IHDR and IEND, but its critical IDAT chunk carries CRC `efa31799`; CRC-32 of the actual `IDAT` type plus data is `efa2a75b`. Reproduction needs no browser or server: base64-decode the literal, read the 11-byte IDAT payload at offset 41, and compare its stored four-byte CRC with `zlib.crc32(b'IDAT' + payload)`. The compressed stream itself inflates, which does not repair the invalid chunk checksum.

Impact: this proposed single accepted EXCUSE upload case does not exercise the stated **valid small PNG** input. The preview's `image.decode()` check at lines 53–56 depends on decoder tolerance for corrupt files; a permissive browser/backend could pass while a strict decoder rejects before the native POST. No actual Chromium failure is asserted by this source review.

Correction: replace only this fixture with a genuinely valid small PNG and independently check signature, dimensions, chunk CRCs and decompression. Keep the native preview, multipart POST and exact byte-readback assertions.

## Source conclusions

- Both tests retain the `mobile-dataset` → `artifact-privacy` automatic wrapper, exact safe reporter, debug/reporting exclusions, trace/video/screenshots off, synthetic-seed and disposable HTTPS loopback:8443 admission. No new global cleanup or secret logging is added. Dataset disposal remains the root-owned task-labelled stack; test-owned browser context is closed in `finally`.
- Admin creation uses the current native `UsersPage` form and `GroupPicker`, waits for same-origin create HTTP201, verifies the exact returned account and ACTIVE STUDENT group grant through canonical readback, compares the ephemeral receipt without rendering credentials in assertion diagnostics, dismisses it and checks its absence after reload. A fresh context uses the returned credential and checks authoritative user/role/group identity. The registry is not used as a plaintext-password source.
- Apart from the PNG defect, EXCUSE targets its own newly created physical lesson and current authoritative eligibility, selects exactly one native Vue option, requires HTTP200 typed PENDING detail, same owned lesson/comment/attachment identity, and authenticated exact-byte readback. Current BFF `StudentApi.submitExcuse` is multipart and returns Detail with HTTP200; retired `/with-file` HTTP410 is not treated as the current API.
- Existing `StudentRequestDomainIT.attachmentSpoofThirdAndOversizeInputsRejectBeforeAnyWrite` supplies the domain file-limit/spoof/no-write matrix. This review does not claim exact retired multipart `/with-file` HTTP410 integration coverage, or relabel a plain POST test as multipart coverage.
- All seven helper changes use a page-created inner locator relative to the candidate passed to `filter({has: ...})`. The outer candidates remain owned by `owner`; removing the duplicate owner prefix fixes matching without admitting foreign-only headings. Inspected `/tmp/rct-static-has-scope-probe.cjs` and its JSON: seven source-shaped DOM cases, old matches 0, corrected owned matches 7, foreign-only matches 0, about:blank/networkRequests 0/Vue false/auth false. This is static DOM evidence, not product-browser validation.

## Verification and limits

Independent frozen-source/type-contract inspection; pure Python PNG structure/CRC/inflate check; scoped `git diff --check` PASS. No browser, API/backend call, build, CI, install, Docker or Gradle executed. Author type checks and finite-control checks are reported separately and were not repeated. No product files changed. Assignment explicitly requests GPT-6.1 Sol/high; runtime model telemetry is unavailable, so no runtime attestation is made.
