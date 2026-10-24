# Fixture-only PNG correction

Frozen original correction `eb6826904f10eea706a26fb021f1993bbd665ea2`, parent `c1ec3de72940e7c6ef243446f8334e3dcbddf03e`. Exact delta: ONE base64 literal in `tests/e2e/specs/student-excuse.spec.ts:12`. Clean WT. Pair bb7074+c1ec remains unchanged otherwise.

Independent report read: `/workspace/rct-pr3-review/shipping/REVIEW-bb7074fac-paired.md`, one P2. The inherited fixture's IDAT checksum was invalid even though its compressed stream inflated. This supersedes the earlier author report's description of the borrowed bytes as a valid PNG. No actual browser rejection/failure is asserted, and no unrelated mobile fixture/product was edited.

Correction recomputes ONLY the IDAT CRC field from the unchanged chunk type/data: stored efa31799 → canonical efa2a75b. Dimensions, compressed pixel content, 68-byte size and all preview/submit/owned-scope/exact-byte-readback assertions remain unchanged. The test constant is now structurally valid rather than relying on a tolerant decoder.

Pure standard-library source readback validation (no browser/SUT/new repository test): strict base64 decode, exact PNG signature, bounded chunk framing/order IHDR→IDAT→IEND, every chunk CRC32, IHDR `(width1,height1,bitDepth8,colorType4,compression0,filter0,interlace0)`, zero-length terminal IEND with no trailing bytes, full zlib EOF/no unused or unconsumed compressed input, decompressed three-byte scanline with valid PNG filter. All PASS. Observed metadata: bytes68; allChunkCrc PASS; IEND PASS; inflate PASS; browserRun false.

Scoped strict TypeScript using the existing external two-spec temporary config and installed types: exit0. `git diff --check` PASS. Existing finite control is unchanged and was NOT repeated. No new tests, browser invocation, SUT/backend/DB action, CI/build/install, config/lock/product change, decoder suppression or weaker assertion. Narrow independent source recheck pending; actual Vue runtime outcome remains unverified by this source packet.
