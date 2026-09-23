# Teacher journal export — 2026-09-24

## Goal

Complete the teacher 122 subject-journal download flow so a teacher can choose DOCX, PDF, PNG pages ZIP, HTML, or XLSX and receive a server-generated file for the exact selected journal context.

## Context and evidence

- Frozen product decision: `docs/product/decisions/2026-09-23-teacher-replacement-and-group-attendance.md`. A teacher with an active current assignment to the group can read that group's full history; attachments and excuse details are outside export scope.
- Consumer source: `docs/wireframes/teacher/122-teacher-attendance.md`, including the five server-generated formats.
- Shared immutable report model and additive renderer target are in commits `47cc451c` and `9cd6c336` in this worktree. The renderer author's contract and its DOCX/PDF/all-page-PNG visual evidence are in `.agent/teacher-word-export-20260923.md`.
- Existing journal reads return at most 100 lesson IDs, scoped to one group/subject/type/semester. Export requests keep those guards and assemble full-semester output as bounded batches per lesson type.
- Existing `ReportService.getTeacherStats` validates current active-group authority and calculates exact selected-lesson metrics from completed, uncancelled lessons. Its `serverNow` is the shared export snapshot time; future lesson columns remain present with missing cells and no marks.

## Relevant scope

- Add teacher BFF routes for the server format catalogue and binary journal download, including no-store response headers, safe filenames/MIME validation, current group authorization and a 413 mapping for size limits.
- Add one teacher Attendance export RPC and service for the 122 subject-journal context only. Read selected-semester bounds from `ListTeacherAssignments(full_semester=true)`, use the current active group gate, preserve exact group/subject/type/period, and reuse guarded journal batches and exact-scope metrics.
- Render DOCX/PDF/all-page PNG ZIP through the shared Word renderer and document-renderer service; produce HTML/XLSX server-side. No browser-side document generation.
- Connect the shared teacher journal UI and client to the server format list and current-context download flow, with loading/error states and stale-context protection.

## Required behavior

- Use full semester bounds, including future lesson columns. Filter selected type(s) and subject on the server; do not accept arbitrary period overrides.
- Fetch full lesson history in batches of at most 100 while retaining the existing same-group/subject/type/semester journal guard. Cells for not-yet-started lessons remain absent so each renderer displays them blank; a real supplied cell with an empty symbol follows the existing `·` convention.
- Use counts, denominator, percentages, roster membership and the `serverNow` snapshot supplied by existing server-side calculations. Closed/uncancelled lessons determine metrics. A zero denominator is shown as no data/dash.
- Do not serialize attachment data, ticket identifiers, excuse reasons, comments, or attachment metadata. Return PNG as `application/zip` with a `.zip` filename and the catalogue label “PNG, архив страниц (.zip)”.
- Keep final export response at or below 20 MiB. DOCX passed to the conversion service is bounded below its 4 MiB gRPC request limit; PDF and PNG conversion can hit the renderer's default 4 MiB gRPC response limit. Such failures map to an actionable 413 and the UI suggests DOCX/HTML/XLSX rather than truncating.

## Constraints and existing patterns

- Authorization remains server-side at both BFF and Attendance boundaries. The Attendance export uses the validated signed teacher token to call the existing teacher Academic assignments RPC; the per-lesson reads go through existing `ReportService` authority checks.
- PDF and multi-page PNG reuse the existing DOCX document renderer. Existing PDF and single-PNG target semantics are unchanged.
- Shared UI uses the existing `URL.createObjectURL`/anchor download pattern from teacher excuse attachments. No public export URL or new host/navigation/auth adapter was introduced.
- No dependency, infrastructure, broad refactor, or extra reporting framework was introduced.

## Acceptance criteria

1. The API serves exactly the five supported format descriptors and returns a file with matching MIME, safe filename, and extension. **Implemented; focused UI/client, BFF and Attendance tests pass.**
2. The teacher 122 export uses the exact selected semester/group/subject/type scope and full semester bounds, rejects a group outside current teacher authority, and reads a >100-lesson selection in valid batches. **Covered by authorization and 100+1 batching tests.**
3. Future cells are blank, historical cells and exact-scope metrics are carried once, and zero-denominator output has no false attendance percentage. **Future-column and XLSX/Word renderer checks pass; service test uses a populated denominator.**
4. All five formats are server-generated; PNG contains every rendered page and keeps ZIP metadata. **DOCX/PDF/PNG are covered by the linked renderer/converter checks and visual evidence; HTML/XLSX are covered by service output checks.**
5. A context change during an export cannot download the previous context's response. **Implemented with a context revision check in the shared UI.**
6. Statistics, roster and weekly download consumers are not claimed complete. Only current teacher 122 subject-journal API/UI is connected; 112 weekly, 113/123 statistics, and 116 roster remain integration gaps.

## Verification

| Check | Result | Evidence |
|---|---|---|
| Static diff hygiene | PASS, exit 0 | `git diff --check`; only Git CRLF and denied global-ignore diagnostics were printed. |
| Frontend strict TypeScript | PASS, exit 0 | From `frontends/mobile-core`: `npm run typecheck`. |
| Frontend lint | PASS, exit 0 | From `frontends/mobile-core`: `npm exec -- eslint --max-warnings=0 src/features/teacher/TeacherJournalScreen.vue src/features/teacher/teacher-client.ts`. The first run reported one `vue/max-attributes-per-line` warning in the new label; it was corrected, and the targeted rerun passed. |
| Focused JVM checks | PASS, exit 0 | `gradlew.bat --system-prop=org.gradle.java.compile-classpath-packaging=true --no-problems-report --no-daemon --no-parallel --max-workers=1 --console=plain :services:attendance-service:attendance-app:test --tests '*TeacherAttendanceExportServiceTest' --tests '*TeacherAttendanceReadGrpcServiceTest' :services:mobile-bff:mobile-bff-app:test --tests '*TeacherReadFacadeTest' --tests '*MobileAttendanceClientErrorTest'`. `BUILD SUCCESSFUL` in 2m49s; 49 actionable tasks, 14 executed. XML results: Attendance export 3/3 and gRPC boundary 3/3; BFF facade 2/2 and error mapping 24/24; zero failures/errors/skips. |
| Intermediate compile attempt | Corrected; final check above passes | First targeted build exited 1 after 41s on two temporary authoring mistakes: `TeacherStatsResult` exposes `serverNow()`, and `teacherId` is required by the journal batch reader. Both were fixed before the successful run. |
| Existing compiler/runtime warnings | OBSERVED, unchanged | Test compilation printed existing Spring `ensureIndex` deprecation warnings in unrelated tests and the JVM class-sharing warning. They do not involve this change and were not modified. |
| Renderer sample | PASS, inherited evidence | `.agent/teacher-word-export-20260923.md`: a 30×36 DOCX sample rendered to a 14-page PDF/PNG sequence; all 23 visual pages were inspected. The 1,740,252-byte PNG ZIP stayed below the current default 4 MiB gRPC response limit. This validates the shared Word/PDF/PNG renderer path, not a live teacher API or TMA host. |
| Live application/TMA acceptance | NOT RUN | No live PWA/TMA host was exercised. Shared browser Blob download uses the existing attachment pattern; Telegram host behavior remains unverified. |

## Diff and limitations

This leaf's scoped changes are limited to:

- `frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue` — format selection, loading/errors, full-context download and stale-context guard.
- `frontends/mobile-core/src/features/teacher/teacher-client.ts` and `teacher-screen.pcss` — typed format metadata/file response, request validation, binary/MIME/filename checks, and small token-based layout additions.
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/TeacherApi.java` and `.../model/TeacherApiModels.java` — two export endpoints and format DTOs.
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherApiController.java`, `.../teacher/TeacherReadFacade.java`, and `.../grpc/MobileAttendanceClient.java` — authorization, download headers, RPC mapping and size error behavior.
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAcademicGrpcClient.java`, `.../grpc/TeacherAttendanceGrpcIdentity.java`, `.../grpc/TeacherAttendanceGrpcIdentityInterceptor.java`, `.../grpc/TeacherAttendanceReadGrpcService.java`, `.../grpc/DocumentRendererGrpcClient.java`, and `.../report/teacher/TeacherAttendanceExportService.java` — signed Academic call, scoped server export/data assembly, batching, renderers and bounded conversion.
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportServiceTest.java` and `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java` — full batching/future-cell/XLSX/authorization and actionable size-limit checks.

The separate dependency commits provide the shared model/proto and renderer/converter. This change does not connect other download points. The sample renderer check does not verify the live `TMAhost`; there is also no explicit receive-limit override on `DocumentRendererGrpcClient`, so the 4 MiB conversion-response bound is the gRPC default rather than an application-configured value. The server enforces the response/input bounds and reports an error without truncation.

## Do not

- Do not broaden the journal API or export authorization to attachments/reasons or to a non-current group.
- Do not claim all wireframe download points are wired, or claim a live Telegram host check.
- Do not change legacy PDF/single-PNG behavior, run a full test suite, push, deploy, or integrate directly into MAIN.
- Preserve foreign dirty changes in `.agent/orchestration-v2/RULES.md` and `.agent/orchestration-v2/LEAF-PACKET.md`; neither file is part of this leaf's changes.
