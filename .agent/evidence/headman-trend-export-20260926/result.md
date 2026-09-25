# Headman trend export result

## Scope and acceptance

Implemented in worktree branch `codex/headman-trend-export-20260926`, based on frozen MAIN `ab764ac4a742d3b0f618a533e73888c5da44a514`. A current-group user must pass the existing `VIEW_STATS` check to export the authoritative selected trend as actual PNG or self-contained HTML. Tickets contain only bounded trend selectors and format; Gateway uses a fixed Attendance route. Existing report PNG-as-ZIP behavior, ticket size/TTL/MIME guards, and selectors remain unchanged.

## Evidence

- Product contract: `docs/wireframes/headman/113-headman-stats.md`, §§4.7 and 4.10. Existing `HeadmanStatsService.trend` supplies current-group context and rechecks `VIEW_STATS`; export calls that service again on redemption.
- Renderer regression verifies PNG signature and decodability, Cyrillic-capable render dimensions, escaped HTML, both series, period labels, numerator/denominator, 0–100 scale, and null-gap segmentation.
- Auth validation verifies graph PNG uses `.png`/`image/png` while the existing table PNG retains `.zip`/`application/zip`. Gateway regression verifies the fixed route, actual graph MIME, returned bytes, and absence of caller-supplied `groupId`.

## Diff

- Attendance adds the bounded export request, format catalogue, renderer, secured requerying export service, endpoint, and renderer/controller fixture checks.
- Auth adds the typed `HEADMAN_STATS_TREND` ticket selector, canonical binding, `.png` filename and kind-specific MIME while retaining existing kinds.
- Gateway adds the fixed typed dispatch and focused regression.
- Mobile-core adds PNG/HTML selection and PWA byte download; the screen uses the existing native download port when present and PWA direct download otherwise.
- TMA retains native ticket download and treats only the new graph PNG kind as `.png`; legacy PNG exports remain `.zip`.
- Exact inventory: `frontends/mobile-core/src/features/headman-stats/HeadmanStatsScreen.vue`, `frontends/mobile-core/src/features/headman-stats/headman-stats-client.ts`, `frontends/mobile-core/src/features/headman-stats/headman-stats-client.test.ts`, `frontends/mobile-core/src/shared/report-download-client.ts`, `frontends/tma-vue/src/report-download-adapter.ts`, `frontends/tma-vue/src/report-download-adapter.test.ts`; `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/ReportDownloadTicketDownloadFilter.java`, `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/ReportDownloadTicketDownloadFilterTest.java`; `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java`, `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/HeadmanStatsTrendExportRequest.java`, `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/HeadmanStatsTrendResponse.java`; `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java`, `HeadmanStatsTrendExportService.java`, `HeadmanStatsTrendFormat.java`, `HeadmanStatsTrendRenderer.java`, `ReportController.java`; `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRendererTest.java`, `ReportControllerMvcTest.java`; `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/IssueReportDownloadTicketRequest.java`, `ReportDownloadKind.java`; `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/ReportDownloadTicketValidationTest.java`; `.agent/evidence/headman-trend-export-20260926/contract.md` and `result.md`.
- The worktree started clean on the assigned branch; existing stash `d5ae138c068baf7d2f9413dbf13d1139f855e5f3` was left untouched. No MAIN files changed.

## Checks

- `npm run typecheck --workspace @rct/mobile-core`: exit 0.
- `npm run typecheck --workspace @rct/tma-vue`: exit 0.
- `npm run lint --workspace @rct/tma-vue`: exit 0.
- Scoped ESLint for the three changed mobile-core feature/client files and shared download client: exit 0.
- `git diff --check`: exit 0 (Git emitted only LF-to-CRLF advisories).
- Targeted Gradle batch, single-worker/no-parallel: Attendance renderer + existing MVC + `HeadmanStatsService` permission tests; Auth ticket validation; Gateway download-filter regression: final exit 0, `BUILD SUCCESSFUL` (1m13s). An earlier attempt stopped in `ReportControllerMvcTest` because its MVC slice lacked the existing `HeadmanStatsService` bean; adding the missing `@MockitoBean` to that fixture made the targeted rerun pass. No Docker or full suite ran.
- Full mobile-core lint: exit 1 from unrelated repository-wide warnings/errors (521 findings; includes unused `AssistantHomeworkScreen` in `shared/components/StudentFeatureOwner.vue`). The changed files pass scoped lint.
- Focused Vitest invocation could not reach tests: Vite/Vitest config discovery was denied while traversing an ancestor directory; exit 1. No frontend unit-test result is claimed.

## Runtime and limits

No live PWA/TMA session or backend runtime was started in this worktree. The backend focused tests exercise generated renderer bytes, ticket validation, and Gateway delivery. Root owns the next integrated runtime on its stand; end-to-end adapter behavior and live permission revocation remain for that run.
