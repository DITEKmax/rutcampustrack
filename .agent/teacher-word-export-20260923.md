# Teacher attendance Word export

## Goal

Provide reusable, readable Word attendance layouts for a teacher-selected lesson set, with the DOCX as the source layout from which PDF and a complete PNG-page archive are produced.

## Context and evidence

- Accepted product references: `docs/wireframes/headman/112-headman-attendance.md`, `113-headman-stats.md`, `116-headman-group.md`, `docs/wireframes/teacher/122-teacher-attendance.md`, and `123-teacher-stats.md`.
- Owner authority: `docs/product/decisions/2026-09-23-teacher-replacement-and-group-attendance.md`; a current active teacher assignment to the group authorizes reading the group journal, including previous lessons. The exporter contract supplies the authorized rows, columns, and metrics.
- Frozen model and transport dependency: `TeacherAttendanceExportModel` plus renderer target contract from shared commit `47cc451c` (present in this assigned worktree as `b8c1ee36`). This leaf does not change the model, proto, generated classes, or API/data assembly.
- Existing implementation pattern: `report/DocxRenderer` packages a DOCX resource and safely substitutes XML content; headman weekly export shares the document-renderer client and Office-to-PDF/PNG service.

## Relevant scope

- Implement two layouts over the same attendance matrix: `WEEKLY_ATTENDANCE` (one server-selected week per model; optional caller-ordered model batch) and `SUBJECT_JOURNAL` (one server-selected subject/period model).
- Current consumer is teacher wireframe 122 and sends a single subject-journal model. Weekly rendering is ready for later callers that pass already bounded weekly models.
- Wireframe consumer map: 112 weekly attendance is a weekly matrix consumer; 113 group/subject statistics needs a separate statistics template; 116/116-1 roster and subject-composition views need separate roster templates; 122 is the current subject journal; 123 student/group statistics needs separate statistics layouts. Statistics and roster exports remain integration gaps, not completed work in this scope.

## Required behavior

- Preserve every supplied student row and every supplied lesson column in order. Matrices split into sequential landscape segments of at most six lesson columns; the student-name column and table header repeat in each segment, and all table rows remain repeatable across vertical pages.
- Print group, resolved subject when present, period/week bounds, selected type labels, lesson date/time/number/subject/type/state, and all supplied attendance symbols. Show `+`, `у`, and `н` as supplied; an absent `cellsByLessonId` key stays blank.
- Match the teacher journal's existing cell convention: when a supplied `Cell` exists but its symbol is blank, show `·`; a missing lesson key still stays blank.
- Display all server-supplied counts, denominator, and percentages. Do not recalculate metrics. With denominator zero, render percentage cells as `—`.
- Weekly models use `periodFrom`/`periodTo` verbatim and are never regrouped by calendar or assigned an academic week number. The batch preserves caller order and starts each model on a new page.
- Translate known lesson states (`PLANNED`, `ACTIVE`, `CLOSED`, `CANCELLED`) to Russian labels; unknown states are omitted. Do not print raw IDs, excuse details, ticket links, comments, or attachments.
- PDF and `PNG_PAGES_ZIP` are converted from the rendered DOCX. The new archive target must contain every PDF page in numeric order and must not alter legacy PDF or single-PNG behavior.

## Constraints

- The renderer consumes only the frozen DTO. It does not fetch records, determine access, sort rows/columns, compute week boundaries, or derive new denominator semantics.
- Common model/proto/generated/client files are owned by the export author. Only this worktree is modified; the accidental earlier MAIN hunk was removed and the exact MAIN target file was verified with empty status/diff.
- This leaf held the shared HEAVY lease for one focused Gradle batch and isolated visual conversion, then released it to the export author after both processes exited.
- The bundled document skill authoring marker succeeded before creating the two source DOCX templates. `render_docx.py` was attempted but exited 1 because Windows has no `soffice.exe`; the approved prebuilt Linux renderer image provided the same LibreOffice/Poppler rendering path successfully, with no install or image rebuild.

## Existing patterns

- `TeacherAttendanceDocxRenderer` reuses the package-replacement strategy from `DocxRenderer`, including placeholder validation and preserved OOXML package entries.
- `OfficeDocumentConverter.convertToPdf` remains the common DOCX-to-PDF path. `convertToPngPagesZip` adds all-page rasterization with a deterministic numeric filename order; existing `convertToPng` remains a single-page PNG.

## Acceptance criteria

1. Both Word resources expose explicit bound placeholders and render to complete DOCX packages.
2. Subject-journal output preserves every row and lesson; both an 8-column split fixture and a 30-student × 36-lesson full-term fixture cover long Cyrillic names/subject labels, supplied marks, and server-provided summary values without IDs.
3. Missing cells render blank; denominator-zero percentages render as dashes; unknown lesson state values are not exposed as raw state codes.
4. A weekly batch preserves the server-provided periods and order and creates a page boundary per model without deriving a week number.
5. A 12-page PDF conversion fixture yields 12 numerically ordered PNG entries in the ZIP; a missing page fails instead of returning an incomplete archive. The gRPC response uses `application/zip` and `.zip`.
6. Render populated subject-journal and weekly DOCX files, including a 30 × 36 full-term sample, through the available LibreOffice/Poppler image and inspect every PNG page at original size; correct clipping, missing glyphs, broken tables, or overflow before claiming layout acceptance.

## Verification

| Check | Status | Exit code / evidence |
|---|---|---|
| MAIN incident audit for `DocumentRendererGrpcServiceImpl.java` | PASS | `git status --short -- <file>` empty; `git diff -- <file>` empty in MAIN, after targeted removal of the accidental hunk. |
| Assigned-WT base and protected foreign changes | PASS | Branch `codex/teacher-word-export-20260923`; shared dependency commit `b8c1ee36`; foreign `.agent/orchestration-v2/RULES.md` and `LEAF-PACKET.md` remain modified and untouched by this leaf. |
| Template authoring marker | PASS | Bundled `mark_artifact_operation_started.mjs` invoked once with `create`, expected output count `2`, output format `docx`; bundled Python `python-docx` version `1.2.0`; builder exit code `0`. |
| Static diff check | PASS | `git diff --check`, exit code `0` for tracked edits. New Java files still need compilation by the coordinated Gradle check. |
| Template OOXML structure | PASS | Bundled Python ZIP/XML read-only check, exit code `0`; both DOCX packages parse, and scalar/structural placeholders are present. |
| Focused Gradle checks | PASS | Exit `0`, `BUILD SUCCESSFUL` in 2m01s, 46 actionable tasks / 12 executed. Task-local filters ran `TeacherAttendanceDocxRendererTest` (4 tests), `OfficeDocumentConverterTest` (9 tests), and `DocumentRendererGrpcServiceImplTest` (3 tests); each XML report has zero failures/errors. Command: `gradlew.bat --system-prop=org.gradle.java.compile-classpath-packaging=true --no-problems-report --no-daemon --no-parallel --max-workers=1 --console=plain :services:attendance-service:attendance-app:test --tests '*TeacherAttendanceDocxRendererTest' :services:document-renderer-service:document-renderer-app:test --tests '*OfficeDocumentConverterTest' --tests '*DocumentRendererGrpcServiceImplTest'`. |
| 8-column and weekly visible-text/layout assertions | PASS | Read-only bundled-Python XML pass, exit `0`, over DOCX outputs written by the focused tests: 3 subject tables; 8+4 lesson columns; 2 matrix/summary page breaks; exact weekly bounds and 5 total structural page breaks; missing-key cells empty; denominator-zero percentages `—`; no unresolved placeholders or raw student IDs. Removed only a negative test substring that matched the valid date `14.09.2026`. |
| Populated Word to PDF/PNG render | PASS | Docker exit `0`, prebuilt `rct/document-renderer-service:e2e-local`, `--network none --read-only`, sample input bind mounted read-only and output on a separate TEMP path. Image tools: `/usr/bin/soffice`, `/usr/bin/pdftoppm`; render output at `%LOCALAPPDATA%/Temp/rct-teacher-word-visual-20260924-r1`. Inspected every page in original detail: subject journal 3 pages, weekly batch 6 pages, 30 × 36 journal 14 pages (23 PNGs total). Cyrillic, long names/subject, repeated table headers and rows, marks, summary values, and pagination are visible with no clipped content or missing glyphs. |
| PNG archive completeness and sample payloads | PASS | Bundled Python exit `0`: numeric `page-0001.png`… archive entries validate (`ZipFile.testzip` clean), and entry counts match page renders. 30 × 36 sample sizes: DOCX 54,993 B; PDF 92,990 B; 14-page ZIP 1,740,252 B. Small subject: 38,700 / 46,774 / 132,205 B (3 pages). Weekly batch: 39,641 / 50,912 / 245,075 B (6 pages). |
| gRPC message-size configuration | OBSERVED | Attendance and mobile-BFF `application.yml` explicitly set server inbound limit 25,165,824 B (24 MiB). `DocumentRendererGrpcClient` has no explicit receive-size override; the 1.74 MB sample response is below gRPC Java's ordinary 4 MiB default, but this is not an explicit per-client setting. No renderer-service max inbound override appears in its `application.yml`. |
| Runtime handoff | PASS | HEAVY released to `teacher_export_gpt6` after focused Gradle exit `0` and Docker visual render exit `0`; no additional builds launched. |

## Do not

- Do not add statistics/roster templates or claim their consumers are finished.
- Do not reconstruct selection, authorization, membership, totals, denominators, percentages, or academic week numbers in this renderer.
- Do not include excuse reasons, ticket URLs/IDs, comments, attachment metadata, or raw student/group/subject/lesson IDs in user-facing DOCX content.
- Do not edit the shared contract or other people's work; do not run a broad test campaign, use MAIN as a writer, or use Terra.

## Placeholder field map

The two resources live under `services/attendance-service/attendance-app/src/main/resources/report-templates/`.

| Placeholder | Bound input / behavior |
|---|---|
| `${REPORT_TITLE}` | Renderer-selected Russian title from `Context.kind`. |
| `${GROUP_LABEL}` | `Context.groupLabel`; no group ID fallback. |
| `${WEEK_RANGE}` | `Context.periodFrom`–`periodTo` for one supplied weekly model, formatted `dd.MM.yyyy`; no academic week calculation. |
| `${SUBJECT_LABEL}` | `Context.subjectLabel`; no subject ID fallback. |
| `${SEMESTER_LABEL}` | `Context.semesterLabel`; no semester ID fallback. |
| `${PERIOD_LABEL}` | `Context.periodFrom`–`periodTo`, formatted `dd.MM.yyyy`. |
| `${TYPE_LABELS}` | `Context.typeLabels`, preserved in supplied order and joined for display. |
| `${GENERATED_AT}` | `Context.generatedAt`, rendered in UTC with an explicit `UTC` label. |
| `${MATRIX_TABLE}` | Structural slot replaced with all ordered lessons and student rows. Header fields come from `Column`; each mark is fetched by the exact lesson ID key from `Row.cellsByLessonId`; missing key means blank. |
| `${SUMMARY_TABLE}` | Structural slot replaced with every row and the supplied `Metrics` counts, denominator, and percentages. Percentages are displayed as given; denominator zero makes percentage cells `—`. |

## Diff and limitations

Current scoped product changes (excluding the shared contract dependency commit):

- `TeacherAttendanceDocxRenderer.java` — new subject-journal and weekly DOCX renderer.
- `teacher-attendance-weekly.docx`, `teacher-attendance-subject-journal.docx` — reusable Word shells with bound scalar and structural placeholders.
- `OfficeDocumentConverter.java` and `DocumentRendererGrpcServiceImpl.java` — additive all-page PNG ZIP conversion path; legacy targets remain unchanged.
- `TeacherAttendanceDocxRendererTest.java`, `OfficeDocumentConverterTest.java`, `DocumentRendererGrpcServiceImplTest.java` — focused behavior checks, including a 30 × 36 sample, blank missing/future keys, zero denominators, weekly boundaries, complete numeric ZIP page sequences, and response MIME/extension.

Remaining integration gaps: subject/group statistics, roster/subject-composition layouts and consumers, and weekly API/UI caller integration. The current teacher 122 API consumer is subject-journal; this leaf exposes the weekly renderer for future callers but does not connect one.
