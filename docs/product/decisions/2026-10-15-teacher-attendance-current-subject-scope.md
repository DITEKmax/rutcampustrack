# D023 — Q022: Teacher attendance is scoped by current group and subject

Received UTC 2026-10-15T13:05:58.831909+00:00 in the main Cloud chat.

Owner exact answer: «Он видит данные по своим группам по тем предметам где он ведёт (не не видит данные посещаемости по предметам группы где он не ведёт и где он не добавлен как пр5еподаватель)».

Teacher attendance data and summaries are restricted to the teacher's currently authorized groups AND subjects to which that teacher is assigned. Attendance for another subject of an otherwise authorized group must not be returned, aggregated, exported or exposed through drilldown. Apply authoritative current assignment/grant checks on server reads and downloads; client filtering is insufficient. The cross-group summary aggregates only those allowed subjects in each allowed group. Current accepted historical reading within an allowed subject remains; this answer does not impose physical lesson.teacherId equality or grant access to unrelated subjects. Retain existing explicit lesson-type filters and current revocation/semester/history guards; do not invent a new per-type permission from this answer.

Supersedes the Q022 unresolved cross-group measure and any earlier whole-group reading interpretation insofar as it exposes attendance of unassigned subjects. Review existing selected-group reads and exports for the same boundary. No schema migration, new web frontend, Angular change or production operation is authorized. Accepted existing all-own-groups summary may now be implemented with this measure and compatible contracts; meaningful own/other-subject/current-revocation checks and independent review remain required.
