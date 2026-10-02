# Exact probe URI static check

- POST /api/schedule/one-off-lessons → schedule contract OneOffLessonApi.java:43 (Idempotency-Key, create DTO).
- GET /api/schedule/groups/{group}/lessons → LessonApi.java:137; PLANNED/ACTIVE/CLOSED/CANCELLED/TRANSFERRED supported enum, exact physical ID selected; date bounds and occurrenceRevision enriched in LessonController.getLessons. Replaces three invented single-lesson GETs; no such REST endpoint exists.
- POST /api/schedule/lessons/{physical}/transfer → LessonApi.java:71; exact requestKey/expectedRevision; PENDING202 /COMPLETED200.
- GET /api/schedule/lesson-transfers/{operation} → LessonApi.java:78.
- POST /api/academic/homeworks; GET /api/academic/homeworks/{id} → HomeworkApi.java:47/56.
- PUT /api/v1/student/homework/{id}/completion; GET /api/v1/student/homework?from&to → existing accepted DATE probe, current StudentApi.java:250/288 contract checked.
- GET /api/attendance/reports/lesson/{physical} → ReportApi.java:83; current response has lessonDate/editable/entries/source.
- GET /api/schedule/one-off-lessons?groupId&dateFrom&dateTo → OneOffLessonApi.java:50, native current physical date BETWEEN inclusive bounds.

No other HTTP calls in probe except existing canonical login helper. No product/API edits. Read-only SQL names confirmed in current entities/migrations; event receipt Mongo fields from current receipt/snapshot documents. Node syntax and PowerShell parse after correction PASS.
