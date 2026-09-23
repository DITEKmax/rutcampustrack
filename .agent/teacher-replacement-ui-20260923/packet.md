# Teacher replacement UI packet

## Goal
Староста в общем PWA/TMA mobile-core экране предметов выбирает нового активного преподавателя и дату начала, отправляет одну durable replacement intent, безопасно возобновляет её после перезагрузки и видит обновлённые назначения только после подтверждённого `COMMITTED`.

## Context / evidence
- Канон: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA256 `1FF4F775373990D2FC1CDEB2DC525AB826929161BB0A4B679F69FE956428B39A`; текущий worktree имеет сохранённые dirty RULES/LEAF-PACKET, не редактировать.
- Проектный GO и состояние: main `.agent/orchestration-v2/CURRENT.md`, 2026-09-23.
- Реальный API оригинал в backend WT `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/map-usage-delivery-20260922/services/academic-service/academic-api-contract/.../AssignmentApi.java`, `ReplaceAssignmentRequest.java`, `AssignmentReplacementResponse.java` и `AssignmentController.java`.
- UI основы: `frontends/mobile-core/src/features/headman-subjects/{headman-subjects-client.ts,HeadmanSubjectsScreen.vue,headman-subjects-screen.pcss}`.
- Стабильная authenticated identity уже приходит в `HeadmanScheduleScreen` как `ProfileSnapshot`; durable storage scope использует только `profile.userId` из `frontends/mobile-core/src/features/profile/profile-types.ts`, никогда `sessionId`, access token или secret.
- Figma/design inputs: `docs/wireframes/headman/116-1-headman-subjects.md`, `docs/design/COMPONENT_REGISTRY.md`, `docs/design/brandbook-v2.md`, `docs/design/tokens-v2.json`, `docs/design/mobile/RutCampusTrack_PWA_TMA_UX_Guide.md`; ПК-116.1 wireframe относится к web desktop, поэтому reuse только layout/labels, а мобильное состояние следует принятому mobile guide и текущей shared PWA/TMA screen.

## Relevant scope
- Base/worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/pwa-install-delivery-20260923`, initial HEAD `1bbb52b02f55ada16183b28107dc41158311f01e`.
- Sole writer: назначенный Luna max leaf; backend/proto owned by `replacement_finish_gpt6` in separate WT.
- Product paths: `frontends/mobile-core/src/features/headman-subjects/headman-subjects-client.ts`, `HeadmanSubjectsScreen.vue`, `headman-subjects-screen.pcss` и минимальная identity plumbing в `frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue`. PWA/TMA `App.vue` уже передают `ProfileSnapshot`; они не менялись.
- Evidence paths: только `.agent/teacher-replacement-ui-20260923/`.
- Нет main integration, commit, push, deploy.

## Required behavior
- `POST /academic/assignments/{id}/replace` body `{ replacementTeacherId: string, effectiveFrom: ISO date, requestKey: UUID }`.
- `GET /academic/assignments/replacements/{operationId}`; consume the same `AssignmentReplacementResponse` shape. Known lifecycle values: `PREPARED`, `APPLIED`, `COMMITTED`; unknown state is never success.
- Use existing active teacher search, preserve session-generation guards, persist a stable request key for retry of the same intent; changed target/date starts a new key only after previous intent is resolved/rejected.
- Persist recovery records separately by authenticated `actorUserId`, group, and request key. Restore only the current actor/group, ignore the old group-only key without migration, and remove only the exact validated request record so parallel tabs retain their own intents. Without an identity, don't restore or send a durable replacement request.
- Show progress, loading, network/error, conflict, 403/denied and pending durable operation states. Reopened UI may safely resume by GET when operation id exists or idempotent POST with the persisted same intent/key when only the response was lost.
- Only a `COMMITTED` response may lead to user success, and first refetch the existing subject/assignment data. Never optimistic mutation or permission expansion.

## Constraints
- Shared PWA/TMA core only; keep Telegram APIs out of domain code, retain online/read-only/denied guards and current session owner generation protection. On identity change, invalidate old callbacks and clear old in-memory data before loading the new scope.
- Use existing teacher search and existing PCSS tokens. PCSS authored lengths in rem; no framework, new tokens, or redesign.
- Backend owns authorization, lifecycle/history, schedule and operation rules.
- Do not alter any existing dirty file or prior PWA feature files.

## Existing patterns
- Current screen/client: subject/semester loading, search result revisions, `loadRevision`, `mutationRevision`, `disposed` and `HeadmanSubjectsApiError` handling.
- Session protection: `frontends/mobile-core/src/shared/session-owner.ts` and generation-bound subject API factory in the client module.
- Mobile control size/focus/theme tokens from `frontends/mobile-core/src/styles/tokens.pcss`.

## Acceptance criteria
- A user can open replacement for a current assignment in the active semester, search/select a teacher, enter date and explicitly submit.
- The real typed REST client posts the frozen request and queries operation status; stable `requestKey` is retained across uncertain retry/reload; changed intent cannot reuse a prior key.
- `PREPARED`/`APPLIED`/unknown remain visibly pending; `409` conflict and `403` denied are visible; unknown lifecycle states never announce success.
- Confirmed `COMMITTED` causes a real refetch; user success appears only when the refetch succeeds. Existing actions and APIs remain headman-authorized and online-only.
- Actor A cannot restore/replay actor B's records; two distinct request keys for the same actor/group coexist across tabs, and completion removes only its own exact record. Reload retry reuses the existing request key. Identity change drops old async results.
- One host Vue TypeScript check passes for the mobile-core package. Product behavior is source-ready; shared backend runtime is pending parent integration.

## Verification
- One host Vue TypeScript check; `git diff --check`; bounded source-flow inspection of actor switching, two independent request keys, exact-record removal, and same-key reload retry. No new test framework or broad suite.
- Runtime evidence pending: root/backend lane owns shared runtime. No mocks presented as acceptance, no Docker/Gradle workload.
- Record source evidence, final diff/inventory, and known limits in `implementation-evidence.md`.

## Do not
- Do not spawn children, broaden design/API/access rules, mock the real path, add wiring tests/full suites, edit backend/proto, alter protected/docs-routing files, integrate/commit/push/deploy, or use Terra.
- WARN/ERROR changes code only after request linkage and reproduction; preserve unrelated dirty files.
