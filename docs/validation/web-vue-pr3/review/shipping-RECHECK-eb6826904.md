# Narrow PNG fixture recheck

Verdict: **PASS**. The sole P2 in `REVIEW-bb7074fac-paired.md` is closed. Combined source verdict for `bb7074fac` + `c1ec3de` + `eb6826904` is **PASS with runtime limits below**; no remaining confirmed P1/P2 in the reviewed three-file packet.

Frozen correction: `c1ec3de72940e7c6ef243446f8334e3dcbddf03e..eb6826904f10eea706a26fb021f1993bbd665ea2`. Exactly one base64 literal changes at `tests/e2e/specs/student-excuse.spec.ts:12`; source outside that literal is identical. No preview, native submission, ownership, privacy or exact-byte-readback assertion changes.

Independent Python standard-library check against both frozen Git blobs confirms:

- Both images are 68 bytes. Only byte offsets 53, 54, 55 (zero-based), within the IDAT CRC field, differ; all bytes outside that four-byte CRC field are identical.
- IDAT stored CRC changes `efa31799` → correct `efa2a75b`. Every IHDR/IDAT/IEND CRC now matches CRC-32 of chunk type plus data.
- Exact PNG signature; bounded chunk framing/order IHDR → IDAT → IEND; IHDR is 1×1, 8-bit grayscale+alpha, compression/filter/interlace zero; empty IEND terminates the file without trailing bytes.
- Unchanged compressed pixel payload reaches full zlib EOF without unused/unconsumed bytes. Its three-byte scanline is `01ffff`, a valid Sub filter byte plus grayscale/alpha pixel bytes.
- Corrected PNG SHA-256: `5e3d382db4dd83d59aa5742793ad6b7903409e865c83bcbc54835049f043bc15`.

Scoped combined `git diff --check` PASS. The first reviewer pure-check script had an incorrect hard-coded expected changed-offset list; the corrected check derives the CRC interval from PNG chunk framing and passed. This was a reviewer probe assertion, not a fixture/product failure.

Read author `VUE-PNG-CORRECTION-eb6826904.md`; reported scoped TypeScript and pure validation are consistent with the frozen delta. No finite-control repeat was needed because logic is unchanged. Earlier conclusions for admin creation, safe/disposable fixtures, current HTTP200 multipart EXCUSE contract, owned readbacks and seven relative-inner-locator corrections remain valid. The existing domain file-limit matrix is reused; exact retired `/with-file` multipart HTTP410 integration coverage remains unclaimed.

Source/pure review only: no browser, SUT/API/backend call, build, CI, install, Docker or Gradle executed. This PASS does not assert an actual native Vue runtime result. No product source was written. Explicit requested assignment remains GPT-6.1 Sol/high; runtime model telemetry is unavailable.
