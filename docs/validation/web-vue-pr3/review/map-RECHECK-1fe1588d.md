# Narrow nullable-enum P2 recheck

Verdict: **PASS**. The sole P2 in `RECHECK-6839fa54c.md` is closed. Combined backend source verdict for0100 +6839 +1fe is PASS with the existing runtime/frontend limits; no remaining confirmed P1/P2 in these bounded packets.

Frozen correction: `6839fa54c517aec025d88b53c7b8f52baf184b08..1fe1588dd1ed635df6a8735d5115d436151e8cec`, exactly `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/WebConfig.java` and `docs/openapi/academic.json`.

The new OpenApiCustomizer targets only `components.schemas.PlanDeletionPreview.properties.deletionScope`. It adds literal null only when the target schema already declares null among its types, already has an enum, and has no null enum member. Missing components/schema/properties and nonnullable enums are unchanged. Reapplication is idempotent. Existing Jackson and MVC enum conversion configuration is untouched; this modifies documentation metadata only.

Independent pure checks against frozen Git blobs confirm:

- The sole semantic OpenAPI difference is one appended literal JSON null in that enum. The property remains optional and has `type:["string","null"]`, `enum:["ALL_VERSIONS","CURRENT_FORMAT",null]`.
- Installed `jsonschema.Draft202012Validator.check_schema` accepts the actual schema. Eleven-case validation accepts omitted, ALL_VERSIONS, CURRENT_FORMAT and literal null; rejects UNKNOWN, string "null", wrong case, number, boolean, object and array. The prior frozen schema still rejects literal null, confirming causal closure.
- DTO, transaction service and both business test blobs byte-match6839 and match the four hashes in `/tmp/map-scope-null-unchanged-business-source-20261024.json`. Generated Academic TypeScript is byte-identical and now agrees with the actual nullable schema.
- Scoped `git diff --check` PASS. Saved `/tmp/map-scope-null-run1-xml/academic-integration` confirms one snapshot and four architecture guards, all zero failures/errors. The prior four real business cases are reused because their source and behavior did not change.

Read `/tmp/map-scope-null-summary-20261024.md` and its pure schema script. Author generator/check and immutable JAR/source receipts are recorded there; this reviewer did not regenerate, rebuild, verify the JAR independently or roll out anything.

No reviewer testsuite, browser, SUT/API/backend request, DB command, build, Gradle, Docker, install, CI or stand mutation occurred. Remote Auth confirmation remains explicitly stubbed in the accepted HTTP tests. This PASS does not establish full password-confirmed WEB/SUT operation or authorize deployment; combined frontend/root integration and actual WEB acceptance remain separate gates. Group-final-deletion policy Q-PROD-004 is unaffected.

Only this immutable report was written. Explicit requested assignment remains GPT-6.1 Sol/high; runtime model telemetry is unavailable, so no runtime model attestation or fallback claim is made.
