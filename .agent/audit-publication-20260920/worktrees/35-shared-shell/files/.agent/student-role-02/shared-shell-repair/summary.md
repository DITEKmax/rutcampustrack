# Shared-shell repair summary

Status: bounded repair complete; ready for fresh independent Sol recheck.

The four fresh review findings are corrected in the assigned shared-shell
scope. External navigation mutations now invalidate the Vue shell through the
stack's observer. Nested initial, cross-root, and replace transitions carry an
owning root, so Back reaches that root and stops. Host replacement unsubscribes
and resets the old state before subscribing the new adapter, with unmount
cleanup. The bottom nav keeps visible `Учёт` while exposing the canonical
`Посещаемость` accessible name and disabled reason. Back uses the project
semantic touch-target alias.

Evidence is in `evidence.md`; exact commands, exit codes, environment, runtime
outputs, and limitations are in `checks.json`. Final focused tests, actual
component probes, workspace typechecks, scoped lint, PWA/TMA builds, keyboard
probe, and diff check passed. Full lint remains exit 1 only for the pre-existing
unrelated `fixture-transport.test.ts:94` finding; it was not altered.

No product, contract, or scope decision is pending from this leaf. No Terra
escalation gate was opened. No commit was created; root owns the stable diff and
integration after independent recheck.
