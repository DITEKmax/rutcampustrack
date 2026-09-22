# Контракт пакета ADMIN group registry/create drafts

Base revision: `351817c04db7374cd39a63a642392eda854f3634`.
Rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA `SHA4E05153BFAE604D6882805D37DD89011CCC7301B4641DEF3FFDCEB22D9D6F364`.
Sole writer: `/ .agent/worktrees/map-usage-delivery-20260922`; MAIN and foreign WIP stay untouched.

## Goal

ADMIN создаёт группу с отдельными буквенным и цифровым кодами и обязательным
сроком обучения, после reload видит серверный реестр с вкладками ACTIVE, DRAFT и
ARCHIVED, поиском, пагинацией и счётчиками. Созданная без старосты группа имеет
серверный статус DRAFT и причину.

## Context/evidence

- `docs/wireframes/admin/133-admin-groups.md` и
  `docs/research/reference-rutcampustrack-design/knowledge/job-stories.md`: код
  разделён, имя собирает сервер, срок обязателен, группа без старосты — черновик,
  статус/причина/счётчики не вычисляются клиентом.
- Решение владельца 2026-09-22: текущий курс определяется первой цифрой
  цифрового кода; отдельное поле `currentCourse` не принимается.
- Existing `GroupService` сохраняет coverage marker вместе с созданием группы;
  этот порядок остаётся одной транзакцией.
- Existing `user_role_grants` является авторитетным источником активной роли
  HEADMAN и STUDENT; `users.is_headman` не используется как обход авторизации.

## Relevant scope

- Academic: V31 additive schema, `Group`/DTOs/repository read adapter/assembler/
  service/controller/API contract, server status and row enrichment.
- Mobile core: typed ADMIN groups client, generation guard, shared feature screen
  and PCSS, navigation export.
- PWA/TMA: minimal ADMIN groups client ownership, route state and screen mount in
  existing app/session owners.

## Required behavior

1. `POST /api/academic/groups/registry` accepts
   `{alphabeticCode,numericCode,trainingDurationYears}`. The server validates
   Cyrillic alphabetic code, three-digit numeric code, known program-type digit,
   positive duration, and `currentCourse <= duration`; it builds the canonical
   name from the two code parts. The course is always numeric-code digit one.
2. Duplicate code pair returns 409 among active groups; archived code pairs may
   be reused because archival retains its identity and sets `is_active=false`.
   The database partial unique index remains the final concurrency guard.
   Creation writes the group and current-semester history coverage marker
   atomically. The new group has no headman and therefore DRAFT.
3. `GET /api/academic/groups/registry` is ADMIN-only, accepts `status`, `search`,
   `page`, and `size`, and returns rows plus total elements/pages and all three
   status counts. ACTIVE/DRAFT are distinguished by an active HEADMAN grant;
   ARCHIVED uses the group lifecycle flag. Student count and headman FIO come from
   server-side batch SQL, never client-side N+1 calls.
4. Legacy groups remain readable. Their duration and split code are `KNOWN` only
   when one canonical predicate accepts the name (type 1, courses 1..4; type 7,
   courses 1..2). Invalid or contradictory names, including course 0, an
   over-duration course and an unknown type, stay nullable with
   `LEGACY_UNKNOWN`. No migration invents a duration, archives a group, or
   removes history. Legacy name parsing is only a read fallback.
5. PWA and TMA use one shared client/screen and generation-bound session owner;
   after create the screen selects DRAFT, resets page/search, and refetches the
   created row and counts.

## Constraints

- ADMIN auth is enforced at the controller and existing gateway/session layer.
- Keep old `POST /academic/groups`, PUT, and existing promote/archive writers
  source compatible. All code-changing writers use the same canonical storage
  helper so name, split code, course and saved duration remain synchronized;
  promotion respects an existing explicit duration and PUT deactivation uses
  the existing archive writer. This is compatibility correction, not new UI.
- No new promotion UI/workflow, delete, export, headman assignment, password
  challenge, transport redesign, lockfile/dependency change, or production data
  operation.
- Keep existing history coverage and current-semester preconditions. Preserve
  all foreign worktree/Main changes.

## Existing patterns

- `GroupService` owns transactions and `GroupHistoryCoverage` writes.
- `GroupRepository`/`GroupSpecifications` own existing CRUD lifecycle reads;
  an additive `GroupRegistryReadRepository` uses parameterized SQL for status,
  counts, page and headman/student projection.
- `GroupService.listAdminGroups` keeps page, total and tab counts in one
  `REPEATABLE_READ` read-only transaction.
- `AdminUsersClient`/`AdminSemesterClient`, `session-owner.ts`,
  `AdminRoleNavigation.vue`, token/PCSS patterns are the frontend baseline.

## Acceptance criteria

- A real ADMIN request creates `ABC-311` (server response exposes the separate
  fields, name and duration) and the same group appears in DRAFT after refetch,
  with reason and count.
- Repeating the code pair produces 409; invalid code/duration is rejected before
  persistence; non-ADMIN access is denied.
- Existing group history coverage remains intact and concurrent duplicate create
  cannot create two active rows; an archived pair remains reusable.
- Registry pages return correct ACTIVE/DRAFT/ARCHIVED counts, search and stable
  pagination; a headman grant changes the computed status on the next read.
- Existing PUT/promotion/archive paths preserve synchronized code fields and
  archived history identity; invalid legacy combinations stay nullable/
  `LEGACY_UNKNOWN`.
- PWA and TMA compile with the new navigation/client/screen wiring; stale session
  generations cannot publish an old registry result.

## Verification

- Targeted backend unit/integration checks for validation, duplicate/auth status,
  draft projection, archived reuse, legacy invalid fallback, PUT/promotion
  synchronization, and atomic coverage where existing harness allows; no full
  suite before a heavy lease.
- Frontend mobile-core/PWA/TMA typecheck and focused client normalization checks
  using existing dependencies; no `npm ci` or lockfile changes.
- Record every executed command, environment, exit code, and evidence in the
  package summary. Runtime/live ADMIN acceptance remains root-owned.

## Do not

- Do not add promotion UI/workflow, delete/export flows, headman assignment,
  student roster management, or broad Group CRUD redesign. The bounded existing
  writer synchronization described above is in scope.
- Do not infer duration for legacy rows when program type is unknown, trust a
  client-computed draft status/count, or use `users.is_headman` as authoritative.
- Do not touch MAIN, foreign unstaged WIP, generated common API artifacts, or
  push/deploy/production data.
