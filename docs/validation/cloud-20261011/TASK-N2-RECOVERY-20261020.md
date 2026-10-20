# N2 — retained session recovery

## Goal

Owner GO: fix independently confirmed N2/P2. After reconnect joins an older refresh which rejects with a network error, the retained Student shell must expose safe session recovery or explicit re-entry. Remain fail-closed until current admission; never blindly repeat an uncertain refresh mutation. Finish at READY_FOR_INDEPENDENT_RECHECK, without merge/deploy.

## Context/evidence

Starting PR/docs4c71f43d, executable178f18e8; exact hashes and clean starting state are in ignored `current-wip/n2-recovery-20261020/starting.safe.json`. Current4c CI39400934450, Coverage39400934459, Security39400934413 and OpenAPI39400934305 are all SUCCESS/attempt1, independently reread. Historical178f PR failure remains evidence, not the current gate status. Built-image scan remains SKIPPED.

External review accepted the diagnostic/privacy/workflow delta, preserved F01–F09/N1 FIXED and confirmed N2 as the only new finding. Historical network cause remains UNKNOWN. Historical v3 39/39,196/196 and its frozen weights are unchanged.

## Relevant scope / ownership

Branch codex/server-validation-20261011, existing draft PR2. Root owns Git, shared task status and integration. Sole developer `/root/n2_astra_author` owns isolated `/tmp/rct-n2-astra-20261020` at baseline4c, production frontend recovery and directly related tests. No owner-local frontend changes are imported. Original checkout, 25 foreign untracked files, worktrees, evidence and data are preserved.

Owner model override for this package: requested gpt-6-astra/high for research, implementation, checks and internal independent review, fresh bounded packets/forknone. Actual runtime metadata UNKNOWN unless supplied by the platform. No global model/config changes; existing root cannot be switched through an available API and performs coordination/integration only. External reviewer remains separate.

## Required behavior

Read real session-owner, PWA fetcher/auth/bootstrap/online handlers, retained Student retry and shared PWA/TMA consumers. Preserve current authority, generation/account isolation, late-response guards and pending command idempotency. A network event or successful Today GET alone cannot restore authority. Provide a usable recovery/re-entry action while retaining the owner and uncertain commands. No refresh storm, unbounded loop, blind POST retry or unconditional remote logout.

Selected correction: the retained-shell retry requests current admission with the existing bearer, without automatic refresh anywhere in the admission chain, including subsequent BFF reads. NETWORK/5xx and same-authority recovery retain the same owner/key and uncertain commands. A confirmed bearer401, typed409 SESSION_STATE_STALE, exact current-session gateway403 WRONG_ROLE or authority change follows the existing generation boundary and explicit re-entry/role selection; old commands are not transferred or replayed into another identity. Bearer401 does not establish remote logout or the state of the refresh session. This uses the owner's permitted explicit re-entry option rather than inventing cross-session command migration.

## Constraints

No main merge/deploy/draft change, other user-chat/reviewer contact, production/real secrets/data/provider operations. B013/B014/B015, rejected Mongo preparation and sandbox bypass remain frozen. Do not repeat the oversized local full privacy launcher or lower reserves/caps. Original deadlines/retries/assertions, privacy, admission, CAS, ACK/DLQ and mandatory gates remain unchanged.

## Existing patterns

Production source chain: session-owner refresh outside bootstrap; goOnline while old same-generation refresh is pending and bootstrap idle; bootstrap coalesces that promise; NETWORK rejection prevents current-session; retained owner stays offline/read-only; Today retry only refetches Today. Existing R1/R2 and busy-bootstrap proofs inform regression selection but were synthetic transport/module cases, not browser/backend acceptance.

## Acceptance

| Finding     | Author state                                   | Independent state                                    |
| ----------- | ---------------------------------------------- | ---------------------------------------------------- |
| F01–F09, N1 | Preserved; applicability checked against delta | FIXED on accepted prior snapshot                     |
| N2          | LOCAL_VERIFIED; HOSTED_PENDING                 | Internal scoped APPROVE; designated external PENDING |

Baseline regression must demonstrate the real production control flow and controlled pending-refresh→online→NETWORK order. Fixed proof must cover retained-shell recovery/re-entry, current admission and suspended roles; also R2, queued/busy online, ordinary reconnect, generation/logout/late responses and pending commands. Applicable PWA/TMA type/build/consumer checks and ordinary gates must bind to the actual new source. Internal review is not designated external APPROVE.

## Verification

Short fix contract → deterministic baseline RED → minimal fix → same regression and relevant safeguards → independent Astra/high review → integration and ordinary source gates → exact source/docs/inventory/evidence handoff. Hosted runtime uses existing permitted isolation; no browser sandbox or Mongo-preparation bypass. Root resource preflight: about3.83GiB workspace free and4.10GiB tmp free,16GiB memory ceiling; local full stack is NO_FIT_PROOF. Lightweight checks remain CPU1/group RSS≤512MiB with bounded output and fresh capacity checks.

## Do not

Do not reopen the ten accepted findings or general auth/CI investigation without new causal evidence. PUT500, activity mismatch and gateway429 historical causes remain separately UNKNOWN. A matching pattern or green gate does not prove historical root cause. No external APPROVE claim, no merge; finish with a reviewable correction package.

## Source-ready checkpoint

Nine changed implementation/test/workflow files are frozen in the author manifest and integrated byte-for-byte into the PR checkout. Internal independent Astra review found no blocking product/test/workflow findings. Final11-case real App/auth/StudentApi control-flow regression passes; immutable baseline remains RED. Mounted-owner retry and uncertain check-in command/key preservation, existing auth and bootstrap regressions, focused TypeScript and20 diagnostic controls pass. Exact commands, source hashes and bounded receipts are retained in ignored evidence; the final handoff will bind published source and hosted results.

Resource limits remain unchanged: combined test launcher and full local PWA typecheck exceeded512MiB and were stopped, NOT_PASS. Separate focused checks passed. Full PWA/TMA typecheck/build and the two new actual-browser/backend scenarios are mandatory hosted PENDING. Local browser/sandbox and Mongo-preparation restrictions were respected. The old D002 test is unchanged; hosted additions use controlled transport rejection with real admission/backend, not an assertion of historical network cause.

## Hosted correction checkpoint

Source6585 is published. Coverage/Security/OpenAPI and both full PWA/TMA consumer build/typecheck jobs PASS. Both hosted active N2 cases PASS; suspended cases fail at the new200/401-only oracle. The exact observed HTTP status is UNKNOWN. Source inspection confirms an existing typed409 SESSION_STATE_STALE response when suspended active-role selection changes session_version; App already routes it to explicit login.

Reviewed correction changes only the new hosted test, its two workflow digest pins and an added real-App handler test. It requires raw `extras.code`, login/navigation0 and no second refresh/role mutation; other409/403/503 fail. Original D002 and product files are unchanged. Expanded12 flow cases, seven negative/positive oracle controls, focused/hosted TypeScript and20 diagnostic controls PASS. Old exact11-case RED/GREEN custody remains valid for those bytes, not claimed for the expanded12-case file. Internal scoped review APPROVE; next-source hosted and designated external acceptance PENDING. Additional PR D002 timeout remains a separate network-cause UNKNOWN. Current runs are allowed to finish before the next normal publication; no blind rerun.

## Typed current-session403 correction

Both source46df hosted suspended cases now retain numeric GET `/api/auth/session`403; active and original D002 pass. Exact observed body code remains UNKNOWN. Production sources show Gateway admission can reject the suspended active grant before downstream version reconciliation; its top-level WRONG_ROLE403 was lost by the current client. The reviewed fix recognizes only the current-session operation with HTTP403, matching raw status403 and top-level WRONG_ROLE, using a private normalized marker. Other403/BFF denials do not become automatic logout. Current-session and refresh-candidate rejected responses are generation-fenced before propagation; same-generation behavior and refresh count are unchanged.

Six final files are integrated byte-for-byte. Same final21 tests: immutable46df baseline1FAIL/20PASS → corrected21PASS. A provisional late-candidate race found independently is fixed and regression-proven. Earlier baseline extraFAIL from sidecar AuthRequestError class identity is retained as harness evidence, not a product defect; corrected immutable baseline custody independently checked. Auth-client28/auth46/bootstrap29, oracle13, diagnostic20 and two scoped TypeScript checks PASS. Internal final APPROVE; ordinary new-source hosted and designated external review remain PENDING. The new hosted403 branch requires the exact raw gateway envelope plus login/navigation0/no extra refresh/role mutation. Unknown403 remains a failure.
