# Narrow recheck: b32d47b0e

Verdict: **PASS**. Previous P2 in `REVIEW-f3c03bd81.md` is closed; no new P1/P2 found in this delta.

Reviewed frozen commit `b32d47b0ea23e6b6242689d4784897e27196a608`, parent `6289ab528`, exactly two changed files. Read-only independent source review; no product edits, build, Gradle, Docker, CI, or repeated integration suite.

## Causal closure

`services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/export/AdminRegistryExportService.java:313–316` now parses both stored and serialized typed query JSON before comparing trees. This normalizes the parser's integer node representation on both sides: a valid small `Long` group ID no longer differs solely because `valueToTree` produced `LongNode` while persisted JSON parsed as `IntNode`.

The comparison still covers the complete selector tree. No selector keys are removed, no fields are projected away, and array ordering remains significant. Existing actor/session/version, kind, private lease, state, and expiry predicates remain unchanged. The existing sanitized error handling remains unchanged.

Independent lightweight Jackson 2.21.4 probe using the same old/new expressions and a typed `Long(11)` field produced:

- Old comparison: `false`; corrected comparison: `true`.
- Changed group, missing group, extra key, or changed array order: `false` in every case.

The application's injected mapper configuration had already been examined for the original finding; this delta adds no mapper configuration changes. `git diff --check 6289ab528..b32d47b0e` passed.

## Regression evidence and limits

Frozen `AdminRegistryExportsIT.java:367` adds a meaningful regression through HTTP enqueue (202), job claim, report admission token, and `/academic/export-jobs/{id}/render`. The test requires a small positive group ID, expects render 200 with one row containing the selected user, then reuses the same private lease with changed and missing group selectors and expects 403. This exercises the previously missed async path and preserves the authorization boundary.

Independently parsed author-preserved XML `/tmp/group-filter-p2-run2-xml/academic-integration/TEST-ru.rutcampustrack.academic.export.AdminRegistryExportsIT.xml`: one test, zero failures, zero errors; testcase `filteredAsyncUsersRenderNormalizesNumericIdsButRejectsChangedOrMissingGroup()`. This is a review of preserved runtime evidence, not a new runtime execution by this reviewer.

The previously reviewed group-filter baseline is not reopened. This verdict makes no rollout, production, or broader runtime claim. Requested review model: `gpt-6.1-sol/high`; no runtime model telemetry claim.
