# F1/F2 correction contract

## Goal
Исправление доказанно отклонённого ONE_OFF запроса без потери неизвестного результата; включительный последний день семестра.
## Context/evidence
Review0df5fe7e: F1/P2 validFrom>selectedDate returns409 before writer.create, frozen intent blocks correction; F2/P2 date==semester.dateTo wrongly blocked. Root expanded exact scope for machine-readable pre-writer refusal; no classification by Russian detail.
## Relevant scope
Original frontend three files; OneOffLessonCoordinator, new OneOffCreateRejectedException, GlobalExceptionHandler, one existing OneOffLessonControllerIT method. Own correction evidence. Baseline0df5fe7e.
## Required behavior
Only coordinator request/remote-authority/semester validation before writer.create gets409 problem type https://api.rutcampustrack.ru/problems/one-off-create-rejected. Status/title/detail compatibility retained. Replay, lifecycle, writer/DB conflicts and403/503 unchanged. First fresh completed typed refusal removes its saved intent and unlocks correction with new UUID. Recovered/prior-unknown/retry/stale context never clears original intent from this type alone. Semester final day is inclusive.
## Constraints
No migration/proto/generated/event/auth changes. No broad discard by409 or timeout. No Russian detail dependency. No children/foreign edits/main/push/deploy.
## Existing patterns
ConflictException subtype plus exact handler problem type; Coordinator prechecks and existing durable replay order retained. Current form/sessionStorage/generation guards retained.
## Acceptance criteria
Known fresh refusal→editable date→new intent; ambiguous/recovered response→original intent. Last semester date accepted, following date rejected. Existing PG HTTP method proves no receipt/origin/outbox on typed refusal, creation on final date, accepted replay conflict retains generic type.
## Verification
Frontend two new regression tests only (prior three skipped), Vue typecheck, scoped lint/diffcheck; root granted one Schedule compileJava/compileTestJava plus existing targeted real PG method. Independent affected review after frozen correction.
## Do not
No old component/full-suite/bus repeats, shared stand/new harness/other lifecycle changes or runtime holder r2 writes (now E-owned).
