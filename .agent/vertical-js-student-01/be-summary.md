# Backend handoff — JS-STUDENT-01-r1

Date: 2026-09-06. Risk: S3. Writer: `/root/geo_contract_owner`. Requested route:
Sol high; the running session did not expose model/effort metadata.

The backend implements the server-owned student geo check-in transaction. One Mongo
transaction coordinates attendance, automatic late-check-in request, durable pair
cooldown, immutable idempotency receipt and outbox. It handles exact time boundaries,
PENDING reuse, REJECTED renewal, geo cancellation, and concurrent geo/headman/manual
writes without stale overwrite. Manual HEADMAN absence stays on the separate appeal
path. Successful student geo writes preserve `STUDENT_GEO` with a null actor.

Mobile BFF now serves the frozen Java-first session, Today, semester schedule and
check-in contract. It composes academic, schedule and attendance gRPC data, keeps
eligibility in attendance, validates internal signed identity, rejects role/group
scope violations, and returns stable Problem Details. Gateway routing issues an
internal token and strips client identity headers. The schedule gRPC response supplies
an `updatedAt` source for the authorized schedule ETag.

`CANCELLED/GEO_CONFIRMED` is closed across the event schema, attendance transitions,
notification bot/service and located legacy UI consumers. A late headman action has
no effect and consumers do not display a false approval or rejection.
The attendance Java API now publishes the four filter states and lowercase response
states into its canonical OpenAPI; all three legacy TypeScript clients are regenerated
and their local aliases derive from that output.

Verification passed Mongo replica-set transaction/race tests (17), event schema tests
(4), real signed HTTP→gRPC auth tests (12), gateway tests (6), canonical OpenAPI drift
(3), notification Java tests (41), the full bot suite (230, 77.88% coverage), legacy
consumer tests/build, and all six runtime boot jars. The final isolated runtime
started five infrastructure containers and six real services, tested all public read
endpoints plus mutations through Gateway→BFF→gRPC, ETag/304, scope rejection, broker
outage/recovery, byte-identical replay, exact Rabbit delivery and JWT negatives. See
`be-checks.json` and runtime result `20260906-184531`.

The implementation is verified and ready for the root-owned integration merge. S3
completion still requires the fresh independent Sol high review; tool concurrency
prevented scheduling it before this freeze.
