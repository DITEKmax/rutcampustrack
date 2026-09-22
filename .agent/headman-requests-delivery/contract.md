# JS-HEADMAN-05/07/43/44 — compact contract

Revision baseline: `fc7f649ce12e04e1c2bbe45cc096f0d97b68ae20`
Rules source: `.agent/orchestration-v2/RULES.md`, SHA `SHA4E05153BFAE604D6882805D37DD89011CCC7301B4641DEF3FFDCEB22D9D6F364`
Scope owner: MAIN product writer `/root/headman_requests_delivery`; runtime d6 is frozen and owned by root.

## Goal

Deliver one authenticated headman queue for `EXCUSE` and `LATE_CHECKIN` requests in PWA/TMA. A headman can inspect the real ticket, lessons and retained attachment descriptors, then approve or reject the whole ticket. A rejection requires a non-blank reason. Archive supports server-side type, student-name and coverage-overlap date filters. A decision is persisted by the existing canonical attendance transaction and is visible after refetch in the student request read model.

## Context / evidence

- Accepted product references: `docs/research/reference-rutcampustrack-design/knowledge/job-stories.md` (JS-HEADMAN-05/07/43/44, around lines 470–488), `docs/wireframes/headman/119-headman-tickets.md`, and the mobile request sections of `docs/design/figma-spec-mobile.md`.
- Existing transport is split across `ExcuseApi` and `LateCheckinApi`; a separate unified server-paginated read model is required.
- `StudentRequestService` already owns authenticated decision rules, pair locking, event publication, attachment lifecycle and student reads. The new headman adapter must call these canonical decisions.
- Existing legacy `ExcuseService`/`LateCheckinService` write through `AttendanceWritePort`; that port currently overwrites `PRESENT`. `PRESENT` must survive an excuse/late decision, including bot/canonical paths, while manual attendance marking remains unchanged.

## Relevant scope

- Attendance API contract and app adapter for `/attendance/requests` (gateway exposes `/api/attendance/requests`).
- Headman read/query projection over `excuse_tickets` and `late_checkin_requests`, including lazy lesson and attachment detail.
- Additive persisted late decision comment so the unified reject reason is visible in archive/detail.
- Shared `mobile-core` headman request client/feature, mounted from existing PWA/TMA headman session/navigation generation owners.
- Focused backend tests for group isolation, rejection validation, coverage overlap, `PRESENT` preservation and decision/refetch persistence; focused frontend client/feature checks.

## Required behavior

### Read contract

`GET /attendance/requests?bucket=OPEN|ARCHIVE&page=0&size=20&type=EXCUSE|LATE_CHECKIN&studentName=<substring>&coverageDateFrom=YYYY-MM-DD&coverageDateTo=YYYY-MM-DD`

The authenticated headman's group is the only scope. No group/student id supplied by the client is trusted. `OPEN` contains pending tickets; `ARCHIVE` contains terminal tickets and is sorted by decision time. Date filters match coverage overlap (`coverageEnd >= from && coverageStart <= to`), never `submittedAt`.

Page content includes `id`, `kind`, `status`, `studentName`, `reason`, `comment`, `coverageStart`, `coverageEnd`, `lessonCount`, `alreadyMarkedCount`, `hasAttachments`, `createdAt`, `updatedAt`, `decisionBy`, `decisionAt`, `decisionComment`. Lesson snapshots and attachment descriptors are not expanded in the page.

`GET /attendance/requests/{id}` returns the same summary plus the persisted lesson rows (`lessonId`, subject/name/type, date, number, current attendance status/source, and whether the lesson belongs to the ticket) and retained attachment descriptors with a download URL. It is lazy from the mobile client.

`GET /attendance/requests/{id}/attachments/{attachmentId}` reuses the existing attachment lifecycle and headman authorization. It never exposes a file from another ticket/group.

### Decision contract

`POST /attendance/requests/{id}/decision` body:

```json
{"decision":"APPROVED"|"REJECTED", "reason":"optional for approval; required and trimmed for rejection"}
```

The server validates the authenticated headman and derives the target group from the persisted ticket. The ticket is indivisible: one decision covers every lesson in an excuse ticket, or the single late-checkin lesson. The server re-reads status under the canonical pair transaction; the client preview is informational only. `PRESENT` rows are never overwritten by a decision. Response is the persisted detail/summary after the transaction so a subsequent GET and student refetch show the terminal decision and comment.

## Constraints

- Preserve student/teacher rights and existing manual attendance marking. Do not add a second attendance writer or BFF/gRPC layer.
- Reuse `StudentRequestService` for decisions and existing attachment authorization/lifecycle. Existing bot decision behavior remains available; the `PRESENT` guard applies to the same canonical/legacy decision write paths.
- Exact group isolation from authenticated headman and no blind trust of path/query group or student IDs.
- PWA and TMA use the shared mobile-core client/feature and generation guards; no duplicate transport logic.
- No notification overhaul, unrelated CRUD, lockfile/dependency update, deploy, push or production data operation.

## Existing patterns

- REST contracts live in `attendance-api-contract`; app controllers implement them and obtain `RequestContext` identity before calling domain services.
- `StudentRequestService.Identity`, `PairWriteCoordinator`, `RequestAttachmentRepository`, `RequestAttachmentDocument` and the existing student union read model are the canonical domain boundaries.
- Mobile API clients use generation-bound owners in `frontends/mobile-core/src/shared/session-owner.ts`; PWA/TMA create clients during headman role activation.
- Mobile UI uses the accepted dark tokens/PCSS and nested `more/...` routes in `HeadmanScheduleScreen`.

## Acceptance criteria

1. A headman sees one server-paginated queue containing both request kinds; a non-headman and another group's headman receive the existing authorization error and cannot infer foreign rows.
2. Page filters for kind, FIO substring and coverage overlap are applied server-side; archive does not use `submittedAt` for the date range.
3. Opening a card fetches real lesson and attachment details; no mock/fixture data is required in production mode; attachment download is scoped.
4. Approval/rejection sends one whole-ticket command. Blank rejection is rejected before mutation and the stored rejection reason is visible after refetch.
5. Existing `PRESENT` survives approve for both old/controller and bot decision entry points, including pair-writer concurrency; manual marking can still change attendance through its own path.
6. A successful decision remains visible in the unified archive/detail and in the existing student request read after a fresh GET.
7. PWA/TMA headman session swaps invalidate the old client generation and do not let a stale response mutate the new role view.

## Verification

- `git diff --check` and targeted source/type checks.
- Attendance focused tests: existing `ExcuseServiceApproveIT` updated to assert `PRESENT` preservation; new/extended decision tests cover rejection reason, group isolation, date overlap, and pair race. Record command and exit code in `.agent/headman-requests-delivery/checks.json`.
- Mobile-core focused client/feature checks and PWA/TMA type/build checks where available; no full-suite campaign.
- Runtime d6 stays untouched. Root owns integrated runtime acceptance; source evidence must identify revision, command, exit code and any runtime limitation.

## Do not

- Do not alter admin-semester, teacher, map or unrelated current App behavior.
- Do not implement client-side group/student filtering or use preview counts as a mutation filter.
- Do not rewrite the attendance transaction, add a parallel writer, weaken authorization, or claim runtime/full acceptance from source checks alone.

## Root decisions recorded

- Root accepted the additive nullable `decisionComment` field on `LateCheckinRequest`; historical and bot records with null remain readable. The new unified reject command requires a non-blank reason.
- Root confirmed that this package enters through the existing full headman authority. Existing assistant `manage_excuses` delegation remains independent and is preserved; this package does not add a global headman barrier or revoke that path.
