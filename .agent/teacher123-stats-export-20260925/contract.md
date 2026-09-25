# Teacher 123 statistics export — compact contract

Contract owner: root. Implementer/writer: assigned teacher-export author.
Base revision: `27a7f8008da416d259257052145ddbfd35b1838d`.
Worktree: `.agent/worktrees/headman-assistants-delivery-20260922`, branch
`codex/teacher-export-20260923`. Main rules SHA256:
`F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.
Risk: S3 because the read path crosses teacher authorization and historical group
data; export is read-only.

## 1. Goal

A teacher downloads a summary for the exact statistics query currently shown in
screen 123, in DOCX, PDF, all-page PNG ZIP, HTML, or XLSX.

## 2. Context / evidence

- The owner GO `GO2026-09-25` resumes this preserved candidate.
- The checkpoint `.agent/orchestration-v2/checkpoints/2026-09-24-stop/REPORT.md`
  identifies this as unfinished teacher 123 export work and records that only
  diff hygiene had previously run.
- The older `.agent/teacher-stats-delivery-20260923/contract.md` scoped export
  outside its earlier statistics task. The current root handoff explicitly
  assigns the preserved teacher 123 export WIP, so this candidate completes
  only that assigned scope; it does not change the product contract or claim
  broader teacher-story acceptance.
- Canonical screen contract: `docs/wireframes/teacher/123-teacher-stats.md`.
  It requires the student/group summary scopes, full query filters and sort,
  numerator/denominator, and no percentage for a zero denominator.
- The source of metrics and current active-group authority is
  `ReportService.getTeacherStats`; export must consume that result.
- The focused renderer test's invalid-control fixture reproduced a DOCX-only
  label break: `\u000b` was treated as a line separator before XML sanitization.
  The correction must keep ordinary CR/LF formatting and remove only the
  forbidden character, preserving adjacent label text.

## 3. Relevant scope

Finish the preserved changes in teacher stats UI/client/PCSS, the teacher read
protobuf and Attendance gRPC/export service, the BFF API/client/facade/controller,
and the new focused renderer/service test. Write this contract, check record, and
delivery summary in this task's new `.agent/teacher123-stats-export-20260925/`
directory. Preserve the other work already present in the assigned worktree.

## 4. Required behavior

- Send the screen's semester, summary scope, group/subject when the scope is
  students, lesson types, server sorts, and server filters with the export call.
- Reuse `ReportService.getTeacherStats` for the same query and current
  authorization. Keep dated roster membership, closed/uncancelled lesson
  denominator, canonical metric order, and server results; do not recalculate.
- Render the student layout for one selected group/subject and the group layout
  for the current teacher's authorized groups. Keep the context period, data
  period, type filter, lesson count and generated time visible.
- Serve DOCX, PDF, PNG pages in a ZIP, HTML, and XLSX with matching filename and
  media type. Preserve numerator and denominator; show a dash when denominator
  is zero.

## 5. Constraints

Keep this read-only and within the existing teacher stats surface. Do not expand
teacher mutation, excuse/attachment, or Telegram/TMA transport permissions. Do
not recompute stats in the BFF or renderer. Keep the dirty worktree's foreign
`.agent/orchestration-v2/RULES.md` and `LEAF-PACKET.md` untouched and out of the
candidate commit. No push, deploy, production data, or MAIN integration.

## 6. Existing patterns

The screen and API already use `TeacherStatsQuery` and the signed teacher read
flow. `ReportService.getTeacherStats` is the canonical server aggregation.
`DocumentRendererGrpcClient` converts DOCX to PDF or a PNG pages ZIP. The shared
teacher UI uses the existing Blob/object-URL download pattern and request
revision guards.

## 7. Acceptance criteria

1. The BFF/UI pass the exact current statistics query, and a context change
   cannot download a result for the previous query.
2. Both summary layouts contain server-ordered rows, four canonical metrics,
  query context, counts and dates; a zero denominator is displayed without a
  numeric zero percentage. XML-forbidden controls in labels are removed without
  turning them into line breaks or changing adjacent text.
3. All five server formats return non-empty content with the agreed media type
   and extension; PNG is a ZIP of rendered pages.
4. Attendance and BFF changes compile and the focused export, gRPC and facade
   tests pass. The mobile-core typecheck/lint and scoped diff hygiene pass.
5. The candidate is committed on the assigned branch without the foreign
   RULES/LEAF files. Runtime remains pending until an owner-authorized release
   window; source checks are not runtime acceptance.

## 8. Verification

Run one bounded Gradle batch with the assigned Java compile-classpath flag and
the Attendance export test, teacher Attendance gRPC test, and teacher BFF
facade test selectors. Run mobile-core typecheck/lint for the changed screen and
client, then `git diff --check`. Record each command, exit code, environment,
revision, and evidence path in this task's `checks.json`. Do not run the full
suite. Runtime is not available in this source-only assignment and must be
recorded as pending, not PASS.

## 9. Do not

Do not redesign the stats screen or API, change metric semantics, alter
`ReportService`, add export transport for TMA, repair unrelated compiler/runtime
warnings, rerun accepted teacher 122/weekly checks, or integrate into MAIN.
