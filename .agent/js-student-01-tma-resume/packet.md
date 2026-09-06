# Compact packet — JS-STUDENT-01 TMA resume verification

Date: 2026-09-06
Risk: S2 (Telegram bootstrap, auth wire and integration boundary)
Role: bounded verification developer; no child agents
Model/effort: `gpt-5.6-luna` / `max`
Checked revision: `834f0ca7975914717bf77ea50c4469b92afcfb32`
Integration revision: `e583f05140c43311b547bbaeaa55c1a7bc3c9ce9`
TMA integration pick: `23e153f1bc6db9c311c3aaf6351eb3395eb7bb3e`
Worktree: `.agent/worktrees/js-student-01/fix-tma`

## Goal

Resume and independently verify the already implemented compact TMA contract at
the checked revision, confirm that integration retained the same TMA and shared
mobile-core content, and add only missing local/runtime evidence.

## Context/evidence

The prior TMA run recorded seven frontend checks and synthetic browser evidence:
official SDK ordering, canonical JSON auth, fixture reuse, token extraction,
401 and missing-host errors, Today/check-in flow, typecheck, lint, production
build and `git diff --check`. The prior artifact records its historical source
revision as `cedce8c…`; this packet does not relabel those results as a fresh
run. The current checked revision is the clean TMA fix commit `834f0ca…`.

Telegram's official Web Apps documentation says to place
`telegram-web-app.js` in `<head>` before other scripts, exposes raw
`window.Telegram.WebApp.initData`, and requires server-side validation. The
existing auth-service `TmaIT` test generates test-only HMAC data and exercises
the cryptographic boundary without a live Telegram host.

## Relevant scope

Read/verify `frontends/tma-vue/**`, compare `frontends/mobile-core/**` with the
integration branch, run the existing auth-service `TmaIT`, and write evidence
only under `.agent/js-student-01-tma-resume/` in this worktree.

## Required behavior

1. Integration preserves the exact TMA and shared mobile-core tree from the
   checked TMA revision.
2. The official SDK precedes the app module; auth sends the existing
   `{ initData }` JSON DTO and keeps clean 401/missing-host behavior.
3. The existing signed-initData integration tests pass with test-only fixtures.
4. Local fixture mode launches, completes Today check-in, and produces a
   screenshot; its synthetic token is never presented as Telegram proof.
5. No code, API, shared component, lockfile, config, secret, or external
   application state is changed.

## Constraints

Use only the assigned clean TMA worktree and isolated ports 5186/5187. Preserve
all work in the dirty root checkout and other worktrees. Do not use a production
bot, tunnel, Telegram account, user credential, live initData, or secret.

## Existing patterns

Use the TMA package scripts, the existing fixture transport and browser flow,
the Gradle `integrationTest` task for `*IT`, and the previous TMA evidence as
historical evidence. `test` intentionally excludes `*IT`; it is not a valid
cryptographic integration proof for `TmaIT`.

## Acceptance criteria

- Integration TMA/shared tree parity is proven by an exit-code-zero diff.
- Existing frontend checks remain represented by the prior evidence without
  blind repetition when the code is unchanged.
- `TmaIT` runs under `integrationTest` with all eight tests passing.
- The local fixture flow and screenshot are recorded with the exact launch
  command and environment warning, if any.
- The branch ends with only the committed resume evidence and no product diff.
- Real Telegram host/session remains explicitly a gap.

## Verification

Record every new command, revision, exit code, environment and observable
evidence in `checks.json`; record scope, parity and runtime distinctions in
`evidence.json`; record the no-new-code diff in `diff.md`. Stop the local
preview after the browser check and confirm ports are free.

## Do not

Do not redesign or alter TMA code, change the backend contract, modify
`frontends/mobile-core`, repeat the full prior green suite without a reason,
claim fixture data is Telegram-signed, claim a live Telegram launch, expose
secrets, or escalate to Terra without a recorded defect/complexity gate.
