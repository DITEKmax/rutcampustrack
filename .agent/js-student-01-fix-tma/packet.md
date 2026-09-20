# Task packet — JS-STUDENT-01 FIX-r2 TMA

Date: 2026-09-06  
Risk: S2 (Telegram bootstrap and auth wire contract)  
Model/effort: `gpt-5.6-luna` / `max`  
Base revision: `cedce8c60aee04261ca87a18b898b148719bee89`  
Branch: `codex/js-student-01-fix-tma`  
Worktree: `.agent/worktrees/js-student-01/fix-tma`

## Goal

Fix the two actual high findings in the production TMA entry path:

- F1: load Telegram's official WebApp SDK before the application module so
  `window.Telegram.WebApp` exists in a real Mini App host.
- F2: send `POST /api/auth/tma` as the canonical JSON DTO `{ initData }` with
  `Content-Type: application/json`, while retaining memory-only access tokens and
  401 reauthentication through fresh Telegram init data.

## Context and evidence

- `frontends/tma-vue/index.html` currently has only the app module and does not
  load `https://telegram.org/js/telegram-web-app.js`.
- `frontends/tma-vue/src/telegram.ts` reads `window.Telegram.WebApp` during
  module bootstrap and `TelegramHost.start()` requires `initData`.
- `frontends/tma-vue/src/App.vue` currently posts raw `initData` as
  `text/plain;charset=UTF-8`; the fixture branch also bypasses the production
  serialization path.
- `services/auth-service/auth-api-contract/.../AuthApi.java:106-107` accepts
  `@RequestBody TmaAuthRequest`; its record contains one `initData` field.
- Existing Java TMA integration tests use the same DTO and remain outside this
  FE lane. A real auth-service run is a root-owned runtime gate.
- Official Telegram guidance requires the SDK script in `<head>` before other
  scripts and identifies `WebApp.initData` as the server-validation input:
  `https://core.telegram.org/bots/webapps`.

## Relevant scope

Own only `frontends/tma-vue/**` and isolated TMA tests/evidence under
`.agent/js-student-01-fix-tma/`. No changes to `frontends/mobile-core`, generated
types, lockfiles, shared workspace config, backend/API contract, Figma, deploy,
push, Telegram messages, or secrets.

## Required behavior

1. The official Telegram SDK URL is present in `<head>` before `/src/main.ts`.
2. Production and fixture auth use one JSON serialization/request helper with
   `{ initData }`, `application/json`, `Accept: application/json`, and
   `credentials: include`.
3. The fixture transport receives the same request init/body/header path as
   production; it remains a synthetic host and does not claim signed Telegram
   verification.
4. Access tokens remain only in the Vue ref; existing 401 reauthentication is
   preserved.
5. Missing host data and HTTP 401 produce clean errors.

## Acceptance criteria

- `frontends/tma-vue` typechecks, lints with zero warnings, and builds with
  `VITE_MOBILE_FIXTURE_MODE` unset.
- Regression tests fail on the old entry/auth behavior and pass after the fix:
  SDK ordering/static entry check, exact JSON wire body/content type, success
  token extraction, 401 failure, and missing-host failure.
- The diff contains only TMA code/tests plus this packet/check/evidence/summary.
- Local evidence explicitly distinguishes synthetic fixture verification from
  the pending real Telegram host and real auth-service runtime.

## Verification

- `npm run typecheck --workspace @rct/tma-vue`
- `npm run lint --workspace @rct/tma-vue`
- `npm run build --workspace @rct/tma-vue` with no fixture-mode variable
- `npm run test --workspace @rct/tma-vue`
- `git diff --check` for the stable branch diff
- Run the changed app locally on an isolated port and record the URL/process
  result; root runs the real auth backend/Telegram host gate separately.

## Do not

Do not add a new auth protocol, alter the Java DTO or gateway route, persist any
token/init data, hide missing-host/401 failures, redesign UI, or treat a fixture
host/mock response as Telegram signature or backend integration proof.
