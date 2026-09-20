# Resume 4 security invalidation repair

## Scope

Risk S3. Sole product edit: `frontends/mobile-core/src/features/profile/SecurityScreen.vue`.
Evidence is confined to `.agent/profile-ui/evidence/resume4-repair/`. No sibling
SFC, PCSS, type/state module, asset, harness, config, lockfile, backend, runtime
or external state was edited by this leaf. Existing dirty work was preserved.

## Criteria

- `ACCOUNT_INVALIDATED` clears all three password values and reveal state on
  entry and on transition out.
- While invalidated, all three password inputs and all three reveal buttons are
  disabled; the external error remains visible.
- Submit remains independently guarded and disabled while invalidated.
- Removing invalidation leaves an empty enabled form.
- Current-password, policy, network and authority errors retain retry input
  under existing busy/offline rules.
- Success and unmount clearing remain unchanged; optional callback absence guard
  remains; invalidation codes are not broadened.

## Evidence

Pre-state independently confirmed source SHA `AB039523841BEE93E78ABCA535DEAB7400DFA36003E1102D83F782911E2B659E` and 10,943 bytes. Static source lines showed the missing
input/toggle disabled bindings and watcher exit handling, matching the supplied
mounted reproduction. No password values are present in evidence.

Post-state source SHA is
`5305389822B603A9727E6E9E8F391314F7B7900EF7AEF92B55C22B57BE91771E` and 11,269
bytes. The six non-owned profile SFCs match the prior manifest bytes/hashes.

## Checks

All current lightweight gates passed with exit code 0: pilot strict
`vue-tsc`, Security-only ESLint, full strict `vue-tsc`, focused seven-SFC ESLint,
profile-state Vitest (20/20), and Vite harness build (44 modules,
`write:false`). Full commands, UTC times, versions and raw output are in
`checks-output.md`; structured statuses and exit codes are in `checks.json`.

## Runtime evidence

Leaf runtime status is `NOT_STARTED`. Root owns the fresh free-port proof,
`127.0.0.1:18110` lease, mounted post-repair browser flow and cleanup. No
runtime verdict is claimed here.

## Diff

The minimal correction adds `previousCode` handling to the existing external
error watcher and six `:disabled="accountInvalidated"` bindings. Existing
clear helper, external-error rendering, independent submit guard, callback
absence guard and success/unmount clear paths remain in place. The exact source
hash/bytes and unchanged sibling hashes are in `source-manifest.json`.

## Limitations

This leaf does not claim mounted browser acceptance, backend/Auth integration,
PWA service-worker behavior, TMA live behavior or full product acceptance.
