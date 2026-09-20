# StudentMapModels Java wire correction evidence

Captured: 2026-09-09 after guarded source/test/manifest write and focused readback. Risk S3. This correction is bounded to StudentMapModels.java, its focused wire test, the affected exact25 manifest line, and this evidence file.

## Authority and scope

- Frozen union contract SHA256: C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74.
- D original backend patch contract SHA256: F7DF5F4074AE4C205889A2EFCB4D3B8189ECCD7FCB8BE43A0C5AB1E4BE35D659.
- Product source preimage: SHA256 AF239F6357A1F182B09454B8F63830B2EA087DE83680287FD1E16247A1E64280; root guard also confirmed the focused test was absent.
- Product changes: Format/FormatState constants are lower-case; FormatSlot component and required schema property are id; ready compares state == ready; non-ready requires id == null, sha256 == null, bytes == 0; only width/height/viewBox carry NON_NULL.
- Explicit JSON null is retained for id and sha256. No current-status, proto, API, SQL, generated, OpenAPI, TypeScript, frontend, or foreign dirty path was changed.

## Required behavior and test

- Ready ObjectMapper output uses format png, state ready, id, sha256 and dimensions; it has no assetId.
- Each of absent, processing, and failed serializes id:null, sha256:null, bytes:0 and omits width, height, and viewBox.
- Non-ready id, non-ready sha256, and non-ready positive bytes each reject with IllegalArgumentException.
- Enum.valueOf accepts lower-case values and rejects uppercase values.
- Focused test path: services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentMapModelsWireContractTest.java.
- Focused test SHA256: 06C041BBE4E72D534E39FC45742D14DC290180A8318BE5FCEF12A208A0BFC459 (4678 bytes).

## Checks and evidence

| Check | Command or guard | Exit | Evidence |
| --- | --- | ---: | --- |
| Source preimage | guarded SHA256 and marker counts | 0 | expected source SHA AF239F6357A1F182B09454B8F63830B2EA087DE83680287FD1E16247A1E64280; assetId count 5; focused test absent |
| Atomic source/test/manifest write | guarded Python marker replacement with fsync and os.replace | 0 | source/test/manifest post-readback passed |
| Source posthash | Get-FileHash StudentMapModels.java | 0 | 62831E2419009CD60361E60F2A139492D07DD54B4A91DBD095BC69B9AA63C6E2 (5153 bytes) |
| Focused test posthash | Get-FileHash StudentMapModelsWireContractTest.java | 0 | 06C041BBE4E72D534E39FC45742D14DC290180A8318BE5FCEF12A208A0BFC459 (4678 bytes) |
| Manifest replacement | exact old map-model line count 1 and inverse byte preservation | 0 | updated map-model line only |
| Focused wire readback | ObjectMapper/constructor/Enum.valueOf assertions in the new test | PENDING | Test source was added; Gradle was not run under this lease |
| Scoped text readback | rg probes for id, lower-case values, state rules, and no assetId | PENDING | Run as the post-write readback |
| Whitespace | git diff --check -- StudentMapModels.java test.java | PENDING | Run as the post-write readback |

## Runtime evidence and limitations

Runtime is N/A for this contract correction. Gradle/test execution is intentionally pending and no product runtime was started. The new test is the focused executable specification for the reviewed wire boundary; root must schedule the appropriate heavy test lease and fresh independent review.

No B1 behavior, converter, API signature, DTO consumer, OpenAPI snapshot, SQL, frontend, or broad refactor is included. No Terra escalation gate was opened.
