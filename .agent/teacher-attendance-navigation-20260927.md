# Teacher attendance/navigation — compact contract and evidence

Base `064faa6742931e5a0698ebc559eae800f8312c7b`; clean inherited worktree was on `codex/teacher-profile-delivery-20260926` at `dc1a6a1f`. New sole-writer branch: `codex/teacher-attendance-navigation-20260927`. Risk S3 (teacher group authorization, historical attendance, period filtering, capability export). The root-approved API delta below is frozen for this implementation.

## 1. Goal

Teacher can open Attendance and Map from the same mobile dock as Today, Stats, and Profile. Attendance uses real group/subject/type/period selections, real historical lessons and student statuses/reasons, and an export for the identical complete context.

## 2. Context / evidence

- Canonical process: `.agent/orchestration-v2/RULES.md` SHA-256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`; canonical `CURRENT.md` read from main, not the stale worktree copy.
- Product sources: `docs/design/mobile/RutCampusTrack_PWA_TMA_UX_Guide.md` §8/T-03/T-05; `docs/design/figma-spec-mobile.md` Parts 24/26; owner decision 2026-09-23 from root packet.
- Baseline inspection: Teacher dock has Today/Stats/Profile; journal is a desktop-style matrix nested from Today and has no period; HTTP journal accepts one `lessonType`; attendance export accepts multiple types but no date range. Journal data already includes concrete lessons, students, status, `excuseReason`, and `ticketId`.
- Whole-group options reuse existing `stats?scope=groups`: BFF builds the complete semester schedule for each current active teacher group; `TeacherStatsSubjectOption` already carries `groupId`, `subjectId`, subject name and lesson types. Do not source options only from Today or personal assignments.
- Existing map client is already bound to the current auth generation in PWA and TMA; pass it to the existing `MapScreen` inside the Teacher owner.

## 3. Relevant scope

- Teacher owner/navigation and journal screens/styles in `frontends/mobile-core`; PWA/TMA `App.vue` map-client passthrough.
- Existing Teacher HTTP contract/client/facade and attendance export protobuf consumers, plus the current `TEACHER_JOURNAL` report-ticket authorization, serialization and download gateway path.
- One `.agent` evidence file: this file. Add no unrelated shared contracts, Academic group-promotion work, configs, lockfiles, or broad documentation.

## 4. Required behavior / API contract

- Teacher roots: Today, Attendance, Stats, Map, Profile. Product Back returns one stack level; TMA host Back follows the same stack; root routes use the current theme and the single shell dock.
- Attendance selections use stable IDs and remain in owner state while navigating roots or refreshing reads. A changed owner/role/filter invalidates old responses and clears old visible data.
- Group, subject, and 1–3 selected lesson types come from server data for active groups. Show lessons as a mobile list and students/reasons as a lesson detail; keep all reads read-only. Existing excuse/ticket detail checks remain authoritative for request details and attachments.
- HTTP `/api/v1/teacher/journal`: repeated `lessonType` values (one value remains a valid singleton); optional `dateFrom` and `dateTo` as an all-or-none pair of ISO dates, inclusive. Both absent means the full selected semester. Reject malformed, partial, reversed, or out-of-semester ranges with 400. Validate group authority and filter group, subject, types, and dates on the server before sort and pagination; retain the existing response model.
- HTTP `/api/v1/teacher/journal/export`: accept the same type and period selectors and export the full matching result, never only the visible page. Carry the selected period through `TeacherAttendanceExportRequest` and through the TMA `TEACHER_JOURNAL` capability ticket, auth, serialization, and gateway download.
- No client-side display-name lookup may replace group/subject IDs. No attendance writes.

## 5. Constraints

- Keep the root-approved single contract delta; source is `TeacherApi`/`TeacherApiModels` plus existing typed client, and `proto/teacher_reads.proto` for internal export. Do not add a parallel DTO or broad RPC.
- Use existing `MapScreen`, `MobileShell`, theme/host adapters, semantic tokens, and mobile list patterns. No giant matrix, fake data, fake buttons, or new visual tokens.
- Do not touch Academic promotion/shared group contracts, the other writer's scope, foreign work, generated files by hand, production data, deployment, push, or main.
- Terra is prohibited at every level. WARN/ERROR alone does not justify code changes; connect it to this request and reproduce first.

## 6. Existing patterns

- `TeacherFeatureOwner` owns the shared PWA/TMA navigation stack; `MobileShell` owns dock and host Back behavior.
- `TeacherApi.stats({scope: 'groups'})` and `TeacherStatsSubjectOption` are the existing whole-group selector source.
- `TeacherJournalScreen` is the current journal owner; `MapScreen` consumes the auth-bound `mapClient` already created by both app shells.
- BFF authorizes active teacher group IDs before attendance reads; the attendance export service authorizes group scope and loads every selected lesson in batches.

## 7. Acceptance criteria

1. All five Teacher destinations are live in one dock, including Map; Today, Stats and Profile remain reachable.
2. Attendance allows group → subject → one-or-more types → inclusive period selection, displays real lesson cards and per-lesson students/status/reasons, and retains selection across root navigation/read refresh.
3. Changing filter or active role cannot flash data from the previous context; historical in-semester lessons remain available to active teachers of the group.
4. Server pagination occurs after context filtering; direct and ticket-based exports use the same filters and cover all matching pages.
5. Invalid/partial/out-of-semester date ranges fail; omitted range resolves to the full semester; no attendance write path is introduced.

## 8. Verification

- Read applicable root/frontend/services/tests AGENTS and `rutcampustrack-design` + `rct-verification` skills. Use scoped mobile typecheck/lint/build and only existing/targeted checks for active-group auth, date filtering before pagination, report-ticket context, and complete export context.
- Record each command, revision, environment, exit code, and evidence below or in the checks section added to this file. Root owns final integrated PWA/TMA runtime acceptance and independent review; do not claim those as leaf PASS.

## 9. Do not

- Do not redesign attendance or navigation beyond the accepted five-root contract; do not shrink the scope to Map or one screen.
- Do not add Academic RPCs or change group-promotion contracts, hide valid group subjects behind personal assignments/Today, change unrelated WARN/ERROR behavior, or expand testing to a full-suite campaign.

## Evidence / checks / runtime / diff

Implementation is on branch `codex/teacher-attendance-navigation-20260927` at base `064faa6742931e5a0698ebc559eae800f8312c7b`. The scoped diff contains the attendance list/detail screen and styles, root navigation/map passthrough, repeated lesson-type and inclusive date-range filters in BFF and export, and matching report-ticket context through auth and gateway.

- Frontend checks from the assigned worktree: `npm run typecheck` (mobile-core, PWA, TMA) exit 0; `npm exec eslint -- --max-warnings=0` over the eight changed frontend source files exit 0 after a scoped `--fix` pass (exit 0).
- Backend targeted Gradle evidence: the accepted Windows workaround was `--system-prop=org.gradle.java.compile-classpath-packaging=true`; BFF and attendance targeted tests passed in the elevated scoped batch. The first batch then found one auth test assertion coupled to Jackson's LocalDate representation. A subsequent targeted Auth + Gateway batch reported `BUILD SUCCESSFUL` (exit 0), log `%TEMP%\teacher-attendance-navigation-gradle-recheck-elevated.log`.
- The Auth test asserts the redeemed typed `LocalDate` period rather than Jackson's wire representation; the final assertion passed in the follow-up correction batch below. Earlier failed attempts were environment/build setup issues (Windows class-directory compilation and sandbox AccessDenied on a shared JAR); their logs are `%TEMP%\teacher-attendance-navigation-gradle.log`, `%TEMP%\teacher-attendance-navigation-gradle-packaging.log`, `%TEMP%\teacher-attendance-navigation-gradle-packaging-nodaemon.log`, and `%TEMP%\teacher-attendance-navigation-gradle-packaging-elevated.log`. No configuration or dependency files were changed.
- `git diff --check` on the final source changes exited 0; Git emitted only the repository's existing LF-to-CRLF notices. The tracked diff is 22 files, 435 insertions and 144 deletions, plus the two new attendance screen/style files and this evidence file.
- Runtime evidence: no PWA/TMA app session was launched in this worktree. Root owns integrated runtime acceptance and independent review; both remain pending. No production or attendance-write operation was run.
- Limits: integrated PWA/TMA runtime remains pending root acceptance. All changes are confined to the approved teacher UI/navigation and journal/export/ticket contract scope; Academic group-promotion work is untouched.

## Independent-review correction

- Finding: a selected subject can have lecture and practice lessons. `TeacherReadFacade` batches those IDs into one journal read, so `JournalScope` must not reject different lesson types after the authorized IDs have been loaded.
- Correction: `JournalScope` now compares group, subject, and semester only. It still validates 1–100 unique positive lesson IDs, and the reader still calls `ReportService.getTeacherLessonAttendance(lessonId, teacherId)` for every lesson. The existing reader test now returns both a lecture and a practice lesson/cell while retaining the cross-group `INVALID_ARGUMENT` case.
- Targeted PowerShell command from the worktree: `./gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.grpc.TeacherAttendanceReadGrpcServiceTest :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.service.ReportDownloadTicketServiceTest --system-prop=org.gradle.java.compile-classpath-packaging=true --no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain` — exit 0, `BUILD SUCCESSFUL` in 59s. Both `:attendance-app:test` and `:auth-app:test` executed; log: `%TEMP%\teacher-attendance-navigation-correction-packaging-elevated.log`.
- Correction diff: only `TeacherAttendanceReadGrpcService.java` and its existing `TeacherAttendanceReadGrpcServiceTest.java`, 33 insertions and 5 deletions. `git diff --check` on this source correction exited 0. BFF was unchanged from its prior PASS; no PWA/TMA runtime was run here.
