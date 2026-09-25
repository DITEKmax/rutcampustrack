# Scope packet — TMA report download frontend

## Goal

Teacher and headman / `VIEW_STATS` assistant select an existing report and download it through Telegram's native `downloadFile` capability in TMA. PWA continues to download the existing Blob and filename without issuing a ticket.

## Context/evidence

- Frozen source: `e185dc1982eadf00c909283459ce2c2b1e1510b9`.
- Assigned clean worktree: `.agent/worktrees/teacher-export-ui-20260924`, branch `codex/tma-download-frontend-20260925`.
- Existing teacher journal/stats and headman weekly export paths use the current UI queries and Blob downloads.
- Backend owns the ticket DTO. Frozen kinds are `TEACHER_JOURNAL`, `TEACHER_STATS`, `HEADMAN_WEEKLY_CURRENT`, `HEADMAN_WEEKLY_SELECTED`; root's follow-up adds the typed `HEADMAN_STATS` selector in the shared boundary only.
- Current mobile design originals were read from the main checkout: `BRAND_DIRECTION.md`, `A11Y_REQUIREMENTS.md`, wireframes 112, 122, 123. The assigned frozen source also contains `COMPONENT_REGISTRY.md`, `brandbook-v2.md`, `tokens-v2.json`, and `figma-spec-mobile.md`. No design/layout change is planned.

## Relevant scope

- Sole writer in this worktree, based on the exact frozen source above.
- Product files: `frontends/mobile-core/src/features/teacher/teacher-client.ts`, `TeacherJournalScreen.vue`, `TeacherStatsScreen.vue`, `frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts`, `HeadmanJournalScreen.vue`, `frontends/mobile-core/src/shared/report-download-client.ts`, `frontends/tma-vue/src/telegram.ts`, and one TMA report-download adapter module.
- Focused security tests may be added alongside the shared boundary / TMA adapter.
- Root retains final application/session wiring ownership until the stats package is stable and explicitly transfers it.

## Required behavior

- Issue authenticated `POST /api/auth/report-download-tickets` with exactly one typed selector matching `kind`; formats are lowercase `docx`, `pdf`, `png`, `html`, `xlsx` (`png` downloads as ZIP).
- Reuse the exact current teacher journal selector, teacher stats scope/filter/sort order, and selected weekly dates. A sole selected current week maps to `HEADMAN_WEEKLY_CURRENT`; other explicit week selections map to `HEADMAN_WEEKLY_SELECTED`.
- Include `HEADMAN_STATS` DTO in the shared request union only, with optional subject/lesson types/sorts/filters and no group, semester, or page fields. Do not edit that screen/client.
- A generation-bound client checks current session after each asynchronous boundary. The TMA adapter checks both session and the screen's query generation after ticket issuance and immediately before calling the host. A stale report request must not call `downloadFile`.
- Preserve the existing session owner's unauthorized refresh flow: on the first 401 only, call `refreshFor(generation)` once with generation guards before and after, then retry once using the refreshed token. Do not retry 400, 403, 409, 429, 503, or a second 401.
- Native mode does not download a Blob first. Only a fixed relative `/api/report-download/<43-char opaque token>` path is accepted; build an HTTPS URL from the app's own origin. Reject query, fragment, credentials, path tricks, and arbitrary hosts. Keep the ticket, init data, and bearer only in request memory; never persist or log them.
- `downloadFile` acceptance means user consent, not proof that Telegram saved the file. Cancellation is not an error. Hosts without the method show a clear unsupported state. Ticket HTTP 400/401/403/409/429/503 errors remain visible.
- PWA stays on its existing Blob/filename path and never requests a ticket.

## Constraints

- No Terra, child agents, push, deploy, production migration, secret reads, backend changes, or modifications outside the assigned file inventory.
- Do not edit `App.vue`, `main.ts`, `mobile-core/index.ts`, shared host/navigation, `StudentFeatureOwner`, headman-stats files, or current session owners until root transfers ownership.
- Do not redesign the UI, change filters, order, calculations, or existing export choices. Avoid new PCSS unless the current semantics cannot express the required status.
- Change existing code for WARN/ERROR only after connecting the evidence to this request and reproducing the defect.

## Existing patterns

- Teacher/headman API clients already carry `assertCurrent` and preserve session generation.
- Teacher stats uses a canonical structured query; its transport emits ordered `sort` and `filter` parameters. Weekly export options already provide ordered selected `weekStart` dates and server formats.
- Existing screens own the PWA Blob, temporary object URL, loading, error, and status state.
- Telegram integration is at the TMA host edge; shared features receive a typed optional port.

## Acceptance criteria

- Teacher journal, teacher stats, current-week, and selected-week TMA paths use the native adapter with the exact selected report parameters and a fresh request/session guard.
- `HEADMAN_STATS` has a correctly typed selector available to the future screen owner without screen/client wiring in this package.
- PWA still uses its existing Blob/filename export path; native mode never downloads that Blob first.
- URL validation, stale query/session rejection, callback consent/cancellation, and old-host unsupported behavior have meaningful focused security coverage.
- Targeted TypeScript checks pass. Real Telegram runtime evidence is recorded separately; source/test evidence is not represented as a genuine host save.

## Verification

- Run the affected `mobile-core` and `tma-vue` TypeScript checks.
- Run focused tests for ticket URL trust boundary, stale request/session guards, and native result semantics.
- Run the existing local TMA page flow only if a suitable runtime can be started without heavy services. Record genuine Telegram runtime as pending when the real host is unavailable.
- Record exact command, exit code, revision, environment, output/log path, diff inventory, and limitations under this evidence directory.

## Do not

- Do not silently fall back to arbitrary or external URLs, Blob downloads in native mode, or false success on callback acceptance.
- Do not include report ticket paths or credentials in logs, analytics, storage, screenshots, or evidence artifacts.
- Do not claim integration or live Telegram acceptance before root wires the port and validates the genuine host.
