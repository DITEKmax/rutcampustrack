# Requests-domain scope and compact contract

## Goal

Implement the attendance student domain for manual `EXCUSE` packages and
manual `LATE_CHECKIN` submissions. The domain owns eligibility, idempotency,
the five-attempt semester budget, owner projections, cancellation, immutable
lesson snapshots, retained attachments, approval invariants and Mongo outbox
writes. Public REST/gRPC/BFF wiring is a later writer scope.

## Context and evidence

- Risk: S3 because this path combines authz, concurrent writes, transactions,
  binary retention and event delivery.
- Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`;
  worktree `codex/student-role-02-requests-domain`.
- Canonical frozen packet: root `.agent/student-role-02/requests-domain-packet.md`.
- Source resolution accepted by root: root `.agent/student-role-02/excuse-source-resolution.md`.
- Existing patterns reused: `PairWriteCoordinator`, Mongo transaction manager,
  attendance repositories, schedule/academic clients and `OutboxStorage`.

## Relevant scope

Own attendance contract enums, excuse/late entities and event schemas, the new
`studentrequest/**` domain package, `MongoConfig` indexes and focused tests in
the attendance app. Preserve all pre-existing work in this worktree.

## Required behavior

- Student reasons are exactly `ILLNESS`, `MEDICAL_EXAMINATION`,
  `COMPETITION_PARTICIPATION`, `FAMILY_CIRCUMSTANCES`, `OTHER`; `OTHER` needs
  a nonblank normalized comment and all comments are capped at 1000 chars.
- Excuse packages are nonempty, unique, current-semester, group-owned and
  atomically validated. Manual late-checkin is one closed/absent lesson and
  charges one of five semester attempts at submit time.
- Receipt uniqueness and canonical payload hashes provide same-key replay and
  mismatch conflict without duplicate files, budget charges or outbox events.
- Pair locks and transaction retries protect overlap, approval/cancel races and
  the `PRESENT` priority rule. Rejection and cancellation never refund budget.
- List/detail/options/cancel/download are owner scoped; snapshots include
  subject name/type and schedule fields.
- Attachments are 0..2 files, each <=10 MiB and total <=20 MiB, validated by
  declared MIME, extension and magic bytes; binary data is in its own Mongo
  collection, retained one UTC calendar year, and absent from tickets/events.

## Constraints

No proto, BFF, frontend, generated contract, lockfile, root config, deploy,
data cleanup, commit, reset or compatibility migration. Legacy transport and
consumer integration remains an explicit handoff gap.

## Existing patterns

Commands validate external schedule/academic snapshots before the local
transaction, then re-read attendance and request state after sorted pair
fences. Mongo indexes and the existing outbox remain the persistence boundary.

## Acceptance criteria

The domain must pass focused authz/regression tests and a real Mongo
replica-set integration suite covering budget concurrency, same-key replay,
atomic rollback, overlap, attachments, expiry, owner projections,
non-refund and `PRESENT` priority. Schema JSON must parse and current
attendance sources must compile.

## Verification

Record exact commands, exit codes, revision, environment, test counts, runtime
logs, scope diff and limitations in `.agent/checks.json`,
`.agent/requests-domain-evidence.md` and `.agent/requests-domain-summary.md`.
Independent Sol review and later API/browser checks remain root work.

## Do not

Do not wire legacy routes, `EventConsumer`, notification-bot or public
transport here. Do not put binary/base64 data into the new ticket/event path,
refund budget, delete retained bytes outside the scheduled task-owned
collection, or claim full product acceptance from domain tests.
