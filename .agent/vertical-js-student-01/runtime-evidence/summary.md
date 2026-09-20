# JS-STUDENT-01 runtime and BFF repair evidence

Scope: typed Mobile BFF client-input failures and the task-local PWA/runtime harness. Product behaviour was changed only in `MobileProblemHandler`; runtime SQL and browser-probe changes are task-owned evidence tooling.

## Criteria and evidence

- Missing and malformed client input return `application/problem+json` with a stable `code`; an invalid present idempotency key is classified through the annotated request-header validation result.
- The student PWA is built from the current frontend revision and is verified at a loopback HTTPS task origin with no persistent token and with an offline shell.
- The runtime harness preserves the accepted Today contract: the two ordinary seeded lessons and the injected headman-blocked lesson remain ordered and visible; the latter has disabled `GEO_BLOCKED` eligibility.

## Checks

| Result | Command | Exit | Evidence |
|---|---|---:|---|
| PASS | `gradlew :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.contractexport.OpenApiSnapshotIT.clientInputFailuresUseTypedProblemDetails" --rerun-tasks` | 0 | Six observable typed-error cases, including absent and short present `Idempotency-Key`. |
| PASS | `gradlew :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.contractexport.OpenApiSnapshotIT" --rerun-tasks` | 0 | 53 s, 39 tasks; canonical OpenAPI snapshot unchanged. |
| PASS | `gradlew :services:mobile-bff:mobile-bff-app:bootJar` and `:services:attendance-service:attendance-app:bootJar` | 0 | Fresh server jars for runtime. |
| PASS | `npm run build` in `frontends/pwa-vue` | 0 | Vue typecheck and Vite production build; `index-CwDHP3Uz.js`. |
| PASS | bundled `node --check .agent/vertical-js-student-01/runtime/pwa-browser-check.mjs` | 0 | Browser harness syntax after bounded-close instrumentation. |
| PASS | bundled `pwsh -NoProfile -File .agent/vertical-js-student-01/runtime/run.ps1 -ValidateOnly` | 0 | Runtime prerequisites and boot jars. |
| PASS | `git diff --check` | 0 | No whitespace defects in the scoped working diff. |

## Runtime

`20260906-224922/runtime-result.json` records a successful real browser phase: `https://127.0.0.1:28443`, login/refresh/session/schedule/today all 200, HttpOnly+Secure+Strict `/api/auth` refresh cookie, activated service worker, `7/7` precache, no persistent JWT or API cache entries, and an offline reload with two rendered lessons and disabled mutation. Screenshot: `20260906-224922/pwa-offline-reload.png`.

That full run exited 1 later at the task-only SQL assertion because its injected schedule item still named the retired `teacher_id` column. The source migration `V3__drop_teacher_id.sql` reproduces the schema cause; the harness now omits only that retired column/value.

`20260906-230112/runtime-result.json` is an intentionally browser-skipped server-only composition run. It passed the six-service start, real gateway login, a headman-blocked POST (`409 CHECKIN_NOT_ELIGIBLE`, zero Mongo writes), and the retired legacy endpoint (`410`, zero Mongo writes), then exposed the stale two-lesson assertion after the harness injected its third blocked lesson. Root accepted the three-lesson/disabled-eligibility contract; the assertion and report serialization now match it. The unchanged later server scenarios remain covered by the prior full server evidence `20260906-184531/runtime-result.json`.

`20260906-225224/runtime-result.json` is a separate bounded-hang record: the browser subprocess produced no JSON before its task-owned process was stopped. The probe now records phase transitions and bounds server/browser close to ten seconds; `server.close()` awaiting a browser connection was a candidate, not a proven cause. No product code was changed for this investigation.

The final frontend asset binding is unchanged after the successful browser phase: no `frontends/` diff is present after integration revision `623460ef`. Current SHA-256 values are `index.html` `0BA3CEEB481F7754EAA9A477EBA30053CCB68BC812AEC1A1F023397D5B4B1677`, `sw.js` `EA5B9FCA0442271483F19A1C73F5B7FA482E792F4C321A25F4366B316AD0AC1D`, `sw-assets.js` `F321EA10DFDBC9C290DD55DE14493CB729456274D0FB2A68897A840842D2EE3A`, and `assets/index-CwDHP3Uz.js` `78882E54ADEBB31B8375A31FD1C7265DC891160F20EEE80C3E4E746E01CD26FD`.

## Limitations

The production non-loopback PWA transport remains unverified because external TLS interception returned 499; evidence is the task HTTPS loopback origin, not a global trust/AV change. Real Telegram delivery is not proven. No additional full runtime was started after the accepted harness-expectation correction; its syntax and preflight checks passed, while the existing browser and server evidence are composed as described above.

## Contract closure

The full attendance unit suite passed after the late-checkin repair (`:services:attendance-service:attendance-app:test`, exit 0). The initial attendance OpenAPI snapshot check reproduced a legacy-checkin drift (exit 1). The canonical `-Popenapi.snapshot.update=true` export, its exact snapshot recheck, and each of the PWA/web-panel/mini-app offline generators then passed (all exit 0). A final generated-types `git diff --exit-code` check passed with no residual drift.

## Routing synchronization decision — 2026-09-06

Owner's current resume decision cancels the stale copied workflow wording, “TMA checks отложены владельцем”, in `docs/agent-workflow.md` (the earlier 06.09.2026 future-batch paragraph). It does not alter the byte-exact canonical routing copy; this task record is the dated superseding decision for the integration handoff. The fresh independent Sol review at integration SHA `552cc555` found no findings. It independently matched `JavaCheckinApi` and the global customizer semantic change, regenerated the exact three legacy client types in memory with byte-equal SHA-256 `d8f4a9227093f4c3e90cf27efe4627140f1702384076c481d17879fbb37a4e80`, and accepted the scoped JSON and whitespace checks.
Routing-copy scope: `AGENTS.md`, `docs/agent-workflow.md`, only section 6 of `docs/implementation/parallel-development.md`, and this task-owned verdict. Criteria: both complete canonical files and section 6 match the current root sources byte-for-byte; all other parallel-development sections stay in the integration version; the TMA cancellation has a dated record. Verification at SHA `552cc555`: exact-copy SHA-256 values `DF68AD6EE6A15EAA3DCE3E7D624C35DAD837ACC7E1D9DC7AFC23DB51F23577E0` and `315FBBFCBB3F141BED520EEE40DD8CAAAF72C46710140E6BE694DD2D85037C2A`; section-6 SHA-256 `7900A761B8663145FB0BE1B996E8BF2E3C2A0353A51CF47D55DBF7E2E734C3EA`; `git diff --check` exit 0. Runtime is N/A for a documentation-only routing synchronization. Limitation: the canonical workflow remains an exact copy, so its superseded TMA sentence is retained there and cancelled only by this dated task decision.