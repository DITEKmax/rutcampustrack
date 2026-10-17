# Гео после ручной отметки «н»

Дата:2026-10-11. Решение владельца Q005 в текущем Cloud-чате.

## D005 — 2026-10-11: запрет гео после ручного «н»
Owner answered Q005: «Сохранить запрет гео после ручного «н»».
Accepted behavior: existing HEADMAN manual ABSENT prevents student geolocation check-in in the live window and the automatic failed-geolocation request path through that blocked attempt; snapshot eligibility reflects the prohibition. Existing accepted past-lesson appeal path, quotas, time windows and other state guards are unchanged.
Supersedes: contradictory manual-ABSENT geo overwrite clause in archived backend-conflicts §3:541–555 for current acceptance.
Affected: StudentCheckinService + StudentAttendanceSnapshotService, current Vue eligibility, tests/manualabsence boundary only.
Canonical: docs/product/decisions/2026-10-11-manual-absence-geo-block.md.
