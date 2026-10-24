# Independent role/semester publication review — initial finding

Initial verdict: **CHANGES REQUIRED — one P2 publication privacy finding**. Root acknowledged it and is correcting a public projection; closure belongs to a separate immutable recheck.

Scope: root0421 documentation WIP; specified PROGRESS/ROLE-ACCEPTANCE/SCREEN-ACTION-MAP/OPEN-QUESTIONS, CURRENT/SLOTS, role-semester checkpoint,77-review manifest and two new admin-native gallery PNGs. No existing83 accepted actions were re-reviewed.

## P2 — public Markdown retains the private binding digest omitted by its JSON projection

Location: `docs/validation/web-vue-pr3/checkpoints/role-semester-20261024/full-prod-source-model.md:12` in the initial reviewed WIP. The Markdown was byte-identical to its original (SHA256 `8f796416bc8cf8d348b181f07758d6ea93986e67ed6d967217cab11989f3b3ee`) and contained the exact original JSON `ownedBindingInputSha256` value. The sibling public JSON correctly omitted that field, but copying the original Markdown exposed the same private binding fingerprint through a second public path.

This violates the explicitly selected publication boundary excluding private binding SHA. The finding concerns a private-input fingerprint, not evidence of published plaintext credentials; its value is intentionally not reproduced here. Pure in-memory cross-comparison of the scoped producer field against public files established the exact match without reading private bindings/credentials. No original Auth session UUID match was found in the scoped checkpoint.

Required correction: replace the Markdown digest with an explicit omission marker, keep the original immutable and its originalSha256, update publishedSha256 and accurately mark the Markdown projection as semantically altered. Do not claim complete semantic equality for redacted text.

## Other scoped checks passed

- Ten original/published checkpoint hashes and declared JSON exact/subset relationships matched. JSON omissions correctly exclude `auth`, `authOwnSessionMetadata`, `ownedBindingInputSha256` and `privateStderrPath`; the Markdown bypass above was the exception.
- Manifest77 contains14 new review copies; their bytes/hashes match original immutable reports, including initial P2 findings and rechecks. The temporary CURRENT76 count typo was corrected by root to77; SLOTS now records the actual-review completion.
- CSV recomputation gives117 active,116 implemented and113 locally verified; ADMIN34/33/30. ACTIVATE's additional verification is distinct from the already verified role-select action and screenshot counts.
- ACTIVATE scope is qualified by native create201/activate200, unique active semester, unchanged prior identity and mandatory producer outbox1+1. Original raw composite exit1/passedfalse/same7Healthyfalse and actual Academic OUT_OF_SERVICE remain visible; absent Attendance activity, trigger chronology limits, async delivery and full-dashboard acceptance are not silently made PASS.
- Fresh-role AFTER shows actual protected UI me200 and guarded `/admin/dashboard`; BEFORE observer's incorrect path/meReads0 is explicitly qualified. Dashboard metric availability is not accepted by that navigation case.
- MAP remains NOT RUN after before-execution auto-review rejection, with zero browser/login/upload/delete/retry, Q-ENV-007 pending and expired admission unusable. Backend Auth-stub checks are not promoted into full real-Auth UI acceptance. No production Compose/full release/Angular removal/CI PASS is claimed.
- Both new PNGs match declared hash/bytes/dimensions and were visually inspected: only synthetic dashboard/semester UI, no visible passwords/tokens/session IDs. Their manifest explicitly contributes zero functional tests. The204 index versus immutable202 portable snapshot/pinac5 is disclosed and is not a finding.
- Source0421/Academic1fe provenance is separate from the future documentation publication SHA. Added local-gates receipt honestly records seven equivalent pinned local checks and CI PAUSED; this reviewer did not execute those gates.

Only read/pure hash/JSON/CSV/projection checks and local image inspection occurred. No new browser, network/API, DB/DML, build/runtime command, CI, source edit or root-repository write. Only this immutable external report was created. Explicit assignment remains GPT-6.1 Sol/high; runtime model telemetry is unavailable.
