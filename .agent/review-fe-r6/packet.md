# R6 frontend repair contract

1. **Goal.** Restore replay-safe student check-in retries in PWA and TMA, preserve browser geolocation reasons, and repair the independently confirmed native `fetch` receiver failure.
2. **Context/evidence.** Frozen base `e583f05140c43311b547bbaeaa55c1a7bc3c9ce9`. Sol/R6 found fresh UUID/body per click in both shells and PWA discarded `GeolocationPositionError`; root reproduced native `fetch` failing because `StudentApi` detaches it from `globalThis`.
3. **Relevant scope.** `frontends/mobile-core/src/{api,domain,features/today}`, their targeted tests, and PWA/TMA shell check-in wiring.
4. **Required behavior.** One in-memory logical command/key per lesson and shell session; retry exact command/key after transport/5xx ambiguity; release it after ACK or non-ambiguous 4xx; no network while offline; PWA maps browser error codes. Native fetch is invoked with `globalThis` and injected fetch is unchanged.
5. **Constraints.** No persistent command/token/queue; no role, logout, offline-write, styling, generated-type, lockfile, config, or API redesign. Preserve unrelated work.
6. **Existing patterns.** `StudentApiError` carries `Response`; `useToday` owns mutation/ACK projection; `unavailableReason` already maps browser errors.
7. **Acceptance criteria.** Lost response after server commit can replay original body/key; definite failure creates a new logical command; concurrent click/geo acquisition is single-flight where practical; PWA codes 1/2/3 map correctly; both shells use shared recovery; default and injected fetch work.
8. **Verification.** Record failing reproduction before correction; targeted Vitest tests; workspace contract/typecheck/lint/build for both Vue apps; runtime when fixture can exercise the changed flow.
9. **Do not.** Do not edit outside assigned frontend scope or add a compatibility/offline persistence layer.
