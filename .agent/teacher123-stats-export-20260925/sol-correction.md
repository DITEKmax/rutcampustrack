# Sol finding correction — 2026-09-25

## Scope and finding

Independent review of candidate `cfada7ab52859c024b781d96a430bd3f0b39b50d`
reported MEDIUM: the contract requires a visible generated time, and DOCX had
one, while HTML and XLSX omitted `context.generatedAt` in both scopes. The
existing fixture supplies `2026-05-02T10:00:00Z`, but assertions did not check
that value in HTML/XLSX.

## Correction

- `TeacherStatsTabularRenderer` now renders `02.05.2026 10:00 UTC`-style time
  using UTC explicitly in HTML and XLSX for both scopes.
- XLSX adds the timestamp to context row 9 and moves the frozen header to row
  10, preserving the preceding eight context fields.
- The existing export test asserts the fixture timestamp in DOCX, HTML, and
  XLSX for each scope and expects the updated XLSX header row.
- No stats query, calculations, filters, or authorization changed.

## Evidence and limits

- Source review reproduced the omission: HTML stopped after `Учтено пар`;
  XLSX context rows were 1–8; DOCX already formatted `generatedAt` in UTC.
- `git diff` for the two implementation/test files was inspected; no other
  product files are part of this correction.
- The correction-targeted command
  `gradlew.bat :services:attendance-service:attendance-app:test --tests
  "*TeacherStatsExportServiceTest" --system-prop=org.gradle.java.compile-classpath-packaging=true
  --no-problems-report --no-daemon --no-parallel --max-workers=1 --console=plain`
  completed with exit 0 (`BUILD SUCCESSFUL`, process handle `51965`). JUnit XML
  at
  `services/attendance-service/attendance-app/build/test-results/test/TEST-ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportServiceTest.xml`
  records 1 test, 0 failures, 0 errors, 0 skipped.
- Runtime remains pending; no application was started for this source-only
  correction.
