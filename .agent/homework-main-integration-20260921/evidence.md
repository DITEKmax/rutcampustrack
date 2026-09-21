# Homework S3 integration — MAIN

## Scope and contract

- Baseline: MAIN `02232784dabd673271ad323cb431d94a17f5736d` (Maps/entry), with
  the existing Journal work from `7a448ec0` preserved.
- Authoritative source: `.agent/worktrees/v2-l5b-historical-attendance`;
  accepted source decision: `.agent/homework-creation-binding/postcorrection-summary.md`;
  final review: `.agent/orchestration-v2/evidence/homework-acceptance-review-20260921.md` (PASS).
- Scope: the frozen 48 accepted feature rows only. The three sync-only rows
  (V18 migration, `InternalJwtClaims`, `InternalJwtValidator`) were excluded
  from transfer and remain byte-identical in MAIN.
- Required behavior: accepted Homework creation is durable in Academic;
  Schedule binding is idempotent; `PENDING` is hidden until confirmation;
  terminal archive remains available through the accepted actor/TLS path.
- Do not: reuse the stale portable patch, remove current Maps/Student/Journal
  behavior, touch foreign WIP, push, merge, deploy, migrate production, or
  rerun the source audit/full suite.

## Transfer and diff evidence

- The first hash comparison had a path-mapping error and compared the source
  tree with itself. This was recorded as a tooling/verification error, not
  product progress. The mapping was corrected and every accepted source row
  was then checked against the explicit absolute source and MAIN paths before
  transfer.
- Result: 48/48 accepted feature rows transferred; 47/47 direct source-byte
  rows still match after the generated OpenAPI update. `docs/openapi/academic.json`
  is the intentional derived exception: it was regenerated from current MAIN
  and retains the five Maps paths while incorporating Homework.
- The only additional product edits are bounded `exactOptionalPropertyTypes`
  fixes in `frontends/mobile-core/src/features/admin-map/AdminMapScreen.vue`
  and `frontends/pwa-vue/src/App.vue`; they preserve the existing Maps UI and
  make optional upload/fetcher props conditional.
- No accepted feature path intersected the pre-existing `3d4115f3`→MAIN
  Maps/Journal change set. Foreign dirty and untracked files were left
  unstaged.

## Checks

| Check | Exit | Evidence |
|---|---:|---|
| Academic + Schedule sequential Java compile | 0 | lease `HW-INTEGRATION-20260921`, session `49013`, `BUILD SUCCESSFUL` |
| Academic `OpenApiSnapshotIT` update with `'-Popenapi.snapshot.update=true'` | 0 | session `79121`, `BUILD SUCCESSFUL`; generated combined snapshot |
| `npm run typecheck --workspace @rct/mobile-core` | 0 | sessions `90947`, `18501` |
| `npm run typecheck --workspace @rct/pwa-vue` | 1→0 | defect reproduced in session `90947`; bounded mapping fix; recheck `11977` |
| `npm run typecheck --workspace @rct/tma-vue` | 1→0 | same affected Maps optional-prop defect; recheck `78933` |
| scoped `git diff --check` | 0 | only normal CRLF conversion warnings were reported |

The source acceptance runtime evidence remains the accepted 7/0/0 Academic
and 1/0/0 Schedule IT result and the PASS final review. MAIN cross-service
TLS publish/archive runtime is intentionally pending the existing root
producer/runner after this commit; no new harness was added.

## Runtime handoff

Use the existing gateway with a real login and selected-role JWT. For a
headman actor, create with `POST /academic/homeworks` (gateway prefix as
configured) and this shape:

```json
{"title":"Pending IT Homework","description":"description","link":null,"subjectId":123,"groupId":456,"semesterId":789,"lessonDate":"<future YYYY-MM-DD>","lessonNumber":1,"requestKey":"<uuid>"}
```

With the Schedule confirmation path unavailable, the first response must be
`202`/`PENDING` and student reads must omit it; retrying the same body and key
after confirmation must produce one durable `201`/`ACTIVE` record. Archive it
with `DELETE /academic/homeworks/<id>` using the same selected-role JWT and
verify `204` plus terminal `ARCHIVED` Schedule binding. Do not use fixture
`X-User-*` headers for this runtime check.

## Limitations and commit

- No source IT rerun, full suite, deploy, migration, or push was performed;
  the requested compile/snapshot/frontend checks are complete.
- The final local commit SHA is recorded by the parent delivery message after
  the scoped commit is created; this file is part of that same scoped change.
