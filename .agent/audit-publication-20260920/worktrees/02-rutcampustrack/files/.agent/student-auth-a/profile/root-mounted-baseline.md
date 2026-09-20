# Root mounted Security baseline — 2026-09-08

## Scope and environment
S3 bounded UI verification of the actual nested SecurityScreen in the local fixture harness. Baseline HEAD8002b9ea4356b10779c5bb9a6d99746d32d78ae2. Parent accepted Codex In-app Browser as the actual standalone UI QA surface; no separate Chrome-name gate is required. Chrome provider returned unavailable; available browser was IAB id1, tab1. Viewport override390×844, URLhttp://127.0.0.1:18110/, task Vite PID41992. No real Auth, credentials, network mutation or account change is involved: root read the harness callback, which increments a local counter and records only whether synthetic input was present.

## Stable source binding
SecurityScreen.vue SHA256 before and after the flow: C98905CD60E49378A15C77390CFA2E95B7AAFF0F2D888467FC60EA3EACA63C07.
Actual nested harness App.vue after flow: SHA25656D9389BAE087410209E2224549729C23EE02E4E67A703173EE831EE41E452F7,16626bytes,last modified2026-09-08 09:01:09 UTC. Root requested a short App/Security source hold while reproducing; post-flow checks found the same Security hash and unchanged App modification time. Verification completed by09:14:19 UTC. Browser operations returned success; this was not a shell test suite.

## Reproduction and actual result
1. Open the real Security component using the visible navigation button.
2. Clear the harness error, show all three password fields, fill synthetic old-fixture / FixtureNew12! / repeated new value. The current fixture is shorter than the new password minimum, intentionally verifying that the old password is exempt from the new policy.
3. Click the visible harness ACCOUNT_INVALIDATED control.
4. Actual viewport screenshot shows all three entered values still rendered alongside the account-invalidated alert. The submit button reports enabled.
5. Click the visible change-password button. DOM snapshot shows fixture counter changing from Отправок:0 to Отправок:1 and result получен, while the invalidation alert remains.

FAIL: explicit account invalidation neither clears the mounted secrets nor blocks the callback. This is a verified UI defect, not a claim about server revocation. Root sent the exact result and correction GO to the assigned fresh UI developer. Required recheck: invalidation clears fields and prevents submit while it persists; ordinary current-password/policy/network errors retain input and allow the intended retry. Recheck after source correction is OPEN.

## Evidence limitation
Shutdown correction: the browser flow used the replacement task server PID47364, started at09:02:16.2860962Z; PID41992 in the earlier environment paragraph was the prior server and is stale. At09:20:23Z exact port18110 had no listeners. Current Security source SHA256 is BCC12C11D21BD9F0F13544C5FBA5E6B34BD8FD5EAFED6D61D2E0D8208C5139AD; no mounted recheck of that hash was completed before the owner pause. Keep the finding bound to C98905CD… as recorded above.

CUA screenshots and DOM snapshots were returned inline during the browser flow. PNG file delivery is still pending. In this browser's read-only evaluation surface, input.value.length returned0 even while the actual screenshot visibly showed the synthetic values; therefore this getter is not accepted as evidence of clearing. An earlier interrupted/HMR-sensitive attempt was not counted. The finding above relies on actual rendered screenshots plus the observable callback counter. Do not claim full seven-screen/theme/responsive/PWA/HTTP acceptance from this baseline.
