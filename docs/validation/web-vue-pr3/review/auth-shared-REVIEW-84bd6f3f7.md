# Auth/shared review — frozen 84bd6f3f7

Verdict: **two confirmed P2 findings**. No P1 found in the bounded packet.

Reviewed frozen root `84bd6f3f7`: AUTH6/OTP/QR/recovery packet `2ee6a85e8`, profile/security `ec7cae095`, protected/null-snapshot correction `2dae67d32`, common live/assistant ownership `a585ff342`, homework live correction `f0eb2bda6`, and QR projection compatibility `86a2be459` / `46519ed30`, as integrated at the frozen root. No moving worktree source was used. New notifications WIP, unrelated role pages, full baseline, Figma and production rollout are excluded.

## Findings

### P2 — Obsolete login recovery can overwrite the successor authentication state

Location: `frontends/web-vue/src/auth/session.ts:128–135` (frozen root).

The catch branch checks `revision === operation && isCurrent()` before awaiting `refresh()`, but does not repeat either check after that await or its rejection. The refresh itself correctly discards obsolete results; the enclosing authentication then continues writing its old error and `phase` anyway.

Concrete sequence: login A has an unknown network result and starts a held refresh; leave/cancel A, then start login B; B has received its access token and is awaiting its authoritative snapshot; the old A refresh settles. A now writes `phase='error'` while the current token belongs to B. B's later successful snapshot restores `ready`, but the A error survives because `acceptSession` does not clear it. This can temporarily replace B's login with the global session-error screen and then show an unrelated failure after a successful B login.

An independent lightweight in-memory harness loaded the actual frozen `session.ts` and Auth adapter (TypeScript transpilation only, existing Vue dependency, no server calls). Output:

```text
duringB={"phase":"error","token":"B-token","error":"A response lost"}
afterB={"phase":"ready","user":"2","error":"A response lost"}
```

Recheck ownership after recovery settles, including the recovery catch path, before reading success state or writing authentication error/phase. Preserve the existing non-replay behavior for an unknown login result.

### P2 — Live profile refresh can splice incompatible account-history pages and skip an event

Location: `frontends/web-vue/src/features/profile/use-profile.ts:150–152,183–184`; the affected existing append is `frontends/mobile-core/src/features/profile/profile-state.ts:243–255`.

The new profile page allows `loadHistory({cursor})` through “Показать ещё”, while its live/focus refresh independently invokes first-page `loadHistory()`. The live blocker includes drafts and mutations, but excludes an active history read. `ProfileState.loadHistory` only guards authority generation, so both responses are accepted under the same session; a late older-page response appends to whichever first-page result arrived most recently and replaces its cursor.

Concrete sequence: displayed page `[e30,e29,e28]`; hold “More” after `e28`; a live event refreshes the first page to `[e31,e30,e29]`; release the held page `[e27,e26,e25]`. Result is `[e31,e30,e29,e27,e26,e25]` with cursor `e25`. Event `e28` is missing, and further pagination continues below it. The security/account history is therefore incomplete until a fresh restart.

An independent lightweight harness ran the frozen actual `ProfileState` with those deferred page responses and produced exactly that list and cursor. This is a state regression reproduction, not a server/browser suite. Serialize the history operations or fence/reset page requests when refreshing the history head; retain a compatible cursor chain before applying append results.

## Reviewed behavior and evidence

- Token/snapshot publication for ordinary password/OTP/QR success remains server-authoritative. Success and decode continuations have operation/identity checks; the exception is the recovery catch continuation identified above.
- QR issuer proof remains volatile, uses local QR encoding, and is not written to URLs or storage. Disposal aborts polling/exchange and clears proof/image. Recovery strips fragment proof, uses a lifecycle operation gate, clears secrets, and presents an unknown completion result without blindly repeating completion.
- Shared transport checks authority before fetch, after Response, and before/after refresh. Profile read/own-profile decode continuations check the captured authority. Own-profile identity is validated; security/session mutations use existing own-session APIs and do not automatically replay unknown mutations.
- Protected child rendering is gated by snapshot and generation; the profile password-policy dereference is gated against null snapshot. QR auth-method decoder additions are compatible across the shared PWA and TMA projections.
- Common live refresh owns one pending invalidation, disposes subscribers on feature teardown, and reconnects by authoritative identity. Ordinary hover/focus no longer suppresses homework updates; pending completion still blocks its automatic refresh.

Read the six requested author evidence JSON files under `/workspace/rct-pr3-runtime/web-foundation/`: `auth-methods-smoke`, `auth-positive-ui`, `profile-security-fresh`, `profile-fresh-smoke`, `homework-focused-live`, `homework-browse-remainder`. They record positive real OTP/QR/recovery, profile/avatar, lost-response password/session handling, logout-all, homework browsing and one actual focused live update; all report empty error arrays. They do not cover the two interleavings above. No credentials, issued codes, private credential artifacts or secret payloads were accessed.

`git show --check` for the seven listed original packet commits passed. Runtime JSON evidence was inspected, not rerun; no browser/full suite, build, Gradle, Docker, installation or CI was executed. Two bounded causal checks used frozen modules in memory and dummy identities/tokens only. No product source was modified. Requested review model remains `gpt-6.1-sol/high`; runtime model telemetry is unavailable and no telemetry/fallback claim is made.
