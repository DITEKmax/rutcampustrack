# Resume 4 Security repair mounted QA

Date: 2026-09-08. Risk S3. Revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Stable Security source SHA256 `5305389822B603A9727E6E9E8F391314F7B7900EF7AEF92B55C22B57BE91771E`, 11269 bytes.

## Runtime

- Existing real-component Vite fixture CWD: `C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/worktrees/profile-ui/.agent/profile-ui/evidence/resume-2026-09-08/harness`
- Command: `node .\\serve.mjs`
- Bind: strict `127.0.0.1:18110`
- Owned server PID: 7860; child PID: 28500
- Browser: Codex in-app browser, mounted real `SecurityScreen.vue`

## Acceptance observations

PASS — invalidation is a sensitive-state boundary:

- After external `ACCOUNT_INVALIDATED`, all three password inputs reported disabled, all three reveal buttons reported disabled, submit reported disabled, and callback count remained `0`.
- A real locator `fill` attempt against the invalidated current-password input was rejected by the disabled control (`fillAccepted=false`).
- A real locator click against the invalidated current-password reveal button was rejected (`revealAccepted=false`).
- After clearing the external invalidation, all three inputs and reveal controls were enabled; all three controls showed the hidden-state `Показать …` labels, no `Скрыть …` control existed, and callback count had not changed.
- The recovered form rendered all three fields empty. Password values are intentionally absent from this artifact.

PASS — ordinary error remains retryable:

- Entered synthetic non-secret fixture values and enabled all three reveal controls.
- External `CURRENT_PASSWORD_INVALID` preserved all three rendered values and all three reveal states, kept its field message visible, left submit enabled, and did not call the callback.

PASS — recovery and success:

- Cleared the ordinary error and used the real form submit path.
- Callback count changed `0 -> 1`; fixture reported `Последний результат: получен`.
- Success cleared the form and restored all three controls to hidden-state labels.
- A second invalidation followed by blocked input/reveal attempts and error clearing left callback count at `1`, with three empty hidden fields.

PASS — browser diagnostics:

- Console warning/error query returned an empty list after all flows.
- No real Auth/BFF/PWA/TMA service was contacted.

## Static checks and cleanup

Current checks are recorded in nested `.agent/profile-ui/evidence/resume4-repair/checks.json`, SHA256 `E6E12BA3766C2F6D0DCBE6CAD6CAB388A69FFDD179281B3BAEFAA76C09A8E21A`: strict vue-tsc PASS, seven-SFC ESLint zero warnings, profile-state Vitest 20/20, Vite harness 44 modules with `write:false`.

The browser tab was closed. Exact owned process tree PID 7860/child28500 was terminated. Independent post-cleanup check at `2026-09-08T20:59:52.7046339Z` reported PID absent and `18110` free with no listeners. The post-QA Security hash remained `5305389822B603A9727E6E9E8F391314F7B7900EF7AEF92B55C22B57BE91771E`/11269 bytes.
