# R2: actual bus applied; historical current flag incorrect

Run20261002-173556877-_mcwtoni, product24e92335/manifest82699f. Probe83409 exit1; runner7729 exit1/cleanupPASS14of14/network/keys/artifacts. Independent chunk5fe8b3 exit0, owned Docker containers/network absent, Java0.

Fresh physical assertion expected original source current=false, target current=true, same occurrence, generation1 to2, target date2026-10-05, source TRANSFERRED. Both group-list reads returned200. R2 raw omitted individual HTTP bodies, so their actual individual fields cannot retrospectively be reconstructed as runtime evidence. Read-only stores show correct physical generations/dates/statuses/occurrence and origin pointer13.

Confirmed source defect: Lesson.java transient current defaults true; getLessonsForGroup maps native loaded entities without filling it; LessonAssembler passes isCurrent into LessonResponse. Group-list query includes retained TRANSFERRED sources. Historical source current is thus deterministically true by this code path, while expected false. Root personally opened and accepted S2 defect. No raw evidence was rewritten to invent missing bodies.

Correction1efa2b08: bounded page-wide canonical occurrence.current_lesson_id comparison; fill transient flags. Entity page and pointer projection share read-only REPEATABLE_READ. No status-derived flag, migration, history exclusion, advisory locks, or mutation. Legacy null-occurrence rows preserve prior current=true; missing occurrence fails closed false. One new existing realPG/API method verifies oldONEOFFfalse/newtrue/sameoccurrence/gen+1/exactDBpointer. Review/runtime pending.

Accepted separately from failed HTTP flag: actual Rabbit delivered v2 to real Academic/Attendance, operation completed and exact retry200; Academic APPLIED receipt/MOVED history once; unchanged homework/binding/publisher/requestKey and completion ID/timestamp; immutable Schedule origin/binding intent, two physical generations with currentpointer13; both real participant ACKs APPLIED; real Attendance v2 APPLIED receipt, zero fabricated future marks. r2-accepted-stores.json is an offline exact comparison of captured api-r2 stores, exit0, not new runtime.

Fresh Homework GET/BFFfeed/Attendance report/publiconeofflist were not reached. Remaining original DTO checks: numeric LessonResponse generation/uppercase LessonStatus; HomeworkResponse stableIDs/UUID/date/number; StudentApiModels.HomeworkResponse.items completed/lessonDate/completedAt; LessonAttendanceResponse exactstudententry/source; oneoff currentphysicaldate. Probe now saves safe source/target/history bodies and latest operation before checks; exactstudententry required.

No third blind wholebus run/build/componentPASS replay. Root chooses minimal remaining acceptance after source review. Future manualmarks prohibited; nonempty marks remain separately accepted Mongo component proof.
