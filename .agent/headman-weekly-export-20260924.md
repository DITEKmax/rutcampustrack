# Headman weekly attendance export — 2026-09-24

## Scope and acceptance

Implement the headman weekly (wireframe 112 weekly) export flow in the assigned worktree only: server catalog for DOCX/PDF/PNG/HTML/XLSX, selection of server-provided week starts, and complete selected-week attendance matrices. Preserve live `VIEW_STATS` authorization, dated roster membership, exact lesson/student IDs, blank missing and future marks, cancelled-lesson exclusion, and the existing closed/history policy. No roster export, attachments, reasons, new summaries, truncation, or changes to teacher 122 behavior beyond the shared renderer's weekly no-summary shape.

Acceptance is one or more selected server weeks downloaded in each advertised format with matching scope and filenames; context changes invalidate an in-flight download; unauthorized or revoked assistants are denied. PDF/PNG derive from DOCX. PNG is a ZIP containing every page. HTML is escaped and tabular; XLSX has readable wrapped headers, explicit widths, numeric-compatible cell values, and frozen name/header panes.

## Changed paths

- API/service and bounded formats: `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java`, `.../dto/report/HeadmanWeeklyExportRequest.java`, `.../HeadmanWeeklyWeeksResponse.java`, new `.../HeadmanWeeklyExportFormatOption.java`; `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java`, `HeadmanWeeklyReportFormat.java`, `HeadmanWeeklyReportFiles.java`, new `HeadmanWeeklyTabularRenderer.java`, `ReportController.java`, `grpc/DocumentRendererGrpcClient.java`, `exception/GlobalExceptionHandler.java`, new `exception/ReportExportTooLargeException.java`.
- Shared attendance model/renderer weekly variant: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportModel.java`, `TeacherAttendanceExportService.java`, `TeacherAttendanceDocxRenderer.java`.
- Headman UI: `frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue`, `headman-journal-client.ts`, `headman-journal-screen.pcss`.
- Focused coverage updated: `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportServiceTest.java`, `HeadmanWeeklyReportControllerIT.java`, `ReportControllerMvcTest.java`, `.../teacher/TeacherAttendanceDocxRendererTest.java`; `frontends/mobile-core/src/features/headman-journal/headman-journal-client.test.ts`.

The worktree also contains pre-existing foreign edits to `.agent/orchestration-v2/RULES.md` and `.agent/orchestration-v2/LEAF-PACKET.md`; they are preserved and excluded from this package.

## Evidence and checks

- Frontend `npm run typecheck`: exit 0 (after conditional `AbortSignal` option fix).
- `npx vitest run src/features/headman-journal/headman-journal-client.test.ts`: exit 0, 1 file / 8 tests.
- Targeted ESLint for the three changed headman-journal files: exit 0.
- `git diff --check`: exit 0; only line-ending notices from Git.
- Initial targeted Gradle `:attendance-app:test` batch: exit 1; compile tasks passed, 22 tests ran and 4 failed. The batch incorrectly passed the `*IT` filter to `test`, which excludes integration-test classes. Findings: size-limit guidance mismatch; fixture expected a mark for a missing student/lesson cell; unused group stub; weekly DOCX page-break count still included the removed weekly summary. All four were corrected together.
- Corrected Gradle aggregate: unit task passed (19/19: service 7, MVC 8, DOCX renderer 4). The first IT attempt exposed that `HeadmanWeeklyTabularRenderer` was not registered as a Spring component. Added `@Component`; `AttendanceApplication` component scanning starts at `ru.rutcampustrack.attendance`.
- Corrected targeted IT rerun `:services:attendance-service:attendance-app:integrationTest --tests '*HeadmanWeeklyReportControllerIT'`: exit 0, 6/6 controller ITs passed. Spring MVC ran against Testcontainers MongoDB; upstream gRPC boundaries remain mocked. This verifies server route/auth/data assembly and binary response headers/payload stubs, not a deployed service.
- No live deployed runtime or real Telegram-host download evidence for this package. DOCX visual generation is inherited from the accepted renderer evidence; HTML/XLSX layout is structurally checked by the focused unit test, not opened in desktop Office/browser.

## Limits

The weekly request is bounded to 64 server-provided weeks and 5,000 scheduled lessons per week; exceeding bounds returns an error rather than silently truncating. The export response cap is 20 MiB; DOCX input to the renderer is capped at 4 MiB, and PNG also remains subject to the existing renderer client receive limit. PWA Blob download is implemented. A real Telegram host download adapter has not been validated, so TMA delivery remains unverified. Stats (113/123), roster (116), and 112 subject exports remain outside this implementation.

## Sol review correction

Sol found a medium file-integrity issue: U+000B in a historical roster name survived both DOCX and XLSX XML escaping and made the generated OpenXML part invalid. No stored names are rewritten. Both weekly XML escape paths now apply the XML 1.0 codepoint predicate already used by the teacher XLSX renderer before escaping markup characters. The regression creates a weekly model from a roster name containing U+000B, verifies the model retains the source name, and parses generated `word/document.xml` and `xl/worksheets/sheet1.xml` with JAXP.

Focused correction verification: `:services:attendance-service:attendance-app:test --tests 'ru.rutcampustrack.attendance.report.HeadmanWeeklyReportServiceTest.xmlRenderersRemoveXml10ForbiddenRosterControlsAndPreserveTheOtherText'` completed with exit 0 (one method). The first attempt had a test compile error from calling a package-private ZIP helper across packages; the test now uses its local ZIP-entry reader. No other checks were rerun.
