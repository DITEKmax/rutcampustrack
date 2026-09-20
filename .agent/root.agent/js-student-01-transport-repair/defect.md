# Recorded defect

- Request/root decision: root assigned the Terra high bounded repair after recorded browser TLS failures and authorized this task-owned probe.
- Reproduction: `& .\\.agent\\root.agent\\js-student-01-transport-repair\\probe\\run-browser-https-transport-probe.ps1` exited `1` before execution with PowerShell `ParserError` at the OpenSSL failure message.
- New evidence: PowerShell parses `$LASTEXITCODE:` as an invalid variable reference.
- Correction: delimit the variable as `${LASTEXITCODE}` in the probe-owned runner.
- Bounded scope: only the new runner in this task-owned evidence area; no integration/runtime source changed.
- Required recheck: run the probe and retain its JSON output and exit code.

## Integration harness defect

- Request/root decision: after the successful minimal transport proof, root transferred exclusive ownership of the integration harness for a real runtime recheck.
- Reproduction: elevated `powershell.exe -NoProfile -File .\\.agent\\vertical-js-student-01\\runtime\\run.ps1` exited `1` before service startup.
- New evidence: Windows PowerShell 5.1 reports that static `[System.Security.Cryptography.RandomNumberGenerator]::GetBytes` is unavailable; prior successful runs used the bundled PowerShell 7 runtime whose .NET surface provides it.
- Root decision/correction: revert the provisional compatibility edit and execute the unchanged harness with `C:\\Users\\maksd\\.cache\\codex-runtimes\\codex-primary-runtime\\dependencies\\native\\powershell\\pwsh.exe -NoProfile -File`.
- Bounded scope: no integration runtime source remains changed by this defect; it does not alter service, PWA or auth code.
- Required recheck: run the unchanged harness under the required PowerShell 7 runtime.

## Cookie-runtime diagnostic

- Request/root decision: root transferred the integration harness after the minimal transport proof; JS-STUDENT-01 requires an HttpOnly, Secure, SameSite=Strict refresh cookie.
- Reproduction: the PowerShell 7 full isolated runtime `20260906-221103` reached six healthy services, HTTPS seed `200`, and login `200`, then failed because browser context had no acceptable `rct_refresh` cookie.
- New evidence/root source check: `AuthCookies.COOKIE_PATH` is `/api/auth`; Playwright `context.cookies(origin)` supplies a URL at `/` and therefore excludes that valid path-scoped cookie. Root identified the exact API-query mismatch.
- Correction: query the cookie jar with `${origin}/api/auth/refresh` for both initial and rotated cookies. Retain safe upstream cookie-attribute projection, and prove the URL-path semantics in a task-owned fixture before the expensive real rerun.
- Bounded scope: `.agent/vertical-js-student-01/runtime/pwa-browser-check.mjs` harness query plus probe/evidence instrumentation only.
- Required recheck: minimal path fixture, then the same isolated runtime.

## Bounded service-worker wait

- Request/root decision: the transferred harness must provide conclusive production PWA runtime evidence.
- Reproduction: after the cookie-query correction, the full runtime reached `production PWA online bootstrap`; Chrome and the bundled Node process stayed active beyond three minutes with no result because `navigator.serviceWorker.ready` has no timeout.
- New evidence: `pwa-browser-check.mjs` awaited that promise directly, while the surrounding browser checks use explicit 20-second limits. The owned run was interrupted for cleanup after this bounded observation.
- Correction: wait for an activated registration through `page.waitForFunction` with 20 seconds, then retain the existing controller check. A service-worker defect now returns evidence instead of hanging the task runtime.
- Bounded scope: `.agent/vertical-js-student-01/runtime/pwa-browser-check.mjs` harness wait only.
- Required recheck: confirm task-resource cleanup, then run the same isolated runtime once; any resulting service-worker failure is product/runtime evidence, not masked by the harness.

## Production StudentApi fetch binding

- Request/root decision: current scope requires real PWA→gateway runtime proof; no frontend product ownership was transferred after this new finding.
- Reproduction: full runtime `20260906-222711` has HTTPS seed/login/refresh all `200`, valid scoped refresh-cookie attributes and a freshly rebuilt production PWA containing service-worker registration. The real PWA then renders `Failed to execute 'fetch' on 'Window': Illegal invocation` instead of `.today-list`.
- New evidence: `frontends/mobile-core/src/api/student-client.ts:30,69` stores the native `fetch` function and invokes it as `this.fetcher(...)`; in Chrome the default function is unbound. Fixture mode supplies an ordinary function, so it did not expose this production path.
- Required correction: under root-assigned frontend ownership, preserve custom `fetcher` injection but default to a wrapper that calls the global `fetch` with its required binding; add a regression test for the default browser transport shape, rebuild production PWA, and rerun only affected PWA runtime.
- Bounded proposed scope: `frontends/mobile-core/src/api/student-client.ts` and its targeted tests. No product/contract redesign is requested; the current transport contract remains unchanged.
