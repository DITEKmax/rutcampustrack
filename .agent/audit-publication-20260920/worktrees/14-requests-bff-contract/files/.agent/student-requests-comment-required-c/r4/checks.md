# commentRequired producer chain — R4 checks

- Revision: d3c31acb8cce53791a4981e5858a37d44fdc9a0e.
- Final source guard: PASS, exact 11/11 current SHA-256 values match R1 plus R2/R3 supersessions.
- Dependency/union guard: PASS; authorization test remains byte-exact D1DDCDE6…, accepted P1 attachment reconciliation and P2 error paths are preserved.
- Runtime XML guard: PASS, seven exact XML files, 67 tests, 0 failures, 0 errors, 0 skipped.
- Command 1: exit 0, 19 tests; retained without rerun.
- Command 2: exit 0, BUILD SUCCESSFUL in 3m48s, 20 tests.
- Command 3: exit 0, BUILD SUCCESSFUL in 1m56s, 28 tests.
- Scope: only this R4 evidence directory was written after runtime; no product/config/lock/generated output edits.
- OpenAPI and generated TypeScript remain deferred to the B1 integration writer.
- Runtime lease: RELEASED.
