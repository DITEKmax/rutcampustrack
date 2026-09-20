# FE13 harness repair checkpoint

Date: 2026-09-08
Risk: S1 bounded harness projection repair
Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
Assigned scope: `Harness.vue` and `fixtures.ts` only.

The pre-edit hashes were `8EE4A0D403C6D8261FF0662B3428C342E8417F9F71008CA9B98835A26AB9E600`
for `Harness.vue` and `BFBD824013214C20708894C6FCD73EABF40366CFB29FF81B86FA25C3EEDC76FE`
for `fixtures.ts`; both matched the nested frozen 23-file manifest before the
first product mutation.

Mutation checkpoint: the fixture file now contains explicit canned selected
aggregates and day/week series for every non-empty lesson-type subset. The
harness keeps read-state and last-type guards, applies the selected subset and
active range through `statisticsDetailProjection`, and updates detail data on
range changes. Type cards and their opaque IDs remain supplied fixture data.

Checks and final scope/hash evidence are pending. No runtime server was started.
