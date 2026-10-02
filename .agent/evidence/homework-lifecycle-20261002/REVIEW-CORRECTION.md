# Consolidated source review correction

Fresh review of c8d83540+da05202b: FAIL, two P2 defects in immutable create retry/time policy.

1. DTO relative @FutureOrPresent rejected original accepted requests before service replay when original day passed after a move. Removed only annotation/import. @NotNull and mode/number validation preserved. Brand-new create still checks date after durable actor/key replay lookup and validates group/subject/semester/date.
2. Persisted-only ARCHIVED checks allowed create replay against effectively expired DATE before Schedule terminal event. Create replay and success use the shared existing Clock/Moscow HomeworkLifecycle predicate. Identity/intent unchanged; no new local persisted archive state.

Affected boundary extends existing cohesive PostgreSQL method: past-original/current-future retry accepted with same identity/current date; future-original/current-expired DATE conflicts with unchanged rows; genuinely new past-date create fails BadRequest. Whitespace check exit0. Scoped compile/PG runtime pending heavy lease/recheck; no broader suite added.

R3 found a persisted JSON representation defect on first legacy edit: Hibernate JacksonJsonFormatMapper serialized HomeworkCreateIntent.lessonDate as [2201,5,13], while V45 requires the exact SQL pre-edit fingerprint with ISO string "2201-05-13". Synthetic actual Hibernate 6.6.53 mapper probe exit0 confirms this; nulls/enums/other fields agree, receipt precedes flush. Fix is field-local @JsonFormat STRING only; immutable SQL guard unchanged. Existing failing PG method now asserts the actual Hibernate canonical date representation before exercising the real legacy capture/receipt guard. R3 terminal session47230 exit1, four PASS, one failure; docker ps exit0 empty. R4 will run only that failed method after affected independent recheck.
