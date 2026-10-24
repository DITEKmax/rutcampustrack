# Narrow existing mobile test-data correction

Frozen original commit `4042d1878cd19ae42cc839e7da6da9c720efe8e2`, parent `eb6826904f10eea706a26fb021f1993bbd665ea2`. Author worktree clean; source-owner STOP/checkpoint after this packet.

Exact inventory: ONE base64 literal at `tests/e2e/specs/mobile-attendance-requests.spec.ts:233`; one insertion/one deletion. Before mutation, strict base64 decoding proved these exact68 bytes were byte-identical to the known bad fixture frozen in `bb7074fac`'s student-excuse spec. Its only invalid chunk was the known IDAT CRC efa31799 versus actual efa2a75b, so the explicitly conditional ownership permission applied.

Only that CRC field was corrected. Pure standard-library readback validation confirms PNG signature, exact IHDR→IDAT→IEND framing/order, ALL chunk CRC32, IHDR1×1/8-bit grayscale-alpha/compression0/filter0/interlace0, zero-length terminal IEND/no trailing bytes and complete zlib EOF/no unused or unconsumed stream. The decompressed scanline has3 bytes and a valid filter. Compressed bytes and decompressed pixel scanline are identical to the original. Pixel-scanline SHA256 `16936d11fec03b650b80969a5865726bfb4cc002f91f436fc7ea45269a57f0b3`; only byte offsets53/54/55 changed, all within IDAT's CRC field. File remains68 bytes.

`git diff --check` PASS. No TypeScript check needed for this exact string-literal data substitution: expression/type/imports/API/assertions/test matrix are byte-identical outside the literal. No new tests, suites, browser/SUT/backend execution, mobile product/feature/API/config/lock/credential/grant changes, or wider PNG search/fix. The one-off correction+pure validation operation is `/tmp/rct-mobile-png-integrity.py` (mutation-time script, not a reusable repository test). No historical browser failure or new functional PASS is claimed.

Existing source and runtime resources remain untouched. No owned dev server/browser/long-running check remains active. Root independently accepts the narrow known-CRC comparison/integrates; no push or main mutation was performed by the author.
