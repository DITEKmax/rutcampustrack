# Homework date generated-contract repair — draft awaiting review verdict

S3. Fresh Luna max solewriter homework-api checkout after fullreviewfinal. No children. Baseline8002b9ea plus stable25files pause snapshot. Dispatch only after root attaches finalreview.

## Goal
Make required server completedAt available in canonical Java-first OpenAPI and generated mobile-core types for shared Homework UI.
## Context/evidence
homework-date-delta-packet.md requires nullable server timestamp in GETitems and PUTcompletion. Root/reviewer opened StudentApiModels.java206–247 and runtimequery/tests: field exists. Canonical docs/openapi/mobile-bff.json StudentHomeworkItem/Completion and generatedmobile-bff.ts242–260 omit it. Old homework-repair export checks predate delta; HTTP7/7 and Academic152/152 do not test generated type use.
## Relevant scope
Solewriter .agent/worktrees/student-role-02/homework-api. Own BFF Java-first export/test evidence, docs/openapi/mobile-bff.json, generatedmobile-bff.ts, affected typed fixtures/tests only. No date-domain/auth/servicepolicy/UI/config/lock/dependencies changes. Raw Academic OpenAPI residue not semantic feature; preserve until integrator excludes. Worktree .agent/student-role-02/homework-date-contract-repair evidence.
## Required behavior
Run canonical export from current Java source then no-update driftcheck; regenerate TS using project command. GETitem and PUTcompletion both require completedAt:string|null with date-time format. No manual interface duplication/typecast to fake field. Update typed fixtures with truthful completion timestamps consistent with boolean. Add meaningful contract assertion proving BOTH schemas require nullable timestamp and runtime/generated boundary agrees. Preserve all four HTTP repairs and currentfeed timestamp semantics.
## Constraints
No hand-editing generatedOpenAPI/TS to bypass Javafirst; diagnose exporter omission if actual generation still drops field. No fake completion dates. Changes outside scope require concrete new defect. No redundant full Academic152 rerun for generated-only delta.
## Existing patterns
OpenApiSnapshotIT -Popenapi.snapshot.update=true then no-update; mobile-core npm run generate:types and project drift check. Canonical Homework Java @Schema requires completedAt with nullable=true. Existing fixtures and StudentApi type aliases.
## Acceptance criteria
Both exported schemas contain required nullable completedAt; generated type visible without casts; affected fixtures/typecheck/scopedlint/coretests/build pass, driftcheck clean. Exact final source hash manifest and repaired-review evidence. Earlier domain checks remain explicitly prior evidence.
## Verification
Read services/frontends/tests AGENTS/rct-verification. Record exact commands/exits/env/revision/counts, before/after schema evidence and no-update check. Gradle --no-daemon with required escalation for known sandbox issue. Fresh independent Sol high recheck required; no full-role PASS.
## Do not
No main integration/commits/deploy/unrelated version fix or whole-suite repeats without cause. Do not call old export checks date-delta PASS. Await root fullreview freeze.

## Root freeze after final review
07.09.2026. Full reviewer report homework-api-review-2.md: exactly1HIGH generatedcontract finding, otherboundedAPI/4repairs accepted. Draft is now FROZEN for dispatch. Allow minimal Java model/OpenAPI customizer correction only if current exporter still omits nullableInstant; first establish actualcurrentexport rather than assume exporter bug (stale artifacts also possible). Reproduce TS missingproperty/schema assertion before fix. Verify actualHTTP GET+PUTtrue timestamp/PUTfalse null with focused7case suite; no fullAcademic rerun for generation-onlychange. No other unresolved domainfinding in thisreview. Source25SHAverified atdispatch, preserve prior date/auth changes.
