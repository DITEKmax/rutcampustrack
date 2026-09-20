# Root isolated session boundary evidence

07.09.2026. Source baseline d3c31acb8cce53791a4981e5858a37d44fdc9a0e. Two actual TypeScript source files copied unchanged into src; source-vs-baseline git diff exit0; both source/snapshot SHA pairs verified with0mismatch after execution. manifest.json contains exact paths and hashes. No product edits.

## Findings

HIGH client command ownership: frontends/mobile-core/src/api/student-client.ts:82,89–91. Start actual StudentApi.setHomeworkCompletion under synthetic ownerA; hold fetch response; replace token owner withB; resolve old response401. Actual client calls onUnauthorized then resends the same PUT/body usingB's token. result.log records two PUTs with ownersA,B. A composable response guard cannot undo that command. This proves client behavior with supplied dynamic token/callback; no server auth bypass, real user command, or complete browser login exploit is claimed.

MEDIUM logout state resurrection: frontends/pwa-vue/src/auth.ts:16,23,27–32. Start actual usePwaAuth.refresh against a deferred synthetic fetch, finish successful logout and snapshot clearing, then resolve prior refresh. Control confirms null token after logout and exactly one snapshot clear; old refresh subsequently restores a non-null token. This proves local token resurrection, not acceptance of a revoked token by the real backend or session-cookie behavior.

Required bounded correction belongs to the future Homework PWA/TMA adapter/session lifecycle writer: capture generation at request/refresh start, invalidate synchronously on clear/logout/identity transition, fail closed before retry/send or token assignment when obsolete. Preserve same-session refresh/retry and single-flight behavior. Shared StudentApi is currently owned by Requests transport; wait for its stable accepted source and assign one sequential writer if its interface needs changes. Auth server revocation remains a separate profile/session contract.

## Verification

Environment Windows, Node24.14.0, installed Vue/esbuild from homework-ui frontend node_modules. No real keys, tokens, DB, network or external messages. All fetches are local stubs and reject unexpected endpoints; synthetic bearer values are not printed.

`node .agent/student-role-02/session-retry-root-probe/build.cjs` sandbox exit1 (esbuild filesystem access denied), same scoped command approved outside sandbox exit0. `node .agent/student-role-02/session-retry-root-probe/probe.cjs` exit1 because desired no-cross-owner/no-resurrection assertions fail; see result.log. This is confirmed defect evidence, not test PASS. Source and probe remain frozen for later independent comparison.
