# BE lane packet — JS-STUDENT-01-r1

Base: `87784165874e2da6fc261abc1c01584e24624289` plus the exact allowlisted overlay
and frozen contract hash. Contract owner integrates and alone changes shared
contracts, proto, generated artifacts/config/locks and global state.

Relevant domain scope: attendance check-in, automatic late-checkin request,
repositories/entities/indexes, Mongo transaction/outbox, write provenance and the
manual headman marking path needed for the shared pair CAS. No past appeal UI/domain
expansion.

Implement r1 server rules: inclusive start-5/end+5 window, durable 300s pair
cooldown, idempotency receipt, one partial-unique PENDING, reuse PENDING request on
failed retry, new request after REJECTED, `CANCELLED/GEO_CONFIRMED` on successful
retry, manual HEADMAN ABSENT block, injected Clock, preserved attendance metadata,
real actor provenance and single transaction including outbox. New attendance RPC
validates signed identity; no trusted client headers.

The snapshot RPC returns one entry for every requested lesson, including an entry
with `ATTENDANCE_STATUS_UNSPECIFIED` when no mark exists. Attendance owns and fills
`eligibility` plus authoritative `server_now`; the BFF only composes these values.
Reject the whole batch for unknown or foreign lesson IDs. Treat every `UNSPECIFIED`
enum from a command as invalid and require coordinate presence (`hasLatitude` and
`hasLongitude`) plus finite/range checks. Pack `StudentCheckinErrorDetail` into gRPC
status details so the BFF maps stable code and `retry_at` to Problem Details without
parsing status text.

Return `ALREADY_PRESENT` eligibility for an existing PRESENT mark. A new idempotency
key then returns a mutation-free 200 PRESENT ACK with current attendance; replay of
the original key still returns its original ACK. Return `LESSON_CANCELLED` for a
cancelled lesson that schedule includes, and reject POST with 409
`CHECKIN_NOT_ELIGIBLE`.

Required tests: boundary times, repeated and mixed idempotency keys, concurrent
geo/decision/manual marking, rollback/outbox redelivery, stale decision, foreign
scope/spoofed or expired identity, headman self-path, consumer handling for CANCELLED,
and actual Mongo replica-set integration. Do not edit BFF public DTOs or generated
artifacts directly; request revision through contract owner.
