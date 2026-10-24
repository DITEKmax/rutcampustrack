# Independent capability-delta review

Verdict: **CHANGES REQUIRED — one P2 contract mismatch**. The service capability values and prior whole-purge security/data conclusions remain accepted; no new backend behavior finding.

Frozen source: `0100dd7c8475bd7b3ae2d294b45d333a8b29f807..6839fa54c517aec025d88b53c7b8f52baf184b08`, exactly six files: preview DTO, transaction service, two existing IT files, Academic OpenAPI snapshot and generated Academic TypeScript. Read frozen Git blobs, author `/tmp/map-deletion-scope-summary-20261024.md`, and saved business/final-metadata XML. No moving-worktree behavior was used.

## Finding

### P2 — published OAS3.1 schema rejects null while the accepted nullable contract and generated type allow it

Location: `docs/openapi/academic.json:18123`, `components.schemas.PlanDeletionPreview.properties.deletionScope`; generated counterpart `frontends/mobile-core/src/api/generated/academic.types.ts:3423`.

The schema is:

```json
{"type":["string","null"],"enum":["ALL_VERSIONS","CURRENT_FORMAT"]}
```

In OAS3.1/JSON Schema, `type` and `enum` are conjunctive constraints. Adding null to the type array does not exempt it from enum validation: literal null is absent from the enum. The generated type nevertheless is `deletionScope?: "ALL_VERSIONS" | "CURRENT_FORMAT" | null`, and the explicitly requested additive contract is optional **and nullable**.

Independent pure reproduction used the installed Python `jsonschema.Draft202012Validator` against the actual frozen `PlanDeletionPreview` schema. `{}`, `{deletionScope:"ALL_VERSIONS"}` and `{deletionScope:"CURRENT_FORMAT"}` validate; `{deletionScope:null}` fails specifically on `enum`. Thus a schema-validating consumer of the published contract rejects the null compatibility form that the generated consumer contract accepts. This is a contract mismatch, not an observed current service failure: both actual service branches emit nonnull values, and the legacy Java constructor's null is omitted by NON_NULL serialization.

Correction: make the canonical OAS3.1 schema actually accept null (for example, include literal JSON null in the enum, or an equivalent nullable union), keep the generated type aligned, and qualify the final snapshot using a direct null-validation check. No whole-purge business rerun is required for an annotation/schema-only fix unless behavior changes.

## Accepted conclusions in this delta

- The two actual construction sites provide `CURRENT_FORMAT` only for the explicit png/svg current-format branch and `ALL_VERSIONS` only for the null-format whole-history purge branch. These describe the already-reviewed server behavior truthfully. No ability is inferred merely from a missing field.
- The seven-argument Java constructor remains source-compatible and passes null; field-level NON_NULL omits it and cannot falsely advertise ALL_VERSIONS. The new field is not required in the snapshot. Existing explicit-format PWA/TMA consumers can continue ignoring it.
- The exact product diff changes only DTO metadata/constructor and the two response constructor arguments. Permission checks, password confirmation, digest domains, receipt replay, purge/selected-format behavior, high-water, rollback and migrations are unchanged from accepted0100.
- Pure semantic JSON comparison confirmed the sole OpenAPI change is this added property. Generated TypeScript adds only its optional union; no unrelated API outputs changed. Scoped `git diff --check` PASS.
- Saved `/tmp/map-deletion-scope-run1-xml/academic-integration` confirms the same four business cases plus snapshot and four architecture guards: nine tests, zero failures/errors. New assertions inspect ALL for actual whole previews and CURRENT via actual selected-format GET; existing isolation/replay/rollback assertions remain. Final `/tmp/map-deletion-scope-metadata2-xml/academic-integration` confirms snapshot plus four guards: five tests, zero failures/errors. A matching snapshot alone does not establish the missing null enum semantics above. The first metadata pass with ignored `nullable=true` is not qualified as final nullable proof.

## Limits

No reviewer testsuite, browser, SUT/API/backend request, DB command, build, Gradle, Docker, install, CI or stand mutation occurred. The only executable checks were pure schema/XML parsing and scoped Git checks. Remote Auth password confirmation remains explicitly stubbed in the HTTP tests; this delta does not prove current-password/full-SUT admission. Author artifact receipt for immutable `04377f9c...`/source6839 and prior frozen0100 was read, not independently rebuilt or rolled out. Frontend API/Page enablement and full WEB acceptance remain separate review/runtime gates. Root integration/promotion conditions and Q-PROD-004 are unaffected.

Only this immutable report was written. Explicit requested assignment remains GPT-6.1 Sol/high; runtime model telemetry is unavailable, so no runtime attestation or fallback claim is made.
