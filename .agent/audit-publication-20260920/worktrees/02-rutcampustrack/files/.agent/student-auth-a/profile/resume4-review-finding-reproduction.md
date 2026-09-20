# Resume 4 review finding reproduction

Date: 2026-09-08. Revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Severity: MEDIUM. Product source under test: nested `frontends/mobile-core/src/features/profile/SecurityScreen.vue`, SHA256 `AB039523841BEE93E78ABCA535DEAB7400DFA36003E1102D83F782911E2B659E`, 10943 bytes.

## Defect

`ACCOUNT_INVALIDATED` clears sensitive state only when the external error enters that code. The invalidated form continues to accept password input and reveal actions. Clearing the external error then leaves those newly entered values and reveal state in the now-enabled form.

## Mounted reproduction

Root started the existing real-component Vite fixture with `node .\\serve.mjs` at strict `127.0.0.1:18110` (owned PID 44064) after the prior cleanup/free-port proof, then used the Codex in-app browser:

1. Opened `Безопасность` and triggered the fixture's external `ACCOUNT_INVALIDATED` control.
2. The three password text fields each reported enabled, and the current-password reveal control reported enabled.
3. Entered synthetic non-secret values in all three fields and invoked the current-password reveal control while invalidation remained active.
4. Cleared the external error through the fixture control.
5. The mounted screen visibly retained all three entered values; current password remained revealed (`Скрыть текущий пароль` was visible), submit became enabled, and callback count remained `Отправок: 0`.

Password values are intentionally omitted. The browser locator's read-only `input.value.length` returned zero despite the mounted screenshot visibly showing the values, so those length readings are discarded and are not used as evidence.

## Impact and correction

The invalidated screen can retain newly entered sensitive values, and error removal exposes a populated submit-ready form instead of an empty recovered form. Repair only `SecurityScreen.vue`: disable all three password inputs and reveal actions while invalidated, and clear sensitive state again when leaving invalidated state. Preserve the independent submit guard, external error visibility, ordinary-error retention/retry, success/unmount clearing, and optional-callback absence guards.

## Runtime cleanup

The reproduction browser tab was closed. Exact owned process tree PID 44064/child10800 was terminated after the observation; the implementation packet requires a fresh `18110` free-port proof before any post-repair mounted run.
