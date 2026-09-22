# JS-ADMIN-08/09 — delivery packet

## Scope и риск

S2: один mobile-core API-клиент с auth-generation boundary, один экран семестров и две role-shell интеграции (PWA/TMA). Сервер уже содержит `GET/POST/PATCH /academic/semesters`; backend и существующий ADMIN map остаются вне diff.

## Compact contract

- **Goal:** ADMIN в PWA и TMA видит семестры, создаёт текущий период и переводит выбранный период в active с сохранением после reload.
- **Context/evidence:** `SemesterApi` отдаёт HATEOAS page и entity model; server create разрешает прошлую дату начала, отклоняет полностью завершённый диапазон и пересечения; server activate транзакционно деактивирует прежний active и активирует target.
- **Relevant scope:** `frontends/mobile-core/src/features/admin-semester/*`, shared generation-bound factory, PWA/TMA auth factories and shells, PWA ADMIN role admission.
- **Required behavior:** реальный `/api/academic/semesters` list/create; create ACK затем GET refetch; activation ровно одним `PATCH /{id}/activate` затем GET refetch; loading/empty/no-active/error states; map/semester admin navigation; stale responses fail closed after role/session generation changes.
- **Constraints:** прошлый `dateFrom` не блокируется в клиенте; `dateTo < dateFrom` блокируется до запроса; overlap и завершённость оставлены серверу; delete/archive, update, overlap preview и backend не входят.
- **Existing patterns:** `StudentApi` transport/error shape, `session-owner.ts` generation guards, `AdminMapScreen`, accepted mobile admin card anatomy and token/PCSS conventions.
- **Acceptance criteria:** два реальных role-client пути, no mocks in production code, server IDs/dates rendered from responses, persisted active visible after list reload, existing admin map remains reachable, no client deactivation sequence.
- **Verification:** frontend typecheck + scoped lint; focused API contract tests for paths/payload/one-PATCH/stale generation; no heavy Gradle/Docker runtime lease available in this checkout.
- **Do not:** broaden to admin CRUD, delete/archive, old Angular app, full suite, backend edits, generated contract edits, deploy or runtime infrastructure.

## Изменённые файлы

- `frontends/mobile-core/src/features/admin-semester/admin-semester-client.ts` — real gateway client, HATEOAS normalization, Problem Details, POST/PATCH transport.
- `frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue` и `admin-semester-screen.pcss` — list/create/activate UI and states.
- `frontends/mobile-core/src/features/admin-semester/AdminRoleNavigation.vue` и `admin-role-navigation.pcss` — map/semester navigation.
- `frontends/mobile-core/src/features/admin-semester/admin-semester-client.test.ts` — focused transport and generation contract checks.
- `frontends/mobile-core/src/shared/session-owner.ts`, `src/index.ts` — generation-bound factory and exports.
- `frontends/pwa-vue/src/auth.ts`, `src/role-flow.ts`, `src/role-flow.test.ts`, `src/App.vue` — ADMIN admission, generation-bound API, semester route and preserved map route.
- `frontends/tma-vue/src/tma-session.ts`, `src/App.vue` — generation-bound API and semester route.

## Sol FAIL3 correction batch

- `AdminSemesterClient.listSemesters` follows the server's `page.totalPages` metadata and requests every subsequent page with the same caller `AbortSignal`; it stops only at the reported last page and rejects inconsistent page numbers.
- `AdminSemesterScreen.refresh` owns a request revision and `AbortController`; stale responses cannot write semesters, error or loading state after a newer refetch, including the POST → refetch path.
- PWA `activateMapRole` receives the candidate generation from bootstrap/login/role selection and asserts it before invalidation and after `clearOwnerSnapshot` before mounting profile/client/view.

## Evidence и checks

| Check | Result |
| --- | ---: |
| `frontends/mobile-core: npm run typecheck` | 0 |
| `frontends/pwa-vue: npm run typecheck` | 0 |
| `frontends/tma-vue: npm run typecheck` | 0 |
| `frontends/mobile-core: npm run lint` | 0 |
| `frontends/pwa-vue: npm run lint` | 0 |
| `frontends/tma-vue: npm run lint` | 0 |
| `frontends/mobile-core: npx vitest run src/features/admin-semester/admin-semester-client.test.ts` | 0 — 5 tests, including multi-page list and shared abort signal |
| `frontends/pwa-vue: npx vitest run src/role-flow.test.ts --configLoader runner` | 0 — 3 tests |
| `git diff --check -- frontends/mobile-core frontends/pwa-vue frontends/tma-vue` | 0 |

## Runtime evidence и ограничения

Runtime не запускался: build/runtime lease находится в отдельном frozen checkout, а этот пакет не меняет backend. Source evidence подтверждает exact paths and server ACK/refetch; browser proof of ADMIN login, create/overlap error, activation and reload остаётся root runtime step. Foreign dirty/untracked `.agent`, docs, configs and frontend changes не трогались. Design delta recorded: archived mobile reference had a read-only semester list; current owner contract explicitly adds create and activate controls while retaining grouped cards and token/PCSS rules.
