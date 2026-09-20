# Requests UI compact contract

Date: 2026-09-08 (Europe/Moscow)
Owner: `/root/requests_ui_implementation` (sole writer in this worktree)
Assigned model/effort: `gpt-5.6-luna / max`; no child agents.
Baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`, clean at task start.
Risk: S3 (five composed mobile screens, controlled transitions, state and accessibility matrix).

## 1. Goal

Implement the fresh compact Requests contract in `frontends/mobile-core/src/features/requests/`: five final mobile views and their controlled transitions for the E integrator. The feature must be usable at 390x844 and remain safe at responsive widths, with Russian `ты` copy, Onest, existing token bindings and PCSS.

## 2. Context / evidence

- Canonical final sources are the guarded local snapshots under `source/design-context/`: `4610-142` open requests, `4610-848846` archive, `4610-848937` request type, `4610-849007` excuse form, and `4610-849072` late confirmation. Root opened the five source PNGs and critical text originals before delegation.
- Missing canonical originals were copied read-only from `C:/Users/maksd/IntelliJIDEA/rutcampustrack` to `source/design-reference/` and `source/wireframes/` before UI work. Their hashes are in `source-sha256.json`.
- Authoritative request DTO source is read-only at `.agent/worktrees/requests-bff-contract/services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java`; feature maps DTOs through typed props/view models and does not call HTTP.
- Dated owner decision 2026-09-08: excuse selection is directly in the form (multiple by-day checkboxes); late check-in has a single lesson select field. This supersedes only the old selection-flow note.
- Existing mobile patterns: `TodayScreen.vue`, `MobileShell.vue`, `MobileBottomNav.vue`, `tokens.pcss`, and `shared/navigation.ts`. Existing shell remains controlled by the E host.

## 3. Relevant scope

Only these feature files may be authored, plus `.agent/student-requests-ui-c/**` evidence/source files:

`RequestsScreen.vue`, `RequestCard.vue`, `RequestTypeScreen.vue`, `ExcuseRequestScreen.vue`, `LateCheckinRequestScreen.vue`, `RequestLessonSelector.vue`, `RequestAttachmentField.vue`, `requests.pcss`, `types.ts`, `state.ts`, `state.test.ts`, `RequestsScreen.test.ts` under `frontends/mobile-core/src/features/requests/`.

The feature exposes typed props/emits for the integrator. Root/index/navigation, shared shell, API clients, generated types, tokens, lockfiles, configs and other features are outside scope.

## 4. Required behavior

- `RequestsScreen` renders controlled open/archive tabs, server-provided request cards, pending/archived status and authoritative actions. Cards show plain lesson lists; no lesson checkboxes.
- `RequestTypeScreen` renders the two final choices and a single Back transition. Direct Requests entry lets the user choose; Today/Attendance can provide a controlled prefilled lesson through the E adapter.
- `ExcuseRequestScreen` renders an inline `fieldset`/`legend` with server lessons grouped by day and checkboxes, reason/options, comment and file field, preserving draft state through own navigation. File limits/options/reasons come from props; local file checks are UX only.
- `LateCheckinRequestScreen` renders one lesson `select`, attempt/budget data and the inline gold confirmation panel with submit/cancel; it is not a modal.
- `RequestCard` and lesson selector never invent eligibility. Pending refs, server reasons, general unavailable text and authoritative states are displayed as supplied. Pending cancel emits intent; opening/archiving uses supplied state/decision.
- Submission busy/error are controlled props. No optimistic final success. Retry/uncertain ACK is delegated to the E adapter with the same idempotency key.
- Loading, empty, error, forbidden/access and offline states are typed and visible/announced. Nullable fields render safely. Expired attachment download actions are not created; actual open/download is an emit.
- Draft persists only within the feature-owned navigation/session generation and clears on owner/session-generation/logout reset, including file references. No offline writes or HTTP calls.

## 5. Constraints

- Vue 3 `<script setup>` + strict TypeScript; all public APIs in feature-local `types.ts` and no manual DTO/API client.
- PCSS in `requests.pcss`, no inline authored styles, utility frameworks or new dependency. Author sizes use `rem`; base is 16 CSS px (no html font-size override). Use scoped semantic/component vars mapped to existing tokens; source-derived local aliases are allowed only when recorded in evidence.
- Russian interface with `ты`, Onest, status symbol plus word, 44px touch targets, keyboard focus-visible labels/fieldset/status ARIA, reduced motion, light/dark preserving composition and no overflow.
- No Telegram/OTP/backend calls, secrets, deploys, live Figma writes, global changes, extra assets or shared-file edits.

## 6. Existing patterns

Use `MobileShell`/`MobileBottomNav` only when supplied by the host; do not duplicate a dock. Follow Today’s typed props/emits, `@mixin rct-focus-ring`, semantic `--rct-*` tokens, and existing icon assets where present. The E integrator owns root navigation, real host, transport and server projection.

## 7. Acceptance criteria

1. Five source compositions are recognizable and faithful at 390x844: open, archive, type, excuse form, late inline confirmation.
2. Direct and prefilled selection transitions are controlled; only excuse uses by-day multi-select, only late uses one lesson select.
3. Authoritative eligibility/options/reasons/file limits/budget/pending refs are reflected without client policy computation; noneligible and busy states block the relevant action and explain using supplied data.
4. Draft retention and purge behavior, cancellation intent, uncertain retry boundary and no optimistic success are observable in tests.
5. Loading/empty/error/forbidden/offline/busy states, keyboard/focus/labels/fieldset/status announcements, light/dark, reduced motion and responsive overflow are covered by code/tests/runtime evidence.
6. Scope diff contains only the declared feature files and `.agent/student-requests-ui-c/**`.

## 8. Verification

- Run applicable frontend `typecheck`, `lint`, targeted Vitest tests and the existing contract test command; record revision, command, exit code, environment and evidence in `.agent/student-requests-ui-c/checks.json`.
- Run a local Vite harness/fixture only in an unused strict port `18540` or `18541` after a free-port check. Capture actual mounted Vue screenshots for all five source states at 390x844; record runtime evidence and screenshot paths. Do not use ports 18500–18539, Gradle, full app or production backends.
- Compare runtime screenshots against the guarded PNGs and fix only in this scope. Root arranges a fresh independent Sol review before E import.

## 9. Do not

Do not create children; modify other worktrees or shared files; redesign the product or old desktop flow; add global Student API, routes, tokens, navigation, assets, lockfiles or config; call backend/Telegram/Figma; read secrets; claim full-app acceptance; or silently escalate to Terra. Any defect/complexity escalation requires a recorded request, reproduction, new evidence, correction, bounded scope and root decision.
